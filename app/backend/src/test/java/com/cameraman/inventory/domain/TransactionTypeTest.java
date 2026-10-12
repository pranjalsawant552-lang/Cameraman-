package com.cameraman.inventory.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionTypeTest {
    @Test void acceptsTheNamesTheUiHistoricallySent() {
        assertEquals(TransactionType.PURCHASE, TransactionType.parse("STOCK_IN"));
        assertEquals(TransactionType.ADJUSTMENT_OUT, TransactionType.parse("stock_out"));
        assertEquals(TransactionType.RETURN, TransactionType.parse("RETURNED"));
        assertEquals(TransactionType.DAMAGE, TransactionType.parse("DAMAGED"));
    }
    @Test void acceptsEnumNamesAndRejectsUnknown() {
        assertEquals(TransactionType.SALE, TransactionType.parse("SALE"));
        assertNull(TransactionType.parse("NOPE"));
        assertNull(TransactionType.parse(null));
    }
    @Test void inboundDirection() {
        assertTrue(TransactionType.PURCHASE.isInbound());
        assertFalse(TransactionType.SALE.isInbound());
    }
}
