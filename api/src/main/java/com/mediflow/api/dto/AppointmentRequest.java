package com.mediflow.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.time.Instant;

/**
 * Body for booking (POST) and rescheduling (PUT) an appointment. Times are ISO-8601 instants
 * with an explicit offset, e.g. {@code 2026-12-01T10:00:00Z} or {@code 2026-12-01T15:30:00+05:30}.
 */
public record AppointmentRequest(
        @NotNull Long patientId,
        @NotNull Long doctorId,
        @NotNull @Future Instant start,
        @NotNull Instant end,
        @Size(max = 500) String reason) {

    public static final Duration MAX_DURATION = Duration.ofHours(8);

    @AssertTrue(message = "end must be after start")
    public boolean isEndAfterStart() {
        return start == null || end == null || end.isAfter(start);
    }

    @AssertTrue(message = "duration must not exceed 8 hours")
    public boolean isWithinMaxDuration() {
        return start == null || end == null || !end.isAfter(start.plus(MAX_DURATION));
    }
}
