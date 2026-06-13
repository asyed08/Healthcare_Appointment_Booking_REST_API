package com.ameen.healthcare.dto.response;

import com.ameen.healthcare.entity.Availability;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Read model for a doctor's weekly availability window.
 */
public record AvailabilityResponse(
        Long id,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime
) {
    public static AvailabilityResponse from(Availability a) {
        return new AvailabilityResponse(a.getId(), a.getDayOfWeek(), a.getStartTime(), a.getEndTime());
    }
}
