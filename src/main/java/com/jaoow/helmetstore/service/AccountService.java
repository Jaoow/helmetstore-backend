package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.AccountInfo;
import com.jaoow.helmetstore.dto.balance.BalanceConversionDTO;
import com.jaoow.helmetstore.model.balance.*;
import com.jaoow.helmetstore.model.user.User;
import com.jaoow.helmetstore.repository.AccountRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.service.user.UserService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserService userService;
    private final ModelMapper modelMapper;
    private final CacheInvalidationService cacheInvalidationService;

    public List<AccountInfo> getAccountInfo(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return new ArrayList<>();
        }

        List<Account> accounts = accountRepository.findAllByUserEmail(principal.getName());
        if (accounts == null || accounts.isEmpty()) {
            return new ArrayList<>();
        }

        return accounts.stream()
                .filter(Objects::nonNull)
                .map(account -> {
                    AccountInfo accountInfo = modelMapper.map(account, AccountInfo.class);
                    accountInfo.setBalance(calculateAccountBalance(account));
                    return accountInfo;
                })
                .collect(Collectors.toList());
    }

    public Optional<Account> findAccountByPaymentMethodAndUser(PaymentMethod paymentMethod, Principal principal) {
        if (paymentMethod == null || principal == null || principal.getName() == null) {
            return Optional.empty();
        }

        Optional<Account> accountOpt = switch (paymentMethod) {
            case CASH ->
                accountRepository.findByUserEmailAndTypeWithTransactions(principal.getName(), AccountType.CASH);
            case PIX, CARD ->
                accountRepository.findByUserEmailAndTypeWithTransactions(principal.getName(), AccountType.BANK);
        };

        return accountOpt.or(() -> {
            try {
                User user = userService.findUserByEmail(principal.getName());
                if (user == null) {
                    return Optional.empty();
                }

                AccountType type = paymentMethod == PaymentMethod.CASH ? AccountType.CASH : AccountType.BANK;
                Account account = Account.builder()
                        .type(type)
                        .user(user)
                        .build();

                return Optional.of(accountRepository.save(account));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    public void convertBalance(BalanceConversionDTO conversionDTO, Principal principal) {
        if (conversionDTO == null || principal == null || principal.getName() == null) {
            throw new IllegalArgumentException("Parâmetros inválidos para conversão de saldo.");
        }

        if (conversionDTO.getFromAccountType() == conversionDTO.getToAccountType()) {
            throw new IllegalArgumentException("Os tipos de conta de origem e destino devem ser diferentes.");
        }

        if (conversionDTO.getAmount() == null || conversionDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor para conversão deve ser maior que zero.");
        }

        User user = userService.findUserByEmail(principal.getName());
        if (user == null) {
            throw new IllegalArgumentException("Usuário não encontrado.");
        }

        Account fromAccount = accountRepository
                .findByUserEmailAndTypeWithTransactions(principal.getName(), conversionDTO.getFromAccountType())
                .orElseGet(() -> {
                    Account newAccount = Account.builder()
                            .type(conversionDTO.getFromAccountType())
                            .user(user)
                            .build();
                    return accountRepository.save(newAccount);
                });

        Account toAccount = accountRepository
                .findByUserEmailAndType(principal.getName(), conversionDTO.getToAccountType())
                .orElseGet(() -> {
                    Account newAccount = Account.builder()
                            .type(conversionDTO.getToAccountType())
                            .user(user)
                            .build();
                    return accountRepository.save(newAccount);
                });

        // Verificar se há saldo suficiente na conta de origem
        BigDecimal fromAccountBalance = calculateAccountBalance(fromAccount);
        if (fromAccountBalance.compareTo(conversionDTO.getAmount()) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente na conta de origem para realizar a conversão.");
        }

        PaymentMethod fromPaymentMethod = conversionDTO.getFromAccountType() == AccountType.CASH ? PaymentMethod.CASH
                : PaymentMethod.PIX;
        PaymentMethod toPaymentMethod = conversionDTO.getToAccountType() == AccountType.CASH ? PaymentMethod.CASH
                : PaymentMethod.PIX;

        // Gerar descrição e referência automaticamente
        String fromAccountLabel = conversionDTO.getFromAccountType() == AccountType.CASH ? "dinheiro"
                : "conta bancária";
        String toAccountLabel = conversionDTO.getToAccountType() == AccountType.CASH ? "dinheiro" : "conta bancária";
        String description = String.format("Conversão de %s para %s", fromAccountLabel, toAccountLabel);
        String reference = String.format("CONV-%d-%s", System.currentTimeMillis(),
                conversionDTO.getFromAccountType().name().charAt(0)
                        + conversionDTO.getToAccountType().name().substring(0, 1));

        LocalDateTime clientDate = conversionDTO.getDate();
        LocalDateTime toTransactionTime = clientDate.plusSeconds(2);

        // Determine wallet destinations based on account types
        AccountType fromWalletDest = conversionDTO.getFromAccountType();
        AccountType toWalletDest = conversionDTO.getToAccountType();

        Transaction fromTransaction = Transaction.builder()
                .date(clientDate)
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.INTERNAL_TRANSFER_OUT) // Transferência interna
                .description(description)
                .amount(conversionDTO.getAmount().negate()) // FIX: Negate expense amount
                .paymentMethod(fromPaymentMethod)
                .reference(reference)
                .account(fromAccount)
                // DOUBLE-ENTRY LEDGER FLAGS
                // Internal transfers affect cash but NOT profit (money movement between accounts)
                .affectsProfit(false)  // Doesn't affect profit calculation
                .affectsCash(true)     // Does affect cash balance
                .walletDestination(fromWalletDest)  // Which wallet is being debited
                .build();

        Transaction toTransaction = Transaction.builder()
                .date(toTransactionTime)
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.INTERNAL_TRANSFER_IN) // Transferência interna
                .description(description)
                .amount(conversionDTO.getAmount())
                .paymentMethod(toPaymentMethod)
                .reference(reference)
                .account(toAccount)
                // DOUBLE-ENTRY LEDGER FLAGS
                // Internal transfers affect cash but NOT profit (money movement between accounts)
                .affectsProfit(false)  // Doesn't affect profit calculation
                .affectsCash(true)     // Does affect cash balance
                .walletDestination(toWalletDest)  // Which wallet is being credited
                .build();

        Transaction savedFromTransaction = transactionRepository.save(fromTransaction);
        Transaction savedToTransaction = transactionRepository.save(toTransaction);

        if (savedFromTransaction.getDate().isAfter(savedToTransaction.getDate())) {
            throw new IllegalStateException("Erro na ordem das transações: saída deve vir antes da entrada");
        }

        cacheInvalidationService.invalidateFinancialCaches();
    }

    /**
     * Calcula o saldo atual de uma conta baseado nas transações
     */
    public BigDecimal calculateAccountBalance(Account account) {
        if (account == null) {
            return BigDecimal.ZERO;
        }

        List<Transaction> transactions = account.getTransactions();
        if (transactions == null || transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return transactions.stream()
                .filter(transaction -> transaction != null && transaction.getAmount() != null)
                .map(transaction -> {
                    if (transaction.getType() == TransactionType.INCOME) {
                        return transaction.getAmount();
                    } else {
                        return transaction.getAmount().negate();
                    }
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calcula o saldo de uma conta por tipo para um usuário específico
     */
    public BigDecimal calculateAccountBalanceByType(String userEmail, AccountType accountType) {
        if (userEmail == null || accountType == null) {
            return BigDecimal.ZERO;
        }

        Optional<Account> accountOpt = accountRepository.findByUserEmailAndTypeWithTransactions(userEmail, accountType);
        if (accountOpt.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return calculateAccountBalance(accountOpt.get());
    }
}
