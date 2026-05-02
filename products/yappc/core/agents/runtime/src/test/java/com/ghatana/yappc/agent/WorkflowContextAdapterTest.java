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
  void builderShouldPopulateIdentifiersAndVariables() { 
    WorkflowContext context =
        WorkflowContextAdapter.builder() 
            .workflowId("wf-123")
            .tenantId("tenant-abc")
            .put("phase", "architecture") 
            .putAll(Map.of("attempt", 2, "approved", true)) 
            .build(); 

    WorkflowContextAdapter adapter = WorkflowContextAdapter.wrap(context); 

    assertThat(context.getWorkflowId()).isEqualTo("wf-123");
    assertThat(context.getTenantId()).isEqualTo("tenant-abc");
    assertThat(adapter.get("phase")).isEqualTo("architecture");
    assertThat(adapter.containsKey("attempt")).isTrue();
    assertThat(adapter.asMap()).containsEntry("approved", true); 
  }

  @Test
  @DisplayName("copy should duplicate variables without sharing the backing map")
  void copyShouldDuplicateVariablesWithoutSharingBackingMap() { 
    WorkflowContext original =
        WorkflowContextAdapter.builder() 
            .workflowId("wf-456")
            .tenantId("tenant-def")
            .put("status", "pending") 
            .build(); 
    WorkflowContextAdapter adapter = WorkflowContextAdapter.wrap(original); 

    WorkflowContext copied = adapter.copy(); 
    copied.setVariable("status", "completed"); 

    assertThat(original.getVariable("status")).isEqualTo("pending");
    assertThat(copied.getVariable("status")).isEqualTo("completed");
    assertThat(copied.getWorkflowId()).isEqualTo(original.getWorkflowId()); 
    assertThat(copied.getTenantId()).isEqualTo(original.getTenantId()); 
  }

  @Test
  @DisplayName("builder should reject missing workflow id")
  void builderShouldRejectMissingWorkflowId() { 
    assertThatThrownBy( 
            () -> WorkflowContextAdapter.builder().tenantId("tenant-only").build())
        .isInstanceOf(NullPointerException.class) 
        .hasMessageContaining("workflowId must be set");
  }
}
