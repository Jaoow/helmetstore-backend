package com.jaoow.helmetstore.dto.fiscal;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO para relatório fiscal de entradas e saídas MEI
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FiscalReportDTO {
    private LocalDate reportPeriodStart;
    private LocalDate reportPeriodEnd;
    private List<PurchaseEntryDTO> entries;
    private List<SaleExitDTO> exits;
    private BigDecimal totalEntries;
    private BigDecimal totalExits;
    private BigDecimal balance;
}