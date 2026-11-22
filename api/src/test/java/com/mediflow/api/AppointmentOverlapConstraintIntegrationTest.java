package com.mediflow.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mediflow.api.entity.Doctor;
import com.mediflow.api.entity.Gender;
import com.mediflow.api.entity.Patient;
import com.mediflow.api.exception.ConstraintMessages;
import com.mediflow.api.repository.AppointmentRepository;
import com.mediflow.api.repository.DoctorRepository;
import com.mediflow.api.repository.PatientRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulates the race where two requests both pass the service-level overlap check: the check is
 * stubbed to report "no overlap", so only the V2 exclusion constraints stand in the way. The API
 * must still answer 409 with the same message as the service check. Each test ends with the
 * failing request, because Postgres aborts the transaction afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentOverlapConstraintIntegrationTest {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final Instant START = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

    @Autowired
    MockMvc mvc;

    @MockitoSpyBean
    AppointmentRepository appointmentRepository;

    @Autowired
    PatientRepository patients;

    @Autowired
    DoctorRepository doctors;

    long patient1;
    long patient2;
    long doctor1;
    long doctor2;

    @BeforeEach
    void setUp() {
        patient1 = patient();
        patient2 = patient();
        doctor1 = doctor();
        doctor2 = doctor();
    }

    @Test
    void racedDoubleBookingOfADoctorReturns409() throws Exception {
        book(patient1, doctor1, 0, 30).andExpect(status().isCreated());
        willReturn(false).given(appointmentRepository).existsDoctorOverlap(any(), any(), any(), any());

        book(patient2, doctor1, 15, 45)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ConstraintMessages.DOCTOR_OVERLAP));
    }

    @Test
    void racedDoubleBookingOfAPatientReturns409() throws Exception {
        book(patient1, doctor1, 0, 30).andExpect(status().isCreated());
        willReturn(false).given(appointmentRepository).existsPatientOverlap(any(), any(), any(), any());

        book(patient1, doctor2, 15, 45)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ConstraintMessages.PATIENT_OVERLAP));
    }

    @Test
    void racedRescheduleIntoABookedSlotReturns409() throws Exception {
        book(patient1, doctor1, 0, 30).andExpect(status().isCreated());
        String json = book(patient2, doctor1, 60, 90).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long second = ((Number) JsonPath.read(json, "$.id")).longValue();
        willReturn(false).given(appointmentRepository).existsDoctorOverlap(any(), any(), any(), any());

        mvc.perform(put("/api/appointments/{id}", second).contentType(MediaType.APPLICATION_JSON)
                        .content(body(patient2, doctor1, 15, 45)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ConstraintMessages.DOCTOR_OVERLAP));
    }

    private ResultActions book(long patientId, long doctorId, int fromMinute, int toMinute) throws Exception {
        return mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                .content(body(patientId, doctorId, fromMinute, toMinute)));
    }

    private static String body(long patientId, long doctorId, int fromMinute, int toMinute) {
        return """
                {"patientId": %d, "doctorId": %d, "start": "%s", "end": "%s"}
                """.formatted(patientId, doctorId,
                START.plus(fromMinute, ChronoUnit.MINUTES), START.plus(toMinute, ChronoUnit.MINUTES));
    }

    private long patient() {
        Patient p = new Patient();
        p.setGivenName("Race");
        p.setFamilyName("Patient" + SEQ.incrementAndGet());
        p.setBirthDate(LocalDate.of(1985, 5, 5));
        p.setGender(Gender.UNKNOWN);
        return patients.saveAndFlush(p).getId();
    }

    private long doctor() {
        Doctor d = new Doctor();
        d.setGivenName("Race");
        d.setFamilyName("Doctor");
        d.setSpecialty("General Practice");
        d.setEmail("race" + SEQ.incrementAndGet() + ".it@example.com");
        return doctors.saveAndFlush(d).getId();
    }
}
