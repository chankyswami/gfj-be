package com.iavtar.gfj_be.model.request;

import com.iavtar.gfj_be.entity.enums.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateClientLedgerRequest {

    @NotNull(message = "Client ID cannot be null")
    private Long clientId;

    @NotNull(message = "Amount cannot be null")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Transaction type cannot be null")
    private TransactionType transactionType;

    @NotNull(message = "Description cannot be null")
    private String description;

    private String reference;

    private String note;
}
