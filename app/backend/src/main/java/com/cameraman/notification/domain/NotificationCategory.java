package com.cameraman.notification.domain;

/**
 * What a user can switch off. {@code mandatory} categories (payments, account/security, staff work
 * items) are always delivered: turning off a payment receipt or a leave decision would only hurt the user.
 */
public enum NotificationCategory {
    BOOKINGS("Booking updates", "Confirmations, reschedules and cancellations", false),
    ORDER_UPDATES("Order progress", "Photo selection, album and delivery progress", false),
    GALLERY("Gallery ready", "When your photos are published", false),
    MARKETING("Reminders & suggestions", "Birthday and anniversary reminders, offers", false),
    PAYMENTS("Payments", "Receipts and payment confirmations", true),
    SYSTEM("Account & work alerts", "Security, leave and staff work items", true);

    private final String label;
    private final String description;
    private final boolean mandatory;

    NotificationCategory(String label, String description, boolean mandatory) {
        this.label = label;
        this.description = description;
        this.mandatory = mandatory;
    }

    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public boolean isMandatory() { return mandatory; }

    /** Maps a notification {@code type} string (as passed to the publisher) to its category. */
    public static NotificationCategory forType(String type) {
        if (type == null) return SYSTEM;
        if (type.startsWith("BOOKING_")) return BOOKINGS;
        if (type.startsWith("ORDER_") || type.startsWith("SELECTION_")) return ORDER_UPDATES;
        if (type.startsWith("GALLERY_")) return GALLERY;
        if (type.startsWith("SUGGESTION")) return MARKETING;
        if (type.startsWith("PAYMENT_")) return PAYMENTS;
        return SYSTEM;
    }
}
