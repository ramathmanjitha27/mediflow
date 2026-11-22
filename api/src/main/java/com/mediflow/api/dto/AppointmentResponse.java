package com.mediflow.api.dto;

import com.mediflow.api.entity.Appointment;
import com.mediflow.api.entity.AppointmentStatus;
import java.time.Instant;

public record AppointmentResponse(
        Long id,
        Long patientId,
        Long doctorId,
        Instant start,
        Instant end,
        AppointmentStatus status,
        String reason,
        String cancellationReason,
        Instant createdAt,
        Instant updatedAt) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getDoctor().getId(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus(),
                appointment.getReason(),
                appointment.getCancellationReason(),
                appointment.getCreatedAt(),
                appointment.getUpdatedAt());
    }
}
