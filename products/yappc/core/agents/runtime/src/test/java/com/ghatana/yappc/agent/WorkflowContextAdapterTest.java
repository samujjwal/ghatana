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
@DisplayName("WorkflowContextAdapter Tests")
class WorkflowContextAdapterTest {

  @Test
  @DisplayName("builder should populate workflow identifiers and variables")
  void builderShouldPopulateIdentifiersAndVariables() { // GH-90000
    WorkflowContext context =
        WorkflowContextAdapter.builder() // GH-90000
            .workflowId("wf-123")
            .tenantId("tenant-abc")
            .put("phase", "architecture") // GH-90000
            .putAll(Map.of("attempt", 2, "approved", true)) // GH-90000
            .build(); // GH-90000

    WorkflowContextAdapter adapter = WorkflowContextAdapter.wrap(context); // GH-90000

    assertThat(context.getWorkflowId()).isEqualTo("wf-123");
    assertThat(context.getTenantId()).isEqualTo("tenant-abc");
    assertThat(adapter.get("phase")).isEqualTo("architecture");
    assertThat(adapter.containsKey("attempt")).isTrue();
    assertThat(adapter.asMap()).containsEntry("approved", true); // GH-90000
  }

  @Test
  @DisplayName("copy should duplicate variables without sharing the backing map")
  void copyShouldDuplicateVariablesWithoutSharingBackingMap() { // GH-90000
    WorkflowContext original =
        WorkflowContextAdapter.builder() // GH-90000
            .workflowId("wf-456")
            .tenantId("tenant-def")
            .put("status", "pending") // GH-90000
            .build(); // GH-90000
    WorkflowContextAdapter adapter = WorkflowContextAdapter.wrap(original); // GH-90000

    WorkflowContext copied = adapter.copy(); // GH-90000
    copied.setVariable("status", "completed"); // GH-90000

    assertThat(original.getVariable("status")).isEqualTo("pending");
    assertThat(copied.getVariable("status")).isEqualTo("completed");
    assertThat(copied.getWorkflowId()).isEqualTo(original.getWorkflowId()); // GH-90000
    assertThat(copied.getTenantId()).isEqualTo(original.getTenantId()); // GH-90000
  }

  @Test
  @DisplayName("builder should reject missing workflow id")
  void builderShouldRejectMissingWorkflowId() { // GH-90000
    assertThatThrownBy( // GH-90000
            () -> WorkflowContextAdapter.builder().tenantId("tenant-only").build())
        .isInstanceOf(NullPointerException.class) // GH-90000
        .hasMessageContaining("workflowId must be set");
  }
}
