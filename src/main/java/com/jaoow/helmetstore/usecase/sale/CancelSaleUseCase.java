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
            validateRefund(request, totalPaid);
            refundTransactionId = generateRefundTransaction(sale, request, principal);
            
            // Note: hasRefund is technically derivable from (refundAmount != null && refundAmount > 0)
            // but we maintain it as a flag for query performance and clarity
            sale.setHasRefund(true);
            sale.setRefundAmount(request.getRefundAmount());
            sale.setRefundPaymentMethod(request.getRefundPaymentMethod());
            sale.setRefundTransactionId(refundTransactionId);
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

        // Prevent duplicate refunds (domain-level protection)
        if (request.getGenerateRefund() && sale.getHasRefund() != null && sale.getHasRefund()) {
            throw new BusinessException("Venda já possui estorno registrado. Não é permitido estorno duplicado.");
        }

        // Validate payment exists when refund is requested
        if (request.getGenerateRefund() && (sale.getPayments() == null || sale.getPayments().isEmpty())) {
            throw new BusinessException("Não é possível gerar estorno para venda sem pagamento");
        }

        // Validate partial cancellation
        if (!request.getCancelEntireSale() && (request.getItemsToCancel() == null || request.getItemsToCancel().isEmpty())) {
            throw new BusinessException("Para cancelamento parcial, é necessário especificar os itens a cancelar");
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

    private void validateRefund(SaleCancellationRequestDTO request, BigDecimal totalPaid) {
        if (request.getRefundAmount() == null) {
            throw new BusinessException("O valor do estorno é obrigatório quando generateRefund = true");
        }

        if (request.getRefundPaymentMethod() == null) {
            throw new BusinessException("O método de reembolso é obrigatório quando generateRefund = true");
        }

        if (request.getRefundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("O valor do estorno deve ser maior que zero");
        }

        if (request.getRefundAmount().compareTo(totalPaid) > 0) {
            throw new BusinessException("O valor do estorno não pode ser maior que o valor pago: " + totalPaid);
        }

        if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("Não é possível gerar estorno para venda não paga");
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
        } else {
            // Check if all items are now cancelled
            boolean allItemsCancelled = sale.getItems().stream()
                    .allMatch(item -> item.getIsCancelled() || 
                             (item.getCancelledQuantity() != null && item.getCancelledQuantity().equals(item.getQuantity())));
            
            if (allItemsCancelled) {
                sale.setStatus(SaleStatus.CANCELLED);
            } else {
                sale.setStatus(SaleStatus.PARTIALLY_CANCELLED);
            }
        }
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
                .affectsProfit(false)  // Refund doesn't affect profit (just returns previous expense)
                .affectsCash(true)     // But it does reduce cash
                .walletDestination(walletDest)
                .build();

        Transaction savedTransaction = transactionRepository.save(refundTransaction);
        return savedTransaction.getId();
    }
}
