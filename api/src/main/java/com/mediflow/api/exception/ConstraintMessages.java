package com.mediflow.api.exception;

import java.util.Map;

/**
 * Client-facing messages for named database constraints. Shared by the service-level checks and
 * the {@link GlobalExceptionHandler} fallback so both paths give the same answer. Messages never
 * describe the other appointment (no patient, reason or times), to avoid leaking PHI.
 */
public final class ConstraintMessages {

    public static final String DOCTOR_OVERLAP = "The doctor already has a booked appointment that overlaps this time";
    public static final String PATIENT_OVERLAP = "The patient already has a booked appointment that overlaps this time";
    public static final String DEFAULT = "The request conflicts with existing data";

    private static final Map<String, String> BY_CONSTRAINT = Map.of(
            "appointment_doctor_no_overlap", DOCTOR_OVERLAP,
            "appointment_patient_no_overlap", PATIENT_OVERLAP,
            "appointment_patient_id_fkey", "The patient has appointments and cannot be deleted",
            "appointment_doctor_id_fkey", "The doctor has appointments and cannot be deleted");

    private ConstraintMessages() {
    }

    /**
     * @param constraint the violated constraint's name, or null if unknown
     * @param summary    the first line of the database error (names only, no row values)
     */
    static String forViolation(String constraint, String summary) {
        if (constraint == null) {
            return DEFAULT;
        }
        // The appointment foreign keys also fire when a booking races with deleting its patient or
        // doctor; the "has appointments" message only fits the delete case.
        if (constraint.endsWith("_fkey") && summary.contains("insert or update on table \"appointment\"")) {
            return "The patient or doctor for this appointment no longer exists";
        }
        return BY_CONSTRAINT.getOrDefault(constraint, DEFAULT);
    }
}
