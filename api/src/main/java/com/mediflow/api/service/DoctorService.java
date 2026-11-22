package com.mediflow.api.service;

import com.mediflow.api.dto.DoctorRequest;
import com.mediflow.api.dto.DoctorResponse;
import com.mediflow.api.entity.Doctor;
import com.mediflow.api.exception.ConflictException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.repository.DoctorRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DoctorService {

    private static final String RESOURCE = "Doctor";

    private final DoctorRepository doctorRepository;

    public List<DoctorResponse> findAll() {
        return doctorRepository.findAll().stream().map(DoctorResponse::from).toList();
    }

    public DoctorResponse findById(Long id) {
        return DoctorResponse.from(getDoctor(id));
    }

    @Transactional
    public DoctorResponse create(DoctorRequest request) {
        String email = request.email().trim();
        if (doctorRepository.existsByEmailIgnoreCase(email)) {
            throw emailTaken(email);
        }
        Doctor doctor = new Doctor();
        apply(doctor, request, email);
        return DoctorResponse.from(doctorRepository.saveAndFlush(doctor));
    }

    @Transactional
    public DoctorResponse update(Long id, DoctorRequest request) {
        Doctor doctor = getDoctor(id);
        String email = request.email().trim();
        if (doctorRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw emailTaken(email);
        }
        apply(doctor, request, email);
        return DoctorResponse.from(doctorRepository.saveAndFlush(doctor));
    }

    @Transactional
    public void delete(Long id) {
        doctorRepository.delete(getDoctor(id));
    }

    private Doctor getDoctor(Long id) {
        return doctorRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    private static void apply(Doctor doctor, DoctorRequest request, String email) {
        doctor.setGivenName(request.givenName().trim());
        doctor.setFamilyName(request.familyName().trim());
        doctor.setSpecialty(request.specialty().trim());
        doctor.setEmail(email);
        doctor.setPhone(StringUtils.hasText(request.phone()) ? request.phone().trim() : null);
        if (request.active() != null) {
            doctor.setActive(request.active());
        }
    }

    private static ConflictException emailTaken(String email) {
        return new ConflictException("A doctor with email " + email + " already exists");
    }
}
