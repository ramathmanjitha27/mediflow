package com.mediflow.api.dto;

import com.mediflow.api.entity.Gender;
import com.mediflow.api.entity.Patient;
import java.time.Instant;
import java.time.LocalDate;

public record PatientResponse(
        Long id,
        String givenName,
        String familyName,
        LocalDate birthDate,
        Gender gender,
        String email,
        String phone,
        Instant createdAt,
        Instant updatedAt) {

    public static PatientResponse from(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getGivenName(),
                patient.getFamilyName(),
                patient.getBirthDate(),
                patient.getGender(),
                patient.getEmail(),
                patient.getPhone(),
                patient.getCreatedAt(),
                patient.getUpdatedAt());
    }
}
