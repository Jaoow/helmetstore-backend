package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.balance.Account;
import com.jaoow.helmetstore.model.balance.AccountType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserEmailAndType(String userEmail, AccountType type);

    @Query("SELECT a FROM Account a WHERE a.user.email = :userEmail")
    @EntityGraph(attributePaths = "transactions")
    List<Account> findAllByUserEmail(@Param("userEmail") String userEmail);

    @Query("SELECT a FROM Account a WHERE a.user.email = :userEmail AND a.type = :type")
    @EntityGraph(attributePaths = "transactions")
    Optional<Account> findByUserEmailAndTypeWithTransactions(@Param("userEmail") String userEmail,
            @Param("type") AccountType type);
}
