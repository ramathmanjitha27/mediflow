-- Appointment booking rules enforced by the database, so concurrent requests can
-- never double-book even if both pass the service-level check.

-- Trusted extension (PG13+): the database owner can create it without superuser.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- FHIR Appointment.cancellationReason (free text)
ALTER TABLE appointment ADD COLUMN cancellation_reason VARCHAR(500);

-- Half-open ranges [start, end): back-to-back appointments do not overlap.
-- Only booked appointments block a slot; cancelled and fulfilled ones do not.
ALTER TABLE appointment
    ADD CONSTRAINT appointment_doctor_no_overlap
    EXCLUDE USING gist (
        doctor_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status = 'booked');

-- A patient cannot be in two booked appointments at the same time either.
ALTER TABLE appointment
    ADD CONSTRAINT appointment_patient_no_overlap
    EXCLUDE USING gist (
        patient_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    ) WHERE (status = 'booked');
