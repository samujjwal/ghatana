/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.service;

import com.ghatana.kernel.ai.*;
import com.ghatana.finance.ai.agents.FraudDetectionResult;

import java.util.Map;
import java.util.UUID;

/**
 * Business logic service for TransactionService
 *
 * @doc.type class
 * @doc.purpose Business logic service for TransactionService
 * @doc.layer product
 * @doc.pattern Service
 */
public class TransactionService {

    private final AgentOrchestrator orchestrator;
    private final AutonomyManager autonomyManager;

    public TransactionService(AgentOrchestrator orchestrator, AutonomyManager autonomyManager) {
        this.orchestrator = orchestrator;
        this.autonomyManager = autonomyManager;
    }

    public TransactionResult processTransaction(Transaction transaction) {
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
