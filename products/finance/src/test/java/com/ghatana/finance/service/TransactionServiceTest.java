/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.service;

import com.ghatana.kernel.ai.*;
import com.ghatana.finance.ai.FinanceAIModule;
import com.ghatana.finance.ai.ModelApprovalRecord;
import com.ghatana.finance.ai.ModelApprovalRepository;
import com.ghatana.finance.ai.agents.FraudDetectionAgent;
import io.activej.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TransactionService with AI orchestration
 */
public class TransactionServiceTest {

    private TransactionService transactionService;
    private AgentOrchestrator orchestrator;
    private FraudDetectionAgent fraudDetectionAgent;

    @BeforeEach
    public void setUp() {
        // Create ActiveJ injector
        Injector injector = Injector.of(FinanceAIModule.create());

        // Get dependencies from injector
        orchestrator = injector.getInstance(AgentOrchestrator.class);

        // Pre-approve the fraud detection model
        ModelApprovalRepository approvalRepository = injector.getInstance(ModelApprovalRepository.class);
        ModelApprovalRecord approval = new ModelApprovalRecord();
        approval.setModelId("fraud-detection-v2");
        approval.setApproved(true);
        approval.setApprover("system");
        approval.setApprovalDate(Instant.now());
        approval.setVersion("2.0");
        approvalRepository.save(approval);

        // Create fraud detection agent
        fraudDetectionAgent = new FraudDetectionAgent(
            injector.getInstance(ModelGovernanceService.class)
        );
        orchestrator.registerAgent(fraudDetectionAgent);

        // Create transaction service
        transactionService = new TransactionService(
            orchestrator,
            injector.getInstance(AutonomyManager.class)
        );
    }

    @Test
    public void testProcessTransaction_LowRisk_ShouldApprove() {
        Transaction transaction = createTransaction("txn-001", 500.0, "USD", "NEW_YORK");
        
        TransactionResult result = transactionService.processTransaction(transaction);
        
        assertNotNull(result);
        assertEquals("APPROVED", result.getStatus());
        assertTrue(result.getMetadata().containsKey("fraud_score"));
    }
    
    @Test
    public void testProcessTransaction_HighRisk_ShouldReject() {
        Transaction transaction = createTransaction("txn-002", 100000.0, "BTC", "UNKNOWN");
        
        TransactionResult result = transactionService.processTransaction(transaction);
        
        assertNotNull(result);
        assertEquals("REJECTED", result.getStatus());
        assertTrue(result.getMessage().contains("Fraud detected"));
    }
    
    @Test
    public void testProcessTransaction_HighValue_ShouldQueueForReview() {
        Transaction transaction = createTransaction("txn-003", 150000.0, "USD", "NEW_YORK");
        
        TransactionResult result = transactionService.processTransaction(transaction);
        
        assertNotNull(result);
        assertEquals("PENDING_REVIEW", result.getStatus());
        assertTrue(result.getMessage().contains("manual review"));
    }
    
    @Test
    public void testProcessTransaction_MediumRisk_ShouldApproveWithWarning() {
        Transaction transaction = createTransaction("txn-004", 15000.0, "EUR", "LONDON");
        
        TransactionResult result = transactionService.processTransaction(transaction);
        
        assertNotNull(result);
        // Should be approved but with metadata showing medium risk
        assertEquals("APPROVED", result.getStatus());
        assertTrue(result.getMetadata().containsKey("risk_level"));
    }

    private Transaction createTransaction(String id, double amount, String currency, String location) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setTenantId("tenant-1");
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setLocation(location);
        transaction.setTimestamp(Instant.now());
        transaction.setStatus("PENDING");
        return transaction;
    }
}
