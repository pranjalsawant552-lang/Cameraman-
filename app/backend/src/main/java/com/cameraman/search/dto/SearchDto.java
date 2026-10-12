package com.cameraman.search.dto;

import lombok.*;

import java.util.List;

public class SearchDto {

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ResultItem {
        private String id;
        private String type;       // CLIENT, BOOKING, GALLERY, INVOICE, PRODUCT
        private String title;      // primary display line
        private String subtitle;   // secondary display line (e.g. mobile, date, status)
        private String linkHint;   // e.g. "/bookings/{id}" — the frontend resolves the actual route
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class QuickSearchResponse {
        private String query;
        private List<ResultItem> clients;
        private List<ResultItem> bookings;
        private List<ResultItem> galleries;
        private List<ResultItem> invoices;
        private List<ResultItem> products;
    }
}
