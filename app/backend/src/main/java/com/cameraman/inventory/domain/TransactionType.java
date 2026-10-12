package com.cameraman.inventory.domain;

import java.util.Locale;

public enum TransactionType {
    PURCHASE, SALE, ADJUSTMENT_IN, ADJUSTMENT_OUT, RETURN, DAMAGE, TRANSFER;

    /**
     * Lenient parse. The UI historically sent STOCK_IN / STOCK_OUT / RETURNED / DAMAGED, which are
     * not enum names, so every "Adjust stock" call failed with an IllegalArgumentException (HTTP 500).
     * Accept both vocabularies; returns null when unknown so the caller can answer with a clean 400.
     */
    public static TransactionType parse(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "STOCK_IN", "IN", "RESTOCK" -> PURCHASE;
            case "STOCK_OUT", "OUT", "USED" -> ADJUSTMENT_OUT;
            case "RETURNED" -> RETURN;
            case "DAMAGED", "WRITE_OFF" -> DAMAGE;
            default -> {
                try { yield valueOf(v); } catch (IllegalArgumentException e) { yield null; }
            }
        };
    }

    /** True when this movement increases the quantity on hand. */
    public boolean isInbound() {
        return this == PURCHASE || this == ADJUSTMENT_IN || this == RETURN;
    }
}
