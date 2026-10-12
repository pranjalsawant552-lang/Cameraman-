package com.cameraman.billing.service;

import com.cameraman.billing.domain.Invoice;
import com.cameraman.billing.domain.InvoiceItem;
import com.cameraman.settings.service.StudioSettingsService;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.layout.font.FontProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private final TemplateEngine templateEngine;
    private final StudioSettingsService settingsService;

    /**
     * iText's default font set has no Devanagari glyphs, so the bill-book template
     * (Marathi labels: कॅमेरामन फोटो स्टुडिओ, अ.क्र., तपशील, एकूण/जमा/बाकी, etc.) would
     * render as blank boxes without this. The font bytes are read from the classpath
     * once at startup and kept in memory; a fresh FontProvider is built from them for
     * every PDF render (see generatePdf) because FontProvider is stateful internally
     * and iText's own guidance is not to share one instance across concurrent
     * conversions on a multi-threaded server.
     */
    private byte[] devanagariRegular;
    private byte[] devanagariBold;

    @PostConstruct
    void loadFonts() {
        try {
            devanagariRegular = readFont("fonts/NotoSansDevanagari-Regular.ttf");
            devanagariBold = readFont("fonts/NotoSansDevanagari-Bold.ttf");
        } catch (Exception e) {
            log.error("Could not load Devanagari font for invoice PDFs — Marathi text will not render correctly: {}",
                    e.getMessage(), e);
        }
    }

    private byte[] readFont(String classpathLocation) throws Exception {
        try (InputStream is = new ClassPathResource(classpathLocation).getInputStream()) {
            return is.readAllBytes();
        }
    }

    private FontProvider buildFontProvider() {
        FontProvider provider = new FontProvider();
        provider.addStandardPdfFonts();
        if (devanagariRegular != null) {
            provider.addFont(devanagariRegular);
        }
        if (devanagariBold != null) {
            provider.addFont(devanagariBold);
        }
        return provider;
    }

    public byte[] generatePdf(Invoice invoice) {
        try {
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("invoice", invoice);
            ctx.setVariable("studio", settingsService.getStudioSettings());
            ctx.setVariable("items", invoice.getItems());

            String format = invoice.getSizeFormat();
            String template = "A6".equals(format) ? "invoice-a6"
                    : "BILLBOOK".equals(format) ? "invoice-billbook"
                    : "invoice-a4";
            String html = templateEngine.process(template, ctx);

            ConverterProperties props = new ConverterProperties();
            props.setFontProvider(buildFontProvider());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HtmlConverter.convertToPdf(html, out, props);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating invoice PDF for {}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
            throw new com.cameraman.common.exception.BusinessException(
                    "Failed to generate PDF: " + e.getMessage(), "PDF_ERROR");
        }
    }
}
