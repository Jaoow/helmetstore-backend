package com.jaoow.helmetstore.usecase.sale;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.sale.ProductExchangeRequestDTO;
import com.jaoow.helmetstore.dto.sale.ProductExchangeResponseDTO;
import com.jaoow.helmetstore.dto.sale.SaleCancellationRequestDTO;
import com.jaoow.helmetstore.dto.sale.SaleCreateDTO;
import com.jaoow.helmetstore.dto.sale.SaleItemCreateDTO;
import com.jaoow.helmetstore.dto.sale.SalePaymentCreateDTO;
import com.jaoow.helmetstore.exception.BusinessException;
import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.ProductExchange;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.balance.*;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.sale.CancellationReason;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.model.sale.SaleStatus;
import com.jaoow.helmetstore.repository.AccountRepository;
import com.jaoow.helmetstore.repository.ProductExchangeRepository;
import com.jaoow.helmetstore.repository.SaleRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Use Case: Exchange Products
 *
 * This use case handles the complete product exchange flow:
 * 1. Validates the original sale and items to return
 * 2. Cancels the returned items (partial cancellation)
 * 3. Calculates financial differences
 * 4. Issues refund if new sale is cheaper (or equal)
 * 5. Creates new sale with exchanged products
 * 6. Records the exchange transaction for traceability
 *
 * Key principles:
 * - Original sale is NEVER edited, only marked with cancellations
 * - All operations are atomic (transactional)
 * - Complete audit trail is maintained
 * - Stock is properly adjusted
 * - Financial records are accurate
 */
@Component
@RequiredArgsConstructor
public class ExchangeProductUseCase {

