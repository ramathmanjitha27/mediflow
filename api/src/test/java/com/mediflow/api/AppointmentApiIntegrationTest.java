package com.mediflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import com.mediflow.api.entity.Doctor;
import com.mediflow.api.entity.Gender;
import com.mediflow.api.entity.Patient;
import com.mediflow.api.exception.ConstraintMessages;
import com.mediflow.api.repository.DoctorRepository;
import com.mediflow.api.repository.PatientRepository;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Booking rules end to end against the local Postgres, including the V2 exclusion constraints.
 * Each test is rolled back. A test that triggers a database error does so as its last step,
 * because Postgres aborts the transaction afterwards.
 *
 * <p>Base slot: doctor A, tomorrow 10:00-10:30 UTC.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentApiIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();

    /** Tomorrow at 00:00 UTC, so every slot below is in the future. */
    private static final Instant DAY = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);

    @Autowired
    MockMvc mvc;

    @Autowired
    PatientRepository patients;

    @Autowired
    DoctorRepository doctors;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManager entityManager;

    long patient;
    long otherPatient;
    long doctorA;
    long doctorB;

    private static Instant at(String hhmm) {
        String[] parts = hhmm.split(":");
        return DAY.plus(Integer.parseInt(parts[0]), ChronoUnit.HOURS).plus(Integer.parseInt(parts[1]), ChronoUnit.MINUTES);
    }

    private long newPatient() {
        Patient p = new Patient();
        p.setGivenName("Test");
        p.setFamilyName("Patient" + SEQ.incrementAndGet());
        p.setBirthDate(LocalDate.of(1990, 1, 1));
        p.setGender(Gender.UNKNOWN);
        return patients.saveAndFlush(p).getId();
    }

    private long newDoctor(boolean active) {
        Doctor d = new Doctor();
        d.setGivenName("Test");
        d.setFamilyName("Doctor");
        d.setSpecialty("General Practice");
        d.setEmail("doctor" + SEQ.incrementAndGet() + ".it@example.com");
        d.setActive(active);
        return doctors.saveAndFlush(d).getId();
    }

    /**
     * The whole test shares one persistence context, unlike real requests. Call this to start the
     * next request with a fresh one, e.g. after changing rows with plain SQL.
     */
    private void newRequestContext() {
        entityManager.flush();
        entityManager.clear();
    }

    @BeforeEach
    void setUp() {
        patient = newPatient();
        otherPatient = newPatient();
        doctorA = newDoctor(true);
        doctorB = newDoctor(true);
    }

    private static String body(long patientId, long doctorId, String from, String to) {
        return """
                {"patientId": %d, "doctorId": %d, "start": "%s", "end": "%s"}
                """.formatted(patientId, doctorId, at(from), at(to));
    }

    private ResultActions book(long patientId, long doctorId, String from, String to) throws Exception {
        return mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                .content(body(patientId, doctorId, from, to)));
    }

    private long bookOk(long patientId, long doctorId, String from, String to) throws Exception {
        String json = book(patientId, doctorId, from, to)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    @Nested
    class DoubleBooking {

        @BeforeEach
        void bookBaseSlot() throws Exception {
            bookOk(patient, doctorA, "10:00", "10:30");
        }

        @Test
        void sameSlotSameDoctorIsRejected() throws Exception {
            book(otherPatient, doctorA, "10:00", "10:30")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(ConstraintMessages.DOCTOR_OVERLAP));
        }

        @Test
        void partialOverlapAtStartIsRejected() throws Exception {
            book(otherPatient, doctorA, "09:45", "10:15").andExpect(status().isConflict());
        }

        @Test
        void partialOverlapAtEndIsRejected() throws Exception {
            book(otherPatient, doctorA, "10:15", "10:45").andExpect(status().isConflict());
        }

        @Test
        void enclosingSlotIsRejected() throws Exception {
            book(otherPatient, doctorA, "09:00", "11:00").andExpect(status().isConflict());
        }

        @Test
        void enclosedSlotIsRejected() throws Exception {
            book(otherPatient, doctorA, "10:10", "10:20").andExpect(status().isConflict());
        }

        @Test
        void backToBackSlotsAreAllowed() throws Exception {
            bookOk(otherPatient, doctorA, "10:30", "11:00");
            bookOk(otherPatient, doctorA, "09:30", "10:00");
        }

        @Test
        void differentDoctorSameSlotIsAllowed() throws Exception {
            bookOk(otherPatient, doctorB, "10:00", "10:30");
        }

        @Test
        void samePatientWithAnotherDoctorAtTheSameTimeIsRejected() throws Exception {
            book(patient, doctorB, "10:15", "10:45")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(ConstraintMessages.PATIENT_OVERLAP));
        }
    }

    @Test
    void cancelledAppointmentFreesTheSlotAndIsTerminal() throws Exception {
        long id = bookOk(patient, doctorA, "10:00", "10:30");

        mvc.perform(post("/api/appointments/{id}/cancel", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\": \"Patient request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.cancellationReason").value("Patient request"));

        bookOk(otherPatient, doctorA, "10:00", "10:30");

        mvc.perform(post("/api/appointments/{id}/cancel", id)).andExpect(status().isConflict());
        mvc.perform(put("/api/appointments/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(patient, doctorA, "12:00", "12:30")))
                .andExpect(status().isConflict());
    }

    @Test
    void fulfilledAppointmentDoesNotBlockTheSlot() throws Exception {
        long id = bookOk(patient, doctorA, "10:00", "10:30");
        jdbc.update("UPDATE appointment SET status = 'fulfilled' WHERE id = ?", id);
        newRequestContext();

        bookOk(otherPatient, doctorA, "10:00", "10:30");
    }

    @Test
    void fulfillOnlyAfterTheAppointmentHasStarted() throws Exception {
        long id = bookOk(patient, doctorA, "10:00", "10:30");

        mvc.perform(post("/api/appointments/{id}/fulfill", id)).andExpect(status().isConflict());

        Instant yesterday = Instant.now().minus(1, ChronoUnit.DAYS);
        jdbc.update("UPDATE appointment SET start_time = ?, end_time = ? WHERE id = ?",
                Timestamp.from(yesterday), Timestamp.from(yesterday.plus(30, ChronoUnit.MINUTES)), id);
        newRequestContext();

        mvc.perform(post("/api/appointments/{id}/fulfill", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("fulfilled"));
        mvc.perform(post("/api/appointments/{id}/fulfill", id)).andExpect(status().isConflict());
    }

    @Test
    void rescheduleChecksOverlapButIgnoresItself() throws Exception {
        bookOk(patient, doctorA, "10:00", "10:30");
        long other = bookOk(otherPatient, doctorA, "11:00", "11:30");

        // Moving within its own slot is fine
        mvc.perform(put("/api/appointments/{id}", other).contentType(MediaType.APPLICATION_JSON)
                        .content(body(otherPatient, doctorA, "11:00", "11:20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.end").value(at("11:20").toString()));

        // Moving into the base slot is not
        mvc.perform(put("/api/appointments/{id}", other).contentType(MediaType.APPLICATION_JSON)
                        .content(body(otherPatient, doctorA, "10:15", "10:45")))
                .andExpect(status().isConflict());
    }

    @Test
    void rescheduleCannotMoveTheAppointmentToAnotherPatient() throws Exception {
        long id = bookOk(patient, doctorA, "10:00", "10:30");

        mvc.perform(put("/api/appointments/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(otherPatient, doctorA, "10:00", "10:30")))
                .andExpect(status().isUnprocessableContent());

        // Changing the doctor is a legitimate reschedule
        mvc.perform(put("/api/appointments/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(body(patient, doctorB, "10:00", "10:30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctorId").value(doctorB));
    }

    @Test
    void unknownOrInactiveReferencesReturn422() throws Exception {
        book(patient, 999_999, "10:00", "10:30").andExpect(status().isUnprocessableContent());
        book(999_999, doctorA, "10:00", "10:30").andExpect(status().isUnprocessableContent());
        book(patient, newDoctor(false), "10:00", "10:30")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.endsWith("is not active")));
    }

    @Test
    void unknownAppointmentReturns404() throws Exception {
        mvc.perform(get("/api/appointments/999999")).andExpect(status().isNotFound());
        mvc.perform(post("/api/appointments/999999/cancel")).andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByDoctorPatientAndStatus() throws Exception {
        bookOk(patient, doctorA, "10:00", "10:30");
        long cancelled = bookOk(otherPatient, doctorA, "11:00", "11:30");
        bookOk(otherPatient, doctorB, "12:00", "12:30");
        mvc.perform(post("/api/appointments/{id}/cancel", cancelled)).andExpect(status().isOk());

        mvc.perform(get("/api/appointments").param("doctorId", String.valueOf(doctorA)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].start").value(at("10:00").toString()));
        mvc.perform(get("/api/appointments").param("patientId", String.valueOf(otherPatient)))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/appointments")
                        .param("doctorId", String.valueOf(doctorA)).param("status", "booked"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void deletingAPatientWithAppointmentsReturns409() throws Exception {
        bookOk(patient, doctorA, "10:00", "10:30");
        newRequestContext();

        mvc.perform(delete("/api/patients/{id}", patient))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The patient has appointments and cannot be deleted"));
    }

    @Test
    void databaseRejectsOverlappingBookingEvenWithoutTheServiceCheck() throws Exception {
        bookOk(patient, doctorA, "10:00", "10:30");

        // A concurrent request that slipped past the service check would hit the exclusion constraint.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO appointment (patient_id, doctor_id, start_time, end_time, status) VALUES (?, ?, ?, ?, 'booked')",
                otherPatient, doctorA, Timestamp.from(at("10:15")), Timestamp.from(at("10:45"))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(ex -> {
                    SQLException sql = (SQLException) ((DataIntegrityViolationException) ex).getMostSpecificCause();
                    assertThat(sql.getSQLState()).isEqualTo("23P01");
                    assertThat(sql.getMessage()).contains("appointment_doctor_no_overlap");
                });
    }
}
