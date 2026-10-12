package com.cameraman.search.controller;

import com.cameraman.billing.domain.Invoice;
import com.cameraman.billing.repository.InvoiceRepository;
import com.cameraman.booking.domain.Booking;
import com.cameraman.booking.repository.BookingRepository;
import com.cameraman.common.response.ApiResponse;
import com.cameraman.gallery.domain.Gallery;
import com.cameraman.gallery.repository.GalleryRepository;
import com.cameraman.product.domain.Product;
import com.cameraman.product.repository.ProductRepository;
import com.cameraman.search.dto.SearchDto;
import com.cameraman.user.domain.User;
import com.cameraman.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Every module already has solid deep-dive pagination + filtering (see e.g.
 * GET /bookings?search=&status=&page=&size=) — what was missing was a single
 * fast "search everything" endpoint for a top-of-dashboard search bar/typeahead,
 * so staff don't need to know in advance whether they're looking for a client,
 * a booking, a gallery, or an invoice before they start typing. Intentionally
 * cheap and shallow (a handful of rows per type, no deep pagination) — this is
 * for "start typing, see likely matches instantly", not a reporting query. The
 * existing per-module search endpoints remain the right tool for a full,
 * paginated, filtered listing.
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Unified quick search / typeahead across clients, bookings, galleries, invoices and products")
public class SearchController {

    private static final int LIMIT = 5;

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final GalleryRepository galleryRepository;
    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;

    @GetMapping("/quick")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','MANAGER','RECEPTIONIST','PHOTOGRAPHER','EDITOR','ACCOUNTANT')")
    public ResponseEntity<ApiResponse<SearchDto.QuickSearchResponse>> quickSearch(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(SearchDto.QuickSearchResponse.builder()
                    .query(q).clients(List.of()).bookings(List.of()).galleries(List.of())
                    .invoices(List.of()).products(List.of()).build()));
        }

        var page = PageRequest.of(0, LIMIT);

        List<SearchDto.ResultItem> clients = userRepository.searchUsersByRole(q, "CLIENT", page)
                .map(this::toClientItem).getContent();
        List<SearchDto.ResultItem> bookings = bookingRepository.searchBookings(q, page)
                .map(this::toBookingItem).getContent();
        List<SearchDto.ResultItem> galleries = galleryRepository.searchGalleries(q, page)
                .map(this::toGalleryItem).getContent();
        List<SearchDto.ResultItem> invoices = invoiceRepository.searchInvoices(q, page)
                .map(this::toInvoiceItem).getContent();
        List<SearchDto.ResultItem> products = productRepository.searchProducts(q, page)
                .map(this::toProductItem).getContent();

        return ResponseEntity.ok(ApiResponse.success(SearchDto.QuickSearchResponse.builder()
                .query(q).clients(clients).bookings(bookings).galleries(galleries)
                .invoices(invoices).products(products).build()));
    }

    private SearchDto.ResultItem toClientItem(User u) {
        return SearchDto.ResultItem.builder()
                .id(u.getId()).type("CLIENT")
                .title((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : ""))
                .subtitle(u.getMobile() != null ? u.getMobile() : u.getEmail())
                .linkHint("/clients/" + u.getId())
                .build();
    }

    private SearchDto.ResultItem toBookingItem(Booking b) {
        return SearchDto.ResultItem.builder()
                .id(b.getId()).type("BOOKING")
                .title(b.getBookingNumber() + " — " + b.getClientName())
                .subtitle(b.getBookingDate() + " · " + b.getStatus().name())
                .linkHint("/bookings/" + b.getId())
                .build();
    }

    private SearchDto.ResultItem toGalleryItem(Gallery g) {
        return SearchDto.ResultItem.builder()
                .id(g.getId()).type("GALLERY")
                .title(g.getName())
                .subtitle(g.getClientName() + " · " + g.getStatus().name())
                .linkHint("/galleries/" + g.getId())
                .build();
    }

    private SearchDto.ResultItem toInvoiceItem(Invoice inv) {
        return SearchDto.ResultItem.builder()
                .id(inv.getId()).type("INVOICE")
                .title(inv.getInvoiceNumber() + " — " + inv.getClientName())
                .subtitle("\u20b9" + inv.getTotalAmount().toPlainString() + " · " + inv.getStatus().name())
                .linkHint("/invoices/" + inv.getId())
                .build();
    }

    private SearchDto.ResultItem toProductItem(Product p) {
        return SearchDto.ResultItem.builder()
                .id(p.getId()).type("PRODUCT")
                .title(p.getName())
                .subtitle("\u20b9" + p.getPrice().toPlainString() + " · " + p.getStockQuantity() + " in stock")
                .linkHint("/products/" + p.getId())
                .build();
    }
}
