package com.ameen.healthcare.dto.response;

import com.ameen.healthcare.entity.Slot;
import com.ameen.healthcare.enums.SlotStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Read model for a bookable slot.
 */
public record SlotResponse(
        Long id,
        LocalDate slotDate,
        LocalTime startTime,
        LocalTime endTime,
        SlotStatus status
) {
    public static SlotResponse from(Slot s) {
        return new SlotResponse(s.getId(), s.getSlotDate(), s.getStartTime(), s.getEndTime(), s.getStatus());
    }
}
