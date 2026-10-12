package com.cameraman.report.dto;
import lombok.*;
import java.time.LocalDate;
public class ReportDto {
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ReportRequest {
        private LocalDate fromDate;
        private LocalDate toDate;
        private String type;
    }
}
