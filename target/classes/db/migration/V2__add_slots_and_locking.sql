-- ============================================================
-- V2__add_slots_and_locking.sql
-- Introduces pre-generated, individually bookable appointment
-- slots and optimistic-locking support to prevent double-booking.
-- ============================================================

-- ---------------------------------------------------------------
-- SLOTS
-- A discrete, bookable time window for a doctor on a concrete date.
-- Generated ahead of time (e.g. 30 days) by a scheduled job from a
-- doctor's recurring weekly `availability` windows.
--
-- The `version` column powers JPA optimistic locking: two concurrent
-- bookings of the same slot will collide on version, and the loser
-- receives a 409 Conflict instead of silently double-booking.
-- ---------------------------------------------------------------
CREATE TABLE slots (
    id         BIGSERIAL   PRIMARY KEY,
    doctor_id  BIGINT      NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
    slot_date  DATE        NOT NULL,
    start_time TIME        NOT NULL,
    end_time   TIME        NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE | BOOKED
    version    BIGINT      NOT NULL DEFAULT 0,            -- optimistic lock
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_slot_times CHECK (end_time > start_time),
    -- A doctor cannot have two slots starting at the same date/time.
    CONSTRAINT uq_slot_doctor_date_start UNIQUE (doctor_id, slot_date, start_time)
);

-- ---------------------------------------------------------------
-- APPOINTMENTS – link to the booked slot + add optimistic lock
-- ---------------------------------------------------------------
ALTER TABLE appointments
    ADD COLUMN slot_id BIGINT REFERENCES slots(id) ON DELETE SET NULL;

ALTER TABLE appointments
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ---------------------------------------------------------------
-- INDEXES – optimise the hot read path: "available slots for a
-- doctor on/after a given date".
-- ---------------------------------------------------------------
CREATE INDEX idx_slots_doctor_date_status ON slots(doctor_id, slot_date, status);
CREATE INDEX idx_slots_status             ON slots(status);
CREATE INDEX idx_appointments_slot_id     ON appointments(slot_id);
