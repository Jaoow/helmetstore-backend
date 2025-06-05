package com.jaoow.helmetstore.controller;

import com.jaoow.helmetstore.dto.balance.AccountInfo;
import com.jaoow.helmetstore.dto.balance.TransactionCreateDTO;
import com.jaoow.helmetstore.service.AccountService;
import com.jaoow.helmetstore.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    @GetMapping
    public List<AccountInfo> getAccountInfo(Principal principal) {
        return accountService.getAccountInfo(principal);
    }

    @PostMapping("/transaction")
    public void createTransaction(@Valid @RequestBody TransactionCreateDTO transactionCreateDTO, Principal principal) {
        transactionService.createManualTransaction(transactionCreateDTO, principal);
    }

    @DeleteMapping("/transaction/{transactionId}")
    public void deleteTransaction(@PathVariable Long transactionId, Principal principal) {
        transactionService.deleteTransactionById(transactionId, principal);
    }

    @PutMapping("/transaction/{transactionId}")
    public void updateTransaction(@PathVariable Long transactionId, @RequestBody TransactionCreateDTO transactionCreateDTO, Principal principal) {
        transactionService.updateTransaction(transactionId, transactionCreateDTO, principal);
    }

}
