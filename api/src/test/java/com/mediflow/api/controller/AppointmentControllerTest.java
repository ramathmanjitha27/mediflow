package com.mediflow.api.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mediflow.api.config.WebConfig;
import com.mediflow.api.dto.AppointmentRequest;
import com.mediflow.api.dto.AppointmentResponse;
import com.mediflow.api.dto.CancelAppointmentRequest;
import com.mediflow.api.entity.AppointmentStatus;
import com.mediflow.api.exception.ConflictException;
import com.mediflow.api.exception.ConstraintMessages;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.exception.UnprocessableEntityException;
import com.mediflow.api.service.AppointmentService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AppointmentController.class)
@Import(WebConfig.class)
class AppointmentControllerTest {

    private static final Instant START = Instant.parse("2099-01-15T10:00:00Z");
    private static final Instant END = Instant.parse("2099-01-15T10:30:00Z");

    private static final AppointmentResponse BOOKED = new AppointmentResponse(
            7L, 1L, 5L, START, END, AppointmentStatus.BOOKED, "Annual check-up", null,
            Instant.parse("2026-11-22T10:00:00Z"), Instant.parse("2026-11-22T10:00:00Z"));

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AppointmentService appointmentService;

    private static String body(String start, String end) {
        return """
                {"patientId": 1, "doctorId": 5, "start": "%s", "end": "%s", "reason": "Annual check-up"}
                """.formatted(start, end);
    }

    private static final String VALID_BODY = body("2099-01-15T10:00:00Z", "2099-01-15T10:30:00Z");

    @Test
    void bookReturns201WithLocationAndFhirStatusCode() throws Exception {
        given(appointmentService.book(any(AppointmentRequest.class))).willReturn(BOOKED);

        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/appointments/7")))
                .andExpect(jsonPath("$.status").value("booked"))
                .andExpect(jsonPath("$.start").value("2099-01-15T10:00:00Z"))
                .andExpect(jsonPath("$.end").value("2099-01-15T10:30:00Z"));

        verify(appointmentService).book(new AppointmentRequest(1L, 5L, START, END, "Annual check-up"));
    }

    @Test
    void bookAcceptsTimesWithOffsetAndNormalizesToUtc() throws Exception {
        given(appointmentService.book(any(AppointmentRequest.class))).willReturn(BOOKED);

        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(body("2099-01-15T15:30:00+05:30", "2099-01-15T16:00:00+05:30")))
                .andExpect(status().isCreated());

        verify(appointmentService).book(new AppointmentRequest(1L, 5L, START, END, "Annual check-up"));
    }

    @Test
    void bookRejectsTimesWithoutOffset() throws Exception {
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(body("2099-01-15T10:00:00", "2099-01-15T10:30:00")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void bookWithMissingFieldsReturns400() throws Exception {
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("patientId", "doctorId", "start", "end")));

        verifyNoInteractions(appointmentService);
    }

    @Test
    void bookWithEndBeforeStartReturns400() throws Exception {
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(body("2099-01-15T10:30:00Z", "2099-01-15T10:00:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("endAfterStart")));

        verifyNoInteractions(appointmentService);
    }

    @Test
    void bookLongerThanEightHoursReturns400() throws Exception {
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(body("2099-01-15T08:00:00Z", "2099-01-15T16:01:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("withinMaxDuration")));
    }

    @Test
    void bookInThePastReturns400() throws Exception {
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON)
                        .content(body("2020-01-15T10:00:00Z", "2020-01-15T10:30:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItem("start")));

        verifyNoInteractions(appointmentService);
    }

    @Test
    void doubleBookingReturns409WithoutDetailsOfTheOtherAppointment() throws Exception {
        given(appointmentService.book(any())).willThrow(new ConflictException(ConstraintMessages.DOCTOR_OVERLAP));

        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(ConstraintMessages.DOCTOR_OVERLAP));
    }

    @Test
    void bookForUnknownDoctorReturns422() throws Exception {
        given(appointmentService.book(any())).willThrow(new UnprocessableEntityException("Doctor with id 5 does not exist"));

        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value("Doctor with id 5 does not exist"));
    }

    @Test
    void listPassesFiltersUsingFhirStatusCode() throws Exception {
        given(appointmentService.search(5L, null, AppointmentStatus.BOOKED)).willReturn(List.of(BOOKED));

        mvc.perform(get("/api/appointments").param("doctorId", "5").param("status", "booked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(7));
    }

    @Test
    void listWithoutFiltersReturnsAll() throws Exception {
        given(appointmentService.search(null, null, null)).willReturn(List.of(BOOKED));

        mvc.perform(get("/api/appointments")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void listWithUnknownStatusReturns400() throws Exception {
        mvc.perform(get("/api/appointments").param("status", "BOGUS")).andExpect(status().isBadRequest());

        verifyNoInteractions(appointmentService);
    }

    @Test
    void getReturnsAppointment() throws Exception {
        given(appointmentService.findById(7L)).willReturn(BOOKED);

        mvc.perform(get("/api/appointments/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(1))
                .andExpect(jsonPath("$.doctorId").value(5));
    }

    @Test
    void getUnknownAppointmentReturns404() throws Exception {
        given(appointmentService.findById(99L)).willThrow(new ResourceNotFoundException("Appointment", 99L));

        mvc.perform(get("/api/appointments/99")).andExpect(status().isNotFound());
    }

    @Test
    void rescheduleReturns200() throws Exception {
        given(appointmentService.reschedule(eq(7L), any(AppointmentRequest.class))).willReturn(BOOKED);

        mvc.perform(put("/api/appointments/7").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void rescheduleIntoOverlapReturns409() throws Exception {
        given(appointmentService.reschedule(eq(7L), any())).willThrow(new ConflictException(ConstraintMessages.DOCTOR_OVERLAP));

        mvc.perform(put("/api/appointments/7").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelWithReasonReturns200() throws Exception {
        AppointmentResponse cancelled = new AppointmentResponse(7L, 1L, 5L, START, END, AppointmentStatus.CANCELLED,
                "Annual check-up", "Patient request", BOOKED.createdAt(), BOOKED.updatedAt());
        given(appointmentService.cancel(eq(7L), any())).willReturn(cancelled);

        mvc.perform(post("/api/appointments/7/cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cancellationReason\": \"Patient request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.cancellationReason").value("Patient request"));

        verify(appointmentService).cancel(7L, new CancelAppointmentRequest("Patient request"));
    }

    @Test
    void cancelWithoutBodyReturns200() throws Exception {
        given(appointmentService.cancel(eq(7L), isNull())).willReturn(BOOKED);

        mvc.perform(post("/api/appointments/7/cancel")).andExpect(status().isOk());
    }

    @Test
    void cancelTerminalAppointmentReturns409() throws Exception {
        given(appointmentService.cancel(eq(7L), any()))
                .willThrow(new ConflictException("Only booked appointments can be cancelled; this one is cancelled"));

        mvc.perform(post("/api/appointments/7/cancel")).andExpect(status().isConflict());
    }

    @Test
    void fulfillReturns200() throws Exception {
        given(appointmentService.fulfill(7L)).willReturn(BOOKED);

        mvc.perform(post("/api/appointments/7/fulfill")).andExpect(status().isOk());
    }

    @Test
    void deleteIsNotSupported() throws Exception {
        mvc.perform(delete("/api/appointments/7")).andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(appointmentService);
    }
}
