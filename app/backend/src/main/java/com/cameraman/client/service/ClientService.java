package com.cameraman.client.service;

import com.cameraman.audit.service.AuditService;
import com.cameraman.billing.domain.Invoice;
import com.cameraman.billing.repository.InvoiceRepository;
import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.domain.BookingStatus;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.client.dto.ClientDto;
import com.cameraman.common.exception.BusinessException;
import com.cameraman.common.exception.DuplicateResourceException;
import com.cameraman.common.exception.ResourceNotFoundException;
import com.cameraman.gallery.domain.Gallery;
import com.cameraman.gallery.repository.GalleryRepository;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/** Admin-side view and light editing of client accounts (the customers, not the staff). */
@Service
@RequiredArgsConstructor
@Transactional
public class ClientService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final InvoiceRepository invoiceRepository;
    private final GalleryRepository galleryRepository;
    private final com.cameraman.studio.repository.StudioSessionRepository studioSessionRepository;
    private final com.cameraman.outdoor.repository.OutdoorShootRepository outdoorShootRepository;
    private final com.cameraman.photoselection.repository.PhotoSelectionRepository photoSelectionRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public ClientDto.Overview getOverview(String clientId) {
        User user = findClient(clientId);

        // Everything the client has, not just the latest few: staff need the full history.
        final int ALL = 500;
        List<Booking> recent = bookingRepository.findByClientId(clientId,
                PageRequest.of(0, ALL, Sort.by("bookingDate").descending())).getContent();
        List<Invoice> invoices = invoiceRepository.findByClientId(clientId,
                PageRequest.of(0, ALL, Sort.by("invoiceDate").descending())).getContent();
        List<Gallery> galleries = galleryRepository.findByClientId(clientId,
                PageRequest.of(0, ALL, Sort.by("createdAt").descending())).getContent();
        // Invoices point at their booking / session / shoot through Invoice.bookingId.
        java.util.Map<String, String> invoiceIdBySource = new java.util.HashMap<>();
        for (Invoice inv : invoices) {
            if (inv.getBookingId() != null) invoiceIdBySource.putIfAbsent(inv.getBookingId(), inv.getId());
        }
        List<ClientDto.VisitSummary> visits = new java.util.ArrayList<>();
        studioSessionRepository.findByClientId(clientId, PageRequest.of(0, ALL, Sort.by("sessionDate").descending()))
                .forEach(s -> visits.add(ClientDto.VisitSummary.builder()
                        .type("STUDIO_SESSION").id(s.getId()).number(s.getSessionNumber()).date(s.getSessionDate())
                        .packageName(s.getPackageName()).status(s.getStatus().name())
                        .totalAmount(s.getTotalAmount()).balanceAmount(s.getBalanceAmount())
                        .invoiceId(invoiceIdBySource.get(s.getId())).build()));
        outdoorShootRepository.findByClientId(clientId, PageRequest.of(0, ALL, Sort.by("shootDate").descending()))
                .forEach(o -> visits.add(ClientDto.VisitSummary.builder()
                        .type("OUTDOOR_SHOOT").id(o.getId()).number(o.getShootNumber()).date(o.getShootDate())
                        .packageName(o.getPackageName()).status(o.getStatus().name())
                        .totalAmount(o.getTotalAmount()).balanceAmount(o.getBalanceAmount())
                        .invoiceId(invoiceIdBySource.get(o.getId())).build()));
        visits.sort(java.util.Comparator.comparing(ClientDto.VisitSummary::getDate,
                java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        java.util.Map<String, String> galleryNames = galleries.stream()
                .collect(Collectors.toMap(Gallery::getId, Gallery::getName, (a, b) -> a));
        List<ClientDto.SelectionSummary> selections = photoSelectionRepository.findByClientId(clientId).stream()
                .map(sel -> ClientDto.SelectionSummary.builder().id(sel.getId()).galleryId(sel.getGalleryId())
                        .galleryName(galleryNames.getOrDefault(sel.getGalleryId(), "Gallery"))
                        .status(sel.getStatus().name()).selectedCount(sel.getSelectedCount())
                        .selectionLimit(sel.getSelectionLimit()).build())
                .collect(Collectors.toList());

        BigDecimal billed = BigDecimal.ZERO, paid = BigDecimal.ZERO, due = BigDecimal.ZERO;
        List<Object[]> totals = invoiceRepository.sumTotalsForClient(clientId);
        if (!totals.isEmpty() && totals.get(0) != null) {
            Object[] row = totals.get(0);
            billed = toMoney(row[0]);
            paid = toMoney(row[1]);
            due = toMoney(row[2]);
        }

        return ClientDto.Overview.builder()
                .id(user.getId()).firstName(user.getFirstName()).lastName(user.getLastName())
                .email(user.getEmail()).mobile(user.getMobile())
                .address(user.getAddress()).notes(user.getNotes())
                .status(user.getStatus().name())
                .emailVerified(user.isEmailVerified()).mobileVerified(user.isMobileVerified())
                .marketingOptIn(user.isMarketingOptIn())
                .createdAt(user.getCreatedAt()).lastLoginAt(user.getLastLoginAt())
                .bookingCount(bookingRepository.countByClientId(clientId))
                .completedBookings(bookingRepository.countByClientIdAndStatus(clientId, BookingStatus.COMPLETED))
                .cancelledBookings(bookingRepository.countByClientIdAndStatus(clientId, BookingStatus.CANCELLED))
                .totalBilled(billed).totalPaid(paid).balanceDue(due)
                .lastBookingDate(recent.isEmpty() ? null : recent.get(0).getBookingDate())
                .recentBookings(recent.stream().map(b -> ClientDto.BookingSummary.builder()
                        .id(b.getId()).bookingNumber(b.getBookingNumber()).bookingDate(b.getBookingDate())
                        .packageName(b.getPackageName())
                        .shootCategory(b.getShootCategory() != null ? b.getShootCategory().name() : null)
                        .status(b.getStatus().name())
                        .orderStage(b.getOrderStage() != null ? b.getOrderStage().name() : null)
                        .locationType(b.getLocationType() != null ? b.getLocationType().name() : null)
                        .invoiceId(invoiceIdBySource.get(b.getId()))
                        .totalAmount(b.getTotalAmount()).balanceAmount(b.getBalanceAmount()).build())
                        .collect(Collectors.toList()))
                .recentInvoices(invoices.stream().map(i -> ClientDto.InvoiceSummary.builder()
                        .id(i.getId()).invoiceNumber(i.getInvoiceNumber()).invoiceDate(i.getInvoiceDate())
                        .status(i.getStatus().name()).totalAmount(i.getTotalAmount())
                        .amountPaid(i.getAmountPaid()).balanceDue(i.getBalanceDue())
                        .sourceId(i.getBookingId()).clientName(i.getClientName()).build())
                        .collect(Collectors.toList()))
                .studioSessionsAndShoots(visits)
                .selections(selections)
                .galleries(galleries.stream().map(g -> ClientDto.GallerySummary.builder()
                        .id(g.getId()).name(g.getName()).status(g.getStatus().name())
                        .photoCount(g.getPhotoCount()).publishedAt(g.getPublishedAt()).build())
                        .collect(Collectors.toList()))
                .build();
    }

    public ClientDto.Overview update(String clientId, ClientDto.UpdateRequest req, String actorId) {
        User user = findClient(clientId);

        String email = req.getEmail() == null ? null : req.getEmail().trim().toLowerCase();
        if (email != null && !email.isEmpty() && !email.equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(email)) {
                throw new DuplicateResourceException("User", "email", email);
            }
            user.setEmail(email);
            user.setEmailVerified(false);
        }
        String mobile = req.getMobile() == null ? null : req.getMobile().trim();
        if (mobile != null && !mobile.equals(user.getMobile() == null ? "" : user.getMobile())) {
            if (!mobile.isEmpty() && userRepository.existsByMobile(mobile)) {
                throw new DuplicateResourceException("User", "mobile", mobile);
            }
            user.setMobile(mobile.isEmpty() ? null : mobile);
            user.setMobileVerified(false);
        }
        user.setFirstName(req.getFirstName().trim());
        user.setLastName(req.getLastName() == null || req.getLastName().isBlank() ? null : req.getLastName().trim());
        user.setAddress(blankToNull(req.getAddress()));
        user.setNotes(blankToNull(req.getNotes()));
        if (req.getMarketingOptIn() != null) user.setMarketingOptIn(req.getMarketingOptIn());
        userRepository.save(user);
        auditService.log("CLIENT_UPDATE", "User", clientId, null, user.getFullName(), actorId);
        return getOverview(clientId);
    }

    /** Only users holding the CLIENT role are reachable through this service. */
    private User findClient(String clientId) {
        User user = userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", "id", clientId));
        boolean isClient = user.getRoles().stream().anyMatch(r -> "CLIENT".equals(r.getName()));
        if (!isClient) {
            throw new BusinessException("That account is not a client. Manage staff from the Staff page.", "NOT_A_CLIENT");
        }
        return user;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static BigDecimal toMoney(Object value) {
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
