package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.platform.workflow.WorkflowContext;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WorkflowContextAdapter} copy and builder behavior.
 *
 * @doc.type class
 * @doc.purpose Verify workflow context adapter utilities behave predictably
 * @doc.layer test
 * @doc.pattern Unit Test
 */
@DisplayName("WorkflowContextAdapter Tests [GH-90000]")
class WorkflowContextAdapterTest {

  @Test
  @DisplayName("builder should populate workflow identifiers and variables [GH-90000]")
  void builderShouldPopulateIdentifiersAndVariables() { // GH-90000
    WorkflowContext context =
        WorkflowContextAdapter.builder() // GH-90000
            .workflowId("wf-123 [GH-90000]")
            .tenantId("tenant-abc [GH-90000]")
            .put("phase", "architecture") // GH-90000
            .putAll(Map.of("attempt", 2, "approved", true)) // GH-90000
            .build(); // GH-90000

    WorkflowContextAdapter adapter = WorkflowContextAdapter.wrap(context); // GH-90000

    assertThat(context.getWorkflowId()).isEqualTo("wf-123 [GH-90000]");
    assertThat(context.getTenantId()).isEqualTo("tenant-abc [GH-90000]");
    assertThat(adapter.get("phase [GH-90000]")).isEqualTo("architecture [GH-90000]");
    assertThat(adapter.containsKey("attempt [GH-90000]")).isTrue();
    assertThat(adapter.asMap()).containsEntry("approved", true); // GH-90000
  }

  @Test
  @DisplayName("copy should duplicate variables without sharing the backing map [GH-90000]")
  void copyShouldDuplicateVariablesWithoutSharingBackingMap() { // GH-90000
    WorkflowContext original =
        WorkflowContextAdapter.builder() // GH-90000
            .workflowId("wf-456 [GH-90000]")
            .tenantId("tenant-def [GH-90000]")
            .put("status", "pending") // GH-90000
            .build(); // GH-90000
    WorkflowContextAdapter adapter = WorkflowContextAdapter.wrap(original); // GH-90000

    WorkflowContext copied = adapter.copy(); // GH-90000
    copied.setVariable("status", "completed"); // GH-90000

    assertThat(original.getVariable("status [GH-90000]")).isEqualTo("pending [GH-90000]");
    assertThat(copied.getVariable("status [GH-90000]")).isEqualTo("completed [GH-90000]");
    assertThat(copied.getWorkflowId()).isEqualTo(original.getWorkflowId()); // GH-90000
    assertThat(copied.getTenantId()).isEqualTo(original.getTenantId()); // GH-90000
  }

  @Test
  @DisplayName("builder should reject missing workflow id [GH-90000]")
  void builderShouldRejectMissingWorkflowId() { // GH-90000
    assertThatThrownBy( // GH-90000
            () -> WorkflowContextAdapter.builder().tenantId("tenant-only [GH-90000]").build())
        .isInstanceOf(NullPointerException.class) // GH-90000
        .hasMessageContaining("workflowId must be set [GH-90000]");
  }
}
