package com.cameraman.notification.service;

import com.cameraman.booking.domain.Booking;
import com.cameraman.photoselection.domain.PhotoSelection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.name:Cameraman Photo Studio}")
    private String appName;

    @Value("${app.base-url:http://localhost:4200}")
    private String baseUrl;

    @Async
    public void sendOtpEmail(String toEmail, String otp, String purpose) {
        try {
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("otp", otp);
            ctx.setVariable("purpose", purpose.replace("_", " ").toLowerCase());
            ctx.setVariable("expiryMinutes", 10);
            ctx.setVariable("appName", appName);
            String html = templateEngine.process("email/otp", ctx);
            sendHtmlEmail(toEmail, "Your OTP - " + appName, html);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String name, String resetToken) {
        try {
            String resetUrl = baseUrl + "/reset-password?token=" + resetToken;
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("name", name);
            ctx.setVariable("resetUrl", resetUrl);
            ctx.setVariable("appName", appName);
            String html = templateEngine.process("email/password-reset", ctx);
            sendHtmlEmail(toEmail, "Reset Your Password - " + appName, html);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendBookingConfirmationEmail(String toEmail, Booking booking) {
        try {
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("booking", booking);
            ctx.setVariable("appName", appName);
            String html = templateEngine.process("email/booking-confirmation", ctx);
            sendHtmlEmail(toEmail, "Booking Confirmed #" + booking.getBookingNumber(), html);
        } catch (Exception e) {
            log.error("Failed to send booking confirmation email: {}", e.getMessage());
        }
    }

    /** Synchronous variant that THROWS on failure, so callers can tell the user the truth. */
    public void sendInvoiceEmailNow(String toEmail, String clientName, String invoiceNumber, byte[] pdfBytes) throws MessagingException {
        Context ctx = new Context(Locale.ENGLISH);
        ctx.setVariable("clientName", clientName);
        ctx.setVariable("invoiceNumber", invoiceNumber);
        ctx.setVariable("appName", appName);
        String html = templateEngine.process("email/invoice", ctx);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(toEmail);
        helper.setSubject("Invoice " + invoiceNumber + " - " + appName);
        helper.setText(html, true);
        helper.addAttachment("invoice-" + invoiceNumber + ".pdf", () -> new java.io.ByteArrayInputStream(pdfBytes));
        mailSender.send(message);
        log.info("Invoice email sent to {}", toEmail);
    }

    /** Returns true only if the mail server accepted the message. */
    public boolean sendPickupReminderEmail(String toEmail, String clientName, String bookingNumber, long daysWaiting) {
        try {
            String waiting = daysWaiting > 0 ? "<p>They have been ready for " + daysWaiting + " day" + (daysWaiting == 1 ? "" : "s") + ".</p>" : "";
            String html = "<p>Hi " + escape(clientName) + ",</p>"
                    + "<p>Your photos/order <b>" + escape(bookingNumber) + "</b> are ready for pickup at the studio.</p>"
                    + waiting
                    + "<p>Please visit us at your convenience to collect them. Thank you!</p>";
            sendHtmlEmail(toEmail, "Your photos are ready for pickup - " + appName, html);
            return true;
        } catch (Exception e) {
            log.error("Failed to send pickup reminder email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }

    @Async
    public void sendInvoiceEmail(String toEmail, String clientName, String invoiceNumber, byte[] pdfBytes) {
        try {
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("clientName", clientName);
            ctx.setVariable("invoiceNumber", invoiceNumber);
            ctx.setVariable("appName", appName);
            String html = templateEngine.process("email/invoice", ctx);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject("Invoice " + invoiceNumber + " - " + appName);
            helper.setText(html, true);
            helper.addAttachment("invoice-" + invoiceNumber + ".pdf",
                    () -> new java.io.ByteArrayInputStream(pdfBytes));
            mailSender.send(message);
            log.info("Invoice email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send invoice email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Async
    public void sendGalleryReadyEmail(String toEmail, String clientName, String galleryToken, String galleryName) {
        try {
            String galleryUrl = baseUrl + "/gallery/" + galleryToken;
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("clientName", clientName);
            ctx.setVariable("galleryName", galleryName);
            ctx.setVariable("galleryUrl", galleryUrl);
            ctx.setVariable("appName", appName);
            String html = templateEngine.process("email/gallery-ready", ctx);
            sendHtmlEmail(toEmail, "Your Gallery is Ready! - " + appName, html);
        } catch (Exception e) {
            log.error("Failed to send gallery ready email: {}", e.getMessage());
        }
    }

    @Async
    public void sendSelectionReminderEmail(String toEmail, String clientName, PhotoSelection selection) {
        try {
            String selectionUrl = baseUrl + "/selection/" + selection.getAccessToken();
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("clientName", clientName);
            ctx.setVariable("selectionUrl", selectionUrl);
            ctx.setVariable("remaining", selection.getSelectionLimit() != null
                    ? selection.getSelectionLimit() - selection.getSelectedCount() : "some");
            ctx.setVariable("appName", appName);
            String html = templateEngine.process("email/selection-reminder", ctx);
            sendHtmlEmail(toEmail, "Complete Your Photo Selection - " + appName, html);
        } catch (Exception e) {
            log.error("Failed to send selection reminder email: {}", e.getMessage());
        }
    }

    @Value("${app.contact.notify-email:}")
    private String contactNotifyEmail;

    @Async
    public void sendContactInquiryNotification(String inquirerName, String inquirerEmail, String inquirerMobile, String message) {
        if (contactNotifyEmail == null || contactNotifyEmail.isBlank()) {
            log.info("app.contact.notify-email is not configured; skipping contact inquiry email notification " +
                    "(the inquiry is still saved and visible in the admin Contact Inquiries list).");
            return;
        }
        try {
            String html = "<h3>New contact form inquiry</h3>"
                    + "<p><b>Name:</b> " + escape(inquirerName) + "</p>"
                    + "<p><b>Email:</b> " + escape(inquirerEmail) + "</p>"
                    + "<p><b>Mobile:</b> " + escape(inquirerMobile == null ? "-" : inquirerMobile) + "</p>"
                    + "<p><b>Message:</b><br/>" + escape(message).replace("\n", "<br/>") + "</p>";
            sendHtmlEmail(contactNotifyEmail, "New Contact Inquiry - " + appName, html);
        } catch (Exception e) {
            log.error("Failed to send contact inquiry notification email: {}", e.getMessage());
        }
    }

    /** Sends a staff reply to a contact inquiry. Unlike the notification emails this one throws on failure,
     *  so a reply is never recorded as "emailed" when it was not. */
    public void sendInquiryReply(String toEmail, String name, String replyText, String originalMessage) {
        try {
            String html = "<p>Hi " + escape(name) + ",</p>"
                    + "<p>" + escape(replyText).replace("\n", "<br/>") + "</p>"
                    + "<hr/><p style=\"color:#888;font-size:12px\">Your message:<br/>"
                    + escape(originalMessage).replace("\n", "<br/>") + "</p>";
            sendHtmlEmail(toEmail, "Re: your enquiry - " + appName, html);
        } catch (Exception e) {
            log.error("Failed to send inquiry reply email: {}", e.getMessage());
            throw new com.cameraman.common.exception.BusinessException(
                    "The email could not be sent. Check the mail settings, or log the reply without sending.", "EMAIL_SEND_FAILED");
        }
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(message);
        log.info("Email sent to {} - Subject: {}", to, subject);
    }
}
