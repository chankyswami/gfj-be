package com.iavtar.gfj_be.repository;

import com.iavtar.gfj_be.entity.ClientLedger;
import com.iavtar.gfj_be.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientLedgerRepository extends JpaRepository<ClientLedger, Long> {

    Optional<ClientLedger> findByTransactionId(String transactionId);
    
    Page<ClientLedger> findByClientIdOrderByCreatedAtDesc(Long clientId, Pageable pageable);
    
    List<ClientLedger> findByClientIdOrderByCreatedAtDesc(Long clientId);
    
    @Query("SELECT COALESCE(SUM(CASE WHEN cl.transactionType = :creditType THEN cl.amount ELSE -cl.amount END), 0) " +
           "FROM ClientLedger cl WHERE cl.clientId = :clientId")
    BigDecimal getClientBalance(@Param("clientId") Long clientId, @Param("creditType") TransactionType creditType);
    
    @Query("SELECT COUNT(cl) FROM ClientLedger cl WHERE cl.clientId = :clientId")
    Long getTransactionCount(@Param("clientId") Long clientId);
}
