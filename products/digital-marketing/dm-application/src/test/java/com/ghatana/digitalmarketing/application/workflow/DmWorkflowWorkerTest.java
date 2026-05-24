package com.ghatana.digitalmarketing.application.workflow;

import com.ghatana.digitalmarketing.application.DmosObservability;
import com.ghatana.digitalmarketing.application.command.DmCommandDispatcher;
import com.ghatana.digitalmarketing.application.command.DmDeadLetterQueue;
import com.ghatana.digitalmarketing.application.command.DmCommandHandler;
import com.ghatana.digitalmarketing.application.command.DmCommandRepository;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.command.DmCommand;
import com.ghatana.digitalmarketing.domain.command.DmCommandStatus;
import com.ghatana.digitalmarketing.domain.command.DmCommandType;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowExecution;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStatus;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStep;
import com.ghatana.digitalmarketing.domain.workflow.DmWorkflowStepStatus;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.observability.TracingManager;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Unit tests for {@link DmWorkflowWorker}.
 *
 * @doc.type class
 * @doc.purpose Verifies workflow step execution and lifecycle driven by DmWorkflowWorker (DMOS-P1-007)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmWorkflowWorker")
class DmWorkflowWorkerTest extends EventloopTestBase {

    private EphemeralWorkflowRepository workflowRepo;
    private EphemeralCommandRepository commandRepo;
    private EphemeralDeadLetterQueue deadLetterQueue;
    private DmWorkflowWorker worker;
    private DmosObservability observability;

    @BeforeEach
    void setUp() {
        workflowRepo = new EphemeralWorkflowRepository();
        commandRepo  = new EphemeralCommandRepository();
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        Metrics metrics = new Metrics(meterRegistry);
        TracingManager tracingManager = TracingManager.createNoOp();
        observability = new DmosObservability(metrics, tracingManager);
        deadLetterQueue = new EphemeralDeadLetterQueue();
        DmCommandDispatcher dispatcher = new DmCommandDispatcher(Map.of(
            DmCommandType.CAMPAIGN_CREATE, cmd -> Promise.of(null),
            DmCommandType.BUDGET_ADJUST,   cmd -> Promise.of(null)
        ));
        worker = new DmWorkflowWorker(workflowRepo, commandRepo, dispatcher, deadLetterQueue, observability, Eventloop.create());
    }

