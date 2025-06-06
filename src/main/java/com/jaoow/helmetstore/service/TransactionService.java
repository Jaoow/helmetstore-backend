package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.exception.AccountNotFoundException;
import com.jaoow.helmetstore.model.Product;
import com.jaoow.helmetstore.model.ProductVariant;
import com.jaoow.helmetstore.model.PurchaseOrder;
import com.jaoow.helmetstore.model.Sale;
import com.jaoow.helmetstore.model.balance.Account;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.model.balance.TransactionType;
import com.jaoow.helmetstore.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final String SALE_REFERENCE_PREFIX = "SALE#";
    private static final String PURCHASE_ORDER_REFERENCE_PREFIX = "PURCHASE_ORDER#";

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final ModelMapper modelMapper;

    @Transactional
    public void createManualTransaction(TransactionCreateDTO dto, Principal principal) {
        Transaction transaction = modelMapper.map(dto, Transaction.class);
        Account account = accountService.findAccountByPaymentMethodAndUser(dto.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException("No account found for the given payment method."));

        transaction.setAccount(account);
        accountService.applyTransaction(account, transaction);
        transactionRepository.save(transaction);
    }

    @Transactional
    public void recordTransactionFromSale(Sale sale, Principal principal) {
        Account account = accountService.findAccountByPaymentMethodAndUser(sale.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException("No account found for the given payment method."));

        BigDecimal totalAmount = sale.getUnitPrice().multiply(BigDecimal.valueOf(sale.getQuantity()));

        LocalDateTime date = sale.getDate();

        Transaction transaction = Transaction.builder()
                .date(date)
                .type(TransactionType.INCOME)
                .description(SALE_REFERENCE_PREFIX + formatProductVariantName(sale.getProductVariant()))
                .amount(totalAmount)
                .paymentMethod(sale.getPaymentMethod())
                .reference(SALE_REFERENCE_PREFIX + sale.getId())
                .account(account)
                .build();

        accountService.applyTransaction(account, transaction);
        transactionRepository.save(transaction);
    }

    @Transactional
    public void recordTransactionFromPurchaseOrder(PurchaseOrder purchaseOrder, Principal principal) {
        // Assuming all purchase orders are paid via PIX
        // TODO: Handle different payment methods for purchase orders
        Account account = accountService.findAccountByPaymentMethodAndUser(PaymentMethod.PIX, principal)
                .orElseThrow(() -> new AccountNotFoundException("No account found for the given payment method."));

        Transaction transaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .description(PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getOrderNumber())
                .amount(purchaseOrder.getTotalAmount())
                .paymentMethod(PaymentMethod.PIX)
                .reference(PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getId())
                .account(account)
                .build();

        accountService.applyTransaction(account, transaction); // maybe should be before save?
        transactionRepository.save(transaction);
    }

    @Transactional
    public void updateTransaction(Long transactionId, TransactionCreateDTO dto, Principal principal) {
        Transaction transaction = transactionRepository.findByIdAndAccountUserEmail(transactionId, principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada com ID: " + transactionId));

        accountService.revertTransaction(transaction.getAccount(), transaction);
        modelMapper.map(dto, transaction);

        Account account = accountService.findAccountByPaymentMethodAndUser(dto.getPaymentMethod(), principal)
                .orElseThrow(() -> new AccountNotFoundException("Nenhuma conta encontrada para o método de pagamento informado."));

        transaction.setAccount(account);
        accountService.applyTransaction(account, transaction);

        transactionRepository.save(transaction);
    }

    @Transactional
    public void deleteTransactionById(Long transactionId, Principal principal) {
        Transaction transaction = transactionRepository.findByIdAndAccountUserEmail(transactionId, principal.getName())
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found with ID: " + transactionId));

        if (transaction.getReference().startsWith(SALE_REFERENCE_PREFIX) || transaction.getReference().startsWith(PURCHASE_ORDER_REFERENCE_PREFIX)) {
            throw new IllegalArgumentException("Você não pode excluir transações vinculadas a vendas ou pedidos de compra.");
        }

        accountService.revertTransaction(transaction.getAccount(), transaction);
        transactionRepository.delete(transaction);
    }

    @Transactional
    public void removeTransactionLinkedToPurchaseOrder(PurchaseOrder purchaseOrder) {
        String reference = PURCHASE_ORDER_REFERENCE_PREFIX + purchaseOrder.getId();
        Transaction transaction = transactionRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found for purchase order ID: " + purchaseOrder.getId()));

        accountService.revertTransaction(transaction.getAccount(), transaction);
        transactionRepository.delete(transaction);
    }

    @Transactional
    public void removeTransactionLinkedToSale(Sale sale) {
        String reference = SALE_REFERENCE_PREFIX + sale.getId();
        Transaction transaction = transactionRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found for sale ID: " + sale.getId()));

        accountService.revertTransaction(transaction.getAccount(), transaction);
        transactionRepository.delete(transaction);
    }

    private String formatProductVariantName(ProductVariant productVariant) {
        Product product = productVariant.getProduct();
        return "%s#%s#%s".formatted(product.getModel(), product.getColor(), productVariant.getSize());
    }
}
