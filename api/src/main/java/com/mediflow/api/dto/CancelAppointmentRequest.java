package com.mediflow.api.dto;

import jakarta.validation.constraints.Size;

/** Optional body for cancelling an appointment (FHIR Appointment.cancellationReason). */
public record CancelAppointmentRequest(@Size(max = 500) String cancellationReason) {
}
