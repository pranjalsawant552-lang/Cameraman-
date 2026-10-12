package com.cameraman.engagement.domain;

/** Which channel a suggestion rule's message goes out on, whether sent by an
 *  admin from the review queue or auto-sent by the engine directly. EMAIL is
 *  deliberately not offered here: the free-text template messages this engine
 *  produces don't have an email layout/template behind them the way
 *  transactional emails (booking confirmation, invoice, etc.) do, so EMAIL
 *  outreach is only wired up in the channel-specific NotificationService
 *  flows, not the generic template flow this module drives. */
public enum OutreachChannel {
    WHATSAPP, SMS
}
