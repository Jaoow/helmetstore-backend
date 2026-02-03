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
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.balance.*;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.sale.CancellationReason;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.model.sale.SaleStatus;
import com.jaoow.helmetstore.repository.AccountRepository;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
import com.jaoow.helmetstore.repository.InventoryRepository;
import com.jaoow.helmetstore.repository.ProductExchangeRepository;
import com.jaoow.helmetstore.repository.ProductVariantRepository;
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
 * 2. Cancels the returned items (OPERATIONAL cancellation)
 * 3. Reverses COGS explicitly (restores cost)
 * 4. Creates derived sale (NO transactions)
 * 5. Creates financial adjustment (ONLY the difference)
 * 6. Records the exchange transaction for traceability
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * ⚠️ RULE OF GOLD:
 * - Exchange is NOT a financial event
 * - Exchange is an ACCOUNTING REAPPOINTMENT
 * - ONLY the difference is a financial event
 * - Original sale keeps profit history (IMMUTABLE)
 * - Transaction is source of truth
 * - Sale is operational aggregator
 * ═══════════════════════════════════════════════════════════════════════════
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
    private final InventoryRepository inventoryRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final ProductVariantRepository productVariantRepository;

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
        // STEP 4b: Validate stock availability for new items BEFORE any changes
        // ⚠️ CRITICAL: Must validate stock before processing returns to ensure
        // atomicity - if stock is insufficient, no changes should be made
        // ========================================================================
        validateNewItemsStockAvailability(request.getNewItems(), inventory);

        // ========================================================================
        // STEP 5: Cancel returned items from original sale (OPERATIONAL ONLY)
        // ========================================================================
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

        // ========================================================================
        // STEP 6: Explicitly reverse COGS for returned items (using REQUEST data)
        // ⚠️ CRITICAL: Uses explicit request data, NOT entity state
        // This prevents ambiguity and ensures correct COGS calculation
        // ========================================================================
        reverseCOGSForReturnedItems(request, originalSale, principal);

        // ========================================================================
        // STEP 6b: Create COGS for new products
        // ⚠️ CRITICAL: New products must have their COGS recorded
        // Since isDerivedFromExchange=true skips all transactions in CreateSaleUseCase,
        // we must explicitly create COGS transactions here
        // ========================================================================
        createCOGSForNewItems(request, originalSale, principal);

        // ========================================================================
        // STEP 7: Create financial adjustment transaction (ONLY THE DIFFERENCE)
        // This is the ONLY financial impact of the exchange
        // ========================================================================
        Long financialAdjustmentTransactionId = null;

        if (amountDifference.compareTo(BigDecimal.ZERO) != 0) {
            financialAdjustmentTransactionId = createFinancialAdjustmentTransaction(
                    amountDifference,
                    request,
                    originalSale,
                    exchangeDate,
                    principal
            );
        }

        // ========================================================================
        // STEP 8: Create new sale with exchanged products (NO FINANCIAL TRANSACTIONS)
        // ========================================================================
        SaleCreateDTO newSaleDTO = buildNewSaleDTO(request, exchangeDate, returnedAmount, amountDifference);
        var newSaleResponse = createSaleUseCase.execute(newSaleDTO, principal);

        // Load the created sale entity
        Sale newSale = saleRepository.findById(newSaleResponse.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Nova venda não encontrada após criação"));

        // ========================================================================
        // STEP 9: Record the exchange transaction
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
                .refundPaymentMethod(amountDifference.compareTo(BigDecimal.ZERO) < 0 ? request.getRefundPaymentMethod() : null)
                .refundTransactionId(financialAdjustmentTransactionId)
                .build();

        ProductExchange savedExchange = productExchangeRepository.save(exchange);

        // ========================================================================
        // STEP 10: Build and return response
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
     * Validates stock availability for all new items BEFORE any changes are made.
     * This ensures atomicity - if any item has insufficient stock, the entire
     * exchange operation is aborted without making any partial changes.
     */
    private void validateNewItemsStockAvailability(
            List<ProductExchangeRequestDTO.NewItemDTO> newItems,
            Inventory inventory
    ) {
        for (ProductExchangeRequestDTO.NewItemDTO item : newItems) {
            ProductVariant variant = productVariantRepository.findById(item.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variante de produto não encontrada: " + item.getVariantId()));

            InventoryItem inventoryItem = inventoryItemRepository
                    .findByInventoryAndProductVariant(inventory, variant)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Produto não encontrado no inventário: " + variant.getSku()));

            if (inventoryItem.getQuantity() < item.getQuantity()) {
                throw new BusinessException(String.format(
                        "Estoque insuficiente para o produto %s. Disponível: %d, Solicitado: %d",
                        variant.getSku(),
                        inventoryItem.getQuantity(),
                        item.getQuantity()
                ));
            }
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
                .hasFinancialImpact(shouldGenerateRefund) // Only has financial impact if there's actual refund
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
     * - A nova venda registra apenas o VALOR PAGO pelo cliente (diferença)
     * - Preços dos itens são ajustados proporcionalmente para refletir apenas o pagamento
     * - Marcada como isDerivedFromExchange = true, portanto NÃO gera transações financeiras
     * - As transações originais são mantidas (reapontamento contábil)
     *
     * Exemplo:
     * - Produto devolvido: R$ 100
     * - Produto novo: R$ 160
     * - Diferença: R$ 60 (cliente paga)
     * - Venda registrada com totalAmount = R$ 60 (mesmo que produto valha R$ 160)
     */
    private SaleCreateDTO buildNewSaleDTO(
            ProductExchangeRequestDTO request,
            LocalDateTime exchangeDate,
            BigDecimal returnedAmount,
            BigDecimal amountDifference
    ) {
        // Use ORIGINAL prices for sale items
        // The isDerivedFromExchange flag ensures no duplicate financial transactions
        // Price adjustment is handled via the separate financial adjustment transaction
        List<SaleItemCreateDTO> saleItems = request.getNewItems().stream()
                .map(item -> SaleItemCreateDTO.builder()
                        .variantId(item.getVariantId())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice()) // Keep original price for proper records
                        .build())
                .toList();

        // Calculate the total value of the new sale
        BigDecimal newSaleTotal = calculateNewSaleAmount(request.getNewItems());

        // Build payments list:
        // The sale total must be covered by:
        // 1. Credit from returned product (returnedAmount) - treated as CASH credit
        // 2. Additional customer payment (amountDifference, if positive)
        List<SalePaymentCreateDTO> payments = new ArrayList<>();

        // First, add the credit from the returned product
        if (returnedAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Cap the credit at the new sale total (in case returned > new sale)
            BigDecimal creditAmount = returnedAmount.min(newSaleTotal);
            payments.add(SalePaymentCreateDTO.builder()
                    .paymentMethod(PaymentMethod.CASH) // Credit applied as CASH
                    .amount(creditAmount)
                    .build());
        }

        // Then, add customer's additional payment if needed
        if (amountDifference.compareTo(BigDecimal.ZERO) > 0) {
            if (request.getNewSalePayments() != null && !request.getNewSalePayments().isEmpty()) {
                payments.addAll(request.getNewSalePayments());
            } else {
                // Default payment if none provided
                payments.add(SalePaymentCreateDTO.builder()
                        .paymentMethod(PaymentMethod.CASH)
                        .amount(amountDifference)
                        .build());
            }
        }

        return SaleCreateDTO.builder()
                .date(exchangeDate)
                .items(saleItems)
                .payments(payments)
                .isDerivedFromExchange(true) // CRITICAL: Prevents duplicate transactions
                .build();
    }

    /**
     * Builds the response DTO
     */
    /**
     * Reverses COGS for returned items in exchange
     *
     * ⚠️ CRITICAL: Uses EXPLICIT request data (itemsToReturn), NOT entity state
     * This prevents ambiguity caused by mutable entity state and ensures correct calculation
     *
     * @param request The exchange request with explicit items to return
     * @param originalSale The original sale
     * @param principal The authenticated user
     */
    private void reverseCOGSForReturnedItems(
            ProductExchangeRequestDTO request,
            Sale originalSale,
            Principal principal
    ) {
        Account systemAccount = accountRepository.findByUserEmailAndType(principal.getName(), AccountType.CASH)
                .orElseThrow(() -> new BusinessException("Conta não encontrada"));

        BigDecimal totalCOGSReversal = BigDecimal.ZERO;

        // ⚠️ Use EXPLICIT request data, not entity state
        for (var itemToReturn : request.getItemsToReturn()) {
            SaleItem saleItem = originalSale.getItems().stream()
                    .filter(si -> si.getId().equals(itemToReturn.getSaleItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Item não encontrado: " + itemToReturn.getSaleItemId()));

            // Use explicit quantity from REQUEST, not entity state
            BigDecimal itemCost = saleItem.getCostBasisAtSale()
                    .multiply(BigDecimal.valueOf(itemToReturn.getQuantityToReturn()));
            totalCOGSReversal = totalCOGSReversal.add(itemCost);
        }

        if (totalCOGSReversal.compareTo(BigDecimal.ZERO) > 0) {
            Transaction cogsReversalTx = Transaction.builder()
                    .date(LocalDateTime.now())
                    .type(TransactionType.INCOME)
                    .detail(TransactionDetail.COGS_REVERSAL)
                    .description(String.format("Reversão COGS - Troca venda #%d", originalSale.getId()))
                    .amount(totalCOGSReversal) // Positive value (reverses the expense)
                    .paymentMethod(PaymentMethod.CASH)
                    .reference("EXCHANGE_COGS#" + originalSale.getId())
                    .referenceSubId(null)
                    .account(systemAccount)
                    .affectsProfit(true)  // Reversal increases profit back
                    .affectsCash(false)   // No cash impact (accounting only)
                    .walletDestination(null)
                    .build();

            transactionRepository.save(cogsReversalTx);
        }
    }

    /**
     * Creates COGS transactions for new products in exchange
     *
     * ⚠️ CRITICAL: Since isDerivedFromExchange=true causes CreateSaleUseCase to skip
     * ALL financial transactions (including COGS), we must explicitly create COGS here.
     * Without this, the new product's cost would never be recorded, breaking profit calculation.
     *
     * @param request The exchange request with new items
     * @param originalSale The original sale (for reference)
     * @param principal The authenticated user
     */
    private void createCOGSForNewItems(
            ProductExchangeRequestDTO request,
            Sale originalSale,
            Principal principal
    ) {
        Account systemAccount = accountRepository.findByUserEmailAndType(principal.getName(), AccountType.CASH)
                .orElseThrow(() -> new BusinessException("Conta não encontrada"));

        Inventory inventory = inventoryRepository.findByUserEmail(principal.getName())
                .orElseThrow(() -> new BusinessException("Inventário não encontrado"));

        BigDecimal totalCOGS = BigDecimal.ZERO;

        for (var newItem : request.getNewItems()) {
            ProductVariant variant = productVariantRepository.findById(newItem.getVariantId())
                    .orElseThrow(() -> new BusinessException("Variante não encontrada: " + newItem.getVariantId()));

            InventoryItem inventoryItem = inventoryItemRepository
                    .findByInventoryAndProductVariant(inventory, variant)
                    .orElseThrow(() -> new BusinessException("Item de inventário não encontrado"));

            // Calculate COGS for this item
            BigDecimal itemCost = inventoryItem.getAverageCost()
                    .multiply(BigDecimal.valueOf(newItem.getQuantity()));
            totalCOGS = totalCOGS.add(itemCost);
        }

        if (totalCOGS.compareTo(BigDecimal.ZERO) > 0) {
            Transaction cogsTx = Transaction.builder()
                    .date(LocalDateTime.now())
                    .type(TransactionType.EXPENSE)
                    .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                    .description(String.format("COGS novos produtos - Troca venda #%d", originalSale.getId()))
                    .amount(totalCOGS.negate()) // Negative value (expense)
                    .paymentMethod(PaymentMethod.CASH)
                    .reference("EXCHANGE_COGS_NEW#" + originalSale.getId())
                    .referenceSubId(null)
                    .account(systemAccount)
                    .affectsProfit(true)  // YES: COGS reduces profit
                    .affectsCash(false)   // No cash impact (accounting only)
                    .walletDestination(null)
                    .build();

            transactionRepository.save(cogsTx);
        }
    }

    /**
     * Creates the financial adjustment transaction for the exchange difference
     *
     * ⚠️ CRITICAL ACCOUNTING RULE:
     * This is the ONLY financial impact of an exchange. Exchanges are accounting reappointments,
     * not new financial events. Only the difference between returned and new items creates
     * a transaction that affects profit/cash.
     *
     * @param amountDifference The difference (positive = additional charge, negative = refund)
     * @param request The exchange request
     * @param originalSale The original sale
     * @param exchangeDate The exchange date
     * @param principal The authenticated user
     * @return The ID of the created transaction, or null if no difference
     */
    private Long createFinancialAdjustmentTransaction(
            BigDecimal amountDifference,
            ProductExchangeRequestDTO request,
            Sale originalSale,
            LocalDateTime exchangeDate,
            Principal principal
    ) {
        // Get user's account for wallet destination
        AccountType walletType = (request.getRefundPaymentMethod() == PaymentMethod.CASH)
                ? AccountType.CASH
                : AccountType.BANK;

        Account userAccount = accountRepository.findByUserEmailAndType(principal.getName(), walletType)
                .orElseThrow(() -> new BusinessException("Conta não encontrada"));

        Transaction transaction;

        if (amountDifference.compareTo(BigDecimal.ZERO) > 0) {
            // ========================================================================
            // Customer pays MORE: Create INCOME transaction for additional charge
            // ========================================================================
            transaction = Transaction.builder()
                    .date(exchangeDate)
                    .amount(amountDifference)  // Positive value (income)
                    .description(String.format(
                            "Cobrança adicional na troca - Venda #%d",
                            originalSale.getId()
                    ))
                    .type(TransactionType.INCOME)
                    .detail(TransactionDetail.SALE)
                    .paymentMethod(request.getNewSalePayments() != null && !request.getNewSalePayments().isEmpty()
                            ? request.getNewSalePayments().get(0).getPaymentMethod()
                            : PaymentMethod.CASH)
                    .reference("EXCHANGE_CHARGE#" + originalSale.getId())
                    .referenceSubId(null)
                    .account(userAccount)
                    .affectsProfit(true)       // YES: This creates profit
                    .affectsCash(true)         // YES: Cash increases
                    .walletDestination(walletType)
                    .build();

        } else {
            // ========================================================================
            // Customer receives REFUND: Create EXPENSE transaction for the difference
            // ========================================================================
            BigDecimal refundAmount = amountDifference.abs();

            transaction = Transaction.builder()
                    .date(exchangeDate)
                    .amount(refundAmount.negate())  // Negative value (expense)
                    .description(String.format(
                            "Reembolso na troca (método: %s) - Venda #%d",
                            request.getRefundPaymentMethod(),
                            originalSale.getId()
                    ))
                    .type(TransactionType.EXPENSE)
                    .detail(TransactionDetail.SALE_REFUND)
                    .paymentMethod(request.getRefundPaymentMethod())
                    .reference("EXCHANGE_REFUND#" + originalSale.getId())
                    .referenceSubId(null)
                    .account(userAccount)
                    .affectsProfit(true)       // YES: This reduces profit
                    .affectsCash(true)         // YES: Cash decreases
                    .walletDestination(walletType)
                    .build();
        }

        Transaction savedTransaction = transactionRepository.save(transaction);
        return savedTransaction.getId();
    }

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
