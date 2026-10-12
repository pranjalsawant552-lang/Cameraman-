package com.cameraman.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These are plain unit tests — no Spring context, no database — deliberately, so they run
 * in milliseconds and can't be broken by infrastructure being unavailable. That also means
 * they're the cheapest possible way to lock down the one piece of this app where a silent
 * bug costs real money: what a client actually gets charged.
 *
 * Every case below was hand-calculated in the review that led to writing these (see the
 * comment above each assertion), specifically so a future change to calculateTotals() or
 * calcLineTotal() that quietly changes the math gets caught immediately instead of showing
 * up as a wrong number on a client's invoice.
 */
class InvoiceCalculationTest {

    @Test
    void singleItem_defaultGst_noDiscounts() {
        InvoiceItem item = InvoiceItem.builder()
                .description("5x7 Photo Print")
                .quantity(2)
                .unitPrice(new BigDecimal("1000.00"))
                .build();
        item.calcLineTotal();
        assertThat(item.getLineTotal()).isEqualByComparingTo("2000.00"); // 1000 x 2

        Invoice invoice = Invoice.builder().clientName("Test Client").invoiceDate(java.time.LocalDate.now()).build();
        invoice.getItems().add(item);
        invoice.calculateTotals();

        assertThat(invoice.getSubtotal()).isEqualByComparingTo("2000.00");
        assertThat(invoice.getTaxableAmount()).isEqualByComparingTo("2000.00");
        // Default split is 9% CGST + 9% SGST = 18% total, matching the studio's flat GST rate
        assertThat(invoice.getCgstAmount()).isEqualByComparingTo("180.00");
        assertThat(invoice.getSgstAmount()).isEqualByComparingTo("180.00");
        assertThat(invoice.getIgstAmount()).isEqualByComparingTo("0.00");
        assertThat(invoice.getTotalTax()).isEqualByComparingTo("360.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("2360.00"); // 2000 + 18%
        assertThat(invoice.getBalanceDue()).isEqualByComparingTo("2360.00"); // nothing paid yet
    }

    @Test
    void itemLevelDiscount_reducesLineTotalBeforeInvoiceDiscount() {
        InvoiceItem item = InvoiceItem.builder()
                .description("Album Upgrade")
                .quantity(3)
                .unitPrice(new BigDecimal("500.00"))
                .discountPercent(new BigDecimal("10.00"))
                .build();
        item.calcLineTotal();
        // gross = 500 x 3 = 1500; 10% off = 150 off; line total = 1350
        assertThat(item.getLineTotal()).isEqualByComparingTo("1350.00");
    }

    @Test
    void invoiceLevelDiscountAmount_appliedBeforeTax() {
        InvoiceItem item = InvoiceItem.builder()
                .description("Wedding Package").quantity(1).unitPrice(new BigDecimal("2000.00")).build();
        item.calcLineTotal();

        Invoice invoice = Invoice.builder().clientName("Test Client").invoiceDate(java.time.LocalDate.now())
                .discountAmount(new BigDecimal("200.00"))
                .build();
        invoice.getItems().add(item);
        invoice.calculateTotals();

        assertThat(invoice.getSubtotal()).isEqualByComparingTo("2000.00");
        assertThat(invoice.getTaxableAmount()).isEqualByComparingTo("1800.00"); // 2000 - 200 discount
        assertThat(invoice.getCgstAmount()).isEqualByComparingTo("162.00"); // 9% of 1800
        assertThat(invoice.getSgstAmount()).isEqualByComparingTo("162.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("2124.00"); // 1800 + 18%
    }

    @Test
    void partialPayment_reflectsInBalanceDue() {
        InvoiceItem item = InvoiceItem.builder()
                .description("Portrait Session").quantity(1).unitPrice(new BigDecimal("2000.00")).build();
        item.calcLineTotal();

        Invoice invoice = Invoice.builder().clientName("Test Client").invoiceDate(java.time.LocalDate.now())
                .amountPaid(new BigDecimal("1000.00"))
                .build();
        invoice.getItems().add(item);
        invoice.calculateTotals();

        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("2360.00"); // 2000 + 18%
        assertThat(invoice.getBalanceDue()).isEqualByComparingTo("1360.00"); // 2360 - 1000 paid
    }

    @Test
    void interState_usesIgstInsteadOfCgstSgst() {
        InvoiceItem item = InvoiceItem.builder()
                .description("Outdoor Shoot Package").quantity(1).unitPrice(new BigDecimal("1000.00")).build();
        item.calcLineTotal();

        // Inter-state billing: IGST replaces the CGST+SGST split, not additive to it —
        // this only happens when the caller explicitly sets these rates (createInvoice
        // doesn't do this automatically today; see the enhancement note in BillingService).
        Invoice invoice = Invoice.builder().clientName("Test Client").invoiceDate(java.time.LocalDate.now())
                .cgstRate(BigDecimal.ZERO).sgstRate(BigDecimal.ZERO).igstRate(new BigDecimal("18.00"))
                .build();
        invoice.getItems().add(item);
        invoice.calculateTotals();

        assertThat(invoice.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(invoice.getSgstAmount()).isEqualByComparingTo("0.00");
        assertThat(invoice.getIgstAmount()).isEqualByComparingTo("180.00"); // 18% of 1000
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("1180.00");
    }

    @Test
    void multipleItems_subtotalIsSumOfLineTotals() {
        InvoiceItem item1 = InvoiceItem.builder().description("Print").quantity(1).unitPrice(new BigDecimal("500.00")).build();
        InvoiceItem item2 = InvoiceItem.builder().description("Frame").quantity(1).unitPrice(new BigDecimal("300.00")).build();
        item1.calcLineTotal();
        item2.calcLineTotal();

        Invoice invoice = Invoice.builder().clientName("Test Client").invoiceDate(java.time.LocalDate.now()).build();
        invoice.getItems().add(item1);
        invoice.getItems().add(item2);
        invoice.calculateTotals();

        assertThat(invoice.getSubtotal()).isEqualByComparingTo("800.00");
    }

    @Test
    void zeroQuantityOrNullUnitPrice_doesNotThrow() {
        // A defensive case: an item saved mid-edit with no price yet shouldn't blow up
        // calculateTotals() for the rest of the invoice.
        InvoiceItem incomplete = InvoiceItem.builder().description("TBD").quantity(1).build();
        incomplete.calcLineTotal();
        assertThat(incomplete.getLineTotal()).isNull();
    }

    @Test
    void incompleteItemInMix_isSkippedNotThrown() {
        // Reproduces the bug above at the invoice level: a priced item alongside an
        // unpriced one used to NPE the entire calculateTotals() call.
        InvoiceItem priced = InvoiceItem.builder().description("Print").quantity(1).unitPrice(new BigDecimal("500.00")).build();
        priced.calcLineTotal();
        InvoiceItem unpriced = InvoiceItem.builder().description("TBD").quantity(1).build();
        unpriced.calcLineTotal();

        Invoice invoice = Invoice.builder().clientName("Test Client").invoiceDate(java.time.LocalDate.now()).build();
        invoice.getItems().add(priced);
        invoice.getItems().add(unpriced);

        invoice.calculateTotals(); // must not throw
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("500.00");
    }
}
