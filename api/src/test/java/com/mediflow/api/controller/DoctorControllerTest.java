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

import com.mediflow.api.dto.DoctorRequest;
import com.mediflow.api.dto.DoctorResponse;
import com.mediflow.api.exception.ConflictException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.service.DoctorService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DoctorController.class)
class DoctorControllerTest {

    private static final String VALID_BODY = """
            {
              "givenName": "Gregory",
              "familyName": "House",
              "specialty": "Diagnostic Medicine",
              "email": "house@example.com",
              "phone": "+94 11 555 0100"
            }
            """;

    private static final DoctorResponse HOUSE = new DoctorResponse(
            5L, "Gregory", "House", "Diagnostic Medicine", "house@example.com", "+94 11 555 0100", true,
            Instant.parse("2026-11-22T10:00:00Z"), Instant.parse("2026-11-22T10:00:00Z"));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DoctorService doctorService;

    @Test
    void listReturnsAllDoctors() throws Exception {
        given(doctorService.findAll()).willReturn(List.of(HOUSE));

        mvc.perform(get("/api/doctors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].specialty").value("Diagnostic Medicine"));
    }

    @Test
    void getReturnsDoctor() throws Exception {
        given(doctorService.findById(5L)).willReturn(HOUSE);

        mvc.perform(get("/api/doctors/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.givenName").value("Gregory"))
                .andExpect(jsonPath("$.familyName").value("House"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getUnknownDoctorReturns404() throws Exception {
        given(doctorService.findById(99L)).willThrow(new ResourceNotFoundException("Doctor", 99L));

        mvc.perform(get("/api/doctors/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Doctor with id 99 not found"));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        given(doctorService.create(any(DoctorRequest.class))).willReturn(HOUSE);

        mvc.perform(post("/api/doctors").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/doctors/5")))
                .andExpect(jsonPath("$.id").value(5));

        verify(doctorService).create(new DoctorRequest(
                "Gregory", "House", "Diagnostic Medicine", "house@example.com", "+94 11 555 0100", null));
    }

    @Test
    void createWithMissingFieldsReturns400WithViolations() throws Exception {
        mvc.perform(post("/api/doctors").contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("givenName", "familyName", "specialty", "email")));

        verifyNoInteractions(doctorService);
    }

    @Test
    void createWithDuplicateEmailReturns409() throws Exception {
        given(doctorService.create(any())).willThrow(new ConflictException("A doctor with email house@example.com already exists"));

        mvc.perform(post("/api/doctors").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void updateReturnsUpdatedDoctor() throws Exception {
        given(doctorService.update(eq(5L), any(DoctorRequest.class))).willReturn(HOUSE);

        mvc.perform(put("/api/doctors/5").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void updateUnknownDoctorReturns404() throws Exception {
        given(doctorService.update(eq(99L), any())).willThrow(new ResourceNotFoundException("Doctor", 99L));

        mvc.perform(put("/api/doctors/99").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/doctors/5")).andExpect(status().isNoContent());

        verify(doctorService).delete(5L);
    }

    @Test
    void deleteUnknownDoctorReturns404() throws Exception {
        willThrow(new ResourceNotFoundException("Doctor", 99L)).given(doctorService).delete(99L);

        mvc.perform(delete("/api/doctors/99")).andExpect(status().isNotFound());
    }
}
