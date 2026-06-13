package com.ameen.healthcare.service;

import com.ameen.healthcare.entity.Availability;
import com.ameen.healthcare.entity.Doctor;
import com.ameen.healthcare.entity.Slot;
import com.ameen.healthcare.enums.SlotStatus;
import com.ameen.healthcare.repository.AvailabilityRepository;
import com.ameen.healthcare.repository.DoctorRepository;
import com.ameen.healthcare.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates pre-computed {@link Slot} rows from a doctor's recurring
 * {@link Availability} windows.
 *
 * <p>Slots are split into 30-minute increments. Each slot is idempotent:
 * if a row already exists for that doctor/date/start-time it is skipped,
 * so the job is safe to re-run at any frequency.
 *
 * <p>A scheduled cron job runs every Sunday at 01:00 to roll the 30-day
 * window forward for all active doctors.
 */
@Service
public class SlotGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(SlotGenerationService.class);

    /** Length of each bookable slot in minutes. */
    private static final int SLOT_DURATION_MINUTES = 30;

    private final DoctorRepository doctorRepository;
    private final AvailabilityRepository availabilityRepository;
    private final SlotRepository slotRepository;

    public SlotGenerationService(DoctorRepository doctorRepository,
                                 AvailabilityRepository availabilityRepository,
                                 SlotRepository slotRepository) {
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.slotRepository = slotRepository;
    }

    // ─── Scheduled job ────────────────────────────────────────────────────────

    /**
     * Runs every Sunday at 01:00 server time and generates slots 30 days ahead
     * for every doctor in the system.
     */
    @Scheduled(cron = "0 0 1 * * SUN")
    public void generateSlotsForAllDoctors() {
        logger.info("Scheduled slot generation started");
        List<Doctor> doctors = doctorRepository.findAll();
        doctors.forEach(d -> generateSlotsForDoctor(d.getId(), 30));
        logger.info("Scheduled slot generation complete for {} doctors", doctors.size());
    }

    // ─── On-demand generation ─────────────────────────────────────────────────

    /**
     * Generates slots for a single doctor up to {@code daysAhead} days from today.
     * Already-existing slots are silently skipped (idempotent).
     *
     * @param doctorId  internal doctor PK
     * @param daysAhead number of calendar days to generate (e.g. 30)
     */
    @Transactional
    public void generateSlotsForDoctor(Long doctorId, int daysAhead) {
        Doctor doctor = doctorRepository.findById(doctorId).orElse(null);
        if (doctor == null) {
            logger.warn("generateSlotsForDoctor: doctor {} not found, skipping", doctorId);
            return;
        }

        List<Availability> windows = availabilityRepository.findByDoctorId(doctorId);
        if (windows.isEmpty()) {
            logger.debug("Doctor {} has no availability windows, skipping slot generation", doctorId);
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(daysAhead);

        List<Slot> toSave = new ArrayList<>();

        for (LocalDate date = today; date.isBefore(horizon); date = date.plusDays(1)) {
            final LocalDate finalDate = date;

            // Find availability windows that apply to this day of week
            List<Availability> dayWindows = windows.stream()
                    .filter(w -> w.getDayOfWeek() == finalDate.getDayOfWeek())
                    .toList();

            for (Availability window : dayWindows) {
                LocalTime cursor = window.getStartTime();

                while (cursor.plusMinutes(SLOT_DURATION_MINUTES).compareTo(window.getEndTime()) <= 0) {
                    LocalTime slotStart = cursor;
                    LocalTime slotEnd = cursor.plusMinutes(SLOT_DURATION_MINUTES);

                    // Idempotency check – skip if slot already exists
                    if (!slotRepository.existsByDoctorIdAndSlotDateAndStartTime(doctorId, finalDate, slotStart)) {
                        Slot slot = new Slot();
                        slot.setDoctor(doctor);
                        slot.setSlotDate(finalDate);
                        slot.setStartTime(slotStart);
                        slot.setEndTime(slotEnd);
                        slot.setStatus(SlotStatus.AVAILABLE);
                        toSave.add(slot);
                    }

                    cursor = slotEnd;
                }
            }
        }

        if (!toSave.isEmpty()) {
            slotRepository.saveAll(toSave);
            logger.info("Generated {} slots for doctor {} over next {} days",
                    toSave.size(), doctorId, daysAhead);
        }
    }
}
