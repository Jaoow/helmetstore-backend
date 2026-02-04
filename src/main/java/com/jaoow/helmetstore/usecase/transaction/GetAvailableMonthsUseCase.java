package com.jaoow.helmetstore.usecase.transaction;

import com.jaoow.helmetstore.dto.balance.AvailableMonthDTO;
import com.jaoow.helmetstore.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Use Case: Get available months with transaction counts
 * 
 * Responsibilities:
 * - Retrieve lightweight month data for UI selectors
 * - Avoid loading full transaction data
 * - Return month/year with transaction counts
 */
@Component
@RequiredArgsConstructor
public class GetAvailableMonthsUseCase {

    private final TransactionRepository transactionRepository;

    public List<AvailableMonthDTO> execute(String userEmail) {
        List<Object[]> results = transactionRepository.findAvailableMonthsWithCount(userEmail);

        return results.stream()
                .map(row -> AvailableMonthDTO.builder()
                        .month(java.time.YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()))
                        .transactionCount(((Number) row[2]).intValue())
                        .build())
                .toList();
    }
}