    private final SaleRepository saleRepository;
    private final ProductExchangeRepository productExchangeRepository;
    private final CancelSaleUseCase cancelSaleUseCase;
    private final CreateSaleUseCase createSaleUseCase;
    private final InventoryHelper inventoryHelper;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, allEntries = true),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, allEntries = true),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, allEntries = true),
            @CacheEvict(value = CacheNames.SALES_HISTORY, allEntries = true)
    })
    @Transactional
    public ProductExchangeResponseDTO execute(ProductExchangeRequestDTO request, Principal principal) {
        LocalDateTime exchangeDate = LocalDateTime.now();
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);

        // ========================================================================
        // STEP 1: Load and validate original sale
        // ========================================================================
        Sale originalSale = saleRepository.findById(request.getOriginalSaleId())
                .orElseThrow(() -> new ResourceNotFoundException("Venda original não encontrada: " + request.getOriginalSaleId()));

        if (!originalSale.getInventory().getId().equals(inventory.getId())) {
            throw new BusinessException("Venda não pertence ao inventário do usuário");
        }

        if (originalSale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessException("Não é possível realizar troca de uma venda totalmente cancelada");
        }

        // ========================================================================
        // STEP 2: Validate items to return and calculate returned amount
        // ========================================================================
        BigDecimal returnedAmount = validateAndCalculateReturnedAmount(originalSale, request.getItemsToReturn());

        // ========================================================================
        // STEP 3: Calculate new sale amount
        // ========================================================================
        BigDecimal newSaleAmount = calculateNewSaleAmount(request.getNewItems());

        // ========================================================================
        // STEP 4: Calculate difference and validate payments
        // ========================================================================
        BigDecimal amountDifference = newSaleAmount.subtract(returnedAmount);
        validateExchangePayments(request, amountDifference);
        validateRefundPaymentMethod(request, amountDifference);

        // ========================================================================
        // STEP 5: Cancel returned items from original sale (partial cancellation)
        // ========================================================================
        Long refundTransactionId = null;
        PaymentMethod refundPaymentMethod = null;

        SaleCancellationRequestDTO cancellationRequest = buildCancellationRequest(
                request,
                returnedAmount,
                amountDifference,
                originalSale
        );

        var cancellationResponse = cancelSaleUseCase.execute(
                originalSale.getId(),
                cancellationRequest,
                principal
        );

        // Capture refund details if refund was issued
        if (cancellationResponse.getHasRefund()) {
            refundTransactionId = cancellationResponse.getRefundTransactionId();
            refundPaymentMethod = cancellationResponse.getRefundPaymentMethod();
        }

        // ========================================================================
        // STEP 6: Create new sale with exchanged products
        // ========================================================================
        SaleCreateDTO newSaleDTO = buildNewSaleDTO(request, exchangeDate, returnedAmount, amountDifference);
        var newSaleResponse = createSaleUseCase.execute(newSaleDTO, principal);

        // Load the created sale entity
        Sale newSale = saleRepository.findById(newSaleResponse.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Nova venda não encontrada após criação"));

        // ========================================================================
        // STEP 7: Record the exchange transaction
        // ========================================================================
        ProductExchange exchange = ProductExchange.builder()
                .exchangeDate(exchangeDate)
                .originalSale(originalSale)
                .newSale(newSale)
                .reason(request.getReason())
                .notes(request.getNotes())
                .processedBy(principal.getName())
                .returnedAmount(returnedAmount)
                .newSaleAmount(newSaleAmount)
                .amountDifference(amountDifference)
                .refundAmount(amountDifference.compareTo(BigDecimal.ZERO) < 0 ? amountDifference.abs() : null)
                .refundPaymentMethod(refundPaymentMethod)
                .refundTransactionId(refundTransactionId)
                .build();

        ProductExchange savedExchange = productExchangeRepository.save(exchange);

        // ========================================================================
        // STEP 8: Build and return response
        // ========================================================================
        return buildResponse(savedExchange);
    }

    /**
     * Validates that all items to return exist and calculates total returned amount
     */
    private BigDecimal validateAndCalculateReturnedAmount(
            Sale originalSale,
            List<ProductExchangeRequestDTO.ItemToReturnDTO> itemsToReturn
    ) {
        BigDecimal total = BigDecimal.ZERO;

        for (var itemToReturn : itemsToReturn) {
            SaleItem saleItem = originalSale.getItems().stream()
                    .filter(si -> si.getId().equals(itemToReturn.getSaleItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Item não encontrado na venda original: " + itemToReturn.getSaleItemId()));

            // Check if item is already fully cancelled
            if (saleItem.getIsCancelled()) {
                throw new BusinessException("Item já está totalmente cancelado: " + itemToReturn.getSaleItemId());
            }

            // Validate quantity
            int alreadyCancelled = saleItem.getCancelledQuantity() != null ? saleItem.getCancelledQuantity() : 0;
            int availableQuantity = saleItem.getQuantity() - alreadyCancelled;

            if (itemToReturn.getQuantityToReturn() > availableQuantity) {
                throw new BusinessException(String.format(
                        "Quantidade a devolver (%d) excede a quantidade disponível (%d) do item %d",
                        itemToReturn.getQuantityToReturn(),
                        availableQuantity,
                        itemToReturn.getSaleItemId()
                ));
            }

            // Calculate proportional amount for returned quantity
            BigDecimal unitPrice = saleItem.getUnitPrice();
            BigDecimal itemReturnedAmount = unitPrice.multiply(BigDecimal.valueOf(itemToReturn.getQuantityToReturn()));
            total = total.add(itemReturnedAmount);
        }

        return total;
    }

    /**
     * Calculates the total amount for the new sale
     */
    private BigDecimal calculateNewSaleAmount(List<ProductExchangeRequestDTO.NewItemDTO> newItems) {
        return newItems.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Validates that payments sum matches the required amount
     */
    private void validateExchangePayments(ProductExchangeRequestDTO request, BigDecimal amountDifference) {
        // Get payments list, handling null case
        List<SalePaymentCreateDTO> payments = request.getNewSalePayments();
        BigDecimal paymentsSum = payments == null || payments.isEmpty()
                ? BigDecimal.ZERO
                : payments.stream()
                    .map(SalePaymentCreateDTO::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedPayment = amountDifference.max(BigDecimal.ZERO);

        if (paymentsSum.compareTo(expectedPayment) != 0) {
            throw new BusinessException(String.format(
                    "Soma dos pagamentos (R$ %.2f) não corresponde ao valor esperado (R$ %.2f)",
                    paymentsSum,
                    expectedPayment
            ));
        }
    }

    /**
     * Validates that refund payment method is provided when refund is needed
     */
    private void validateRefundPaymentMethod(ProductExchangeRequestDTO request, BigDecimal amountDifference) {
        boolean needsRefund = amountDifference.compareTo(BigDecimal.ZERO) < 0;

        if (needsRefund && request.getRefundPaymentMethod() == null) {
            throw new BusinessException(
                    "O método de reembolso é obrigatório quando o valor da nova venda é menor que o valor devolvido"
            );
        }
    }

    /**
     * Builds the cancellation request for the original sale
     */
    private SaleCancellationRequestDTO buildCancellationRequest(
            ProductExchangeRequestDTO request,
            BigDecimal returnedAmount,
            BigDecimal amountDifference,
            Sale originalSale
    ) {
        // Convert items to cancellation format
        List<SaleCancellationRequestDTO.ItemCancellationDTO> itemCancellations = request.getItemsToReturn().stream()
                .map(item -> SaleCancellationRequestDTO.ItemCancellationDTO.builder()
                        .itemId(item.getSaleItemId())
                        .quantityToCancel(item.getQuantityToReturn())
                        .build())
                .toList();

        // Determine if this is a full or partial cancellation
        // It's a full cancellation if ALL items are being returned with their FULL quantities
        boolean isCancelEntireSale = isReturningAllItems(originalSale, request.getItemsToReturn());

        // Determine if refund should be generated
        // Refund is only generated when new sale is cheaper (amountDifference < 0)
        boolean shouldGenerateRefund = amountDifference.compareTo(BigDecimal.ZERO) < 0;

        // Refund amount is the absolute value of the negative difference
        BigDecimal refundAmount = shouldGenerateRefund ? amountDifference.abs() : null;

        // Use the refund payment method from request (explicitly defined by operator)
        PaymentMethod refundMethod = shouldGenerateRefund ? request.getRefundPaymentMethod() : null;

        return SaleCancellationRequestDTO.builder()
                .cancelEntireSale(isCancelEntireSale)
                .itemsToCancel(itemCancellations)
                .reason(CancellationReason.DEVOLUCAO) // Exchange is a type of return
                .notes("Cancelamento para troca de produtos. Motivo: " + request.getReason().getDescription() +
                        (request.getNotes() != null ? ". Obs: " + request.getNotes() : ""))
                .generateRefund(shouldGenerateRefund)
                .refundAmount(refundAmount)
                .refundPaymentMethod(refundMethod)
                .isPartOfExchange(true) // Mark as part of exchange
                .build();
    }

    /**
     * Checks if all items from the sale are being returned with full quantities
     */
    private boolean isReturningAllItems(Sale originalSale, List<ProductExchangeRequestDTO.ItemToReturnDTO> itemsToReturn) {
        // Get all non-cancelled items from the sale
        List<SaleItem> activeItems = originalSale.getItems().stream()
                .filter(item -> !item.getIsCancelled())
                .toList();

        // If not returning the same number of items, it's partial
        if (itemsToReturn.size() != activeItems.size()) {
            return false;
        }

        // Check if each item is being returned in full quantity
        for (SaleItem saleItem : activeItems) {
            int alreadyCancelled = saleItem.getCancelledQuantity() != null ? saleItem.getCancelledQuantity() : 0;
            int availableQuantity = saleItem.getQuantity() - alreadyCancelled;

            ProductExchangeRequestDTO.ItemToReturnDTO returnItem = itemsToReturn.stream()
                    .filter(item -> item.getSaleItemId().equals(saleItem.getId()))
                    .findFirst()
                    .orElse(null);

            // If item not in return list or not returning full quantity, it's partial
            if (returnItem == null || returnItem.getQuantityToReturn() != availableQuantity) {
                return false;
            }
        }

        return true;
    }

    /**
     * Builds the new sale DTO
     *
     * Payment logic for exchanges:
     * - Same value: No payments needed (COGS already reversed, no money in/out)
     * - More expensive: Only charge the difference (new sale payment)
     * - Cheaper: Refund handled by CancelSaleUseCase, no payment for new sale
     */
    private SaleCreateDTO buildNewSaleDTO(
            ProductExchangeRequestDTO request,
            LocalDateTime exchangeDate,
            BigDecimal returnedAmount,
            BigDecimal amountDifference
    ) {
        // Use REAL prices for items (no adjustment)
        List<SaleItemCreateDTO> saleItems = request.getNewItems().stream()
                .map(item -> SaleItemCreateDTO.builder()
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .build())
                .toList();

        // Payment logic:
        // - If amountDifference > 0 (more expensive): customer pays the difference
        // - If amountDifference <= 0 (same or cheaper): no payment (refund handled separately)
        List<SalePaymentCreateDTO> payments = new ArrayList<>();

        if (amountDifference.compareTo(BigDecimal.ZERO) > 0) {
            // Customer needs to pay additional amount
            if (request.getNewSalePayments() != null) {
                payments.addAll(request.getNewSalePayments());
            }
        }
        // No payments for same value or cheaper exchanges

        return SaleCreateDTO.builder()
                .date(exchangeDate)
                .items(saleItems)
                .payments(payments)
                .build();
    }

    /**
     * Builds the response DTO
     */
    private ProductExchangeResponseDTO buildResponse(ProductExchange exchange) {
        BigDecimal amountDiff = exchange.getAmountDifference();

        return ProductExchangeResponseDTO.builder()
                .exchangeId(exchange.getId())
                .exchangeDate(exchange.getExchangeDate())
                .originalSaleId(exchange.getOriginalSale().getId())
                .newSaleId(exchange.getNewSale().getId())
                .reason(exchange.getReason())
                .notes(exchange.getNotes())
                .processedBy(exchange.getProcessedBy())
                .returnedAmount(exchange.getReturnedAmount())
                .newSaleAmount(exchange.getNewSaleAmount())
                .amountDifference(amountDiff)
                // Calculated fields
                .hasRefund(amountDiff.compareTo(BigDecimal.ZERO) < 0)
                .refundAmount(exchange.getRefundAmount())
                .refundPaymentMethod(exchange.getRefundPaymentMethod())
                .refundTransactionId(exchange.getRefundTransactionId())
                .hasAdditionalCharge(amountDiff.compareTo(BigDecimal.ZERO) > 0)
                .additionalChargeAmount(amountDiff.compareTo(BigDecimal.ZERO) > 0 ? amountDiff : null)
                .message("Troca realizada com sucesso")
                .build();
    }
}
