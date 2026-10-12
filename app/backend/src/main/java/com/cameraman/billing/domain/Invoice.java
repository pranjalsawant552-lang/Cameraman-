package com.cameraman.billing.domain;

import com.cameraman.common.domain.BaseAuditEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices", indexes = {
    @Index(name = "idx_invoice_number", columnList = "invoice_number", unique = true),
    @Index(name = "idx_invoice_booking", columnList = "booking_id"),
    @Index(name = "idx_invoice_client", columnList = "client_id"),
    @Index(name = "idx_invoice_status", columnList = "status"),
    @Index(name = "idx_invoice_date", columnList = "invoice_date")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Invoice extends BaseAuditEntity {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 25)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_type", nullable = false)
    @Builder.Default
    private InvoiceType invoiceType = InvoiceType.ONLINE;

    @Column(name = "billing_type", length = 50)
    private String billingType; // PACKAGE, ALBUM, FRAME, PRODUCT, PASSPORT_PHOTO, WALK_IN

    @Column(name = "booking_id", length = 36)
    private String bookingId;

    @Column(name = "client_id", length = 36)
    private String clientId;

    @Column(name = "client_name", nullable = false, length = 120)
    private String clientName;

    @Column(name = "client_mobile", length = 15)
    private String clientMobile;

    @Column(name = "client_email", length = 150)
    private String clientEmail;

    @Column(name = "client_address", columnDefinition = "TEXT")
    private String clientAddress;

    @Column(name = "client_gstin", length = 20)
    private String clientGstin;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "taxable_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "cgst_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal cgstRate = new BigDecimal("9.00");

    @Column(name = "sgst_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal sgstRate = new BigDecimal("9.00");

    @Column(name = "igst_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal igstRate = BigDecimal.ZERO;

    @Column(name = "cgst_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(name = "sgst_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "igst_amount", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(name = "total_tax", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalTax = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "amount_paid", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "balance_due", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal balanceDue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "terms_conditions", columnDefinition = "TEXT")
    private String termsConditions;

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "pdf_s3_key", length = 500)
    private String pdfS3Key;

    @Column(name = "whatsapp_sent")
    @Builder.Default
    private boolean whatsappSent = false;

    @Column(name = "email_sent")
    @Builder.Default
    private boolean emailSent = false;

    @Column(name = "size_format", length = 10)
    @Builder.Default
    private String sizeFormat = "A4"; // A4, A6, or BILLBOOK

    /** Who generated this bill, captured by name at the moment of creation — not just the
     *  raw user ID that BaseAuditEntity.createdBy already tracks for the audit trail.
     *  Stored as text (not just a foreign key lookup) so it stays accurate on the printed
     *  invoice even if that staff member is later renamed or their account is removed. */
    @Column(name = "generated_by_name", length = 150)
    private String generatedByName;

    @Column(name = "generated_by_user_id", length = 36)
    private String generatedByUserId;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Order of operations (this is what a client is actually charged, so it is pinned by tests):
     * <ol>
     *   <li>each line: qty x unit price, less its own line-discount %  (see InvoiceItem.calcLineTotal)</li>
     *   <li>subtotal = sum of line totals</li>
     *   <li>invoice discount = (discountPercent % of subtotal) + flat discountAmount,
     *       never more than the subtotal, never negative</li>
     *   <li>taxable = subtotal - invoice discount</li>
     *   <li>CGST / SGST / IGST are each rounded to 2 dp (HALF_UP) on the taxable amount, so the
     *       printed components always add up exactly to the total tax</li>
     * </ol>
     */
    public void calculateTotals() {
        subtotal = money(items.stream()
                .map(InvoiceItem::getLineTotal)
                .filter(java.util.Objects::nonNull) // an item mid-entry with no price yet has a null
                                                      // lineTotal — skip it rather than NPE the whole invoice
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        BigDecimal pct = discountPercent != null ? discountPercent : BigDecimal.ZERO;
        BigDecimal flat = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        BigDecimal discount = money(subtotal.multiply(pct).divide(HUNDRED)).add(flat.max(BigDecimal.ZERO));
        discount = money(discount.min(subtotal).max(BigDecimal.ZERO));

        taxableAmount = subtotal.subtract(discount);
        cgstAmount = money(taxableAmount.multiply(cgstRate).divide(HUNDRED));
        sgstAmount = money(taxableAmount.multiply(sgstRate).divide(HUNDRED));
        igstAmount = money(taxableAmount.multiply(igstRate).divide(HUNDRED));
        totalTax = cgstAmount.add(sgstAmount).add(igstAmount);
        totalAmount = taxableAmount.add(totalTax);
        balanceDue = totalAmount.subtract(amountPaid);
    }
}
