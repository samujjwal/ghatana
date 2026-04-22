package com.ghatana.yappc.agent;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.health.HealthStatus.Status;
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
@DisplayName("HumanInTheLoopCoordinatorAgent Tests [GH-90000]")
class HumanInTheLoopCoordinatorAgentTest extends EventloopTestBase {

    private HumanInTheLoopCoordinatorAgent agent;
    private AgentContext context;
    private ApprovalRequest testRequest;

    @BeforeEach
    void setUp() { // GH-90000
        context = AgentContext.builder() // GH-90000
                .agentId("HumanInTheLoopCoordinatorAgentTest [GH-90000]")
                .turnId("turn-001 [GH-90000]")
                .tenantId("tenant-test [GH-90000]")
                .sessionId("session-test [GH-90000]")
                .memoryStore(new EventLogMemoryStore()) // GH-90000
                .build(); // GH-90000

        testRequest = new ApprovalRequest( // GH-90000
                "",
                "tenant-test",
                "project-123",
                "source-agent-1",
                ApprovalRequest.TYPE_PHASE_ADVANCE,
                "phase-1",
                "phase-2",
                "Gate requires manual approval",
                List.of("criterion-1 [GH-90000]"),
                List.of("artifact-1 [GH-90000]"),
                null
        );
    }

    @Nested
    @DisplayName("Constructor & Guards [GH-90000]")
    class ConstructorAndGuards {

