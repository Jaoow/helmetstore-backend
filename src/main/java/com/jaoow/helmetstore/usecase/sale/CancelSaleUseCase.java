package com.jaoow.helmetstore.usecase.sale;

import com.jaoow.helmetstore.cache.CacheNames;
import com.jaoow.helmetstore.dto.sale.SaleCancellationRequestDTO;
import com.jaoow.helmetstore.dto.sale.SaleCancellationResponseDTO;
import com.jaoow.helmetstore.exception.BusinessException;
import com.jaoow.helmetstore.exception.ResourceNotFoundException;
import com.jaoow.helmetstore.helper.InventoryHelper;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.balance.*;
import com.jaoow.helmetstore.model.inventory.Inventory;
import com.jaoow.helmetstore.model.inventory.InventoryItem;
import com.jaoow.helmetstore.model.sale.SaleItem;
import com.jaoow.helmetstore.model.sale.SaleStatus;
import com.jaoow.helmetstore.repository.AccountRepository;
import com.jaoow.helmetstore.repository.InventoryItemRepository;
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
import java.util.List;

/**
 * Use Case: Cancel a sale (total or partial)
 *
 * Responsibilities:
 * - Validate cancellation request
 * - Update sale status
 * - Reverse inventory (return stock)
 * - Generate refund transaction if needed
 * - Record cancellation metadata
 */
@Component
@RequiredArgsConstructor
public class CancelSaleUseCase {

    private final SaleRepository saleRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final InventoryHelper inventoryHelper;

    @Caching(evict = {
            @CacheEvict(value = CacheNames.PRODUCT_INDICATORS, allEntries = true),
            @CacheEvict(value = CacheNames.MOST_SOLD_PRODUCTS, allEntries = true),
            @CacheEvict(value = CacheNames.PRODUCT_STOCK, allEntries = true),
            @CacheEvict(value = CacheNames.REVENUE_AND_PROFIT, allEntries = true),
            @CacheEvict(value = CacheNames.SALES_HISTORY, allEntries = true)
    })
    @Transactional
    public SaleCancellationResponseDTO execute(Long saleId, SaleCancellationRequestDTO request, Principal principal) {
        // 1. Load and validate sale
        Inventory inventory = inventoryHelper.getInventoryFromPrincipal(principal);
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new ResourceNotFoundException("Venda não encontrada: " + saleId));

        if (!sale.getInventory().getId().equals(inventory.getId())) {
            throw new BusinessException("Venda não pertence ao inventário do usuário");
        }

        // 2. Validate cancellation
        validateCancellation(sale, request);

        // 3. Calculate total paid amount
        BigDecimal totalPaid = sale.getPayments().stream()
                .map(payment -> payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Reverse inventory
        if (request.getCancelEntireSale()) {
            reverseTotalInventory(sale, inventory);
        } else {
            reversePartialInventory(sale, request.getItemsToCancel(), inventory);
        }

        // 5. Update sale status
        updateSaleStatus(sale, request, principal);

        // 6. Generate refund transaction if needed
        Long refundTransactionId = null;
        if (request.getGenerateRefund()) {
            validateRefund(sale, request, totalPaid);
            refundTransactionId = generateRefundTransaction(sale, request, principal);

            // Note: hasRefund is technically derivable from (refundAmount != null && refundAmount > 0)
            // but we maintain it as a flag for query performance and clarity
            sale.setHasRefund(true);

            // Accumulate refund amount if multiple refunds occur
            BigDecimal previousRefund = sale.getRefundAmount() != null ? sale.getRefundAmount() : BigDecimal.ZERO;
            sale.setRefundAmount(previousRefund.add(request.getRefundAmount()));

            sale.setRefundPaymentMethod(request.getRefundPaymentMethod());
            sale.setRefundTransactionId(refundTransactionId);

            // Reverse COGS to restore profit calculation in double ledger
            reverseCOGSTransactions(sale, request, principal);
        }

        // 7. Save sale
        Sale savedSale = saleRepository.save(sale);

        // 8. Build response
        return SaleCancellationResponseDTO.builder()
                .saleId(savedSale.getId())
                .status(savedSale.getStatus())
                .cancelledAt(savedSale.getCancelledAt())
                .cancelledBy(savedSale.getCancelledBy())
                .cancellationReason(savedSale.getCancellationReason())
                .cancellationNotes(savedSale.getCancellationNotes())
                .hasRefund(savedSale.getHasRefund())
                .refundAmount(savedSale.getRefundAmount())
                .refundPaymentMethod(savedSale.getRefundPaymentMethod())
                .refundTransactionId(refundTransactionId)
                .message("Venda cancelada com sucesso")
                .build();
    }

