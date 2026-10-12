package com.cameraman.notification.service;

import com.cameraman.booking.domain.Booking;
import com.cameraman.photoselection.domain.PhotoSelection;
import com.cameraman.whatsapp.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailService emailService;
    private final WhatsAppService whatsAppService;

    @Async
    public void sendBookingConfirmation(Booking booking) {
        String clientName = booking.getClientName();
        String mobile = booking.getClientMobile();
        String email = booking.getClientEmail();
        String bookingNum = booking.getBookingNumber();
        String date = booking.getBookingDate().toString();
        String time = booking.getStartTime() != null ? booking.getStartTime().toString() : "";
        String pkg = booking.getPackageName() != null ? booking.getPackageName() : booking.getType().name();

        if (mobile != null) {
            whatsAppService.sendBookingConfirmation(mobile, clientName, bookingNum, date, time, pkg);
        }
        if (email != null) {
            emailService.sendBookingConfirmationEmail(email, booking);
        }
    }

    @Async
    public void sendBookingRescheduled(Booking booking, String reason) {
        String message = "Your booking " + booking.getBookingNumber() +
                " has been rescheduled to " + booking.getBookingDate() +
                " at " + booking.getStartTime() + ". Reason: " + reason;
        if (booking.getClientMobile() != null) {
            whatsAppService.sendTextMessage(booking.getClientMobile(), message);
        }
    }

    @Async
    public void sendBookingCancelled(Booking booking) {
        String message = "Your booking " + booking.getBookingNumber() +
                " has been cancelled. Reason: " + booking.getCancelledReason() +
                ". Please contact us for rescheduling or refunds.";
        if (booking.getClientMobile() != null) {
            whatsAppService.sendTextMessage(booking.getClientMobile(), message);
        }
    }

    @Async
    public void sendGalleryPublished(String mobile, String email, String clientName,
                                      String galleryToken, String galleryName, String studioName) {
        if (mobile != null) {
            whatsAppService.sendGalleryLink(mobile, clientName, galleryToken, studioName);
        }
        if (email != null) {
            emailService.sendGalleryReadyEmail(email, clientName, galleryToken, galleryName);
        }
    }

    @Async
    public void sendSelectionLockedNotification(PhotoSelection selection) {
        log.info("Selection {} locked by client {} with {} photos selected",
                selection.getAccessToken(), selection.getClientName(), selection.getSelectedCount());
        // Notify admin/photographer about locked selection
    }

    @Async
    public void sendPaymentConfirmation(String mobile, String email, String clientName,
                                         String invoiceNumber, java.math.BigDecimal amount) {
        String message = String.format("Payment of ₹%s received for invoice %s. Thank you, %s!",
                amount.toPlainString(), invoiceNumber, clientName);
        if (mobile != null) {
            whatsAppService.sendTextMessage(mobile, message);
        }
    }

    @Async
    public void sendOrderStageUpdate(Booking booking, com.cameraman.booking.domain.OrderStage stage) {
        String message = String.format(
                "Hi %s, your order %s has moved to the next stage: *%s*. We'll keep you posted!",
                booking.getClientName(), booking.getBookingNumber(), stage.getLabel());
        if (booking.getClientMobile() != null) {
            whatsAppService.sendTextMessage(booking.getClientMobile(), message);
        }
    }
}
