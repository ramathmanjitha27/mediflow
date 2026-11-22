package com.mediflow.api.service;

import static com.mediflow.api.repository.AppointmentRepository.NO_ID;

import com.mediflow.api.dto.AppointmentRequest;
import com.mediflow.api.dto.AppointmentResponse;
import com.mediflow.api.dto.CancelAppointmentRequest;
import com.mediflow.api.entity.Appointment;
import com.mediflow.api.entity.AppointmentStatus;
import com.mediflow.api.entity.Doctor;
import com.mediflow.api.entity.Patient;
import com.mediflow.api.exception.ConflictException;
import com.mediflow.api.exception.ConstraintMessages;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.exception.UnprocessableEntityException;
import com.mediflow.api.repository.AppointmentRepository;
import com.mediflow.api.repository.DoctorRepository;
import com.mediflow.api.repository.PatientRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Booking rules. Overlaps are checked here for a clear 409, and enforced again by exclusion
 * constraints in the database (V2) so concurrent requests cannot both succeed. Writes use
 * {@code saveAndFlush} so a constraint violation surfaces inside this call.
 *
 * <p>Reschedule, cancel and fulfill lock the appointment row first, so concurrent changes are
 * serialized and the terminal states really are terminal.
 *
 * <p>Appointments are never deleted: they are part of the clinical record. Only {@code booked}
 * appointments can be changed, cancelled or fulfilled; {@code cancelled} and {@code fulfilled}
 * are terminal, so re-booking means creating a new appointment.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    public List<AppointmentResponse> search(Long doctorId, Long patientId, AppointmentStatus status) {
        return appointmentRepository.search(doctorId, patientId, status).stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    public AppointmentResponse findById(Long id) {
        return AppointmentResponse.from(getAppointment(id));
    }

    @Transactional
    public AppointmentResponse book(AppointmentRequest request) {
        Appointment appointment = new Appointment();
        appointment.setStatus(AppointmentStatus.BOOKED);
        applySchedule(appointment, request, NO_ID);
        return AppointmentResponse.from(appointmentRepository.saveAndFlush(appointment));
    }

    @Transactional
    public AppointmentResponse reschedule(Long id, AppointmentRequest request) {
        Appointment appointment = getAppointmentForUpdate(id);
        requireBooked(appointment, "changed");
        if (!appointment.getPatient().getId().equals(request.patientId())) {
            // Moving an appointment (and its reason) to another patient's record is not a reschedule.
            throw new UnprocessableEntityException("patientId cannot be changed; cancel and book a new appointment");
        }
        applySchedule(appointment, request, id);
        return AppointmentResponse.from(appointmentRepository.saveAndFlush(appointment));
    }

    @Transactional
    public AppointmentResponse cancel(Long id, CancelAppointmentRequest request) {
        Appointment appointment = getAppointmentForUpdate(id);
        requireBooked(appointment, "cancelled");
        appointment.setStatus(AppointmentStatus.CANCELLED);
        String reason = request == null ? null : request.cancellationReason();
        appointment.setCancellationReason(StringUtils.hasText(reason) ? reason.trim() : null);
        return AppointmentResponse.from(appointmentRepository.saveAndFlush(appointment));
    }

    @Transactional
    public AppointmentResponse fulfill(Long id) {
        Appointment appointment = getAppointmentForUpdate(id);
        requireBooked(appointment, "fulfilled");
        if (appointment.getStartTime().isAfter(Instant.now())) {
            throw new ConflictException("An appointment cannot be fulfilled before it starts");
        }
        appointment.setStatus(AppointmentStatus.FULFILLED);
        return AppointmentResponse.from(appointmentRepository.saveAndFlush(appointment));
    }

    private void applySchedule(Appointment appointment, AppointmentRequest request, Long excludeId) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new UnprocessableEntityException("Patient with id " + request.patientId() + " does not exist"));
        Doctor doctor = doctorRepository.findById(request.doctorId())
                .orElseThrow(() -> new UnprocessableEntityException("Doctor with id " + request.doctorId() + " does not exist"));
        if (!doctor.isActive()) {
            throw new UnprocessableEntityException("Doctor with id " + doctor.getId() + " is not active");
        }
        if (appointmentRepository.existsDoctorOverlap(doctor.getId(), request.start(), request.end(), excludeId)) {
            throw new ConflictException(ConstraintMessages.DOCTOR_OVERLAP);
        }
        if (appointmentRepository.existsPatientOverlap(patient.getId(), request.start(), request.end(), excludeId)) {
            throw new ConflictException(ConstraintMessages.PATIENT_OVERLAP);
        }
        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setStartTime(request.start());
        appointment.setEndTime(request.end());
        appointment.setReason(StringUtils.hasText(request.reason()) ? request.reason().trim() : null);
    }

    private static void requireBooked(Appointment appointment, String action) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ConflictException("Only booked appointments can be %s; this one is %s"
                    .formatted(action, appointment.getStatus().getCode()));
        }
    }

    private Appointment getAppointment(Long id) {
        return appointmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }

    /** Locks the row so concurrent reschedule/cancel/fulfill calls cannot overwrite each other. */
    private Appointment getAppointmentForUpdate(Long id) {
        return appointmentRepository.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
    }
}
