package com.ameen.healthcare.repository;

import com.ameen.healthcare.entity.Slot;
import com.ameen.healthcare.enums.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Data-access for pre-generated bookable {@link Slot}s. */
@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {

    /** Available slots for a doctor on/after a date – the hot read path. */
    List<Slot> findByDoctorIdAndSlotDateGreaterThanEqualAndStatusOrderBySlotDateAscStartTimeAsc(
            Long doctorId, LocalDate fromDate, SlotStatus status);

    boolean existsByDoctorIdAndSlotDateAndStartTime(
            Long doctorId, LocalDate slotDate, LocalTime startTime);
}
