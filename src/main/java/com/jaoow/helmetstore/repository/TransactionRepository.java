package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.balance.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdAndAccountUserEmail(Long id, String accountUserEmail);

    Optional<Transaction> findByReference(String reference);
}