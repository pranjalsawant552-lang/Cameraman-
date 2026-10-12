package com.cameraman.booking.domain;

/**
 * The end-to-end order pipeline a confirmed booking moves through, from
 * confirmation to final delivery. This is deliberately separate from
 * {@link BookingStatus} — BookingStatus is the coarse booking lifecycle
 * (Pending/Confirmed/Cancelled/etc.), while OrderStage tracks fine-grained
 * fulfilment progress *after* a booking is confirmed, matching the client
 * journey shown on the client-facing status tracker.
 *
 * Stages are ordered; {@link #ordinal()} is used to render a progress
 * stepper and to prevent moving a stage backward by accident (staff can
 * still explicitly re-open a stage if genuinely needed — see
 * BookingService.updateOrderStage).
 */
public enum OrderStage {
    BOOKING_CONFIRMED("Booking Confirmed"),
    ADVANCE_PAID("Advance Paid"),
    SHOOT_DONE("Shoot Done"),
    PHOTOS_UPLOADED("Photos Uploaded"),
    SELECTION_IN_PROGRESS("Selection In Progress"),
    SELECTION_LOCKED("Selection Locked"),
    ALBUM_DESIGNING("Album Designing"),
    PRINTING("Printing"),
    QUALITY_CHECK("Quality Check"),
    DISPATCHED("Dispatched"),
    DELIVERED("Delivered");

    private final String label;

    OrderStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
