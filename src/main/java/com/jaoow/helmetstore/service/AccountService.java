package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.AccountInfo;
import com.jaoow.helmetstore.dto.balance.BalanceConversionDTO;
import com.jaoow.helmetstore.model.balance.Account;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.model.balance.TransactionDetail;
import com.jaoow.helmetstore.model.balance.TransactionType;
import com.jaoow.helmetstore.model.user.User;
import com.jaoow.helmetstore.repository.AccountRepository;
import com.jaoow.helmetstore.repository.TransactionRepository;
import com.jaoow.helmetstore.service.user.UserService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserService userService;
    private final ModelMapper modelMapper;
    private final CacheInvalidationService cacheInvalidationService;

    public List<AccountInfo> getAccountInfo(Principal principal) {
        List<Account> accounts = accountRepository.findAllByUserEmail(principal.getName());
        return accounts.stream()
                .map(account -> modelMapper.map(account, AccountInfo.class))
                .collect(Collectors.toList());
    }

    Optional<Account> findAccountByPaymentMethodAndUser(PaymentMethod paymentMethod, Principal principal) {
        Optional<Account> accountOpt = switch (paymentMethod) {
            case CASH -> accountRepository.findByUserEmailAndType(principal.getName(), AccountType.CASH);
            case PIX, CARD -> accountRepository.findByUserEmailAndType(principal.getName(), AccountType.BANK);
        };

        return accountOpt.or(() -> {
            User user = userService.findUserByEmail(principal.getName());

            AccountType type = paymentMethod == PaymentMethod.CASH ? AccountType.CASH : AccountType.BANK;
            Account account = Account.builder()
                    .type(type)
                    .balance(BigDecimal.ZERO)
                    .user(user)
                    .build();

            return Optional.of(accountRepository.save(account));
        });
    }

    public void applyTransaction(@NonNull Account account, @NonNull Transaction transaction) {
        if (!accountRepository.existsById(account.getId())) {
            throw new IllegalArgumentException("Account does not exist: " + account.getId());
        }

        if (transaction.getAccount() == null || !transaction.getAccount().getId().equals(account.getId())) {
            throw new IllegalArgumentException("Transaction account does not match the provided account.");
        }

        switch (transaction.getType()) {
            case INCOME -> {
                BigDecimal newBalance = account.getBalance().add(transaction.getAmount());
                account.setBalance(newBalance);
            }
            case EXPENSE -> {
                BigDecimal newBalance = account.getBalance().subtract(transaction.getAmount());
                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Insufficient funds for this transaction.");
                }
                account.setBalance(newBalance);
            }
        }

        accountRepository.save(account);
    }

    public void revertTransaction(@NonNull Account account, @NonNull Transaction transaction) {
        if (!accountRepository.existsById(account.getId())) {
            throw new IllegalArgumentException("Account does not exist: " + account.getId());
        }

        if (transaction.getAccount() == null || !transaction.getAccount().getId().equals(account.getId())) {
            throw new IllegalArgumentException("Transaction account does not match the provided account.");
        }

        switch (transaction.getType()) {
            case INCOME -> {
                BigDecimal newBalance = account.getBalance().subtract(transaction.getAmount());
                if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Insufficient funds for this transaction.");
                }
                account.setBalance(newBalance);
            }
            case EXPENSE -> {
                BigDecimal newBalance = account.getBalance().add(transaction.getAmount());
                account.setBalance(newBalance);
            }
        }
        accountRepository.save(account);
    }

    @Transactional
    public void convertBalance(BalanceConversionDTO conversionDTO, Principal principal) {
        User user = userService.findUserByEmail(principal.getName());

        if (conversionDTO.getFromAccountType() == conversionDTO.getToAccountType()) {
            throw new IllegalArgumentException("Os tipos de conta de origem e destino devem ser diferentes.");
        }

        Account fromAccount = accountRepository
                .findByUserEmailAndType(principal.getName(), conversionDTO.getFromAccountType())
                .orElseGet(() -> {
                    Account newAccount = Account.builder()
                            .type(conversionDTO.getFromAccountType())
                            .balance(BigDecimal.ZERO)
                            .user(user)
                            .build();
                    return accountRepository.save(newAccount);
                });

        Account toAccount = accountRepository
                .findByUserEmailAndType(principal.getName(), conversionDTO.getToAccountType())
                .orElseGet(() -> {
                    Account newAccount = Account.builder()
                            .type(conversionDTO.getToAccountType())
                            .balance(BigDecimal.ZERO)
                            .user(user)
                            .build();
                    return accountRepository.save(newAccount);
                });

        if (fromAccount.getBalance().compareTo(conversionDTO.getAmount()) < 0) {
            throw new IllegalArgumentException("Saldo insuficiente na conta de origem para realizar a conversão.");
        }

        PaymentMethod fromPaymentMethod = conversionDTO.getFromAccountType() == AccountType.CASH ? PaymentMethod.CASH
                : PaymentMethod.PIX;
        PaymentMethod toPaymentMethod = conversionDTO.getToAccountType() == AccountType.CASH ? PaymentMethod.CASH
                : PaymentMethod.PIX;

        String defaultDescription = String.format("Conversão de %s para %s",
                conversionDTO.getFromAccountType() == AccountType.CASH ? "dinheiro" : "conta bancária",
                conversionDTO.getToAccountType() == AccountType.CASH ? "dinheiro" : "conta bancária");

        Transaction fromTransaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.EXPENSE)
                .detail(TransactionDetail.INTER_ACCOUNT_TRANSFER)
                .description(
                        conversionDTO.getDescription() != null ? conversionDTO.getDescription() : defaultDescription)
                .amount(conversionDTO.getAmount())
                .paymentMethod(fromPaymentMethod)
                .reference(conversionDTO.getReference())
                .account(fromAccount)
                .build();

        Transaction toTransaction = Transaction.builder()
                .date(LocalDateTime.now())
                .type(TransactionType.INCOME)
                .detail(TransactionDetail.INTER_ACCOUNT_TRANSFER)
                .description(
                        conversionDTO.getDescription() != null ? conversionDTO.getDescription() : defaultDescription)
                .amount(conversionDTO.getAmount())
                .paymentMethod(toPaymentMethod)
                .reference(conversionDTO.getReference())
                .account(toAccount)
                .build();

        applyTransaction(fromAccount, fromTransaction);
        applyTransaction(toAccount, toTransaction);

        transactionRepository.save(fromTransaction);
        transactionRepository.save(toTransaction);

        cacheInvalidationService.invalidateFinancialCaches();
    }
}
