package com.iavtar.gfj_be.service;

import com.iavtar.gfj_be.entity.AppUser;
import com.iavtar.gfj_be.entity.Client;
import com.iavtar.gfj_be.entity.ClientLedger;
import com.iavtar.gfj_be.entity.enums.TransactionType;
import com.iavtar.gfj_be.exception.LedgerException;
import com.iavtar.gfj_be.model.request.CreateClientLedgerRequest;
import com.iavtar.gfj_be.model.response.ClientLedgerResponse;
import com.iavtar.gfj_be.model.response.ClientLedgerSummaryResponse;
import com.iavtar.gfj_be.model.response.ServiceResponse;
import com.iavtar.gfj_be.repository.AppUserRepository;
import com.iavtar.gfj_be.repository.ClientLedgerRepository;
import com.iavtar.gfj_be.repository.ClientRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClientLedgerServiceImpl implements ClientLedgerService {

    @Autowired
    private ClientLedgerRepository clientLedgerRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private LedgerIdGeneratorService idGeneratorService;

    @Override
    public ResponseEntity<?> createTransaction(CreateClientLedgerRequest request) {
        try {
            log.info("Creating client ledger transaction for clientId: {}, amount: {}, type: {}", 
                    request.getClientId(), request.getAmount(), request.getTransactionType());
            
            // Validate client exists
            Client client = clientRepository.findById(request.getClientId())
                    .orElseThrow(() -> new LedgerException("Client not found with ID: " + request.getClientId()));

            // Validate transaction type
            if (request.getTransactionType() == null) {
                throw new LedgerException("Transaction type cannot be null");
            }

            // Validate amount
            if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new LedgerException("Amount must be greater than zero");
            }

            // Generate unique transaction ID
            String transactionId = idGeneratorService.generateId();

            // Create ledger transaction
            ClientLedger ledger = ClientLedger.builder()
                    .clientId(request.getClientId())
                    .transactionId(transactionId)
                    .amount(request.getAmount())
                    .transactionType(request.getTransactionType())
                    .description(request.getDescription())
                    .note(request.getNote())
                    .build();

            ClientLedger savedLedger = clientLedgerRepository.save(ledger);
            log.info("Created ledger transaction: {} for client: {}", transactionId, client.getClientName());

            return new ResponseEntity<>(ServiceResponse.builder()
                    .message("Transaction created successfully")
                    .data(ClientLedgerResponse.from(savedLedger))
                    .build(), HttpStatus.CREATED);

        } catch (LedgerException e) {
            log.error("Error creating transaction: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error creating transaction: {}", e.getMessage());
            throw new LedgerException("Failed to create transaction: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<ClientLedgerSummaryResponse> getClientLedger(Long clientId, int page, int size) {
        try {
            log.info("Fetching client ledger for clientId: {}, page: {}, size: {}", clientId, page, size);
            
            // Validate client exists
            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new LedgerException("Client not found with ID: " + clientId));

            // Log client information for debugging
            log.debug("Client found - ID: {}, Name: {}, Email: {}, CreatedAt: {}, EIN: {}", 
                    client.getId(), 
                    client.getClientName(), 
                    client.getEmail(), 
                    client.getCreatedAt(), 
                    client.getEinNumber());

            // Validate pagination parameters
            if (page < 0) page = 0;
            if (size <= 0 || size > 100) size = 20;

            Pageable pageable = PageRequest.of(page, size);
            Page<ClientLedger> ledgerPage = clientLedgerRepository.findByClientIdOrderByCreatedAtDesc(clientId, pageable);

            // Get client balance and transaction count
            BigDecimal balance = clientLedgerRepository.getClientBalance(clientId, TransactionType.CREDIT);
            Long transactionCount = clientLedgerRepository.getTransactionCount(clientId);

            // Convert to response DTOs
            List<ClientLedgerResponse> transactions = ledgerPage.getContent().stream()
                    .map(ClientLedgerResponse::from)
                    .collect(Collectors.toList());
            
            // Get agent information safely
            String agentName = "Unknown Agent";
            if (client.getAgentId() != null) {
                Optional<AppUser> user = userRepository.findById(client.getAgentId());
                if (user.isPresent()) {
                    AppUser agent = user.get();
                    agentName = (agent.getFirstName() != null ? agent.getFirstName() : "") + 
                               " " + 
                               (agent.getLastName() != null ? agent.getLastName() : "").trim();
                }
            }
            
            // Build response using the correct structure
            ClientLedgerSummaryResponse response = ClientLedgerSummaryResponse.builder()
                    .clientId(clientId)
                    .clientName(client.getClientName() != null ? client.getClientName() : "Unknown Client")
                    .email(client.getEmail() != null ? client.getEmail() : "No Email")
                    .agentName(agentName)
                    .einNumber(client.getEinNumber() != null ? client.getEinNumber() : "No EIN")
                    .clientSince(client.getCreatedAt() != null ? client.getCreatedAt().toString() : "Unknown")
                    .transactions(transactions.stream()
                            .map(tx -> ClientLedgerSummaryResponse.TransactionSummary.builder()
                                    .transactionId(tx.getTransactionId())
                                    .amount(tx.getAmount())
                                    .transactionType(tx.getTransactionType())
                                    .description(tx.getDescription())
                                    .note(tx.getNote())
                                    .createdAt(tx.getCreatedAt() != null ? 
                                            java.sql.Date.valueOf(tx.getCreatedAt().toLocalDate()) : null)
                                    .build())
                            .collect(Collectors.toList()))
                    .totalCredit(BigDecimal.ZERO) // Will be calculated separately
                    .totalCreditTransactions(0) // Will be calculated separately
                    .totalDebit(BigDecimal.ZERO) // Will be calculated separately
                    .totalDebitTransactions(0) // Will be calculated separately
                    .totalTransactions(transactionCount.intValue())
                    .currentBalance(balance)
                    .totalElements(ledgerPage.getTotalElements())
                    .totalPages(ledgerPage.getTotalPages())
                    .page(page)
                    .size(size)
                    .build();

            // Calculate totals
            List<ClientLedger> allTransactions = clientLedgerRepository.findByClientIdOrderByCreatedAtDesc(clientId);
            BigDecimal totalCredit = BigDecimal.ZERO;
            BigDecimal totalDebit = BigDecimal.ZERO;
            int totalCreditTransactions = 0;
            int totalDebitTransactions = 0;

            for (ClientLedger transaction : allTransactions) {
                if (TransactionType.CREDIT.equals(transaction.getTransactionType())) {
                    totalCredit = totalCredit.add(transaction.getAmount());
                    totalCreditTransactions++;
                } else if (TransactionType.DEBIT.equals(transaction.getTransactionType())) {
                    totalDebit = totalDebit.add(transaction.getAmount());
                    totalDebitTransactions++;
                }
            }

            response.setTotalCredit(totalCredit);
            response.setTotalCreditTransactions(totalCreditTransactions);
            response.setTotalDebit(totalDebit);
            response.setTotalDebitTransactions(totalDebitTransactions);

            log.info("Successfully fetched client ledger for clientId: {} with {} transactions", clientId, response.getTransactions().size());
            return new ResponseEntity<>(response, HttpStatus.OK);

        } catch (LedgerException e) {
            log.error("Error fetching client ledger: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching client ledger: {}", e.getMessage());
            throw new LedgerException("Failed to fetch client ledger: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<?> getTransaction(String transactionId) {
        try {
            log.info("Fetching transaction with ID: {}", transactionId);
            
            ClientLedger ledger = clientLedgerRepository.findByTransactionId(transactionId)
                    .orElseThrow(() -> new LedgerException("Transaction not found with ID: " + transactionId));

            return new ResponseEntity<>(ServiceResponse.builder()
                    .message("Transaction retrieved successfully")
                    .data(ClientLedgerResponse.from(ledger))
                    .build(), HttpStatus.OK);

        } catch (LedgerException e) {
            log.error("Error fetching transaction: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching transaction: {}", e.getMessage());
            throw new LedgerException("Failed to fetch transaction: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<?> deleteTransaction(String transactionId) {
        try {
            log.info("Deleting transaction with ID: {}", transactionId);
            
            ClientLedger ledger = clientLedgerRepository.findByTransactionId(transactionId)
                    .orElseThrow(() -> new LedgerException("Transaction not found with ID: " + transactionId));

            clientLedgerRepository.delete(ledger);
            log.info("Deleted ledger transaction: {}", transactionId);

            return new ResponseEntity<>(ServiceResponse.builder()
                    .message("Transaction deleted successfully")
                    .build(), HttpStatus.OK);

        } catch (LedgerException e) {
            log.error("Error deleting transaction: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error deleting transaction: {}", e.getMessage());
            throw new LedgerException("Failed to delete transaction: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<?> getClientBalance(Long clientId) {
        try {
            log.info("Fetching client balance for clientId: {}", clientId);
            
            // Validate client exists
            Client client = clientRepository.findById(clientId)
                    .orElseThrow(() -> new LedgerException("Client not found with ID: " + clientId));

            BigDecimal balance = clientLedgerRepository.getClientBalance(clientId, TransactionType.CREDIT);

            return new ResponseEntity<>(ServiceResponse.builder()
                    .message("Client balance retrieved successfully")
                    .data(balance)
                    .build(), HttpStatus.OK);

        } catch (LedgerException e) {
            log.error("Error fetching client balance: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error fetching client balance: {}", e.getMessage());
            throw new LedgerException("Failed to fetch client balance: " + e.getMessage());
        }
    }
}
