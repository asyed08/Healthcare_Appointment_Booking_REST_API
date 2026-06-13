package com.ameen.healthcare.enums;

/**
 * Lifecycle states of a bookable appointment slot.
 *
 * <pre>
 *   AVAILABLE → BOOKED   (on successful appointment creation)
 *   BOOKED    → AVAILABLE (on appointment cancellation)
 * </pre>
 */
public enum SlotStatus {
    AVAILABLE,
    BOOKED
}
