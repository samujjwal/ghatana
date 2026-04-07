package com.ghatana.products.finance;

import com.ghatana.finance.service.Transaction;
import com.ghatana.kernel.ai.AgentOrchestrator;
import com.ghatana.kernel.ai.AutonomyManager;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

final class FinanceTransactionRuntimeTestSupport {

    private FinanceTransactionRuntimeTestSupport() {
    }

    static Transaction createTransaction(String transactionId) {
        Transaction transaction = new Transaction();
        transaction.setId(transactionId);
        transaction.setTenantId("tenant-1");
        transaction.setAmount(500.0);
        transaction.setCurrency("USD");
        transaction.setLocation("NEW_YORK");
        transaction.setMerchantCategory("RETAIL");
        transaction.setCounterpartyCountry("US");
        transaction.setPaymentMethod("CARD");
        transaction.setVelocity(1.0);
        transaction.setTimestamp(Instant.parse("2026-04-06T12:00:00Z"));
        transaction.setStatus("PENDING");
        return transaction;
    }

    static final class StubAiRuntime implements AgentOrchestrator, AutonomyManager {
        private final AtomicInteger executions = new AtomicInteger();

        int executions() {
            return executions.get();
        }

        @Override
        public AgentResponse executeAgent(KernelAgent agent, AgentRequest request) {
            executions.incrementAndGet();
            return agent.execute(request);
        }

        @Override
        public void registerAgent(KernelAgent agent) {
        }

        @Override
        public void unregisterAgent(String agentId) {
        }

        @Override
        public java.util.List<KernelAgent> getAvailableAgents() {
            return java.util.List.of(getAgent("finance.fraud-detection"));
        }

        @Override
        public WorkflowResult executeAgentWorkflow(java.util.List<KernelAgent> agents, AgentRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public KernelAgent getAgent(String agentId) {
            return new KernelAgent() {
                @Override
                public String getAgentId() {
                    return "finance.fraud-detection";
                }

                @Override
                public String getName() {
                    return "stub-agent";
                }

                @Override
                public String getDescription() {
                    return "stub-agent";
                }

                @Override
                public AgentResponse execute(AgentRequest request) {
                    return AgentResponse.builder()
                        .requestId(request.getRequestId())
                        .success(true)
                        .result(com.ghatana.finance.ai.FraudDetectionResult.clean("txn", "acct"))
                        .metadata(Map.of())
                        .build();
                }

                @Override
                public AgentCapabilities getCapabilities() {
                    return new AgentCapabilities() {
                        @Override
                        public java.util.List<String> getSupportedOperations() {
                            return java.util.List.of("detect_fraud");
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
            };
        }

        @Override
        public io.activej.promise.Promise<AgentResponse> executeAgentAsync(KernelAgent agent, AgentRequest request) {
            return io.activej.promise.Promise.of(executeAgent(agent, request));
        }

        @Override
        public void configureAutonomyLevel(String agentId, AutonomyLevel level) {
        }

        @Override
        public boolean requiresHumanReview(AgentRequest request, KernelAgent agent) {
            return false;
        }

        @Override
        public void recordAutonomousDecision(AutonomousDecision decision) {
        }

        @Override
        public java.util.List<AutonomousDecision> getAutonomousDecisions(String agentId, TimeWindow window) {
            return java.util.List.of();
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
}