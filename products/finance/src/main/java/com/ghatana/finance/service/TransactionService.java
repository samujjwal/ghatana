/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.service;

import com.ghatana.finance.ai.FraudDetectionResult;
import com.ghatana.kernel.ai.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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

    private final AgentOrchestrator orchestrator;
    private final AutonomyManager autonomyManager;
    private final Map<String, TransactionResult> processedTransactions = new ConcurrentHashMap<>();

    public TransactionService(AgentOrchestrator orchestrator, AutonomyManager autonomyManager) {
        this.orchestrator = orchestrator;
        this.autonomyManager = autonomyManager;
    }

    public TransactionResult processTransaction(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");

        String transactionId = transaction.getId();
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transaction id must not be blank");
        }

        TransactionResult cachedResult = processedTransactions.get(transactionId);
        if (cachedResult != null) {
            log.debug("Idempotent skip: transaction '{}' already processed with status '{}'",
                transactionId, cachedResult.getStatus());
            return cachedResult;
        }

        TransactionResult result = processNewTransaction(transaction);
        TransactionResult existingResult = processedTransactions.putIfAbsent(transactionId, result);
        return existingResult != null ? existingResult : result;
    }

    private TransactionResult processNewTransaction(Transaction transaction) {
        // Create agent request
        AgentOrchestrator.AgentRequest request = new AgentOrchestrator.AgentRequest(
            UUID.randomUUID().toString(),
            "detect_fraud",
            transaction.toMap(),
            Map.of(
                "tenant_id", transaction.getTenantId(),
                "amount", transaction.getAmount()
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
        // Queue transaction for human review
        return TransactionResult.pendingReview(
            "Transaction queued for manual review",
            Map.of("request_id", request.getRequestId())
        );
    }
}
