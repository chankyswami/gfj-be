package com.iavtar.gfj_be.service;

import com.iavtar.gfj_be.entity.AppUser;
import com.iavtar.gfj_be.entity.Client;
import com.iavtar.gfj_be.entity.ClientLedger;
import com.iavtar.gfj_be.entity.Quotation;
import com.iavtar.gfj_be.entity.enums.RoleType;
import com.iavtar.gfj_be.entity.enums.TransactionType;
import com.iavtar.gfj_be.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AnalyticsService {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private ClientLedgerRepository clientLedgerRepository;

    @Autowired
    private MaterialRepository materialRepository;

    public Map<String, Object> getDashboardAnalytics() {
        log.info("Getting dashboard analytics");
        Map<String, Object> analytics = new HashMap<>();
        
        // User counts
        long totalAgents = appUserRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName() == RoleType.AGENT))
                .count();
        long totalClients = clientRepository.count();
        long totalQuotations = quotationRepository.count();
        long totalTransactions = clientLedgerRepository.count();
        
        analytics.put("totalAgents", totalAgents);
        analytics.put("totalClients", totalClients);
        analytics.put("totalQuotations", totalQuotations);
        analytics.put("totalTransactions", totalTransactions);
        
        // Financial summary - Updated to use ClientLedger
        BigDecimal totalRevenue = clientLedgerRepository.findAll().stream()
                .filter(tx -> TransactionType.CREDIT.equals(tx.getTransactionType()))
                .map(ClientLedger::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalQuotationValue = quotationRepository.findAll().stream()
                .map(Quotation::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        analytics.put("totalRevenue", totalRevenue);
        analytics.put("totalQuotationValue", totalQuotationValue);
        
        return analytics;
    }

    public Map<String, Object> getQuotationAnalytics(String period) {
        log.info("Getting quotation analytics for period: {}", period);
        Map<String, Object> analytics = new HashMap<>();
        
        List<Quotation> quotations = quotationRepository.findAll();
        
        // Status distribution
        Map<String, Long> statusDistribution = quotations.stream()
                .collect(Collectors.groupingBy(
                        q -> q.getQuotationStatus() != null ? q.getQuotationStatus() : "UNKNOWN",
                        Collectors.counting()
                ));
        
        // Price analytics
        DoubleSummaryStatistics priceStats = quotations.stream()
                .map(Quotation::getPrice)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .summaryStatistics();
        
        // Monthly trend (last 12 months)
        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);
        Map<String, Long> monthlyTrend = quotations.stream()
                .filter(q -> q.getCreatedAt() != null && q.getCreatedAt().isAfter(twelveMonthsAgo))
                .collect(Collectors.groupingBy(
                        q -> q.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()
                ));
        
        analytics.put("statusDistribution", statusDistribution);
        analytics.put("priceStatistics", Map.of(
                "average", priceStats.getAverage(),
                "min", priceStats.getMin(),
                "max", priceStats.getMax(),
                "count", priceStats.getCount()
        ));
        analytics.put("monthlyTrend", monthlyTrend);
        
        return analytics;
    }

    public Map<String, Object> getFinancialAnalytics(String period) {
        log.info("Getting financial analytics for period: {}", period);
        Map<String, Object> analytics = new HashMap<>();
        
        List<ClientLedger> transactions = clientLedgerRepository.findAll();
        
        // Payment method distribution - Note: ClientLedger doesn't have paymentMethod, so we'll skip this
        // Map<String, Long> paymentMethodDistribution = transactions.stream()
        //         .collect(Collectors.groupingBy(
        //                 tx -> tx.getPaymentMethod() != null ? tx.getPaymentMethod() : "UNKNOWN",
        //                 Collectors.counting()
        //         ));
        
        // Transaction type distribution - Updated to use enum
        Map<String, Long> transactionTypeDistribution = transactions.stream()
                .collect(Collectors.groupingBy(
                        tx -> tx.getTransactionType() != null ? tx.getTransactionType().name() : "UNKNOWN",
                        Collectors.counting()
                ));
        
        // Payment status distribution - Note: ClientLedger doesn't have paymentStatus, so we'll skip this
        // Map<String, Long> paymentStatusDistribution = transactions.stream()
        //         .collect(Collectors.groupingBy(
        //                 tx -> tx.getPaymentStatus() != null ? tx.getPaymentStatus() : "UNKNOWN",
        //                 Collectors.counting()
        //         ));
        
        // Revenue by month - Updated to use ClientLedger and LocalDateTime
        LocalDateTime twelveMonthsAgo = LocalDateTime.now().minusMonths(12);
        Map<String, BigDecimal> monthlyRevenue = transactions.stream()
                .filter(tx -> tx.getCreatedAt() != null && tx.getCreatedAt().isAfter(twelveMonthsAgo))
                .filter(tx -> TransactionType.CREDIT.equals(tx.getTransactionType()))
                .collect(Collectors.groupingBy(
                        tx -> tx.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.reducing(BigDecimal.ZERO, ClientLedger::getAmount, BigDecimal::add)
                ));
        
        // analytics.put("paymentMethodDistribution", paymentMethodDistribution);
        analytics.put("transactionTypeDistribution", transactionTypeDistribution);
        // analytics.put("paymentStatusDistribution", paymentStatusDistribution);
        analytics.put("monthlyRevenue", monthlyRevenue);
        
        return analytics;
    }

    public Map<String, Object> getClientAnalytics() {
        log.info("Getting client analytics");
        Map<String, Object> analytics = new HashMap<>();
        
        List<Client> clients = clientRepository.findAll();
        
        // Geographic distribution
        Map<String, Long> stateDistribution = clients.stream()
                .filter(c -> c.getState() != null)
                .collect(Collectors.groupingBy(
                        Client::getState,
                        Collectors.counting()
                ));
        
        Map<String, Long> cityDistribution = clients.stream()
                .filter(c -> c.getCity() != null)
                .collect(Collectors.groupingBy(
                        Client::getCity,
                        Collectors.counting()
                ));
        
        // Client value analysis (based on quotations)
        Map<Long, BigDecimal> clientQuotationValues = quotationRepository.findAll().stream()
                .filter(q -> q.getClientId() != null && q.getPrice() != null)
                .collect(Collectors.groupingBy(
                        Quotation::getClientId,
                        Collectors.reducing(BigDecimal.ZERO, Quotation::getPrice, BigDecimal::add)
                ));
        
        // Top clients by quotation value
        List<Map<String, Object>> topClients = clientQuotationValues.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    Client client = clientRepository.findById(entry.getKey()).orElse(null);
                    Map<String, Object> clientInfo = new HashMap<>();
                    clientInfo.put("clientId", entry.getKey());
                    clientInfo.put("clientName", client != null ? client.getClientName() : "Unknown");
                    clientInfo.put("totalQuotationValue", entry.getValue());
                    return clientInfo;
                })
                .collect(Collectors.toList());
        
        analytics.put("stateDistribution", stateDistribution);
        analytics.put("cityDistribution", cityDistribution);
        analytics.put("topClients", topClients);
        analytics.put("totalClients", clients.size());
        
        return analytics;
    }

    public Map<String, Object> getAgentAnalytics() {
        log.info("Getting agent analytics");
        Map<String, Object> analytics = new HashMap<>();
        
        List<AppUser> agents = appUserRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName() == RoleType.AGENT))
                .collect(Collectors.toList());
        
        // Agent performance (based on quotations)
        Map<Long, Long> agentQuotationCounts = quotationRepository.findAll().stream()
                .filter(q -> q.getAgentId() != null)
                .collect(Collectors.groupingBy(
                        Quotation::getAgentId,
                        Collectors.counting()
                ));
        
        Map<Long, BigDecimal> agentQuotationValues = quotationRepository.findAll().stream()
                .filter(q -> q.getAgentId() != null && q.getPrice() != null)
                .collect(Collectors.groupingBy(
                        Quotation::getAgentId,
                        Collectors.reducing(BigDecimal.ZERO, Quotation::getPrice, BigDecimal::add)
                ));
        
        // Top performing agents
        List<Map<String, Object>> topAgents = agentQuotationValues.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    AppUser agent = appUserRepository.findById(entry.getKey()).orElse(null);
                    Map<String, Object> agentInfo = new HashMap<>();
                    agentInfo.put("agentId", entry.getKey());
                    agentInfo.put("agentName", agent != null ? 
                            agent.getFirstName() + " " + agent.getLastName() : "Unknown");
                    agentInfo.put("totalQuotationValue", entry.getValue());
                    agentInfo.put("quotationCount", agentQuotationCounts.getOrDefault(entry.getKey(), 0L));
                    return agentInfo;
                })
                .collect(Collectors.toList());
        
        analytics.put("totalAgents", agents.size());
        analytics.put("topAgents", topAgents);
        analytics.put("agentPerformance", Map.of(
                "quotationCounts", agentQuotationCounts,
                "quotationValues", agentQuotationValues
        ));
        
        return analytics;
    }

    public Map<String, Object> getBusinessTrends(int months) {
        log.info("Getting business trends for last {} months", months);
        Map<String, Object> trends = new HashMap<>();
        
        LocalDateTime startDate = LocalDateTime.now().minusMonths(months);
        
        // Quotation trends
        Map<String, Long> quotationTrends = quotationRepository.findAll().stream()
                .filter(q -> q.getCreatedAt() != null && q.getCreatedAt().isAfter(startDate))
                .collect(Collectors.groupingBy(
                        q -> q.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()
                ));
        
        // Revenue trends - Updated to use ClientLedger and LocalDateTime
        Map<String, BigDecimal> revenueTrends = clientLedgerRepository.findAll().stream()
                .filter(tx -> tx.getCreatedAt() != null && tx.getCreatedAt().isAfter(startDate))
                .filter(tx -> TransactionType.CREDIT.equals(tx.getTransactionType()))
                .collect(Collectors.groupingBy(
                        tx -> tx.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.reducing(BigDecimal.ZERO, ClientLedger::getAmount, BigDecimal::add)
                ));
        
        // Client acquisition trends
        Map<String, Long> clientAcquisitionTrends = clientRepository.findAll().stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(startDate))
                .collect(Collectors.groupingBy(
                        c -> c.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                        Collectors.counting()
                ));
        
        trends.put("quotationTrends", quotationTrends);
        trends.put("revenueTrends", revenueTrends);
        trends.put("clientAcquisitionTrends", clientAcquisitionTrends);
        
        return trends;
    }

    public Map<String, Object> getPerformanceSummary() {
        log.info("Getting performance summary");
        Map<String, Object> summary = new HashMap<>();
        
        // Calculate key metrics
        long totalQuotations = quotationRepository.count();
        long totalClients = clientRepository.count();
        long totalAgents = appUserRepository.findAll().stream()
                .filter(user -> user.getRoles().stream()
                        .anyMatch(role -> role.getName() == RoleType.AGENT))
                .count();
        
        // Updated to use ClientLedger and TransactionType enum
        BigDecimal totalRevenue = clientLedgerRepository.findAll().stream()
                .filter(tx -> TransactionType.CREDIT.equals(tx.getTransactionType()))
                .map(ClientLedger::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalQuotationValue = quotationRepository.findAll().stream()
                .map(Quotation::getPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Calculate averages
        double avgQuotationsPerClient = totalClients > 0 ? (double) totalQuotations / totalClients : 0;
        double avgQuotationsPerAgent = totalAgents > 0 ? (double) totalQuotations / totalAgents : 0;
        double avgRevenuePerClient = totalClients > 0 ? totalRevenue.doubleValue() / totalClients : 0;
        
        summary.put("totalQuotations", totalQuotations);
        summary.put("totalClients", totalClients);
        summary.put("totalAgents", totalAgents);
        summary.put("totalRevenue", totalRevenue);
        summary.put("totalQuotationValue", totalQuotationValue);
        summary.put("avgQuotationsPerClient", avgQuotationsPerClient);
        summary.put("avgQuotationsPerAgent", avgQuotationsPerAgent);
        summary.put("avgRevenuePerClient", avgRevenuePerClient);
        
        return summary;
    }
} 