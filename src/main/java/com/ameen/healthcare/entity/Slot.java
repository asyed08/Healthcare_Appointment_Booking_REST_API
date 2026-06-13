package com.ameen.healthcare.entity;

import com.ameen.healthcare.enums.SlotStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A discrete, individually bookable time window for a {@link Doctor} on a
 * concrete calendar date.
 *
 * <p>Slots are pre-generated ahead of time (e.g. 30 days in advance) by a
 * scheduled job from the doctor's recurring weekly {@link Availability}
 * windows. Treating availability as concrete slots (rather than computing it
 * on the fly) keeps the read path cheap and mirrors how real EHR systems
 * manage provider schedules.
 *
 * <p>The {@link Version @Version} field enables optimistic locking: if two
 * patients attempt to book the same slot concurrently, the second write fails
 * with an {@link jakarta.persistence.OptimisticLockException}, which the
 * service layer translates into a {@code 409 Conflict}.
 */
@Entity
@Table(name = "slots")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SlotStatus status = SlotStatus.AVAILABLE;

    /** Optimistic-lock version – prevents concurrent double-booking. */
    @Version
    @Column(nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
