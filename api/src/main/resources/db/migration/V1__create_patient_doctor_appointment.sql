-- Field names follow FHIR (Patient, Practitioner, Appointment) in snake_case.

CREATE TABLE patient (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    given_name   VARCHAR(100) NOT NULL,
    family_name  VARCHAR(100) NOT NULL,
    birth_date   DATE         NOT NULL,
    gender       VARCHAR(10)  NOT NULL
        CONSTRAINT patient_gender_chk CHECK (gender IN ('male', 'female', 'other', 'unknown')),
    email        VARCHAR(255),
    phone        VARCHAR(30),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX patient_email_uq ON patient (lower(email)) WHERE email IS NOT NULL;

CREATE TABLE doctor (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    given_name   VARCHAR(100) NOT NULL,
    family_name  VARCHAR(100) NOT NULL,
    specialty    VARCHAR(100) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    phone        VARCHAR(30),
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX doctor_email_uq ON doctor (lower(email));

CREATE TABLE appointment (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    patient_id   BIGINT       NOT NULL REFERENCES patient (id),
    doctor_id    BIGINT       NOT NULL REFERENCES doctor (id),
    start_time   TIMESTAMPTZ  NOT NULL,
    end_time     TIMESTAMPTZ  NOT NULL,
    -- Subset of FHIR AppointmentStatus
    status       VARCHAR(20)  NOT NULL DEFAULT 'booked'
        CONSTRAINT appointment_status_chk
        CHECK (status IN ('proposed', 'booked', 'arrived', 'fulfilled', 'cancelled', 'noshow')),
    reason       VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT appointment_time_chk CHECK (end_time > start_time)
);

CREATE INDEX appointment_doctor_start_idx  ON appointment (doctor_id, start_time);
CREATE INDEX appointment_patient_start_idx ON appointment (patient_id, start_time);
