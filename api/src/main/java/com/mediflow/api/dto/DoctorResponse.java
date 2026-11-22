package com.mediflow.api.dto;

import com.mediflow.api.entity.Doctor;
import java.time.Instant;

public record DoctorResponse(
        Long id,
        String givenName,
        String familyName,
        String specialty,
        String email,
        String phone,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static DoctorResponse from(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getGivenName(),
                doctor.getFamilyName(),
                doctor.getSpecialty(),
                doctor.getEmail(),
                doctor.getPhone(),
                doctor.isActive(),
                doctor.getCreatedAt(),
                doctor.getUpdatedAt());
    }
}
