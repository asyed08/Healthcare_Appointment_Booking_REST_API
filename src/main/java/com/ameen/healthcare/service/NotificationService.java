package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.event.AppointmentCancelledEvent;
import com.ameen.healthcare.dto.event.AppointmentCreatedEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Handles all outbound notifications for appointment lifecycle events.
 *
 * <p><b>Email:</b> Implemented via Spring's {@link JavaMailSender} (SMTP).
 * Configure {@code MAIL_HOST}, {@code MAIL_USERNAME}, {@code MAIL_PASSWORD} env vars.
 *
 * <p><b>SMS:</b> Structured stub — wire up Twilio SDK here when API keys are available.
 * Replace {@link #sendSmsNotification} body with:
 * <pre>
 *   Message message = Message.creator(
 *       new PhoneNumber(toPhone), new PhoneNumber(fromPhone), body
 *   ).create();
 * </pre>
 *
 * <p><b>Calendar:</b> Structured stub — wire up Google Calendar / Microsoft Graph here.
 * Replace {@link #addCalendarEvent} and {@link #removeCalendarEvent} bodies with
 * the relevant API client calls using the provided event details.
 */
@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy 'at' h:mm a");

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@healthcare.com}")
    private String fromAddress;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // =========================================================================
    // Appointment Created — patient confirmation email
    // =========================================================================

    /**
     * Sends a booking confirmation email to the patient.
     * Runs asynchronously so it never blocks the Kafka consumer thread.
     */
    @Async
    public void sendBookingConfirmationToPatient(AppointmentCreatedEvent event) {
        String subject = "Appointment Confirmed — " + event.getDoctorName();
        String body = buildBookingConfirmationHtml(event);
        sendEmail(event.getPatientEmail(), subject, body, "booking confirmation");
    }

    /**
     * Sends a new-appointment notification email to the doctor.
     * Runs asynchronously.
     */
    @Async
    public void sendNewAppointmentAlertToDoctor(AppointmentCreatedEvent event) {
        // Doctors do not store a separate email in this event — log intent and
        // delegate to SMS/push when the doctor's email field is available.
        // If a doctorEmail field is added to the event, replace the line below
        // with: sendEmail(event.getDoctorEmail(), subject, body, "doctor alert");
        logger.info("[NOTIFICATION] New appointment alert queued for doctor '{}': appointmentId={}, dateTime={}",
                event.getDoctorName(), event.getAppointmentId(),
                event.getAppointmentDateTime().format(DATE_FORMAT));
        sendSmsNotification(
                event.getPatientPhoneNumber(),
                "New appointment booked with you on " + event.getAppointmentDateTime().format(DATE_FORMAT)
                        + ". Appointment ID: " + event.getAppointmentId()
        );
    }

    /**
     * Creates a calendar entry for the newly booked appointment.
     */
    @Async
    public void createCalendarEntry(AppointmentCreatedEvent event) {
        addCalendarEvent(
                event.getPatientEmail(),
                "Appointment with " + event.getDoctorName(),
                event.getAppointmentDateTime(),
                event.getAppointmentDateTime().plusMinutes(30),
                "Appointment ID: " + event.getAppointmentId()
                        + (event.getReason() != null ? " | Reason: " + event.getReason() : "")
        );
    }

    // =========================================================================
    // Appointment Cancelled — cancellation emails
    // =========================================================================

    /**
     * Sends a cancellation confirmation email to the patient.
     */
    @Async
    public void sendCancellationConfirmationToPatient(AppointmentCancelledEvent event) {
        String subject = "Appointment Cancelled — " + event.getDoctorName();
        String body = buildCancellationConfirmationHtml(event);
        sendEmail(event.getPatientEmail(), subject, body, "cancellation confirmation");
    }

    /**
     * Notifies the doctor that an appointment was cancelled.
     */
    @Async
    public void sendCancellationAlertToDoctor(AppointmentCancelledEvent event) {
        logger.info("[NOTIFICATION] Cancellation alert queued for doctor '{}': appointmentId={}, cancelledAt={}",
                event.getDoctorName(), event.getAppointmentId(), event.getCancelledAt());
        sendSmsNotification(
                null, // doctor phone not in this event — extend AppointmentCancelledEvent if needed
                "Appointment ID " + event.getAppointmentId() + " for "
                        + event.getAppointmentDateTime().format(DATE_FORMAT) + " was cancelled."
        );
    }

    /**
     * Removes the calendar entry for the cancelled appointment.
     */
    @Async
    public void deleteCalendarEntry(AppointmentCancelledEvent event) {
        removeCalendarEvent(
                event.getPatientEmail(),
                event.getAppointmentDateTime(),
                event.getAppointmentId()
        );
    }

    // =========================================================================
    // Internal — Email dispatch
    // =========================================================================

    private void sendEmail(String to, String subject, String htmlBody, String context) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = isHtml
            mailSender.send(message);
            logger.info("[EMAIL] Sent {} to '{}'", context, to);
        } catch (MailException | MessagingException ex) {
            // Log and continue — do not propagate to Kafka consumer so the offset is committed.
            // In production, push to a dead-letter queue or retry table for observability.
            logger.error("[EMAIL] Failed to send {} to '{}': {}", context, to, ex.getMessage(), ex);
        }
    }

    // =========================================================================
    // Internal — SMS stub (wire up Twilio here)
    // =========================================================================

    /**
     * SMS dispatch stub.
     *
     * <p>To activate, add the Twilio Java SDK to pom.xml:
     * <pre>{@code
     * <dependency>
     *   <groupId>com.twilio.sdk</groupId>
     *   <artifactId>twilio</artifactId>
     *   <version>9.x.x</version>
     * </dependency>
     * }</pre>
     * Then initialise Twilio in a @PostConstruct or @Configuration class and replace
     * the log below with:
     * <pre>{@code
     *   Twilio.init(accountSid, authToken);
     *   Message.creator(new PhoneNumber(to), new PhoneNumber(fromPhone), body).create();
     * }</pre>
     */
    private void sendSmsNotification(String toPhone, String body) {
        if (toPhone == null || toPhone.isBlank()) {
            logger.debug("[SMS] Skipped — no phone number provided. Message: {}", body);
            return;
        }
        // STUB: Replace with Twilio or AWS SNS call
        logger.info("[SMS-STUB] Would send SMS to '{}': {}", toPhone, body);
    }

    // =========================================================================
    // Internal — Calendar stub (wire up Google Calendar / Microsoft Graph here)
    // =========================================================================

    /**
     * Calendar create-event stub.
     *
     * <p>To activate with Google Calendar:
     * <ol>
     *   <li>Add {@code google-api-services-calendar} dependency.</li>
     *   <li>Obtain OAuth2 credentials (service account or user consent).</li>
     *   <li>Build a {@link com.google.api.services.calendar.Calendar} client.</li>
     *   <li>Create an {@link com.google.api.services.calendar.model.Event} and insert it.</li>
     * </ol>
     *
     * <p>To activate with Microsoft Graph (Outlook Calendar), use the
     * {@code microsoft-graph} SDK and call {@code graphClient.me().events().post(event)}.
     */
    private void addCalendarEvent(String attendeeEmail, String title,
                                   java.time.LocalDateTime start, java.time.LocalDateTime end,
                                   String description) {
        // STUB: Replace with Google Calendar API or Microsoft Graph call
        logger.info("[CALENDAR-STUB] Would create event '{}' for '{}' from {} to {}",
                title, attendeeEmail, start.format(DATE_FORMAT), end.format(DATE_FORMAT));
    }

    /**
     * Calendar delete-event stub.
     *
     * <p>Requires the external calendar event ID (returned when the event was created).
     * Extend the {@link AppointmentCancelledEvent} with a {@code calendarEventId} field
     * and store it when the appointment is created.
     */
    private void removeCalendarEvent(String attendeeEmail,
                                      java.time.LocalDateTime appointmentDateTime,
                                      Long appointmentId) {
        // STUB: Replace with Google Calendar API delete or Microsoft Graph call
        logger.info("[CALENDAR-STUB] Would delete calendar event for appointmentId={} (attendee='{}', dateTime={})",
                appointmentId, attendeeEmail, appointmentDateTime.format(DATE_FORMAT));
    }

    // =========================================================================
    // HTML email templates
    // =========================================================================

    private String buildBookingConfirmationHtml(AppointmentCreatedEvent event) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;color:#333'>"
                + "<div style='max-width:600px;margin:0 auto;padding:20px'>"
                + "<h2 style='color:#2c7be5'>Appointment Confirmed ✓</h2>"
                + "<p>Your appointment has been successfully booked.</p>"
                + "<table style='width:100%;border-collapse:collapse;margin:20px 0'>"
                + row("Appointment ID", String.valueOf(event.getAppointmentId()))
                + row("Doctor", event.getDoctorName())
                + row("Date &amp; Time", event.getAppointmentDateTime().format(DATE_FORMAT))
                + (event.getReason() != null ? row("Reason", event.getReason()) : "")
                + "</table>"
                + "<p style='color:#888;font-size:12px'>To cancel, please log in to your account or contact us at least 2 hours before the appointment.</p>"
                + "<p style='color:#888;font-size:12px'>This is an automated message — please do not reply.</p>"
                + "</div></body></html>";
    }

    private String buildCancellationConfirmationHtml(AppointmentCancelledEvent event) {
        return "<!DOCTYPE html><html><body style='font-family:Arial,sans-serif;color:#333'>"
                + "<div style='max-width:600px;margin:0 auto;padding:20px'>"
                + "<h2 style='color:#e63757'>Appointment Cancelled</h2>"
                + "<p>Your appointment has been cancelled as requested.</p>"
                + "<table style='width:100%;border-collapse:collapse;margin:20px 0'>"
                + row("Appointment ID", String.valueOf(event.getAppointmentId()))
                + row("Doctor", event.getDoctorName())
                + row("Original Date &amp; Time", event.getAppointmentDateTime().format(DATE_FORMAT))
                + (event.getCancellationReason() != null ? row("Reason", event.getCancellationReason()) : "")
                + row("Cancelled At", event.getCancelledAt() != null
                        ? event.getCancelledAt().format(DATE_FORMAT) : "—")
                + "</table>"
                + "<p>You can book a new appointment at any time through your patient portal.</p>"
                + "<p style='color:#888;font-size:12px'>This is an automated message — please do not reply.</p>"
                + "</div></body></html>";
    }

    private static String row(String label, String value) {
        return "<tr>"
                + "<td style='padding:8px;border:1px solid #ddd;background:#f8f9fa;font-weight:bold'>" + label + "</td>"
                + "<td style='padding:8px;border:1px solid #ddd'>" + value + "</td>"
                + "</tr>";
    }
}
