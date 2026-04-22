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
@DisplayName("DefaultWorkflowContext [GH-90000]")
class DefaultWorkflowContextTest {

    @Nested
    @DisplayName("constructor [GH-90000]")
    class Constructor {

        @Test
        @DisplayName("should set workflowId and tenantId [GH-90000]")
        void shouldSetIds() { // GH-90000
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "tenant-1"); // GH-90000

            assertThat(ctx.getWorkflowId()).isEqualTo("wf-1 [GH-90000]");
            assertThat(ctx.getTenantId()).isEqualTo("tenant-1 [GH-90000]");
        }

        @Test
        @DisplayName("should auto-generate correlationId [GH-90000]")
        void shouldAutoGenerateCorrelationId() { // GH-90000
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "tenant-1"); // GH-90000

            assertThat(ctx.getCorrelationId()).isNotNull().isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should accept explicit correlationId [GH-90000]")
        void shouldAcceptExplicitCorrelationId() { // GH-90000
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "tenant-1", "corr-42"); // GH-90000

            assertThat(ctx.getCorrelationId()).isEqualTo("corr-42 [GH-90000]");
        }

        @Test
        @DisplayName("should reject null workflowId [GH-90000]")
        void shouldRejectNullWorkflowId() { // GH-90000
            assertThatThrownBy(() -> new DefaultWorkflowContext(null, "tenant-1")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should reject null tenantId [GH-90000]")
        void shouldRejectNullTenantId() { // GH-90000
            assertThatThrownBy(() -> new DefaultWorkflowContext("wf-1", null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("variables [GH-90000]")
    class Variables {

        @Test
        @DisplayName("should store and retrieve variables [GH-90000]")
        void shouldStoreAndRetrieve() { // GH-90000
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1"); // GH-90000
            ctx.setVariable("key", "value"); // GH-90000

            assertThat(ctx.getVariable("key [GH-90000]")).isEqualTo("value [GH-90000]");
        }

        @Test
        @DisplayName("should return null for missing variable [GH-90000]")
        void shouldReturnNullForMissing() { // GH-90000
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1"); // GH-90000

            assertThat(ctx.getVariable("nonexistent [GH-90000]")).isNull();
        }

        @Test
        @DisplayName("should remove variable when set to null [GH-90000]")
        void shouldRemoveOnNull() { // GH-90000
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1"); // GH-90000
            ctx.setVariable("key", "value"); // GH-90000
            ctx.setVariable("key", null); // GH-90000

            assertThat(ctx.getVariable("key [GH-90000]")).isNull();
        }

        @Test
        @DisplayName("should return immutable copy of variables map [GH-90000]")
        void shouldReturnImmutableVariables() { // GH-90000
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1"); // GH-90000
            ctx.setVariable("a", 1); // GH-90000

            Map<String, Object> vars = ctx.getVariables(); // GH-90000
            assertThat(vars).containsEntry("a", 1); // GH-90000

            // Modifying original should not affect returned map
            ctx.setVariable("b", 2); // GH-90000
            assertThat(vars).doesNotContainKey("b [GH-90000]");
        }
    }

    @Nested
    @DisplayName("copy [GH-90000]")
    class Copy {

        @Test
        @DisplayName("should create independent copy [GH-90000]")
        void shouldCreateIndependentCopy() { // GH-90000
            DefaultWorkflowContext original = new DefaultWorkflowContext("wf-1", "t-1", "corr-1"); // GH-90000
            original.setVariable("x", 42); // GH-90000
            original.setCurrentStep("step-1 [GH-90000]");
            original.setCategory("cat-A [GH-90000]");

            WorkflowContext copy = original.copy(); // GH-90000

            assertThat(copy.getWorkflowId()).isEqualTo("wf-1 [GH-90000]");
            assertThat(copy.getTenantId()).isEqualTo("t-1 [GH-90000]");
            assertThat(copy.getCorrelationId()).isEqualTo("corr-1 [GH-90000]");
            assertThat(copy.getVariable("x [GH-90000]")).isEqualTo(42);
            assertThat(copy.getCurrentStep()).isEqualTo("step-1 [GH-90000]");
            assertThat(copy.getCategory()).isEqualTo("cat-A [GH-90000]");
        }

        @Test
        @DisplayName("should not share variable state with original [GH-90000]")
        void shouldNotShareState() { // GH-90000
            DefaultWorkflowContext original = new DefaultWorkflowContext("wf-1", "t-1"); // GH-90000
            original.setVariable("key", "original"); // GH-90000

            WorkflowContext copy = original.copy(); // GH-90000
            original.setVariable("key", "changed"); // GH-90000

            assertThat(copy.getVariable("key [GH-90000]")).isEqualTo("original [GH-90000]");
        }
    }

    @Nested
    @DisplayName("step and category [GH-90000]")
    class StepAndCategory {

        @Test
        @DisplayName("should initially have null currentStep [GH-90000]")
        void shouldHaveNullStep() { // GH-90000
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1"); // GH-90000
            assertThat(ctx.getCurrentStep()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should set and get currentStep [GH-90000]")
        void shouldSetStep() { // GH-90000
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1"); // GH-90000
            ctx.setCurrentStep("step-2 [GH-90000]");
            assertThat(ctx.getCurrentStep()).isEqualTo("step-2 [GH-90000]");
        }

        @Test
        @DisplayName("should set and get category [GH-90000]")
        void shouldSetCategory() { // GH-90000
            DefaultWorkflowContext ctx = new DefaultWorkflowContext("wf-1", "t-1"); // GH-90000
            ctx.setCategory("analysis [GH-90000]");
            assertThat(ctx.getCategory()).isEqualTo("analysis [GH-90000]");
        }
    }
}
