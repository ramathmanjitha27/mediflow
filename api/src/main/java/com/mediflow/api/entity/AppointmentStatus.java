package com.mediflow.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/**
 * Subset of FHIR AppointmentStatus. Serialized as its lowercase FHIR code in JSON, query
 * parameters and the database. {@code booked} is the only non-terminal state.
 */
public enum AppointmentStatus {
    BOOKED("booked"),
    CANCELLED("cancelled"),
    FULFILLED("fulfilled");

    private final String code;

    AppointmentStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static AppointmentStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(s -> s.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown appointment status: " + code));
    }
}
