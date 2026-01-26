package com.jaoow.helmetstore.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.YearMonth;

/**
 * Lightweight DTO for listing available months without transactions.
 * Used for month selector in UI without loading full transaction data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableMonthDTO {
    private YearMonth month;
    private int transactionCount;
}
