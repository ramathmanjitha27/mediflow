package com.mediflow.api.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.api.dto.PatientRequest;
import com.mediflow.api.dto.PatientResponse;
import com.mediflow.api.entity.Gender;
import com.mediflow.api.exception.ConflictException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.service.PatientService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PatientController.class)
class PatientControllerTest {

    private static final String VALID_BODY = """
            {
              "givenName": "Ada",
              "familyName": "Lovelace",
              "birthDate": "1990-12-10",
              "gender": "female",
              "email": "ada@example.com",
              "phone": "+94 77 123 4567"
            }
            """;

    private static final PatientResponse ADA = new PatientResponse(
            1L, "Ada", "Lovelace", LocalDate.of(1990, 12, 10), Gender.FEMALE,
            "ada@example.com", "+94 77 123 4567",
            Instant.parse("2026-11-22T10:00:00Z"), Instant.parse("2026-11-22T10:00:00Z"));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PatientService patientService;

    @Test
    void listReturnsAllPatients() throws Exception {
        given(patientService.findAll()).willReturn(List.of(ADA));

        mvc.perform(get("/api/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].givenName").value("Ada"));
    }

    @Test
    void getReturnsPatientWithFhirStyleFields() throws Exception {
        given(patientService.findById(1L)).willReturn(ADA);

        mvc.perform(get("/api/patients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.givenName").value("Ada"))
                .andExpect(jsonPath("$.familyName").value("Lovelace"))
                .andExpect(jsonPath("$.birthDate").value("1990-12-10"))
                .andExpect(jsonPath("$.gender").value("female"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-22T10:00:00Z"));
    }

    @Test
    void getUnknownPatientReturns404() throws Exception {
        given(patientService.findById(99L)).willThrow(new ResourceNotFoundException("Patient", 99L));

        mvc.perform(get("/api/patients/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Patient with id 99 not found"));
    }

    @Test
    void getWithNonNumericIdReturns400() throws Exception {
        mvc.perform(get("/api/patients/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        given(patientService.create(any(PatientRequest.class))).willReturn(ADA);

        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/patients/1")))
                .andExpect(jsonPath("$.id").value(1));

        verify(patientService).create(new PatientRequest(
                "Ada", "Lovelace", LocalDate.of(1990, 12, 10), Gender.FEMALE, "ada@example.com", "+94 77 123 4567"));
    }

    @Test
    void createWithInvalidFieldsReturns400WithViolations() throws Exception {
        String body = """
                {
                  "givenName": " ",
                  "familyName": "Lovelace",
                  "birthDate": "2999-01-01",
                  "gender": "female",
                  "email": "not-an-email"
                }
                """;

        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("givenName", "birthDate", "email")));

        verifyNoInteractions(patientService);
    }

    @Test
    void createWithUnknownGenderReturns400() throws Exception {
        String body = VALID_BODY.replace("\"female\"", "\"robot\"");

        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(patientService);
    }

    @Test
    void createWithDuplicateEmailReturns409() throws Exception {
        given(patientService.create(any())).willThrow(new ConflictException("A patient with email ada@example.com already exists"));

        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A patient with email ada@example.com already exists"));
    }

    @Test
    void updateReturnsUpdatedPatient() throws Exception {
        given(patientService.update(eq(1L), any(PatientRequest.class))).willReturn(ADA);

        mvc.perform(put("/api/patients/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateUnknownPatientReturns404() throws Exception {
        given(patientService.update(eq(99L), any())).willThrow(new ResourceNotFoundException("Patient", 99L));

        mvc.perform(put("/api/patients/99").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateWithInvalidBodyReturns400() throws Exception {
        mvc.perform(put("/api/patients/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("givenName", "familyName", "birthDate", "gender")));

        verifyNoInteractions(patientService);
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/patients/1")).andExpect(status().isNoContent());

        verify(patientService).delete(1L);
    }

    @Test
    void deleteUnknownPatientReturns404() throws Exception {
        willThrow(new ResourceNotFoundException("Patient", 99L)).given(patientService).delete(99L);

        mvc.perform(delete("/api/patients/99")).andExpect(status().isNotFound());
    }
}
