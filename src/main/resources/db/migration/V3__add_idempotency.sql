-- ============================================================
-- V3__add_idempotency.sql
-- Supports retry-safe booking via client-supplied Idempotency-Key.
-- ============================================================

-- ---------------------------------------------------------------
-- IDEMPOTENCY_KEYS
-- Stores the first response produced for a given Idempotency-Key so
-- that duplicate POST /appointments requests (e.g. network retries)
-- within the TTL window return the original result instead of
-- creating a second booking.
--
-- `request_hash` guards against accidental key reuse with a different
-- payload (returns 422 in that case at the application layer).
-- ---------------------------------------------------------------
CREATE TABLE idempotency_keys (
    id            BIGSERIAL    PRIMARY KEY,
    idem_key      VARCHAR(255) NOT NULL UNIQUE,
    request_hash  VARCHAR(64)  NOT NULL,          -- SHA-256 of the request body
    response_body TEXT,                           -- serialized original response
    status_code   INT,                            -- original HTTP status
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    expires_at    TIMESTAMP    NOT NULL           -- created_at + 24h
);

-- Fast lookup by key and efficient TTL cleanup.
CREATE INDEX idx_idempotency_expires_at ON idempotency_keys(expires_at);
