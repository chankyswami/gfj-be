package com.iavtar.gfj_be.model.response;

import com.iavtar.gfj_be.entity.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@Builder
public class ClientLedgerSummaryResponse {
    
    // Client information
    private Long clientId;
    private String clientName;
    private String email;
    private String agentName;
    private String einNumber;
    private String clientSince;
    
    // Transaction summary
    private List<TransactionSummary> transactions;
    
    // Financial summary
    private BigDecimal totalCredit;
    private int totalCreditTransactions;
    private BigDecimal totalDebit;
    private int totalDebitTransactions;
    private int totalTransactions;
    
    // Legacy fields for backward compatibility
    private BigDecimal currentBalance;
    private Long totalElements;
    private int totalPages;
    private int page;
    private int size;
    
    @Data
    @Builder
    public static class TransactionSummary {
        private String transactionId;
        private BigDecimal amount;
        private TransactionType transactionType;
        private String description;
        private String note;
        private Date createdAt;
    }
}
