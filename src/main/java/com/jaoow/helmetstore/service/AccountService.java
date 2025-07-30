package com.jaoow.helmetstore.service;

import com.jaoow.helmetstore.dto.balance.AccountInfo;
import com.jaoow.helmetstore.model.balance.Account;
import com.jaoow.helmetstore.model.balance.AccountType;
import com.jaoow.helmetstore.model.balance.PaymentMethod;
import com.jaoow.helmetstore.model.balance.Transaction;
import com.jaoow.helmetstore.model.user.User;
import com.jaoow.helmetstore.repository.AccountRepository;
import com.jaoow.helmetstore.service.user.UserService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserService userService;
    private final ModelMapper modelMapper;

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
}
