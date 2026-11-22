package com.mediflow.api.repository;

import com.mediflow.api.entity.Appointment;
import com.mediflow.api.entity.AppointmentStatus;
import java.time.Instant;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /** Sentinel for {@code excludeId} when there is no appointment to exclude (i.e. on create). */
    long NO_ID = -1L;

    /**
     * Loads the appointment with a row lock (SELECT ... FOR UPDATE) for the rest of the transaction,
     * so concurrent status changes run one after another and each sees the other's result.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(Long id);

    /** True if the doctor has a booked appointment overlapping [start, end), other than {@code excludeId}. */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.doctor.id = :doctorId
              and a.status = com.mediflow.api.entity.AppointmentStatus.BOOKED
              and a.startTime < :end and a.endTime > :start
              and a.id <> :excludeId
            """)
    boolean existsDoctorOverlap(Long doctorId, Instant start, Instant end, Long excludeId);

    /** True if the patient has a booked appointment overlapping [start, end), other than {@code excludeId}. */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.patient.id = :patientId
              and a.status = com.mediflow.api.entity.AppointmentStatus.BOOKED
              and a.startTime < :end and a.endTime > :start
              and a.id <> :excludeId
            """)
    boolean existsPatientOverlap(Long patientId, Instant start, Instant end, Long excludeId);

    /** All filters are optional; null means "any". */
    @Query("""
            select a from Appointment a
            where (:doctorId is null or a.doctor.id = :doctorId)
              and (:patientId is null or a.patient.id = :patientId)
              and (:status is null or a.status = :status)
            order by a.startTime, a.id
            """)
    List<Appointment> search(Long doctorId, Long patientId, AppointmentStatus status);
}
