package com.cameraman.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** GST split, rounding and discount-order rules for Invoice.calculateTotals(). */
class InvoiceGstTest {

    private InvoiceItem item(String unitPrice, int qty, String linePct) {
        InvoiceItem i = InvoiceItem.builder().description("x").quantity(qty)
                .unitPrice(new BigDecimal(unitPrice))
                .discountPercent(new BigDecimal(linePct)).build();
        i.calcLineTotal();
        return i;
    }

    private Invoice invoice(String cgst, String sgst, String igst, InvoiceItem... items) {
        Invoice inv = Invoice.builder().clientName("c").invoiceDate(LocalDate.now())
                .cgstRate(new BigDecimal(cgst)).sgstRate(new BigDecimal(sgst)).igstRate(new BigDecimal(igst)).build();
        for (InvoiceItem i : items) inv.getItems().add(i);
        return inv;
    }

    @Test
    void intraState_splitsEvenlyIntoCgstAndSgst() {
        Invoice inv = invoice("9.00", "9.00", "0.00", item("1000.00", 1, "0"));
        inv.calculateTotals();
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("90.00");
        assertThat(inv.getSgstAmount()).isEqualByComparingTo("90.00");
        assertThat(inv.getIgstAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("1180.00");
    }

    @Test
    void interState_isPureIgst() {
        Invoice inv = invoice("0.00", "0.00", "18.00", item("1000.00", 1, "0"));
        inv.calculateTotals();
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getSgstAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getIgstAmount()).isEqualByComparingTo("180.00");
    }

    @Test
    void exempt_hasNoTax() {
        Invoice inv = invoice("0.00", "0.00", "0.00", item("1234.00", 1, "0"));
        inv.calculateTotals();
        assertThat(inv.getTotalTax()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("1234.00");
    }

    @Test
    void taxComponents_areRoundedToTwoDecimals_andSumExactlyToTotalTax() {
        // 333.33 x 9% = 29.9997 -> 30.00 each; total tax must equal the sum of the rounded parts
        Invoice inv = invoice("9.00", "9.00", "0.00", item("333.33", 1, "0"));
        inv.calculateTotals();
        assertThat(inv.getCgstAmount().scale()).isEqualTo(2);
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("30.00");
        assertThat(inv.getSgstAmount()).isEqualByComparingTo("30.00");
        assertThat(inv.getTotalTax()).isEqualByComparingTo(inv.getCgstAmount().add(inv.getSgstAmount()));
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("393.33");
    }

    @Test
    void halfUpRounding_onTheThirdDecimal() {
        // 0.05 x 9% = 0.0045 -> 0.00 ; 0.50 x 9% = 0.045 -> 0.05 (HALF_UP)
        Invoice inv = invoice("9.00", "9.00", "0.00", item("0.50", 1, "0"));
        inv.calculateTotals();
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("0.05");
    }

    @Test
    void lineDiscount_thenInvoicePercent_thenFlat_thenTax() {
        // line: 2 x 1000 less 10% = 1800 ; invoice 10% = 180 ; flat 120 -> taxable 1500 ; 18% GST = 270
        Invoice inv = invoice("9.00", "9.00", "0.00", item("1000.00", 2, "10"));
        inv.setDiscountPercent(new BigDecimal("10"));
        inv.setDiscountAmount(new BigDecimal("120.00"));
        inv.calculateTotals();
        assertThat(inv.getSubtotal()).isEqualByComparingTo("1800.00");
        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("1500.00");
        assertThat(inv.getTotalTax()).isEqualByComparingTo("270.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("1770.00");
    }

    @Test
    void discountLargerThanSubtotal_isCappedAndNeverGoesNegative() {
        Invoice inv = invoice("9.00", "9.00", "0.00", item("500.00", 1, "0"));
        inv.setDiscountAmount(new BigDecimal("9999.00"));
        inv.calculateTotals();
        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getTotalAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void negativeFlatDiscount_isIgnored() {
        Invoice inv = invoice("0.00", "0.00", "0.00", item("500.00", 1, "0"));
        inv.setDiscountAmount(new BigDecimal("-50.00"));
        inv.calculateTotals();
        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void balanceDue_tracksAmountPaid() {
        Invoice inv = invoice("9.00", "9.00", "0.00", item("1000.00", 1, "0"));
        inv.setAmountPaid(new BigDecimal("500.00"));
        inv.calculateTotals();
        assertThat(inv.getBalanceDue()).isEqualByComparingTo("680.00");
    }

    @Test
    void calculatingTwice_isStable() {
        Invoice inv = invoice("9.00", "9.00", "0.00", item("999.99", 3, "5"));
        inv.setDiscountPercent(new BigDecimal("2.5"));
        inv.calculateTotals();
        BigDecimal first = inv.getTotalAmount();
        inv.calculateTotals();
        assertThat(inv.getTotalAmount()).isEqualByComparingTo(first);
    }
}
