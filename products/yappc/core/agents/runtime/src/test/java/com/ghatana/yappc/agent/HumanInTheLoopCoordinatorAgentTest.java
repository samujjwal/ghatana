package com.ghatana.yappc.agent;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.HealthStatus;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link HumanInTheLoopCoordinatorAgent} — human-in-the-loop approval coordination.
 *
 * @doc.type class
 * @doc.purpose Unit tests for human approval gate coordination
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("HumanInTheLoopCoordinatorAgent Tests")
class HumanInTheLoopCoordinatorAgentTest extends EventloopTestBase {

    private HumanInTheLoopCoordinatorAgent agent;
    private AgentContext context;
    private ApprovalRequest testRequest;

    @BeforeEach
    void setUp() {
        context = AgentContext.builder()
                .agentId("HumanInTheLoopCoordinatorAgentTest")
                .turnId("turn-001")
                .tenantId("tenant-test")
                .sessionId("session-test")
                .memoryStore(new EventLogMemoryStore())
                .build();

        testRequest = new ApprovalRequest(
                "",
                "tenant-test",
                "project-123",
                "source-agent-1",
                ApprovalRequest.TYPE_PHASE_ADVANCE,
                "phase-1",
                "phase-2",
                "Gate requires manual approval",
                List.of("criterion-1"),
                List.of("artifact-1"),
                null
        );
    }

    @Nested
    @DisplayName("Constructor & Guards")
    class ConstructorAndGuards {

