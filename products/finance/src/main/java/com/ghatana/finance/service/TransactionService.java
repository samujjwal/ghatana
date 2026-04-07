/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.service;

import com.ghatana.finance.ai.FraudDetectionResult;
import com.ghatana.kernel.ai.*;
import com.ghatana.platform.core.exception.RateLimitExceededException;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.Set;

/**
 * Business logic service for TransactionService
 *
 * @doc.type class
 * @doc.purpose Business logic service for TransactionService
 * @doc.layer product
 * @doc.pattern Service
 */
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int MAX_TRANSACTIONS_PER_TENANT_PER_WINDOW = 120;
    private static final Set<String> ALLOWED_STATUSES = Set.of(
        "PENDING",
        "NEW",
        "APPROVED",
        "REJECTED",
        "SETTLED",
        "CANCELLED"
    );

    private final AgentOrchestrator orchestrator;
    private final AutonomyManager autonomyManager;
    private final Clock clock;
    private final RateLimiter transactionRateLimiter;
    private final TransactionProcessingIdempotencyStore processedTransactions;

    public TransactionService(AgentOrchestrator orchestrator, AutonomyManager autonomyManager) {
        this(
            orchestrator,
            autonomyManager,
            Clock.systemUTC(),
            createDefaultRateLimiter(),
            new TransactionProcessingIdempotencyStore(IDEMPOTENCY_TTL, Clock.systemUTC())
        );
    }

    TransactionService(
            AgentOrchestrator orchestrator,
            AutonomyManager autonomyManager,
            Clock clock,
            RateLimiter transactionRateLimiter,
            TransactionProcessingIdempotencyStore processedTransactions) {
        this.orchestrator = orchestrator;
        this.autonomyManager = autonomyManager;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.transactionRateLimiter = Objects.requireNonNull(
            transactionRateLimiter,
            "transactionRateLimiter must not be null"
        );
        this.processedTransactions = Objects.requireNonNull(
            processedTransactions,
            "processedTransactions must not be null"
        );
    }

    public TransactionResult processTransaction(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");

        Transaction sanitizedTransaction = sanitizeTransaction(transaction);
        enforceRateLimit(sanitizedTransaction.getTenantId());

        String transactionId = sanitizedTransaction.getId();
        String transactionFingerprint = fingerprintFor(sanitizedTransaction);

        TransactionResult cachedResult = processedTransactions.get(transactionId, transactionFingerprint);
        if (cachedResult != null) {
            log.debug("Idempotent skip: transaction '{}' already processed with status '{}'",
                transactionId, cachedResult.getStatus());
            return cachedResult;
        }

        TransactionResult result = processNewTransaction(sanitizedTransaction);
        return processedTransactions.putIfAbsent(transactionId, transactionFingerprint, result);
    }

    private TransactionResult processNewTransaction(Transaction transaction) {
        String correlationId = UUID.randomUUID().toString();
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            correlationId,
            "detect_fraud",
            transaction.toMap(),
            Map.of(
                "tenant_id", transaction.getTenantId(),
                "amount", transaction.getAmount(),
                "transaction_id", transaction.getId(),
                "correlation_id", correlationId,
                "submitted_at", Instant.now(clock).toString()
            )
        );
        
        // Get fraud detection agent
        AgentOrchestrator.KernelAgent agent = orchestrator.getAgent("finance.fraud-detection");
        
        if (agent == null) {
            throw new IllegalStateException("Fraud detection agent not registered");
        }
        
        // Check if human review is required
        boolean requiresReview = autonomyManager.requiresHumanReview(request, agent);
        
        if (requiresReview) {
            return queueForReview(transaction, request);
        }
        
        // Execute agent
        AgentOrchestrator.AgentResponse response = orchestrator.executeAgent(agent, request);
        
        // Record autonomous decision
        AutonomyManager.AutonomousDecision decision = new AutonomyManager.AutonomousDecision(
            agent.getAgentId(),
            request,
            response.getResult(),
            requiresReview
        );
        autonomyManager.recordAutonomousDecision(decision);
        
        // Process based on fraud detection result
        if (response.isSuccess()) {
            FraudDetectionResult fraudResult = (FraudDetectionResult) response.getResult();
            
            if (fraudResult.isFraudulent()) {
                return TransactionResult.rejected("Fraud detected: " + fraudResult.getRiskLevel());
            }
            
            return TransactionResult.approved(Map.of(
                "fraud_score", fraudResult.getFraudScore(),
                "risk_level", fraudResult.getRiskLevel(),
                "confidence", fraudResult.getConfidence()
            ));
        }
        
        return TransactionResult.error("Fraud detection failed");
    }
    
    private TransactionResult queueForReview(Transaction transaction, AgentOrchestrator.AgentRequest request) {
        return TransactionResult.pendingReview(
            "Transaction queued for manual review",
            Map.of(
                "request_id", request.getRequestId(),
                "transaction_id", transaction.getId(),
                "correlation_id", request.getRequestId()
            )
        );
    }

    private void enforceRateLimit(String tenantId) {
        if (!transactionRateLimiter.tryAcquire(tenantId).allowed()) {
            throw new TransactionRateLimitExceededException(
                "Transaction processing rate limit exceeded for tenant: " + tenantId,
                RATE_LIMIT_WINDOW.toMillis()
            );
        }
    }

    private static Transaction sanitizeTransaction(Transaction transaction) {
        String transactionId = TransactionInputSanitizationUtils.requireSafeIdentifier(transaction.getId(), "id");
        String tenantId = TransactionInputSanitizationUtils.requireSafeIdentifier(transaction.getTenantId(), "tenantId");
        double amount = TransactionInputSanitizationUtils.requirePositiveAmount(transaction.getAmount(), "amount");
        String currency = TransactionInputSanitizationUtils.requireSafeCode(transaction.getCurrency(), "currency");
        String location = TransactionInputSanitizationUtils.requireSafeCode(transaction.getLocation(), "location");
        String merchantCategory = TransactionInputSanitizationUtils.requireSafeCode(
            transaction.getMerchantCategory(),
            "merchantCategory"
        );
        String counterpartyCountry = TransactionInputSanitizationUtils.requireSafeCode(
            transaction.getCounterpartyCountry(),
            "counterpartyCountry"
        );
        String paymentMethod = TransactionInputSanitizationUtils.requireSafeCode(
            transaction.getPaymentMethod(),
            "paymentMethod"
        );
        double velocity = TransactionInputSanitizationUtils.requireNonNegativeMetric(
            transaction.getVelocity(),
            "velocity"
        );
        Instant timestamp = Objects.requireNonNull(transaction.getTimestamp(), "timestamp must not be null");
        String status = TransactionInputSanitizationUtils.requireAllowedValue(
            transaction.getStatus(),
            "status",
            ALLOWED_STATUSES
        );

        Transaction sanitized = new Transaction();
        sanitized.setId(transactionId);
        sanitized.setTenantId(tenantId);
        sanitized.setAmount(amount);
        sanitized.setCurrency(currency);
        sanitized.setLocation(location);
        sanitized.setMerchantCategory(merchantCategory);
        sanitized.setCounterpartyCountry(counterpartyCountry);
        sanitized.setPaymentMethod(paymentMethod);
        sanitized.setVelocity(velocity);
        sanitized.setTimestamp(timestamp);
        sanitized.setStatus(status);
        return sanitized;
    }

    private static String fingerprintFor(Transaction transaction) {
        return String.join(
            "|",
            transaction.getId(),
            transaction.getTenantId(),
            Double.toString(transaction.getAmount()),
            transaction.getCurrency(),
            transaction.getLocation(),
            transaction.getMerchantCategory(),
            transaction.getCounterpartyCountry(),
            transaction.getPaymentMethod(),
            Double.toString(transaction.getVelocity()),
            transaction.getTimestamp().toString(),
            transaction.getStatus()
        );
    }

    private static RateLimiter createDefaultRateLimiter() {
        return DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(MAX_TRANSACTIONS_PER_TENANT_PER_WINDOW)
                .burstSize(MAX_TRANSACTIONS_PER_TENANT_PER_WINDOW)
                .windowDuration(RATE_LIMIT_WINDOW)
                .build()
        );
    }

    static final class TransactionRateLimitExceededException extends RateLimitExceededException {
        TransactionRateLimitExceededException(String message, long retryAfterMillis) {
            super(message, retryAfterMillis);
        }
    }
}