        @Test
        @DisplayName("null gateway → NullPointerException [GH-90000]")
        void nullGatewayThrowsNPE() { // GH-90000
            assertThatThrownBy(() -> new HumanInTheLoopCoordinatorAgent( // GH-90000
                    (HumanInTheLoopCoordinatorAgent.ApprovalGateway) null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("gateway [GH-90000]");
        }

        @Test
        @DisplayName("null pollingConfig → NullPointerException [GH-90000]")
        void nullPollingConfigThrowsNPE() { // GH-90000
            HumanInTheLoopCoordinatorAgent.ApprovalGateway gateway = createStubGateway( // GH-90000
                    req -> Promise.of("id [GH-90000]"), (tid, rid) -> Promise.of(null));
            assertThatThrownBy(() -> new HumanInTheLoopCoordinatorAgent(gateway, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("pollingConfig [GH-90000]");
        }

        @Test
        @DisplayName("valid constructor with custom pollingConfig [GH-90000]")
        void constructorWithCustomPollingConfig() { // GH-90000
            HumanInTheLoopCoordinatorAgent.PollingConfig config =
                    new HumanInTheLoopCoordinatorAgent.PollingConfig(1000L, 10); // GH-90000
            HumanInTheLoopCoordinatorAgent.ApprovalGateway gateway = createStubGateway( // GH-90000
                    req -> Promise.of("id [GH-90000]"), (tid, rid) -> Promise.of(null));
            agent = new HumanInTheLoopCoordinatorAgent(gateway, config); // GH-90000
            assertThat(agent).isNotNull(); // GH-90000
        }
    }

    @Nested
    @DisplayName("TypedAgent Contract [GH-90000]")
    class TypedAgentContract {

        @BeforeEach
        void createAgent() { // GH-90000
            agent = HumanInTheLoopCoordinatorAgent.of( // GH-90000
                    req -> Promise.of("test-id [GH-90000]"),
                    (tid, rid) -> Promise.of(null)); // GH-90000
        }

        @Test
        @DisplayName("descriptor returns valid AgentDescriptor [GH-90000]")
        void descriptorReturnsValidDescriptor() { // GH-90000
            AgentDescriptor desc = agent.descriptor(); // GH-90000
            assertThat(desc).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("initialize completes successfully [GH-90000]")
        void initializeCompletes() { // GH-90000
            Promise<Void> result = agent.initialize(AgentConfig.builder() // GH-90000
                    .agentId("test [GH-90000]")
                    .type(AgentType.DETERMINISTIC) // GH-90000
                    .build()); // GH-90000
            Void unused = runPromise(() -> result); // GH-90000
            assertThat(unused).isNull(); // GH-90000
        }

        @Test
        @DisplayName("shutdown completes successfully [GH-90000]")
        void shutdownCompletes() { // GH-90000
            Promise<Void> result = agent.shutdown(); // GH-90000
            Void unused = runPromise(() -> result); // GH-90000
            assertThat(unused).isNull(); // GH-90000
        }

        @Test
        @DisplayName("healthCheck returns HEALTHY [GH-90000]")
        void healthCheckReturnsHealthy() { // GH-90000
            HealthStatus status = runPromise(() -> agent.healthCheck()); // GH-90000
            assertThat(status.getStatus()).isEqualTo(Status.HEALTHY); // GH-90000
        }
    }

    @Nested
    @DisplayName("process — happy path [GH-90000]")
    class ProcessHappyPath {

        @Test
        @DisplayName("approval from first poll returns success with decision [GH-90000]")
        void approvalFromFirstPoll() { // GH-90000
            ApprovalDecision approvedDecision = ApprovalDecision.approved( // GH-90000
                    "req-001", "reviewer-1", "Looks good");

            agent = HumanInTheLoopCoordinatorAgent.of( // GH-90000
                    req -> Promise.of("req-001 [GH-90000]"),
                    (tid, rid) -> Promise.of(approvedDecision)); // GH-90000

            AgentResult<ApprovalDecision> result = runPromise(() -> // GH-90000
                    agent.process(context, testRequest)); // GH-90000

            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(result.getOutput()).isEqualTo(approvedDecision); // GH-90000
            assertThat(result.getOutput().isApproved()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("rejection from first poll returns success [GH-90000]")
        void rejectionFromFirstPoll() { // GH-90000
            ApprovalDecision rejectedDecision = ApprovalDecision.rejected( // GH-90000
                    "req-002", "reviewer-2", "Needs more work");

            agent = HumanInTheLoopCoordinatorAgent.of( // GH-90000
                    req -> Promise.of("req-002 [GH-90000]"),
                    (tid, rid) -> Promise.of(rejectedDecision)); // GH-90000

            AgentResult<ApprovalDecision> result = runPromise(() -> // GH-90000
                    agent.process(context, testRequest)); // GH-90000

            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(result.getOutput().isRejected()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("approval after multiple polls returns success [GH-90000]")
        void approvalAfterMultiplePolls() { // GH-90000
            AtomicInteger pollCount = new AtomicInteger(0); // GH-90000
            ApprovalDecision approvedDecision = ApprovalDecision.approved( // GH-90000
                    "req-003", "reviewer-3", "Approved");

            agent = new HumanInTheLoopCoordinatorAgent( // GH-90000
                    createStubGateway( // GH-90000
                            req -> Promise.of("req-003 [GH-90000]"),
                            (tid, rid) -> { // GH-90000
                                int count = pollCount.getAndIncrement(); // GH-90000
                                // Return null for first 2 polls, then decision
                                if (count < 2) return Promise.of(null); // GH-90000
                                return Promise.of(approvedDecision); // GH-90000
                            }),
                    new HumanInTheLoopCoordinatorAgent.PollingConfig(0L, 100)); // GH-90000

            AgentResult<ApprovalDecision> result = runPromise(() -> // GH-90000
                    agent.process(context, testRequest)); // GH-90000

            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(result.getOutput().isApproved()).isTrue(); // GH-90000
            assertThat(pollCount.get()).isGreaterThanOrEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("tenant context flows through to gateway [GH-90000]")
        void tenantContextPropagatesToGateway() { // GH-90000
            String[] capturedTenantId = {null};
            ApprovalDecision decision = ApprovalDecision.approved("req-004", "reviewer", "ok"); // GH-90000

            agent = HumanInTheLoopCoordinatorAgent.of( // GH-90000
                    req -> Promise.of("req-004 [GH-90000]"),
                    (tid, rid) -> { // GH-90000
                        capturedTenantId[0] = tid;
                        return Promise.of(decision); // GH-90000
                    });

            AgentResult<ApprovalDecision> result = runPromise(() -> // GH-90000
                    agent.process(context, testRequest)); // GH-90000

            assertThat(result.isFailed()).isFalse(); // GH-90000
            assertThat(capturedTenantId[0]).isEqualTo("tenant-test [GH-90000]");
        }
    }

    @Nested
    @DisplayName("process — timeout & errors [GH-90000]")
    class ProcessTimeoutAndErrors {

        @Test
        @DisplayName("polling timeout → failed AgentResult [GH-90000]")
        void pollingTimeoutReturnsFailed() { // GH-90000
            agent = new HumanInTheLoopCoordinatorAgent( // GH-90000
                    createStubGateway( // GH-90000
                            req -> Promise.of("req-timeout [GH-90000]"),
                            (tid, rid) -> Promise.of(null)), // GH-90000
                    new HumanInTheLoopCoordinatorAgent.PollingConfig(0L, 3)); // GH-90000

            AgentResult<ApprovalDecision> result = runPromise(() -> // GH-90000
                    agent.process(context, testRequest)); // GH-90000

            assertThat(result.isFailed()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("null context → NullPointerException [GH-90000]")
        void nullContextThrowsNPE() { // GH-90000
            agent = HumanInTheLoopCoordinatorAgent.of( // GH-90000
                    req -> Promise.of("id [GH-90000]"),
                    (tid, rid) -> Promise.of(null)); // GH-90000

            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> agent.process(null, testRequest))) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("context [GH-90000]");
        }

        @Test
        @DisplayName("null input → NullPointerException [GH-90000]")
        void nullInputThrowsNPE() { // GH-90000
            agent = HumanInTheLoopCoordinatorAgent.of( // GH-90000
                    req -> Promise.of("id [GH-90000]"),
                    (tid, rid) -> Promise.of(null)); // GH-90000

            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> agent.process(context, null))) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("input [GH-90000]");
        }
    }

    @Nested
    @DisplayName("approval type handling [GH-90000]")
    class ApprovalTypeHandling {

        @Test
        @DisplayName("PHASE_ADVANCE approval type [GH-90000]")
        void phaseAdvanceTypeAllowed() { // GH-90000
            ApprovalRequest phaseAdvanceReq = new ApprovalRequest( // GH-90000
                    "", "tenant-test", "project-x", "agent-1",
                    ApprovalRequest.TYPE_PHASE_ADVANCE,
                    "phase-1", "phase-2", "reason", List.of(), List.of(), null); // GH-90000

            ApprovalDecision decision = ApprovalDecision.approved("req", "reviewer", "ok"); // GH-90000

            agent = HumanInTheLoopCoordinatorAgent.of( // GH-90000
                    req -> Promise.of("req [GH-90000]"),
                    (tid, rid) -> Promise.of(decision)); // GH-90000

            AgentResult<ApprovalDecision> result = runPromise(() -> // GH-90000
                    agent.process(context, phaseAdvanceReq)); // GH-90000

            assertThat(result.isFailed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DEPLOYMENT approval type [GH-90000]")
        void deploymentTypeAllowed() { // GH-90000
            ApprovalRequest deployReq = new ApprovalRequest( // GH-90000
                    "", "tenant-test", "project-y", "agent-2",
                    ApprovalRequest.TYPE_DEPLOYMENT,
                    null, null, "Deploy to prod?", List.of(), List.of(), null); // GH-90000

            ApprovalDecision decision = ApprovalDecision.approved("req2", "reviewer", "deploy!"); // GH-90000

            agent = HumanInTheLoopCoordinatorAgent.of( // GH-90000
                    req -> Promise.of("req2 [GH-90000]"),
                    (tid, rid) -> Promise.of(decision)); // GH-90000

            AgentResult<ApprovalDecision> result = runPromise(() -> // GH-90000
                    agent.process(context, deployReq)); // GH-90000

            assertThat(result.isFailed()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("RISK_ACCEPTANCE approval type [GH-90000]")
        void riskAcceptanceTypeAllowed() { // GH-90000
            ApprovalRequest riskReq = new ApprovalRequest( // GH-90000
                    "", "tenant-test", "project-z", "agent-3",
                    ApprovalRequest.TYPE_RISK_ACCEPTANCE,
                    null, null, "Accept risk?", List.of("high-risk [GH-90000]"), List.of(), null);

            ApprovalDecision decision = ApprovalDecision.approved("req3", "reviewer", "risk accepted"); // GH-90000

            agent = HumanInTheLoopCoordinatorAgent.of( // GH-90000
                    req -> Promise.of("req3 [GH-90000]"),
                    (tid, rid) -> Promise.of(decision)); // GH-90000

            AgentResult<ApprovalDecision> result = runPromise(() -> // GH-90000
                    agent.process(context, riskReq)); // GH-90000

            assertThat(result.isFailed()).isFalse(); // GH-90000
        }
    }

    private HumanInTheLoopCoordinatorAgent.ApprovalGateway createStubGateway( // GH-90000
            Function<ApprovalRequest, Promise<String>> submitFn,
            BiFunction<String, String, Promise<ApprovalDecision>> pollFn) {

        return new HumanInTheLoopCoordinatorAgent.ApprovalGateway() { // GH-90000
            @Override
            public @NotNull Promise<String> submit(@NotNull ApprovalRequest request) { // GH-90000
                return submitFn.apply(request); // GH-90000
            }

            @Override
            public @NotNull Promise<ApprovalDecision> poll(@NotNull String tenantId, @NotNull String requestId) { // GH-90000
                return pollFn.apply(tenantId, requestId); // GH-90000
            }
        };
    }
}