    private DmWorkflowExecution pendingWorkflow(String correlationId, List<DmWorkflowStep> steps) {
        DmWorkflowExecution wf = DmWorkflowExecution.builder()
            .id("wf-" + correlationId)
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .name("Test Workflow")
            .correlationId(correlationId)
            .steps(steps)
            .currentStepIndex(0)
            .status(DmWorkflowStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        workflowRepo.save(wf);
        return wf;
    }

    private DmCommand pendingCommand(String correlationId, DmCommandType type) {
        DmCommand cmd = DmCommand.builder()
            .id("cmd-" + correlationId)
            .commandType(type)
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .correlationId(correlationId)
            .issuedBy("system")
            .serializedPayload("{}")
            .status(DmCommandStatus.PENDING)
            .attemptCount(0)
            .createdAt(Instant.now())
            .scheduledAt(Instant.now())
            .build();
        commandRepo.save(cmd);
        return cmd;
    }

    private DmWorkflowStep pendingStep(String name) {
        return DmWorkflowStep.builder()
            .name(name)
            .stepType("ACTION")
            .status(DmWorkflowStepStatus.PENDING)
            .build();
    }

    @Test
    @DisplayName("tick — no-op when no active workflows for tenant")
    void tick_noActiveWorkflows_noOp() {
        runPromise(() -> worker.tick("tenant-empty"));
        assertThat(workflowRepo.store).isEmpty();
    }

    @Test
    @DisplayName("tick — rejects null tenantId")
    void tick_rejectsNullTenantId() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> runPromise(() -> worker.tick(null)));
    }

    @Test
    @DisplayName("tick — rejects blank tenantId")
    void tick_rejectsBlankTenantId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> runPromise(() -> worker.tick("   ")));
    }

    @Test
    @DisplayName("tick — PENDING workflow with matching command → transitions to RUNNING then COMPLETED")
    void tick_pendingWorkflow_transitionsToCompleted() {
        String corr = "corr-campaign";
        DmWorkflowExecution wf = pendingWorkflow(corr, List.of(pendingStep("create-campaign")));
        pendingCommand(corr, DmCommandType.CAMPAIGN_CREATE);

        runPromise(() -> worker.tick("tenant-1"));

        DmWorkflowExecution result = workflowRepo.store.get(wf.getId());
        assertThat(result.getStatus()).isEqualTo(DmWorkflowStatus.COMPLETED);
    }

    @Test
    @DisplayName("tick — failing dispatcher marks workflow FAILED")
    void tick_dispatcherFailure_marksWorkflowFailed() {
        DmCommandDispatcher failingDispatcher = new DmCommandDispatcher(Map.of(
            DmCommandType.CAMPAIGN_CREATE, cmd -> Promise.ofException(new RuntimeException("downstream fail"))
        ));
        DmWorkflowWorker failWorker = new DmWorkflowWorker(
            workflowRepo, commandRepo, failingDispatcher, deadLetterQueue, observability, Eventloop.create());

        String corr = "corr-fail";
        DmWorkflowExecution wf = pendingWorkflow(corr, List.of(pendingStep("create-campaign-fail")));
        pendingCommand(corr, DmCommandType.CAMPAIGN_CREATE);

        assertThatExceptionOfType(RuntimeException.class)
            .isThrownBy(() -> runPromise(() -> failWorker.tick("tenant-1")))
            .withMessageContaining("downstream fail");

        DmWorkflowExecution result = workflowRepo.store.get(wf.getId());
        assertThat(result.getStatus()).isEqualTo(DmWorkflowStatus.FAILED);
        assertThat(result.getFailureReason()).contains("downstream fail");
    }

    @Test
    @DisplayName("tick — RUNNING workflow with no matching command leaves workflow unchanged")
    void tick_runningWorkflow_noMatchingCommand_failsWorkflow() {
        String corr = "corr-no-cmd";
        DmWorkflowExecution wf = DmWorkflowExecution.builder()
            .id("wf-running")
            .tenantId("tenant-1")
            .workspaceId("ws-1")
            .name("Running Workflow")
            .correlationId(corr)
            .steps(List.of(pendingStep("missing-cmd-step")))
            .currentStepIndex(0)
            .status(DmWorkflowStatus.RUNNING)
            .createdAt(Instant.now())
            .build();
        workflowRepo.store.put(wf.getId(), wf);

        assertThatExceptionOfType(IllegalStateException.class)
            .isThrownBy(() -> runPromise(() -> worker.tick("tenant-1")))
            .withMessageContaining("No PENDING command");

        DmWorkflowExecution result = workflowRepo.store.get(wf.getId());
        assertThat(result.getStatus()).isEqualTo(DmWorkflowStatus.FAILED);
    }

    @Test
    @DisplayName("constructor — rejects null workflowRepository")
    void constructor_rejectsNullWorkflowRepo() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new DmWorkflowWorker(null, commandRepo,
                new DmCommandDispatcher(Map.of(DmCommandType.CAMPAIGN_CREATE, c -> Promise.of(null))),
                deadLetterQueue,
                observability,
                Eventloop.create()));
    }

    @Test
    @DisplayName("constructor — rejects batchSize <= 0")
    void constructor_rejectsInvalidBatchSize() {
        DmCommandDispatcher dispatcher = new DmCommandDispatcher(
            Map.of(DmCommandType.CAMPAIGN_CREATE, c -> Promise.of(null)));
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmWorkflowWorker(
                workflowRepo,
                commandRepo,
                dispatcher,
                deadLetterQueue,
                observability,
                Eventloop.create(),
                0));
    }

    // ── Test Doubles ──────────────────────────────────────────────────────────

    private static final class EphemeralWorkflowRepository implements DmWorkflowRepository {
        final ConcurrentHashMap<String, DmWorkflowExecution> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmWorkflowExecution> save(DmWorkflowExecution execution) {
            store.put(execution.getId(), execution);
            return Promise.of(execution);
        }

        @Override
        public Promise<Optional<DmWorkflowExecution>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmWorkflowExecution>> findByStatus(
                String tenantId, DmWorkflowStatus status, int limit) {
            List<DmWorkflowExecution> result = new ArrayList<>();
            for (DmWorkflowExecution e : store.values()) {
                if (e.getTenantId().equals(tenantId) && e.getStatus() == status) {
                    result.add(e);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<List<DmWorkflowExecution>> findActive(String tenantId, int limit) {
            List<DmWorkflowExecution> result = new ArrayList<>();
            for (DmWorkflowExecution e : store.values()) {
                boolean active = e.getStatus() == DmWorkflowStatus.RUNNING
                    || e.getStatus() == DmWorkflowStatus.PAUSED;
                if (e.getTenantId().equals(tenantId) && active) {
                    result.add(e);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<DmWorkflowExecution> update(DmWorkflowExecution execution) {
            store.put(execution.getId(), execution);
            return Promise.of(execution);
        }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmWorkflowStatus status) {
            long count = store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId) && e.getStatus() == status)
                .count();
            return Promise.of(count);
        }
    }

    private static final class EphemeralCommandRepository implements DmCommandRepository {
        final ConcurrentHashMap<String, DmCommand> store = new ConcurrentHashMap<>();

        @Override
        public Promise<DmCommand> save(DmCommand command) {
            store.put(command.getId(), command);
            return Promise.of(command);
        }

        @Override
        public Promise<Optional<DmCommand>> findById(String id) {
            return Promise.of(Optional.ofNullable(store.get(id)));
        }

        @Override
        public Promise<List<DmCommand>> findPending(String tenantId, int limit) {
            List<DmCommand> result = new ArrayList<>();
            for (DmCommand c : store.values()) {
                if (c.getTenantId().equals(tenantId) && c.getStatus() == DmCommandStatus.PENDING) {
                    result.add(c);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<List<DmCommand>> findByTypeAndStatus(
                String tenantId, DmCommandType commandType, DmCommandStatus status, int limit) {
            List<DmCommand> result = new ArrayList<>();
            for (DmCommand c : store.values()) {
                if (c.getTenantId().equals(tenantId)
                        && c.getCommandType() == commandType
                        && c.getStatus() == status) {
                    result.add(c);
                    if (result.size() >= limit) break;
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<DmCommand> update(DmCommand command) {
            store.put(command.getId(), command);
            return Promise.of(command);
        }

        @Override
        public Promise<Long> countByStatus(String tenantId, DmCommandStatus status) {
            long count = store.values().stream()
                .filter(c -> c.getTenantId().equals(tenantId) && c.getStatus() == status)
                .count();
            return Promise.of(count);
        }
    }

    private static final class EphemeralDeadLetterQueue implements DmDeadLetterQueue {
        @Override
        public Promise<Void> moveToDlq(DmOperationContext ctx, DmCommand command, String finalFailureReason) {
            return Promise.complete();
        }

        @Override
        public Promise<Optional<DlqEntry>> findById(DmOperationContext ctx, String commandId) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<DlqEntry>> list(DmOperationContext ctx, int limit) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<String> replay(DmOperationContext ctx, String dlqEntryId, String replayedBy) {
            return Promise.of(dlqEntryId);
        }

        @Override
        public Promise<Void> delete(DmOperationContext ctx, String dlqEntryId) {
            return Promise.complete();
        }
    }
}
