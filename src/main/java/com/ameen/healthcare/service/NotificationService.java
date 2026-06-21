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

    @Async
    public void sendBookingConfirmationToPatient(AppointmentCreatedEvent event) {
        String subject = "Appointment Confirmed — " + event.getDoctorName();
        sendEmail(event.getPatientEmail(), subject, buildBookingConfirmationHtml(event), "booking confirmation");
    }

    @Async
    public void sendCancellationConfirmationToPatient(AppointmentCancelledEvent event) {
        String subject = "Appointment Cancelled — " + event.getDoctorName();
        sendEmail(event.getPatientEmail(), subject, buildCancellationConfirmationHtml(event), "cancellation confirmation");
    }

    private void sendEmail(String to, String subject, String htmlBody, String context) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            logger.info("[EMAIL] Sent {} to '{}'", context, to);
        } catch (MailException | MessagingException ex) {
            logger.error("[EMAIL] Failed to send {} to '{}': {}", context, to, ex.getMessage(), ex);
        }
    }

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