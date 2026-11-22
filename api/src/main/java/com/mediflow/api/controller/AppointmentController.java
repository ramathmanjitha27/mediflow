package com.mediflow.api.controller;

import com.mediflow.api.dto.AppointmentRequest;
import com.mediflow.api.dto.AppointmentResponse;
import com.mediflow.api.dto.CancelAppointmentRequest;
import com.mediflow.api.entity.AppointmentStatus;
import com.mediflow.api.service.AppointmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Appointments have no DELETE: they are part of the clinical record. Status changes go through
 * explicit commands ({@code /cancel}, {@code /fulfill}) so only valid transitions are possible.
 */
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping
    public List<AppointmentResponse> list(
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) AppointmentStatus status) {
        return appointmentService.search(doctorId, patientId, status);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(@PathVariable Long id) {
        return appointmentService.findById(id);
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody AppointmentRequest request) {
        AppointmentResponse created = appointmentService.book(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public AppointmentResponse reschedule(@PathVariable Long id, @Valid @RequestBody AppointmentRequest request) {
        return appointmentService.reschedule(id, request);
    }

    @PostMapping("/{id}/cancel")
    public AppointmentResponse cancel(
            @PathVariable Long id, @Valid @RequestBody(required = false) CancelAppointmentRequest request) {
        return appointmentService.cancel(id, request);
    }

    @PostMapping("/{id}/fulfill")
    public AppointmentResponse fulfill(@PathVariable Long id) {
        return appointmentService.fulfill(id);
    }
}
