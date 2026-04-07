/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.service;

import com.ghatana.finance.ai.FinanceAIModule;
import com.ghatana.finance.ai.FinanceFraudDetectionKernelAgent;
import com.ghatana.finance.ai.FraudDetectionResult;
import com.ghatana.finance.ai.ModelApprovalRecord;
import com.ghatana.finance.ai.ModelApprovalRepository;
import com.ghatana.finance.ai.ModelRecord;
import com.ghatana.finance.ai.ModelRepository;
import io.activej.inject.Injector;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ghatana.kernel.ai.AgentOrchestrator;
import com.ghatana.kernel.ai.AutonomyManager;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for TransactionService with AI orchestration
 */
public class TransactionServiceTest {

    private TransactionService transactionService;
    private AgentOrchestrator orchestrator;
    private FinanceFraudDetectionKernelAgent fraudDetectionAgent;

    @BeforeEach
    public void setUp() {
        Injector injector = Injector.of(FinanceAIModule.create());
        orchestrator = injector.getInstance(AgentOrchestrator.class);
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

        fraudDetectionAgent = injector.getInstance(FinanceFraudDetectionKernelAgent.class);
        orchestrator.registerAgent(fraudDetectionAgent);

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

        TransactionService idempotentService = createService(
            countingOrchestrator,
            recordingAutonomyManager,
            Clock.systemUTC(),
            10,
            Duration.ofHours(24)
        );
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

        assertEquals("id is required", exception.getMessage());
    }

    @Test
    public void testProcessTransaction_DuplicateTransactionIdWithDifferentPayload_ShouldFailFast() {
        CountingOrchestrator countingOrchestrator = new CountingOrchestrator();
        RecordingAutonomyManager recordingAutonomyManager = new RecordingAutonomyManager();
        countingOrchestrator.registerAgent(new StubFraudAgent());

        TransactionService idempotentService = createService(
            countingOrchestrator,
            recordingAutonomyManager,
            Clock.systemUTC(),
            10,
            Duration.ofHours(24)
        );
        Transaction first = createTransaction("txn-conflict", 750.0, "USD", "NEW_YORK");
        Transaction conflicting = createTransaction("txn-conflict", 800.0, "USD", "NEW_YORK");

        idempotentService.processTransaction(first);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> idempotentService.processTransaction(conflicting)
        );

        assertEquals("Transaction 'txn-conflict' was already processed with different content", exception.getMessage());
        assertEquals(1, countingOrchestrator.executions.get());
    }

    @Test
    public void testProcessTransaction_ExpiredIdempotencyEntry_ShouldReprocess() {
        CountingOrchestrator countingOrchestrator = new CountingOrchestrator();
        RecordingAutonomyManager recordingAutonomyManager = new RecordingAutonomyManager();
        countingOrchestrator.registerAgent(new StubFraudAgent());
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T00:00:00Z"));

        TransactionService idempotentService = createService(
            countingOrchestrator,
            recordingAutonomyManager,
            clock,
            10,
            Duration.ofMinutes(5)
        );
        Transaction transaction = createTransaction("txn-expiring", 750.0, "USD", "NEW_YORK");

        idempotentService.processTransaction(transaction);
        clock.advance(Duration.ofMinutes(6));
        idempotentService.processTransaction(transaction);

        assertEquals(2, countingOrchestrator.executions.get());
    }

    @Test
    public void testProcessTransaction_RateLimitExceeded_ShouldThrow() {
        CountingOrchestrator countingOrchestrator = new CountingOrchestrator();
        RecordingAutonomyManager recordingAutonomyManager = new RecordingAutonomyManager();
        countingOrchestrator.registerAgent(new StubFraudAgent());

        TransactionService limitedService = createService(
            countingOrchestrator,
            recordingAutonomyManager,
            Clock.systemUTC(),
            1,
            Duration.ofHours(24)
        );

        limitedService.processTransaction(createTransaction("txn-limit-1", 500.0, "USD", "NEW_YORK"));

        TransactionService.TransactionRateLimitExceededException exception = assertThrows(
            TransactionService.TransactionRateLimitExceededException.class,
            () -> limitedService.processTransaction(createTransaction("txn-limit-2", 600.0, "USD", "NEW_YORK"))
        );

        assertTrue(exception.getMessage().contains("tenant-1"));
    }

    @Test
    public void testProcessTransaction_UnsafeCurrency_ShouldFailFast() {
        Transaction transaction = createTransaction("txn-unsafe", 500.0, "<script>", "NEW_YORK");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> transactionService.processTransaction(transaction)
        );

        assertEquals("currency must contain only safe code characters", exception.getMessage());
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

    private static TransactionService createService(
            AgentOrchestrator orchestrator,
            AutonomyManager autonomyManager,
            Clock clock,
            int maxRequestsPerMinute,
            Duration idempotencyTtl) {
        RateLimiter limiter = DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(maxRequestsPerMinute)
                .burstSize(maxRequestsPerMinute)
                .windowDuration(Duration.ofMinutes(1))
                .build()
        );
        return new TransactionService(
            orchestrator,
            autonomyManager,
            clock,
            limiter,
            new TransactionProcessingIdempotencyStore(idempotencyTtl, clock)
        );
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

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }

    private static final class RecordingAutonomyManager implements AutonomyManager {
        private final List<AutonomyManager.AutonomousDecision> recordedDecisions = new ArrayList<>();

        @Override
        public void configureAutonomyLevel(String agentId, AutonomyManager.AutonomyLevel level) {
        }

        @Override
        public boolean requiresHumanReview(AgentOrchestrator.AgentRequest request, AgentOrchestrator.KernelAgent agent) {
            return false;
        }

        @Override
        public void recordAutonomousDecision(AutonomyManager.AutonomousDecision decision) {
            recordedDecisions.add(decision);
        }

        @Override
        public List<AutonomyManager.AutonomousDecision> getAutonomousDecisions(
                String agentId,
                AutonomyManager.TimeWindow window) {
            return List.copyOf(recordedDecisions);
        }

        @Override
        public AutonomyManager.AutonomyLevel getAutonomyLevel(String agentId) {
            return AutonomyManager.AutonomyLevel.HIGH;
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