    private void validateCancellation(Sale sale, SaleCancellationRequestDTO request) {
        // Cannot cancel an already fully cancelled sale
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessException("Não é possível cancelar uma venda já totalmente cancelada");
        }

        // Validate payment exists when refund is requested
        if (request.getGenerateRefund() && (sale.getPayments() == null || sale.getPayments().isEmpty())) {
            throw new BusinessException("Não é possível gerar estorno para venda sem pagamento");
        }

        // Validate partial cancellation
        if (!request.getCancelEntireSale() && (request.getItemsToCancel() == null || request.getItemsToCancel().isEmpty())) {
            throw new BusinessException("Para cancelamento parcial, é necessário especificar os itens a cancelar");
        }

        // Partial cancellation not allowed for sales with only one item
        if (!request.getCancelEntireSale() && sale.getItems().size() == 1) {
            throw new BusinessException("Não é possível cancelar parcialmente uma venda com apenas um item");
        }

        // Partial cancellation requires refund (business rule)
        if (!request.getCancelEntireSale() && !request.getGenerateRefund()) {
            throw new BusinessException("Cancelamento parcial exige estorno do valor proporcional");
        }

        // Validate items to cancel
        if (!request.getCancelEntireSale()) {
            for (var itemCancellation : request.getItemsToCancel()) {
                SaleItem item = sale.getItems().stream()
                        .filter(si -> si.getId().equals(itemCancellation.getItemId()))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException("Item não encontrado na venda: " + itemCancellation.getItemId()));

                if (item.getIsCancelled()) {
                    throw new BusinessException("Item já foi cancelado: " + itemCancellation.getItemId());
                }

                int remainingQuantity = item.getQuantity() - (item.getCancelledQuantity() != null ? item.getCancelledQuantity() : 0);
                if (itemCancellation.getQuantityToCancel() > remainingQuantity) {
                    throw new BusinessException("Quantidade a cancelar excede a quantidade disponível do item");
                }
            }
        }
    }

    private void validateRefund(Sale sale, SaleCancellationRequestDTO request, BigDecimal totalPaid) {
        if (request.getRefundAmount() == null) {
            throw new BusinessException("O valor do estorno é obrigatório quando generateRefund = true");
        }

        if (request.getRefundPaymentMethod() == null) {
            throw new BusinessException("O método de reembolso é obrigatório quando generateRefund = true");
        }

        if (request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("O valor do estorno deve ser maior que zero");
        }

        if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("Não é possível gerar estorno para venda não paga");
        }

        // Verify that total refund (including previous refunds) does not exceed total paid
        BigDecimal totalAlreadyRefunded = sale.getRefundAmount() != null ? sale.getRefundAmount() : BigDecimal.ZERO;
        BigDecimal totalRefundAfterThisRequest = totalAlreadyRefunded.add(request.getRefundAmount());

        if (totalRefundAfterThisRequest.compareTo(totalPaid) > 0) {
            throw new BusinessException(String.format(
                "O valor do estorno (R$ %.2f) somado aos estornos já realizados (R$ %.2f) excede o valor total pago na venda (R$ %.2f). Disponível para estorno: R$ %.2f",
                request.getRefundAmount(),
                totalAlreadyRefunded,
                totalPaid,
                totalPaid.subtract(totalAlreadyRefunded)
            ));
        }
    }

    private void reverseTotalInventory(Sale sale, Inventory inventory) {
        // Only reverse inventory - item cancellation status is updated separately in updateSaleStatus
        for (SaleItem item : sale.getItems()) {
            if (!item.getIsCancelled()) {
                reverseInventoryForItem(item, item.getQuantity(), inventory);
            }
        }
    }

    private void reversePartialInventory(Sale sale, List<SaleCancellationRequestDTO.ItemCancellationDTO> itemsToCancel, Inventory inventory) {
        for (var cancellation : itemsToCancel) {
            SaleItem item = sale.getItems().stream()
                    .filter(si -> si.getId().equals(cancellation.getItemId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("Item não encontrado: " + cancellation.getItemId()));

            reverseInventoryForItem(item, cancellation.getQuantityToCancel(), inventory);

            // Update item cancellation tracking
            int currentCancelled = item.getCancelledQuantity() != null ? item.getCancelledQuantity() : 0;
            item.setCancelledQuantity(currentCancelled + cancellation.getQuantityToCancel());

            if (item.getCancelledQuantity().equals(item.getQuantity())) {
                item.setIsCancelled(true);
            }
        }
    }

    private void reverseInventoryForItem(SaleItem item, Integer quantity, Inventory inventory) {
        InventoryItem inventoryItem = inventoryItemRepository
                .findByInventoryAndProductVariant(inventory, item.getProductVariant())
                .orElseThrow(() -> new BusinessException("Item de inventário não encontrado para a variante: " + item.getProductVariant().getId()));

        // Return stock
        inventoryItem.setQuantity(inventoryItem.getQuantity() + quantity);
        inventoryItemRepository.save(inventoryItem);
    }

    private void updateSaleStatus(Sale sale, SaleCancellationRequestDTO request, Principal principal) {
        sale.setCancelledAt(LocalDateTime.now());
        sale.setCancelledBy(principal.getName());
        sale.setCancellationReason(request.getReason());
        sale.setCancellationNotes(request.getNotes());

        if (request.getCancelEntireSale()) {
            sale.setStatus(SaleStatus.CANCELLED);
            // Mark all items as cancelled
            for (SaleItem item : sale.getItems()) {
                item.setIsCancelled(true);
                item.setCancelledQuantity(item.getQuantity());
            }
            // Zero out totals for fully cancelled sale
            sale.setTotalAmount(BigDecimal.ZERO);
            sale.setTotalProfit(BigDecimal.ZERO);
        } else {
            // Check if all items are now cancelled
            boolean allItemsCancelled = sale.getItems().stream()
                    .allMatch(item -> item.getIsCancelled() ||
                             (item.getCancelledQuantity() != null && item.getCancelledQuantity().equals(item.getQuantity())));

            if (allItemsCancelled) {
                sale.setStatus(SaleStatus.CANCELLED);
                sale.setTotalAmount(BigDecimal.ZERO);
                sale.setTotalProfit(BigDecimal.ZERO);
            } else {
                sale.setStatus(SaleStatus.PARTIALLY_CANCELLED);
                // Recalculate totals based on remaining (non-cancelled) items
                recalculateSaleTotals(sale);
            }
        }
    }

    /**
     * Recalculates sale totals (amount and profit) based on active items
     * Used after partial cancellation to reflect the actual sale value
     */
    private void recalculateSaleTotals(Sale sale) {
        BigDecimal newTotalAmount = BigDecimal.ZERO;
        BigDecimal newTotalProfit = BigDecimal.ZERO;

        for (SaleItem item : sale.getItems()) {
            // Calculate remaining quantity for this item
            int remainingQuantity = item.getQuantity() - (item.getCancelledQuantity() != null ? item.getCancelledQuantity() : 0);

            if (remainingQuantity > 0) {
                // Item value = unit price * remaining quantity
                BigDecimal itemAmount = item.getUnitPrice().multiply(BigDecimal.valueOf(remainingQuantity));
                newTotalAmount = newTotalAmount.add(itemAmount);

                // Item profit = (unit price - unit cost) * remaining quantity
                BigDecimal itemProfit = item.getUnitPrice()
                    .subtract(item.getCostBasisAtSale())
                    .multiply(BigDecimal.valueOf(remainingQuantity));
                newTotalProfit = newTotalProfit.add(itemProfit);
            }
        }

        sale.setTotalAmount(newTotalAmount);
        sale.setTotalProfit(newTotalProfit);
    }

    private Long generateRefundTransaction(Sale sale, SaleCancellationRequestDTO request, Principal principal) {
        // Find account for refund payment method
        AccountType accountType = (request.getRefundPaymentMethod() == PaymentMethod.CASH)
                ? AccountType.CASH
                : AccountType.BANK;

        Account account = accountRepository.findByUserEmailAndType(principal.getName(), accountType)
                .orElseThrow(() -> new BusinessException("Conta não encontrada para o método de pagamento: " + request.getRefundPaymentMethod()));

        // Determine wallet destination
        AccountType walletDest = (request.getRefundPaymentMethod() == PaymentMethod.CASH)
                ? AccountType.CASH
                : AccountType.BANK;

        // Create refund transaction (EXPENSE - money going out)
        Transaction refundTransaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.REFUND)
                .description("Estorno de venda #" + sale.getId() + " - " + request.getReason().getDescription())
                .amount(request.getRefundAmount().negate()) // Negative value (expense)
                .paymentMethod(request.getRefundPaymentMethod())
                .reference("SALE_REFUND#" + sale.getId())
                .account(account)
                // DOUBLE-ENTRY LEDGER FLAGS
                .affectsProfit(false)   // Refund DOES affect profit (reduces profit)
                .affectsCash(true)     // And reduces cash
                .walletDestination(walletDest)
                .build();

        Transaction savedTransaction = transactionRepository.save(refundTransaction);
        return savedTransaction.getId();
    }

    /**
     * Reverses COGS (Cost of Goods Sold) transactions for cancelled items
     * This restores the cost to profit since the product returned to inventory
     */
    private void reverseCOGSTransactions(Sale sale, SaleCancellationRequestDTO request, Principal principal) {
        Account systemAccount = accountRepository.findByUserEmailAndType(principal.getName(), AccountType.CASH)
                .orElseThrow(() -> new BusinessException("Conta não encontrada"));

        if (request.getCancelEntireSale()) {
            // Reverse COGS for all items
            BigDecimal totalCOGSReversal = BigDecimal.ZERO;

            for (SaleItem item : sale.getItems()) {
                if (!item.getIsCancelled()) {
                    BigDecimal itemCost = item.getCostBasisAtSale().multiply(BigDecimal.valueOf(item.getQuantity()));
                    totalCOGSReversal = totalCOGSReversal.add(itemCost);
                }
            }

            if (totalCOGSReversal.compareTo(BigDecimal.ZERO) > 0) {
                Transaction cogsReversalTx = Transaction.builder()
                        .date(LocalDateTime.now())
                        .type(TransactionType.INCOME)
                        .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                        .description("Reversão COGS - Cancelamento total venda #" + sale.getId())
                        .amount(totalCOGSReversal) // Positive value (reverses the expense)
                        .paymentMethod(PaymentMethod.CASH)
                        .reference("SALE_CANCEL#" + sale.getId())
                        .account(systemAccount)
                        .affectsProfit(true)  // Reversal increases profit back
                        .affectsCash(false)   // No cash impact (accounting only)
                        .walletDestination(null)
                        .build();

                transactionRepository.save(cogsReversalTx);
            }
        } else {
            // Reverse COGS only for cancelled items
            BigDecimal totalCOGSReversal = BigDecimal.ZERO;

            for (var cancellation : request.getItemsToCancel()) {
                SaleItem item = sale.getItems().stream()
                        .filter(si -> si.getId().equals(cancellation.getItemId()))
                        .findFirst()
                        .orElseThrow();

                BigDecimal itemCost = item.getCostBasisAtSale().multiply(BigDecimal.valueOf(cancellation.getQuantityToCancel()));
                totalCOGSReversal = totalCOGSReversal.add(itemCost);
            }

            if (totalCOGSReversal.compareTo(BigDecimal.ZERO) > 0) {
                Transaction cogsReversalTx = Transaction.builder()
                        .date(LocalDateTime.now())
                        .type(TransactionType.INCOME)
                        .detail(TransactionDetail.COST_OF_GOODS_SOLD)
                        .description("Reversão COGS - Cancelamento parcial venda #" + sale.getId())
                        .amount(totalCOGSReversal) // Positive value (reverses the expense)
                        .paymentMethod(PaymentMethod.CASH)
                        .reference("SALE_CANCEL#" + sale.getId())
                        .account(systemAccount)
                        .affectsProfit(true)  // Reversal increases profit back
                        .affectsCash(false)   // No cash impact (accounting only)
                        .walletDestination(null)
                        .build();

                transactionRepository.save(cogsReversalTx);
            }
        }
    }
}
