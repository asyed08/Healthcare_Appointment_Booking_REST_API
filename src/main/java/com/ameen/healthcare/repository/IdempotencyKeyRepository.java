package com.ameen.healthcare.repository;

import com.ameen.healthcare.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/** Data-access for idempotency deduplication records. */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByIdemKey(String idemKey);

    /** Periodic cleanup — delete expired entries older than 24 h. */
    @Modifying
    @Transactional
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt < :now")
    void deleteExpired(LocalDateTime now);
}
