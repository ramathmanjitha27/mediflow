package com.mediflow.api.service;

import com.mediflow.api.dto.PatientRequest;
import com.mediflow.api.dto.PatientResponse;
import com.mediflow.api.entity.Patient;
import com.mediflow.api.exception.ConflictException;
import com.mediflow.api.exception.ResourceNotFoundException;
import com.mediflow.api.repository.PatientRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientService {

    private static final String RESOURCE = "Patient";

    private final PatientRepository patientRepository;

    public List<PatientResponse> findAll() {
        return patientRepository.findAll().stream().map(PatientResponse::from).toList();
    }

    public PatientResponse findById(Long id) {
        return PatientResponse.from(getPatient(id));
    }

    @Transactional
    public PatientResponse create(PatientRequest request) {
        String email = normalize(request.email());
        if (email != null && patientRepository.existsByEmailIgnoreCase(email)) {
            throw emailTaken(email);
        }
        Patient patient = new Patient();
        apply(patient, request, email);
        return PatientResponse.from(patientRepository.saveAndFlush(patient));
    }

    @Transactional
    public PatientResponse update(Long id, PatientRequest request) {
        Patient patient = getPatient(id);
        String email = normalize(request.email());
        if (email != null && patientRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw emailTaken(email);
        }
        apply(patient, request, email);
        return PatientResponse.from(patientRepository.saveAndFlush(patient));
    }

    @Transactional
    public void delete(Long id) {
        patientRepository.delete(getPatient(id));
        // Flush so a foreign-key violation (patient has appointments) surfaces here as a 409.
        patientRepository.flush();
    }

    private Patient getPatient(Long id) {
        return patientRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(RESOURCE, id));
    }

    private static void apply(Patient patient, PatientRequest request, String email) {
        patient.setGivenName(request.givenName().trim());
        patient.setFamilyName(request.familyName().trim());
        patient.setBirthDate(request.birthDate());
        patient.setGender(request.gender());
        patient.setEmail(email);
        patient.setPhone(normalize(request.phone()));
    }

    /** Trims optional text and turns blank values into null. */
    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static ConflictException emailTaken(String email) {
        return new ConflictException("A patient with email " + email + " already exists");
    }
}
