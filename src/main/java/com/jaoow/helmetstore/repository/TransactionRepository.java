package com.jaoow.helmetstore.repository;

import com.jaoow.helmetstore.model.balance.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

       Optional<Transaction> findByIdAndAccountUserEmail(Long id, String accountUserEmail);

       Optional<Transaction> findByReference(String reference);

       List<Transaction> findAllByReference(String reference);

       @Query("SELECT t FROM Transaction t JOIN t.account a WHERE a.user.email = :userEmail ORDER BY t.date DESC")
       List<Transaction> findByAccountUserEmail(@Param("userEmail") String userEmail);

       @Query("SELECT t FROM Transaction t JOIN t.account a WHERE a.user.email = :userEmail " +
                     "AND t.date >= :startDate AND t.date < :endDate ORDER BY t.date DESC")
       List<Transaction> findByAccountUserEmailAndDateRange(
                     @Param("userEmail") String userEmail,
                     @Param("startDate") LocalDateTime startDate,
                     @Param("endDate") LocalDateTime endDate);

       @Query("SELECT DISTINCT YEAR(t.date) as year, MONTH(t.date) as month " +
                     "FROM Transaction t JOIN t.account a WHERE a.user.email = :userEmail " +
                     "ORDER BY year DESC, month DESC")
       List<Object[]> findDistinctMonthsByUserEmail(@Param("userEmail") String userEmail);
}