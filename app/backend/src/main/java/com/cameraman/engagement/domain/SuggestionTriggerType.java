package com.cameraman.engagement.domain;

public enum SuggestionTriggerType {
    /** offsetDays relative to a date answered in an intake question (e.g. "expected
     *  delivery date" minus 60 days -> suggest a maternity/newborn package). */
    RELATIVE_TO_ANSWER_DATE,
    /** offsetDays relative to the booking's own shoot date (e.g. birthday shoot
     *  date + 358 days -> "book next year's birthday shoot" reminder). */
    RELATIVE_TO_BOOKING_DATE,
    /** offsetDays relative to the booking's actual delivery date (order stage
     *  DELIVERED) — e.g. +5 days -> "how did we do?" review request. */
    RELATIVE_TO_DELIVERY_DATE
}
