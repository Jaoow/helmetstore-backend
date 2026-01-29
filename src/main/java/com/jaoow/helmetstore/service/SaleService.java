package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.AvailableMonthDTO;
import com.jaoow.helmetstore.dto.sale.*;
import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.repository.SaleRepository;
import com.jaoow.helmetstore.usecase.sale.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.util.List;

/**
 * Sale Service - Orchestrates sale-related operations using use cases
 *
 * This service acts as a facade, delegating business logic to specific use cases.
 * Each use case encapsulates a single business operation with clear responsibilities.
 */
@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final InventoryHelper inventoryHelper;
    private final ModelMapper modelMapper;

    // Use Cases
    private final CreateSaleUseCase createSaleUseCase;
    private final UpdateSaleUseCase updateSaleUseCase;
    private final DeleteSaleUseCase deleteSaleUseCase;
    private final GetSaleHistoryUseCase getSaleHistoryUseCase;
    private final GenerateSaleReceiptUseCase generateSaleReceiptUseCase;
    private final CancelSaleUseCase cancelSaleUseCase;

    @Transactional(readOnly = true)
    public Page<SaleResponseDTO> findAll(Pageable pageable, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        Page<Sale> salesPage = saleRepository.findAllByInventoryPaginated(inventory, pageable);
        return salesPage.map(sale -> modelMapper.map(sale, SaleResponseDTO.class));
    }

    public SaleResponseDTO save(SaleCreateDTO dto, Principal principal) {
        return createSaleUseCase.execute(dto, principal);
    }

    public SaleResponseDTO update(Long saleId, SaleCreateDTO dto, Principal principal) {
        return updateSaleUseCase.execute(saleId, dto, principal);
    }

    public void delete(Long id, Principal principal) {
        deleteSaleUseCase.execute(id, principal);
    }

    public SaleHistoryResponse getHistory(Integer year, Integer month, Principal principal) {
        return getSaleHistoryUseCase.execute(year, month, principal);
    }

    public byte[] generateReceipt(Long saleId, Principal principal) {
        return generateSaleReceiptUseCase.execute(saleId, principal);
    }

    public SaleCancellationResponseDTO cancelSale(Long saleId, SaleCancellationRequestDTO request, Principal principal) {
        return cancelSaleUseCase.execute(saleId, request, principal);
    }

    /**
     * Get available months with sale counts (lightweight for UI month selectors).
     */
    public List<AvailableMonthDTO> getAvailableMonths(Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        List<Object[]> results = saleRepository.findAvailableMonthsWithCount(inventory);

        return results.stream()
                .map(row -> AvailableMonthDTO.builder()
                        .month(java.time.YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()))
                        .transactionCount(((Number) row[2]).intValue())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public SaleDetailDTO getById(Long id, Principal principal) {
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        // Fetch items primeiro
        Sale sale = saleRepository.findByIdAndInventoryWithItems(id, inventory)
                .orElseThrow(() -> new ResourceNotFoundException("Sale not found with ID: " + id));

        // Depois fetch payments (evita MultipleBagFetchException)
        saleRepository.findByIdAndInventoryWithPayments(id, inventory);

        return convertToSaleDetailDTO(sale);
    }

    // ========================================================================
    // Private Helper Methods (to be migrated to use cases later)
    // ========================================================================

    private SaleDetailDTO convertToSaleDetailDTO(Sale sale) {
        SaleDetailDTO dto = SaleDetailDTO.builder()
                .id(sale.getId())
                .date(sale.getDate())
                .totalAmount(sale.getTotalAmount())
                .totalProfit(sale.getTotalProfit())
                .build();

        // Convert sale items
        if (sale.getItems() != null && !sale.getItems().isEmpty()) {
            List<com.jaoow.helmetstore.dto.sale.SaleItemDTO> itemDTOs = sale.getItems().stream()
                    .map(this::convertToSaleItemDTO)
                    .collect(java.util.stream.Collectors.toList());
            dto.setItems(itemDTOs);

            // Collect all product variants from sale items
            List<com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO> productVariants = sale.getItems().stream()
                    .map(com.jaoow.helmetstore.model.sale.SaleItem::getProductVariant)
                    .distinct()
                    .map(variant -> modelMapper.map(variant, com.jaoow.helmetstore.dto.reference.SimpleProductVariantDTO.class))
                    .collect(java.util.stream.Collectors.toList());
            dto.setProductVariants(productVariants);

            // Collect all products from sale items
            List<com.jaoow.helmetstore.dto.reference.SimpleProductDTO> products = sale.getItems().stream()
                    .map(item -> item.getProductVariant().getProduct())
                    .distinct()
                    .map(product -> modelMapper.map(product, com.jaoow.helmetstore.dto.reference.SimpleProductDTO.class))
                    .collect(java.util.stream.Collectors.toList());
            dto.setProducts(products);
        }

        // Map payments explicitly
        List<com.jaoow.helmetstore.dto.sale.SalePaymentDTO> paymentDTOs = sale.getPayments() == null ? java.util.Collections.emptyList()
                : sale.getPayments().stream()
                        .map(p -> com.jaoow.helmetstore.dto.sale.SalePaymentDTO.builder()
                                .paymentMethod(p.getPaymentMethod())
                                .amount(p.getAmount())
                                .build())
                        .collect(java.util.stream.Collectors.toList());
        dto.setPayments(paymentDTOs);

        return dto;
    }

    private com.jaoow.helmetstore.dto.sale.SaleItemDTO convertToSaleItemDTO(com.jaoow.helmetstore.model.sale.SaleItem saleItem) {
        return com.jaoow.helmetstore.dto.sale.SaleItemDTO.builder()
                .id(saleItem.getId())
                .productVariantId(saleItem.getProductVariant().getId())
                .quantity(saleItem.getQuantity())
                .unitPrice(saleItem.getUnitPrice())
                .unitProfit(saleItem.getUnitProfit())
                .totalItemPrice(saleItem.getTotalItemPrice())
                .totalItemProfit(saleItem.getTotalItemProfit())
                .isCancelled(saleItem.getIsCancelled())
                .cancelledQuantity(saleItem.getCancelledQuantity())
                .build();
    }
}
