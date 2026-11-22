package com.mediflow.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for creating a doctor (POST) and replacing one (PUT).
 *
 * @param active optional; defaults to {@code true} on create and is left unchanged on update when omitted
 */
public record DoctorRequest(
        @NotBlank @Size(max = 100) String givenName,
        @NotBlank @Size(max = 100) String familyName,
        @NotBlank @Size(max = 100) String specialty,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 30) String phone,
        Boolean active) {
}
