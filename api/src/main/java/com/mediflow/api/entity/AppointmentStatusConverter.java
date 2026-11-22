package com.mediflow.api.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link AppointmentStatus} as its FHIR code, matching the CHECK constraint in V1. */
@Converter(autoApply = true)
public class AppointmentStatusConverter implements AttributeConverter<AppointmentStatus, String> {

    @Override
    public String convertToDatabaseColumn(AppointmentStatus status) {
        return status == null ? null : status.getCode();
    }

    @Override
    public AppointmentStatus convertToEntityAttribute(String code) {
        return code == null ? null : AppointmentStatus.fromCode(code);
    }
}
