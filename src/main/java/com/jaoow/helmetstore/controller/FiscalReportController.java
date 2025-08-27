package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.fiscal.FiscalReportDTO;
import com.jaoow.helmetstore.service.FiscalExportService;
import com.jaoow.helmetstore.service.FiscalReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Controller para relatórios fiscais MEI
 */
@RestController
@RequestMapping("/reports/fiscal")
@RequiredArgsConstructor
public class FiscalReportController {
    
    private final FiscalReportService fiscalReportService;
    private final FiscalExportService fiscalExportService;

    /**
     * Gera relatório de entradas e saídas para MEI (Livro Caixa)
     */
    @GetMapping("/entry-exit")
    public FiscalReportDTO getEntryExitReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Principal principal) {
        return fiscalReportService.generateEntryExitReport(startDate, endDate, principal);
    }

    /**
     * Exporta relatório fiscal consolidado em PDF
     */
    @GetMapping("/export/pdf")
    public ResponseEntity<byte[]> exportConsolidatedPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Principal principal) {
        try {
            byte[] pdfContent = fiscalExportService.exportConsolidatedFiscalReport(startDate, endDate, principal);
            
            String filename = String.format("relatorio-fiscal-mei-%s-a-%s.pdf",
                    startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfContent);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Exporta dados fiscais em formato CSV para planilhas
     */
    @GetMapping("/export/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Principal principal) {
        try {
            String csvContent = fiscalExportService.exportToCSV(startDate, endDate, principal);
            
            String filename = String.format("dados-fiscais-mei-%s-a-%s.csv",
                    startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setContentDispositionFormData("attachment", filename);
            headers.add("Content-Type", "text/csv; charset=UTF-8");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(csvContent);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}