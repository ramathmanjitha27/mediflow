package com.mediflow.api.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** FHIR AdministrativeGender. Serialized as its lowercase FHIR code in JSON and the database. */
public enum Gender {
    MALE("male"),
    FEMALE("female"),
    OTHER("other"),
    UNKNOWN("unknown");

    private final String code;

    Gender(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static Gender fromCode(String code) {
        return Arrays.stream(values())
                .filter(g -> g.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown gender code: " + code));
    }
}
