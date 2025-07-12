package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.balance.Account;
import com.jaoow.helmetstore.model.balance.AccountType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserEmailAndType(String userEmail, AccountType type);

    @Query("SELECT a.balance FROM Account a WHERE a.user.email = :userEmail AND a.type = :accountType")
    Optional<BigDecimal> findBalanceByUserEmailAndType(
            @Param("userEmail") String userEmail,
            @Param("accountType") AccountType accountType
    );

    @Query("SELECT a FROM Account a WHERE a.user.email = :userEmail")
    @EntityGraph(attributePaths = "transactions")
    List<Account> findAllByUserEmail(@Param("userEmail") String userEmail);
}