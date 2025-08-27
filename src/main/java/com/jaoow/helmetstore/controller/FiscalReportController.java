package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.fiscal.FiscalReportDTO;
import com.jaoow.helmetstore.service.FiscalReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;

/**
 * Controller para relatórios fiscais MEI
 */
@RestController
@RequestMapping("/reports/fiscal")
@RequiredArgsConstructor
public class FiscalReportController {
    
    private final FiscalReportService fiscalReportService;

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
}