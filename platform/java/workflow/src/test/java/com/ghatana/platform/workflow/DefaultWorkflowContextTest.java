package com.ghatana.platform.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link DefaultWorkflowContext}.
 *
 * @doc.type class
 * @doc.purpose Workflow context creation, variable management, and copy semantics tests
 * @doc.layer core
 * @doc.pattern Unit Test
 */
@DisplayName("DefaultWorkflowContext")
class DefaultWorkflowContextTest {

    @Nested
    @DisplayName("constructor")
    class Constructor {

        @Test
        @DisplayName("should set workflowId and tenantId")
        void shouldSetIds() {
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "tenant-1");

            assertThat(ctx.getWorkflowId()).isEqualTo("wf-1");
            assertThat(ctx.getTenantId()).isEqualTo("tenant-1");
        }

        @Test
        @DisplayName("should auto-generate correlationId")
        void shouldAutoGenerateCorrelationId() {
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "tenant-1");

            assertThat(ctx.getCorrelationId()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("should accept explicit correlationId")
        void shouldAcceptExplicitCorrelationId() {
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "tenant-1", "corr-42");

            assertThat(ctx.getCorrelationId()).isEqualTo("corr-42");
        }

        @Test
        @DisplayName("should reject null workflowId")
        void shouldRejectNullWorkflowId() {
            assertThatThrownBy(() -> new DefaultWorkflowContext(null, "tenant-1"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null tenantId")
        void shouldRejectNullTenantId() {
            assertThatThrownBy(() -> new DefaultWorkflowContext("wf-1", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("variables")
    class Variables {

        @Test
        @DisplayName("should store and retrieve variables")
        void shouldStoreAndRetrieve() {
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1");
            ctx.setVariable("key", "value");

            assertThat(ctx.getVariable("key")).isEqualTo("value");
        }

        @Test
        @DisplayName("should return null for missing variable")
        void shouldReturnNullForMissing() {
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1");

            assertThat(ctx.getVariable("nonexistent")).isNull();
        }

        @Test
        @DisplayName("should remove variable when set to null")
        void shouldRemoveOnNull() {
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1");
            ctx.setVariable("key", "value");
            ctx.setVariable("key", null);

            assertThat(ctx.getVariable("key")).isNull();
        }

        @Test
        @DisplayName("should return immutable copy of variables map")
        void shouldReturnImmutableVariables() {
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1");
            ctx.setVariable("a", 1);

            Map<String, Object> vars = ctx.getVariables();
            assertThat(vars).containsEntry("a", 1);

            // Modifying original should not affect returned map
            ctx.setVariable("b", 2);
            assertThat(vars).doesNotContainKey("b");
        }
    }

    @Nested
    @DisplayName("copy")
    class Copy {

        @Test
        @DisplayName("should create independent copy")
        void shouldCreateIndependentCopy() {
            DefaultWorkflowContext original = new DefaultWorkflowContext("wf-1", "t-1", "corr-1");
            original.setVariable("x", 42);
            original.setCurrentStep("step-1");
            original.setCategory("cat-A");

            WorkflowContext copy = original.copy();

            assertThat(copy.getWorkflowId()).isEqualTo("wf-1");
            assertThat(copy.getTenantId()).isEqualTo("t-1");
            assertThat(copy.getCorrelationId()).isEqualTo("corr-1");
            assertThat(copy.getVariable("x")).isEqualTo(42);
            assertThat(copy.getCurrentStep()).isEqualTo("step-1");
            assertThat(copy.getCategory()).isEqualTo("cat-A");
        }

        @Test
        @DisplayName("should not share variable state with original")
        void shouldNotShareState() {
            DefaultWorkflowContext original = new DefaultWorkflowContext("wf-1", "t-1");
            original.setVariable("key", "original");

            WorkflowContext copy = original.copy();
            original.setVariable("key", "changed");

            assertThat(copy.getVariable("key")).isEqualTo("original");
        }
    }

    @Nested
    @DisplayName("step and category")
    class StepAndCategory {

        @Test
        @DisplayName("should initially have null currentStep")
        void shouldHaveNullStep() {
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1");
            assertThat(ctx.getCurrentStep()).isNull();
        }

        @Test
        @DisplayName("should set and get currentStep")
        void shouldSetStep() {
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1");
            ctx.setCurrentStep("step-2");
            assertThat(ctx.getCurrentStep()).isEqualTo("step-2");
        }

        @Test
        @DisplayName("should set and get category")
        void shouldSetCategory() {
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1");
            ctx.setCategory("analysis");
            assertThat(ctx.getCategory()).isEqualTo("analysis");
        }
    }
}
