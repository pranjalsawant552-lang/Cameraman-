package com.cameraman.engagement.domain;

public enum OutreachStatus {
    PENDING_REVIEW, APPROVED_SENT, EDITED_SENT, DISMISSED, FAILED,
    /** Sent immediately by the suggestion engine itself, no staff review — see
     *  SuggestionRule.autoSend. Only used when the rule is marked auto-send AND the
     *  candidate has opted in to marketing/re-engagement contact. */
    AUTO_SENT,
    /** Auto-send was attempted (rule + consent both allowed it) but delivery failed
     *  (e.g. WhatsApp/SMS provider error) — left here for staff to notice and send
     *  manually, rather than silently disappearing. */
    AUTO_SEND_FAILED
}
