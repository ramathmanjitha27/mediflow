package com.mediflow.api.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Exercises each handler through a stub controller that throws on demand. */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    record StubRequest(@NotBlank String name) {
    }

    @RestController
    static class StubController {
        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Thing", 7);
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("Already exists");
        }

        @GetMapping("/integrity")
        void integrity() {
            throw new DataIntegrityViolationException("FK violation on secret_table");
        }

        @GetMapping("/overlap")
        void overlap() {
            throw new DataIntegrityViolationException("insert failed", new java.sql.SQLException(
                    "ERROR: conflicting key value violates exclusion constraint \"appointment_doctor_no_overlap\"", "23P01"));
        }

        @GetMapping("/fk")
        void foreignKey() {
            throw new DataIntegrityViolationException("delete failed", new java.sql.SQLException(
                    "ERROR: update or delete on table \"patient\" violates foreign key constraint "
                            + "\"appointment_patient_id_fkey\" on table \"appointment\"", "23503"));
        }

        @GetMapping("/overlap-with-detail")
        void overlapWithDetail() {
            java.sql.SQLException sql = new java.sql.SQLException(
                    "ERROR: conflicting key value violates exclusion constraint \"appointment_patient_no_overlap\"\n"
                            + "  Detail: Key (patient_id, tstzrange(start_time, end_time, '[)'))=(42, [\"2026-12-01\"))",
                    "23P01");
            throw new DataIntegrityViolationException("could not execute statement",
                    new org.hibernate.exception.ConstraintViolationException("could not execute statement", sql,
                            "appointment_patient_no_overlap"));
        }

        @GetMapping("/fk-insert")
        void foreignKeyOnInsert() {
            throw new DataIntegrityViolationException("insert failed", new java.sql.SQLException(
                    "ERROR: insert or update on table \"appointment\" violates foreign key constraint "
                            + "\"appointment_doctor_id_fkey\"", "23503"));
        }

        @GetMapping("/unprocessable")
        void unprocessable() {
            throw new UnprocessableEntityException("Doctor with id 3 is not active");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("internal secret");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody StubRequest request) {
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StubController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void notFoundReturns404Problem() throws Exception {
        mvc.perform(get("/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Thing with id 7 not found"));
    }

    @Test
    void conflictReturns409Problem() throws Exception {
        mvc.perform(get("/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Already exists"));
    }

    @Test
    void dataIntegrityViolationReturns409WithoutDatabaseDetails() throws Exception {
        mvc.perform(get("/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data"));
    }

    @Test
    void exclusionConstraintViolationMapsToOverlapMessage() throws Exception {
        mvc.perform(get("/overlap"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ConstraintMessages.DOCTOR_OVERLAP));
    }

    @Test
    void foreignKeyViolationMapsToHasAppointmentsMessage() throws Exception {
        mvc.perform(get("/fk"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The patient has appointments and cannot be deleted"));
    }

    @Test
    void constraintNameComesFromHibernateAndRowValuesAreNeverLogged(CapturedOutput output) throws Exception {
        mvc.perform(get("/overlap-with-detail"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ConstraintMessages.PATIENT_OVERLAP));

        assertThat(output).contains("constraint=appointment_patient_no_overlap sqlState=23P01");
        assertThat(output).doesNotContain("Key (").doesNotContain("42, [");
    }

    @Test
    void foreignKeyViolationOnInsertSaysReferenceIsGone() throws Exception {
        mvc.perform(get("/fk-insert"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The patient or doctor for this appointment no longer exists"));
    }

    @Test
    void unprocessableReturns422Problem() throws Exception {
        mvc.perform(get("/unprocessable"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Doctor with id 3 is not active"));
    }

    @Test
    void unexpectedExceptionReturns500WithoutInternals() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

    @Test
    void validationErrorListsFieldViolations() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void malformedJsonReturns400Problem() throws Exception {
        mvc.perform(post("/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }
}