        @Test
        @DisplayName("null gateway → NullPointerException")
        void nullGatewayThrowsNPE() {
            assertThatThrownBy(() -> new HumanInTheLoopCoordinatorAgent(
                    (HumanInTheLoopCoordinatorAgent.ApprovalGateway) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("gateway");
        }

        @Test
        @DisplayName("null pollingConfig → NullPointerException")
        void nullPollingConfigThrowsNPE() {
            HumanInTheLoopCoordinatorAgent.ApprovalGateway gateway = createStubGateway(
                    req -> Promise.of("id"), (tid, rid) -> Promise.of(null));
            assertThatThrownBy(() -> new HumanInTheLoopCoordinatorAgent(gateway, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("pollingConfig");
        }

        @Test
        @DisplayName("valid constructor with custom pollingConfig")
        void constructorWithCustomPollingConfig() {
            HumanInTheLoopCoordinatorAgent.PollingConfig config = 
                    new HumanInTheLoopCoordinatorAgent.PollingConfig(1000L, 10);
            HumanInTheLoopCoordinatorAgent.ApprovalGateway gateway = createStubGateway(
                    req -> Promise.of("id"), (tid, rid) -> Promise.of(null));
            agent = new HumanInTheLoopCoordinatorAgent(gateway, config);
            assertThat(agent).isNotNull();
        }
    }

    @Nested
    @DisplayName("TypedAgent Contract")
    class TypedAgentContract {

        @BeforeEach
        void createAgent() {
            agent = HumanInTheLoopCoordinatorAgent.of(
                    req -> Promise.of("test-id"),
                    (tid, rid) -> Promise.of(null));
        }

        @Test
        @DisplayName("descriptor returns valid AgentDescriptor")
        void descriptorReturnsValidDescriptor() {
            AgentDescriptor desc = agent.descriptor();
            assertThat(desc).isNotNull();
        }

        @Test
        @DisplayName("initialize completes successfully")
        void initializeCompletes() {
            Promise<Void> result = agent.initialize(AgentConfig.builder()
                    .agentId("test")
                    .type(AgentType.DETERMINISTIC)
                    .build());
            Void unused = runPromise(() -> result);
            assertThat(unused).isNull();
        }

        @Test
        @DisplayName("shutdown completes successfully")
        void shutdownCompletes() {
            Promise<Void> result = agent.shutdown();
            Void unused = runPromise(() -> result);
            assertThat(unused).isNull();
        }

        @Test
        @DisplayName("healthCheck returns HEALTHY")
        void healthCheckReturnsHealthy() {
            HealthStatus status = runPromise(() -> agent.healthCheck());
            assertThat(status).isEqualTo(HealthStatus.HEALTHY);
        }
    }

    @Nested
    @DisplayName("process — happy path")
    class ProcessHappyPath {

        @Test
        @DisplayName("approval from first poll returns success with decision")
        void approvalFromFirstPoll() {
            ApprovalDecision approvedDecision = ApprovalDecision.approved(
                    "req-001", "reviewer-1", "Looks good");

            agent = HumanInTheLoopCoordinatorAgent.of(
                    req -> Promise.of("req-001"),
                    (tid, rid) -> Promise.of(approvedDecision));

            AgentResult<ApprovalDecision> result = runPromise(() ->
                    agent.process(context, testRequest));

            assertThat(result.isFailed()).isFalse();
            assertThat(result.getOutput()).isEqualTo(approvedDecision);
            assertThat(result.getOutput().isApproved()).isTrue();
        }

        @Test
        @DisplayName("rejection from first poll returns success")
        void rejectionFromFirstPoll() {
            ApprovalDecision rejectedDecision = ApprovalDecision.rejected(
                    "req-002", "reviewer-2", "Needs more work");

            agent = HumanInTheLoopCoordinatorAgent.of(
                    req -> Promise.of("req-002"),
                    (tid, rid) -> Promise.of(rejectedDecision));

            AgentResult<ApprovalDecision> result = runPromise(() ->
                    agent.process(context, testRequest));

            assertThat(result.isFailed()).isFalse();
            assertThat(result.getOutput().isRejected()).isTrue();
        }

        @Test
        @DisplayName("approval after multiple polls returns success")
        void approvalAfterMultiplePolls() {
            AtomicInteger pollCount = new AtomicInteger(0);
            ApprovalDecision approvedDecision = ApprovalDecision.approved(
                    "req-003", "reviewer-3", "Approved");

            agent = new HumanInTheLoopCoordinatorAgent(
                    createStubGateway(
                            req -> Promise.of("req-003"),
                            (tid, rid) -> {
                                int count = pollCount.getAndIncrement();
                                // Return null for first 2 polls, then decision
                                if (count < 2) return Promise.of(null);
                                return Promise.of(approvedDecision);
                            }),
                    new HumanInTheLoopCoordinatorAgent.PollingConfig(0L, 100));

            AgentResult<ApprovalDecision> result = runPromise(() ->
                    agent.process(context, testRequest));

            assertThat(result.isFailed()).isFalse();
            assertThat(result.getOutput().isApproved()).isTrue();
            assertThat(pollCount.get()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("tenant context flows through to gateway")
        void tenantContextPropagatesToGateway() {
            String[] capturedTenantId = {null};
            ApprovalDecision decision = ApprovalDecision.approved("req-004", "reviewer", "ok");

            agent = HumanInTheLoopCoordinatorAgent.of(
                    req -> Promise.of("req-004"),
                    (tid, rid) -> {
                        capturedTenantId[0] = tid;
                        return Promise.of(decision);
                    });

            AgentResult<ApprovalDecision> result = runPromise(() ->
                    agent.process(context, testRequest));

            assertThat(result.isFailed()).isFalse();
            assertThat(capturedTenantId[0]).isEqualTo("tenant-test");
        }
    }

    @Nested
    @DisplayName("process — timeout & errors")
    class ProcessTimeoutAndErrors {

        @Test
        @DisplayName("polling timeout → failed AgentResult")
        void pollingTimeoutReturnsFailed() {
            agent = new HumanInTheLoopCoordinatorAgent(
                    createStubGateway(
                            req -> Promise.of("req-timeout"),
                            (tid, rid) -> Promise.of(null)),
                    new HumanInTheLoopCoordinatorAgent.PollingConfig(0L, 3));

            AgentResult<ApprovalDecision> result = runPromise(() ->
                    agent.process(context, testRequest));

            assertThat(result.isFailed()).isTrue();
        }

        @Test
        @DisplayName("null context → NullPointerException")
        void nullContextThrowsNPE() {
            agent = HumanInTheLoopCoordinatorAgent.of(
                    req -> Promise.of("id"),
                    (tid, rid) -> Promise.of(null));

            assertThatThrownBy(() ->
                    runPromise(() -> agent.process(null, testRequest)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("context");
        }

        @Test
        @DisplayName("null input → NullPointerException")
        void nullInputThrowsNPE() {
            agent = HumanInTheLoopCoordinatorAgent.of(
                    req -> Promise.of("id"),
                    (tid, rid) -> Promise.of(null));

            assertThatThrownBy(() ->
                    runPromise(() -> agent.process(context, null)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("input");
        }
    }

    @Nested
    @DisplayName("approval type handling")
    class ApprovalTypeHandling {

        @Test
        @DisplayName("PHASE_ADVANCE approval type")
        void phaseAdvanceTypeAllowed() {
            ApprovalRequest phaseAdvanceReq = new ApprovalRequest(
                    "", "tenant-test", "project-x", "agent-1",
                    ApprovalRequest.TYPE_PHASE_ADVANCE,
                    "phase-1", "phase-2", "reason", List.of(), List.of(), null);

            ApprovalDecision decision = ApprovalDecision.approved("req", "reviewer", "ok");

            agent = HumanInTheLoopCoordinatorAgent.of(
                    req -> Promise.of("req"),
                    (tid, rid) -> Promise.of(decision));

            AgentResult<ApprovalDecision> result = runPromise(() ->
                    agent.process(context, phaseAdvanceReq));

            assertThat(result.isFailed()).isFalse();
        }

        @Test
        @DisplayName("DEPLOYMENT approval type")
        void deploymentTypeAllowed() {
            ApprovalRequest deployReq = new ApprovalRequest(
                    "", "tenant-test", "project-y", "agent-2",
                    ApprovalRequest.TYPE_DEPLOYMENT,
                    null, null, "Deploy to prod?", List.of(), List.of(), null);

            ApprovalDecision decision = ApprovalDecision.approved("req2", "reviewer", "deploy!");

            agent = HumanInTheLoopCoordinatorAgent.of(
                    req -> Promise.of("req2"),
                    (tid, rid) -> Promise.of(decision));

            AgentResult<ApprovalDecision> result = runPromise(() ->
                    agent.process(context, deployReq));

            assertThat(result.isFailed()).isFalse();
        }

        @Test
        @DisplayName("RISK_ACCEPTANCE approval type")
        void riskAcceptanceTypeAllowed() {
            ApprovalRequest riskReq = new ApprovalRequest(
                    "", "tenant-test", "project-z", "agent-3",
                    ApprovalRequest.TYPE_RISK_ACCEPTANCE,
                    null, null, "Accept risk?", List.of("high-risk"), List.of(), null);

            ApprovalDecision decision = ApprovalDecision.approved("req3", "reviewer", "risk accepted");

            agent = HumanInTheLoopCoordinatorAgent.of(
                    req -> Promise.of("req3"),
                    (tid, rid) -> Promise.of(decision));

            AgentResult<ApprovalDecision> result = runPromise(() ->
                    agent.process(context, riskReq));

            assertThat(result.isFailed()).isFalse();
        }
    }

    private HumanInTheLoopCoordinatorAgent.ApprovalGateway createStubGateway(
            Function<ApprovalRequest, Promise<String>> submitFn,
            BiFunction<String, String, Promise<ApprovalDecision>> pollFn) {

        return new HumanInTheLoopCoordinatorAgent.ApprovalGateway() {
            @Override
            public @NotNull Promise<String> submit(@NotNull ApprovalRequest request) {
                return submitFn.apply(request);
            }

            @Override
            public @NotNull Promise<ApprovalDecision> poll(@NotNull String tenantId, @NotNull String requestId) {
                return pollFn.apply(tenantId, requestId);
            }
        };
    }
}
