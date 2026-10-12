package com.cameraman.report.service;

import com.cameraman.billing.repository.InvoiceRepository;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.inventory.repository.InventoryRepository;
import com.cameraman.report.dto.ReportDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final BookingRepository bookingRepository;
    private final InvoiceRepository invoiceRepository;
    private final InventoryRepository inventoryRepository;
    private final com.cameraman.gallery.repository.GalleryRepository galleryRepository;
    private final com.cameraman.user.repository.UserRepository userRepository;
    private final com.cameraman.studio.repository.StudioSessionRepository studioSessionRepository;
    private final com.cameraman.outdoor.repository.OutdoorShootRepository outdoorShootRepository;
    private final com.cameraman.photoselection.repository.PhotoSelectionRepository photoSelectionRepository;

    public byte[] generateBookingsReport(LocalDate from, LocalDate to) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Bookings");

            // Header style
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle dataStyle = createDataStyle(workbook);
            XSSFCellStyle dateStyle = createDataStyle(workbook);

            // Title row
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Bookings Report - " + from + " to " + to);
            CellRangeAddress titleRange = new CellRangeAddress(0, 0, 0, 9);
            sheet.addMergedRegion(titleRange);

            // Header row
            String[] headers = {"Booking #", "Client Name", "Mobile", "Date", "Package", "Type",
                    "Status", "Photographer", "Total Amount", "Balance Due"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            var bookings = bookingRepository.findByBookingDateBetween(from, to);
            int rowNum = 3;
            for (var booking : bookings) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(booking.getBookingNumber());
                row.createCell(1).setCellValue(booking.getClientName() != null ? booking.getClientName() : "");
                row.createCell(2).setCellValue(booking.getClientMobile() != null ? booking.getClientMobile() : "");
                row.createCell(3).setCellValue(booking.getBookingDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                row.createCell(4).setCellValue(booking.getPackageName() != null ? booking.getPackageName() : "");
                row.createCell(5).setCellValue(booking.getType().name());
                row.createCell(6).setCellValue(booking.getStatus().name());
                row.createCell(7).setCellValue(booking.getPhotographer() != null ? booking.getPhotographer().getFullName() : "");
                row.createCell(8).setCellValue(booking.getTotalAmount() != null ? booking.getTotalAmount().doubleValue() : 0);
                row.createCell(9).setCellValue(booking.getBalanceAmount() != null ? booking.getBalanceAmount().doubleValue() : 0);
                for (int i = 0; i < headers.length; i++) row.getCell(i).setCellStyle(dataStyle);
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating bookings report: {}", e.getMessage(), e);
            throw new com.cameraman.common.exception.BusinessException("Failed to generate report", "REPORT_ERROR");
        }
    }

    public byte[] generateRevenueReport(LocalDate from, LocalDate to) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Revenue");
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle dataStyle = createDataStyle(workbook);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Revenue Report - " + from + " to " + to);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

            String[] headers = {"Invoice #", "Date", "Client Name", "Mobile", "Billing Type",
                    "Subtotal", "GST", "Total", "Status"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            var invoices = invoiceRepository.findAll();
            int rowNum = 3;
            for (var inv : invoices) {
                if (inv.getInvoiceDate().isBefore(from) || inv.getInvoiceDate().isAfter(to)) continue;
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(inv.getInvoiceNumber());
                row.createCell(1).setCellValue(inv.getInvoiceDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                row.createCell(2).setCellValue(inv.getClientName());
                row.createCell(3).setCellValue(inv.getClientMobile() != null ? inv.getClientMobile() : "");
                row.createCell(4).setCellValue(inv.getBillingType() != null ? inv.getBillingType() : "");
                row.createCell(5).setCellValue(inv.getSubtotal().doubleValue());
                row.createCell(6).setCellValue(inv.getTotalTax().doubleValue());
                row.createCell(7).setCellValue(inv.getTotalAmount().doubleValue());
                row.createCell(8).setCellValue(inv.getStatus().name());
                for (int i = 0; i < headers.length; i++) row.getCell(i).setCellStyle(dataStyle);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new com.cameraman.common.exception.BusinessException("Failed to generate report", "REPORT_ERROR");
        }
    }

    public byte[] generateInventoryReport() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Inventory");
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle dataStyle = createDataStyle(workbook);
            XSSFCellStyle lowStockStyle = workbook.createCellStyle();
            lowStockStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            lowStockStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Inventory Report - " + LocalDate.now());
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

            String[] headers = {"SKU", "Name", "Category", "Unit", "Qty On Hand",
                    "Reorder Level", "Cost Price", "Selling Price", "Status"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            var items = inventoryRepository.findAll();
            int rowNum = 3;
            for (var item : items) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getSku());
                row.createCell(1).setCellValue(item.getName());
                row.createCell(2).setCellValue(item.getCategory().name());
                row.createCell(3).setCellValue(item.getUnit());
                row.createCell(4).setCellValue(item.getQuantityOnHand());
                row.createCell(5).setCellValue(item.getReorderLevel());
                row.createCell(6).setCellValue(item.getCostPrice() != null ? item.getCostPrice().doubleValue() : 0);
                row.createCell(7).setCellValue(item.getSellingPrice() != null ? item.getSellingPrice().doubleValue() : 0);
                row.createCell(8).setCellValue(item.isOutOfStock() ? "OUT OF STOCK" : item.isLowStock() ? "LOW STOCK" : "OK");
                XSSFCellStyle style = item.isLowStock() ? lowStockStyle : dataStyle;
                for (int i = 0; i < headers.length; i++) row.getCell(i).setCellStyle(style);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new com.cameraman.common.exception.BusinessException("Failed to generate report", "REPORT_ERROR");
        }
    }

    public byte[] generateInvoicesReport(LocalDate from, LocalDate to) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Invoices");
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle dataStyle = createDataStyle(workbook);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Invoices Report - " + from + " to " + to);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));

            String[] headers = {"Invoice #", "Client Name", "Invoice Date", "Due Date", "Status",
                    "Total Amount", "Amount Paid", "Balance Due", "Generated By"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            var invoices = invoiceRepository.findAll().stream()
                    .filter(inv -> inv.getInvoiceDate() != null
                            && !inv.getInvoiceDate().isBefore(from) && !inv.getInvoiceDate().isAfter(to))
                    .toList();
            int rowNum = 3;
            for (var inv : invoices) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(inv.getInvoiceNumber());
                row.createCell(1).setCellValue(inv.getClientName() != null ? inv.getClientName() : "");
                row.createCell(2).setCellValue(inv.getInvoiceDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
                row.createCell(3).setCellValue(inv.getDueDate() != null ? inv.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "");
                row.createCell(4).setCellValue(inv.getStatus().name());
                row.createCell(5).setCellValue(inv.getTotalAmount() != null ? inv.getTotalAmount().doubleValue() : 0);
                row.createCell(6).setCellValue(inv.getAmountPaid() != null ? inv.getAmountPaid().doubleValue() : 0);
                row.createCell(7).setCellValue(inv.getBalanceDue() != null ? inv.getBalanceDue().doubleValue() : 0);
                row.createCell(8).setCellValue(inv.getGeneratedByName() != null ? inv.getGeneratedByName() : "");
                for (int i = 0; i < headers.length; i++) row.getCell(i).setCellStyle(dataStyle);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating invoices report: {}", e.getMessage(), e);
            throw new com.cameraman.common.exception.BusinessException("Failed to generate report", "REPORT_ERROR");
        }
    }

    public byte[] generateGalleriesReport(LocalDate from, LocalDate to) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Galleries");
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle dataStyle = createDataStyle(workbook);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Galleries Report - " + from + " to " + to);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));

            String[] headers = {"Gallery Name", "Client Name", "Status", "Photo Count", "View Count", "Selections Made", "Published Date"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            var galleries = galleryRepository.findAll().stream()
                    .filter(g -> g.getCreatedAt() != null
                            && !g.getCreatedAt().toLocalDate().isBefore(from) && !g.getCreatedAt().toLocalDate().isAfter(to))
                    .toList();
            int rowNum = 3;
            for (var g : galleries) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(g.getName());
                row.createCell(1).setCellValue(g.getClientName() != null ? g.getClientName() : "");
                row.createCell(2).setCellValue(g.getStatus().name());
                row.createCell(3).setCellValue(g.getPhotoCount());
                row.createCell(4).setCellValue(g.getViewCount());
                row.createCell(5).setCellValue(photoSelectionRepository.findByGalleryId(g.getId()).size());
                row.createCell(6).setCellValue(g.getPublishedAt() != null ? g.getPublishedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "Not published");
                for (int i = 0; i < headers.length; i++) row.getCell(i).setCellStyle(dataStyle);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating galleries report: {}", e.getMessage(), e);
            throw new com.cameraman.common.exception.BusinessException("Failed to generate report", "REPORT_ERROR");
        }
    }

    /** "Total Bookings" and "Total Spent" deliberately count across all three booking-shaped
     *  tracks (Booking, StudioSession, OutdoorShoot) — the same unification used in the
     *  suggestion engine — not just the original Booking table, since a client's real
     *  history now regularly lives across all three. */
    public byte[] generateClientsReport() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Clients");
            XSSFCellStyle headerStyle = createHeaderStyle(workbook);
            XSSFCellStyle dataStyle = createDataStyle(workbook);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Clients Report - " + LocalDate.now());
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            String[] headers = {"Name", "Mobile", "Email", "Total Bookings", "Total Spent", "Client Since"};
            Row headerRow = sheet.createRow(2);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            var clients = userRepository.findByRoleName("CLIENT", org.springframework.data.domain.Pageable.unpaged()).getContent();

            var bookingCounts = new java.util.HashMap<String, Long>();
            bookingRepository.findAll().forEach(b -> { if (b.getClient() != null) bookingCounts.merge(b.getClient().getId(), 1L, Long::sum); });
            studioSessionRepository.findAll().forEach(s -> { if (s.getClient() != null) bookingCounts.merge(s.getClient().getId(), 1L, Long::sum); });
            outdoorShootRepository.findAll().forEach(o -> { if (o.getClient() != null) bookingCounts.merge(o.getClient().getId(), 1L, Long::sum); });

            var spendByClient = new java.util.HashMap<String, java.math.BigDecimal>();
            invoiceRepository.findAll().forEach(inv -> {
                if (inv.getClientId() != null && inv.getAmountPaid() != null) {
                    spendByClient.merge(inv.getClientId(), inv.getAmountPaid(), java.math.BigDecimal::add);
                }
            });

            int rowNum = 3;
            for (var client : clients) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(client.getFullName());
                row.createCell(1).setCellValue(client.getMobile() != null ? client.getMobile() : "");
                row.createCell(2).setCellValue(client.getEmail() != null ? client.getEmail() : "");
                row.createCell(3).setCellValue(bookingCounts.getOrDefault(client.getId(), 0L));
                row.createCell(4).setCellValue(spendByClient.getOrDefault(client.getId(), java.math.BigDecimal.ZERO).doubleValue());
                row.createCell(5).setCellValue(client.getCreatedAt() != null ? client.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "");
                for (int i = 0; i < headers.length; i++) row.getCell(i).setCellStyle(dataStyle);
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generating clients report: {}", e.getMessage(), e);
            throw new com.cameraman.common.exception.BusinessException("Failed to generate report", "REPORT_ERROR");
        }
    }


    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 26, (byte) 115, (byte) 232}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private XSSFCellStyle createDataStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
