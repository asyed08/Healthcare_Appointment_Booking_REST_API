-- ============================================================
-- V1__init.sql
-- Initial schema for the Healthcare Appointment Booking system
-- ============================================================

-- ---------------------------------------------------------------
-- USERS
-- Core authentication table. Every actor in the system (doctor or
-- patient) has one row here that holds their credentials and role.
-- ---------------------------------------------------------------
CREATE TABLE users (
    id         BIGSERIAL    PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL,          -- PATIENT | DOCTOR
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------
-- DOCTORS
-- Professional profile linked 1-to-1 with a users row.
-- ---------------------------------------------------------------
CREATE TABLE doctors (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    specialization VARCHAR(150) NOT NULL,
    license_number VARCHAR(100) NOT NULL UNIQUE,
    phone          VARCHAR(20),
    bio            TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------
-- PATIENTS
-- Patient profile linked 1-to-1 with a users row.
-- ---------------------------------------------------------------
CREATE TABLE patients (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    phone         VARCHAR(20),
    address       TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- ---------------------------------------------------------------
-- AVAILABILITY
-- Weekly recurring time windows during which a doctor can be
-- booked (e.g. MONDAY 09:00 – 17:00).
-- ---------------------------------------------------------------
CREATE TABLE availability (
    id          BIGSERIAL   PRIMARY KEY,
    doctor_id   BIGINT      NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    day_of_week VARCHAR(15) NOT NULL,   -- Java DayOfWeek name: MONDAY … SUNDAY
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_availability_times CHECK (end_time > start_time)
);

-- ---------------------------------------------------------------
-- APPOINTMENTS
-- A booking record that ties a patient to a doctor for a specific
-- date and time window.
-- Status lifecycle: PENDING → CONFIRMED → COMPLETED | CANCELLED
-- ---------------------------------------------------------------
CREATE TABLE appointments (
    id               BIGSERIAL   PRIMARY KEY,
    patient_id       BIGINT      NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id        BIGINT      NOT NULL REFERENCES doctors(id)  ON DELETE CASCADE,
    appointment_date DATE        NOT NULL,
    start_time       TIME        NOT NULL,
    end_time         TIME        NOT NULL,
    status           VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    notes            TEXT,
    created_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_appointment_times CHECK (end_time > start_time)
);

-- ---------------------------------------------------------------
-- INDEXES – speed up the most common query patterns
-- ---------------------------------------------------------------
CREATE INDEX idx_doctors_user_id         ON doctors(user_id);
CREATE INDEX idx_patients_user_id        ON patients(user_id);
CREATE INDEX idx_availability_doctor_id  ON availability(doctor_id);
CREATE INDEX idx_appointments_patient_id ON appointments(patient_id);
CREATE INDEX idx_appointments_doctor_id  ON appointments(doctor_id);
CREATE INDEX idx_appointments_date       ON appointments(appointment_date);
CREATE INDEX idx_appointments_status     ON appointments(status);

