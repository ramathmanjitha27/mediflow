package com.mediflow.api.dto;

import com.mediflow.api.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Body for creating a patient (POST) and replacing one (PUT). */
public record PatientRequest(
        @NotBlank @Size(max = 100) String givenName,
        @NotBlank @Size(max = 100) String familyName,
        @NotNull @PastOrPresent LocalDate birthDate,
        @NotNull Gender gender,
        @Email @Size(max = 255) String email,
        @Size(max = 30) String phone) {
}
