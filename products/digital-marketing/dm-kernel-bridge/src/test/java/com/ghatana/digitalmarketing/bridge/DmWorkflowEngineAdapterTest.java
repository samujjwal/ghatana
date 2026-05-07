package com.ghatana.digitalmarketing.bridge;

import com.ghatana.digitalmarketing.contracts.ActorRef;
import com.ghatana.digitalmarketing.contracts.DmCorrelationId;
import com.ghatana.digitalmarketing.contracts.DmIdempotencyKey;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.platform.workflow.engine.DurableWorkflowEngine;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DmWorkflowEngineAdapter} (KERNEL-P1-1).
 */
@DisplayName("DmWorkflowEngineAdapter")
@ExtendWith(MockitoExtension.class)
class DmWorkflowEngineAdapterTest extends EventloopTestBase {

    @Mock
    private DurableWorkflowEngine engine;

    private DmWorkflowEngineAdapter adapter;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        adapter = new DmWorkflowEngineAdapter(engine);
        ctx = DmOperationContext.builder()
            .tenantId(DmTenantId.of("tenant-1"))
            .workspaceId(DmWorkspaceId.of("ws-1"))
            .actor(ActorRef.user("user-1"))
            .correlationId(DmCorrelationId.of("corr-1"))
            .idempotencyKey(DmIdempotencyKey.of("idk-1"))
            .build();
    }

    @Test
    @DisplayName("submit delegates to DurableWorkflowEngine with correct workflow ID and step count")
    void submitDelegatesToEngine() {
        WorkflowStep step1 = context -> Promise.of(context);
        WorkflowStep step2 = context -> Promise.of(context);

        DurableWorkflowEngine.WorkflowRun run = new DurableWorkflowEngine.WorkflowRun("wf-1", 2);
        com.ghatana.platform.workflow.DefaultWorkflowContext resultCtx =
            new com.ghatana.platform.workflow.DefaultWorkflowContext("wf-1", "tenant-1", "corr-1");
        DurableWorkflowEngine.WorkflowExecution execution =
            new DurableWorkflowEngine.WorkflowExecution("wf-1", Promise.of(resultCtx), run);

        when(engine.submit(anyString(), any(), any())).thenReturn(execution);

        Boolean result = runPromise(() -> adapter.submit(ctx, "wf-1", List.of(step1, step2)));

        assertThat(result).isTrue();
        verify(engine).submit(anyString(), any(), any());
    }

    @Test
    @DisplayName("submit returns true immediately for empty step list without calling engine")
    void submitEmptyStepsReturnsImmediately() {
        Boolean result = runPromise(() -> adapter.submit(ctx, "wf-empty", List.of()));

        assertThat(result).isTrue();
        // Engine should NOT be called for empty step list
    }

    @Test
    @DisplayName("submit propagates engine failure as a rejected promise")
    void submitPropagatesEngineFailure() {
        WorkflowStep step = context -> Promise.ofException(new RuntimeException("step-failure"));

        DurableWorkflowEngine.WorkflowRun run = new DurableWorkflowEngine.WorkflowRun("wf-fail", 1);
        DurableWorkflowEngine.WorkflowExecution execution =
            new DurableWorkflowEngine.WorkflowExecution("wf-fail",
                Promise.ofException(new RuntimeException("workflow-failed")), run);

        when(engine.submit(anyString(), any(), any())).thenReturn(execution);

        assertThatThrownBy(() -> runPromise(() -> adapter.submit(ctx, "wf-fail", List.of(step))))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("workflow-failed");
    }

    @Test
    @DisplayName("inMemory() factory creates a functional adapter backed by InMemoryWorkflowStateStore")
    void inMemoryFactoryCreatesAdapter() {
        DmWorkflowEngineAdapter inMemoryAdapter = DmWorkflowEngineAdapter.inMemory();
        assertThat(inMemoryAdapter).isNotNull();
    }

    @Test
    @DisplayName("submit requires non-null ctx")
    void submitNullCtxThrows() {
        assertThatThrownBy(() -> adapter.submit(null, "wf-1", List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("submit requires non-null workflowId")
    void submitNullWorkflowIdThrows() {
        assertThatThrownBy(() -> adapter.submit(ctx, null, List.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("submit requires non-null steps")
    void submitNullStepsThrows() {
        assertThatThrownBy(() -> adapter.submit(ctx, "wf-1", null))
            .isInstanceOf(NullPointerException.class);
    }
}
