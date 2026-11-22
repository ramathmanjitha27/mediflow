package com.mediflow.api.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link Gender} as its FHIR code, matching the CHECK constraint in V1. */
@Converter(autoApply = true)
public class GenderConverter implements AttributeConverter<Gender, String> {

    @Override
    public String convertToDatabaseColumn(Gender gender) {
        return gender == null ? null : gender.getCode();
    }

    @Override
    public Gender convertToEntityAttribute(String code) {
        return code == null ? null : Gender.fromCode(code);
    }
}
