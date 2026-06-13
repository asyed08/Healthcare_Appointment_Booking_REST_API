package com.ameen.healthcare.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Stores the first response produced for a given {@code Idempotency-Key} header
 * so that duplicate POST /appointments requests (network retries) within 24 hours
 * receive the original result instead of creating a second booking.
 *
 * <p>The {@code requestHash} (SHA-256 of the request body) guards against accidental
 * key reuse with a different payload — the service returns 422 in that case.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idem_key", nullable = false, unique = true, length = 255)
    private String idemKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public IdempotencyKey() {}

    // Getters
    public Long getId() { return id; }
    public String getIdemKey() { return idemKey; }
    public String getRequestHash() { return requestHash; }
    public String getResponseBody() { return responseBody; }
    public Integer getStatusCode() { return statusCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }

    // Setters
    public void setId(Long id) { this.id = id; }
    public void setIdemKey(String idemKey) { this.idemKey = idemKey; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
