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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DmWorkflowEngineAdapter} (KERNEL-P1-1).
 */
@DisplayName("DmWorkflowEngineAdapter")
class DmWorkflowEngineAdapterTest extends EventloopTestBase {

    private DmWorkflowEngineAdapter adapter;
    private DmOperationContext ctx;

    @BeforeEach
    void setUp() {
        DurableWorkflowEngine engine = DurableWorkflowEngine.builder()
            .stateStore(new DurableWorkflowEngine.InMemoryWorkflowStateStore())
            .build();
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
    @DisplayName("submit executes workflow steps successfully")
    void submitExecutesWorkflowSuccessfully() {
        WorkflowStep step1 = context -> Promise.of(context);
        WorkflowStep step2 = context -> Promise.of(context);

        Boolean result = runPromise(() -> adapter.submit(ctx, "wf-1", List.of(step1, step2)));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("submit returns true immediately for empty step list without calling engine")
    void submitEmptyStepsReturnsImmediately() {
        Boolean result = runPromise(() -> adapter.submit(ctx, "wf-empty", List.of()));

        assertThat(result).isTrue();
        // Engine should NOT be called for empty step list
    }

    @Test
    @DisplayName("submit propagates step failure as a rejected promise")
    void submitPropagatesStepFailure() {
        WorkflowStep step = context -> Promise.ofException(new RuntimeException("step-failure"));

        assertThatThrownBy(() -> runPromise(() -> adapter.submit(ctx, "wf-fail", List.of(step))))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("adapter can be constructed with a test in-memory state store")
    void adapterCanBeConstructedWithInMemoryStateStoreInTests() {
        DurableWorkflowEngine engine = DurableWorkflowEngine.builder()
            .stateStore(new DurableWorkflowEngine.InMemoryWorkflowStateStore())
            .build();

        DmWorkflowEngineAdapter localAdapter = new DmWorkflowEngineAdapter(engine);

        assertThat(localAdapter).isNotNull();
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
