package com.mediflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.mediflow.api.dto.AppointmentRequest;
import com.mediflow.api.entity.AppointmentStatus;
import com.mediflow.api.entity.Doctor;
import com.mediflow.api.entity.Gender;
import com.mediflow.api.entity.Patient;
import com.mediflow.api.exception.ConflictException;
import com.mediflow.api.repository.AppointmentRepository;
import com.mediflow.api.repository.DoctorRepository;
import com.mediflow.api.repository.PatientRepository;
import com.mediflow.api.service.AppointmentService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two real, concurrent transactions (so this class is not @Transactional and cleans up after
 * itself). Proves that status changes lock the appointment row, so a terminal state cannot be
 * overwritten by a request that read the row before it changed.
 */
@SpringBootTest
class AppointmentConcurrencyIntegrationTest {

    @Autowired
    AppointmentService appointmentService;

    @Autowired
    AppointmentRepository appointmentRepository;

    @Autowired
    PatientRepository patients;

    @Autowired
    DoctorRepository doctors;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    long patientId;
    long doctorId;
    long appointmentId;

    @BeforeEach
    void setUp() {
        Patient patient = new Patient();
        patient.setGivenName("Lock");
        patient.setFamilyName("Patient");
        patient.setBirthDate(LocalDate.of(1980, 3, 3));
        patient.setGender(Gender.UNKNOWN);
        patientId = patients.save(patient).getId();

        Doctor doctor = new Doctor();
        doctor.setGivenName("Lock");
        doctor.setFamilyName("Doctor");
        doctor.setSpecialty("General Practice");
        doctor.setEmail("lock.concurrency.it@example.com");
        doctorId = doctors.save(doctor).getId();

        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        appointmentId = appointmentService.book(
                new AppointmentRequest(patientId, doctorId, start, start.plus(30, ChronoUnit.MINUTES), null)).id();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM appointment WHERE id = ?", appointmentId);
        jdbc.update("DELETE FROM patient WHERE id = ?", patientId);
        jdbc.update("DELETE FROM doctor WHERE id = ?", doctorId);
    }

    @Test
    void concurrentCancelWaitsForTheLockAndThenSeesTheTerminalState() throws Exception {
        CompletableFuture<?>[] concurrentCancel = new CompletableFuture<?>[1];

        transactionTemplate.executeWithoutResult(tx -> {
            // Transaction 1 locks the row and cancels the appointment...
            var appointment = appointmentRepository.findByIdForUpdate(appointmentId).orElseThrow();
            appointment.setStatus(AppointmentStatus.CANCELLED);

            // ...while transaction 2 tries to cancel it too.
            concurrentCancel[0] = CompletableFuture.runAsync(() -> appointmentService.cancel(appointmentId, null));
            sleep(Duration.ofMillis(500));
            assertThat(concurrentCancel[0]).as("second cancel must block on the row lock").isNotDone();
        }); // commit releases the lock

        assertThat(concurrentCancel[0])
                .failsWithin(10, TimeUnit.SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(ConflictException.class);
        assertThat(appointmentService.findById(appointmentId).status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
