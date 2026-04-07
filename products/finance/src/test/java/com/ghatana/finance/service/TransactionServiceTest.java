/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.service;

import com.ghatana.finance.ai.FinanceFraudDetectionKernelAgent;
import com.ghatana.finance.ai.FinanceAIModule;
import com.ghatana.finance.ai.FraudDetectionResult;
import com.ghatana.finance.ai.ModelApprovalRecord;
import com.ghatana.finance.ai.ModelApprovalRepository;
import com.ghatana.finance.ai.ModelRecord;
import com.ghatana.finance.ai.ModelRepository;
import com.ghatana.kernel.ai.*;
import io.activej.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for TransactionService with AI orchestration
 */
public class TransactionServiceTest {

    private TransactionService transactionService;
    private AgentOrchestrator orchestrator;
    private FinanceFraudDetectionKernelAgent fraudDetectionAgent;

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

        ModelRepository modelRepository = injector.getInstance(ModelRepository.class);
        ModelRecord model = new ModelRecord();
        model.setModelId("fraud-detection-v2");
        model.setMetadata(Map.of("endpoint", "http://127.0.0.1:1/unreachable"));
        modelRepository.save(model);

        // Create fraud detection agent with injector-managed inference wiring
        fraudDetectionAgent = injector.getInstance(FinanceFraudDetectionKernelAgent.class);
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

    @Test
    public void testProcessTransaction_DuplicateTransactionId_ShouldReuseOriginalResult() {
        CountingOrchestrator countingOrchestrator = new CountingOrchestrator();
        RecordingAutonomyManager recordingAutonomyManager = new RecordingAutonomyManager();
        countingOrchestrator.registerAgent(new StubFraudAgent());

        TransactionService idempotentService = new TransactionService(countingOrchestrator, recordingAutonomyManager);
        Transaction transaction = createTransaction("txn-duplicate", 750.0, "USD", "NEW_YORK");

        TransactionResult firstResult = idempotentService.processTransaction(transaction);
        TransactionResult duplicateResult = idempotentService.processTransaction(transaction);

        assertSame(firstResult, duplicateResult);
        assertEquals(1, countingOrchestrator.executions.get());
        assertEquals(1, recordingAutonomyManager.recordedDecisions.size());
    }

    @Test
    public void testProcessTransaction_MissingTransactionId_ShouldFailFast() {
        Transaction transaction = createTransaction(null, 750.0, "USD", "NEW_YORK");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> transactionService.processTransaction(transaction)
        );

        assertEquals("transaction id must not be blank", exception.getMessage());
    }

    private Transaction createTransaction(String id, double amount, String currency, String location) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setTenantId("tenant-1");
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setLocation(location);
        transaction.setMerchantCategory("BTC".equals(currency) ? "CRYPTO_EXCHANGE" : "RETAIL");
        transaction.setCounterpartyCountry("UNKNOWN".equals(location) ? "RU" : location);
        transaction.setPaymentMethod("BTC".equals(currency) ? "WIRE_TRANSFER" : "CARD");
        transaction.setVelocity(amount >= 100000.0 ? 15.0 : amount >= 15000.0 ? 4.0 : 1.0);
        transaction.setTimestamp(Instant.parse("2026-04-06T12:00:00Z"));
        transaction.setStatus("PENDING");
        return transaction;
    }

    private static final class CountingOrchestrator implements AgentOrchestrator {
        private final java.util.Map<String, KernelAgent> agents = new java.util.HashMap<>();
        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public AgentResponse executeAgent(KernelAgent agent, AgentRequest request) {
            executions.incrementAndGet();
            return agent.execute(request);
        }

        @Override
        public void registerAgent(KernelAgent agent) {
            agents.put(agent.getAgentId(), agent);
        }

        @Override
        public void unregisterAgent(String agentId) {
            agents.remove(agentId);
        }

        @Override
        public List<KernelAgent> getAvailableAgents() {
            return List.copyOf(agents.values());
        }

        @Override
        public WorkflowResult executeAgentWorkflow(List<KernelAgent> agents, AgentRequest request) {
            throw new UnsupportedOperationException("Not needed for test");
        }

        @Override
        public KernelAgent getAgent(String agentId) {
            return agents.get(agentId);
        }

        @Override
        public io.activej.promise.Promise<AgentResponse> executeAgentAsync(KernelAgent agent, AgentRequest request) {
            return io.activej.promise.Promise.of(executeAgent(agent, request));
        }
    }

    private static final class RecordingAutonomyManager implements AutonomyManager {
        private final List<AutonomousDecision> recordedDecisions = new ArrayList<>();

        @Override
        public void configureAutonomyLevel(String agentId, AutonomyLevel level) {
        }

        @Override
        public boolean requiresHumanReview(AgentOrchestrator.AgentRequest request, AgentOrchestrator.KernelAgent agent) {
            return false;
        }

        @Override
        public void recordAutonomousDecision(AutonomousDecision decision) {
            recordedDecisions.add(decision);
        }

        @Override
        public List<AutonomousDecision> getAutonomousDecisions(String agentId, TimeWindow window) {
            return List.copyOf(recordedDecisions);
        }

        @Override
        public AutonomyLevel getAutonomyLevel(String agentId) {
            return AutonomyLevel.HIGH;
        }

        @Override
        public void approveDecision(String decisionId, String approver) {
        }

        @Override
        public void rejectDecision(String decisionId, String rejector, String reason) {
        }
    }

    private static final class StubFraudAgent implements AgentOrchestrator.KernelAgent {
        @Override
        public String getAgentId() {
            return "finance.fraud-detection";
        }

        @Override
        public String getName() {
            return "stub-fraud-agent";
        }

        @Override
        public String getDescription() {
            return "stub-fraud-agent";
        }

        @Override
        public AgentOrchestrator.AgentResponse execute(AgentOrchestrator.AgentRequest request) {
            FraudDetectionResult fraudResult = FraudDetectionResult.clean("txn-duplicate", "acct-1");
            return AgentOrchestrator.AgentResponse.builder()
                .requestId(request.getRequestId())
                .success(true)
                .result(fraudResult)
                .metadata(Map.of())
                .build();
        }

        @Override
        public AgentOrchestrator.AgentCapabilities getCapabilities() {
            return new AgentOrchestrator.AgentCapabilities() {
                @Override
                public List<String> getSupportedOperations() {
                    return List.of("detect_fraud");
                }

                @Override
                public Map<String, Object> getMetadata() {
                    return Map.of();
                }

                @Override
                public boolean supportsOperation(String operation) {
                    return "detect_fraud".equals(operation);
                }
            };
        }
    }
}
