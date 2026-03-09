package com.ghatana.yappc.sdlc;

import com.ghatana.platform.workflow.WorkflowContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Adapter for WorkflowContext to provide convenience methods used by SDLC agents.
 *
 * @doc.type class
 * @doc.purpose Adapter providing convenience methods for workflow context operations
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class WorkflowContextAdapter {

  private final WorkflowContext delegate;

  private WorkflowContextAdapter(WorkflowContext delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
  }

  /** Wrap a WorkflowContext with adapter methods. */
  public static WorkflowContextAdapter wrap(WorkflowContext context) {
    return new WorkflowContextAdapter(context);
  }

  /** Get a variable (alias for getVariable). */
  public Object get(String key) {
    return delegate.getVariable(key);
  }

  /** Set a variable (alias for setVariable). */
  public void put(String key, Object value) {
    delegate.setVariable(key, value);
  }

  /** Check if a variable exists. */
  public boolean containsKey(String key) {
    return delegate.getVariable(key) != null;
  }

  /** Get all data as a map. */
  public Map<String, Object> getData() {
    return new HashMap<>(delegate.getVariables());
  }

  /** Get all variables as a map (alias for getData). */
  public Map<String, Object> asMap() {
    return getData();
  }

  /**
   * Create a shallow copy of this context. Note: This creates a new context with the same
   * workflow/tenant IDs and copies all variables.
   */
  public WorkflowContext copy() {
    WorkflowContext newContext =
        WorkflowContext.forWorkflow(delegate.getWorkflowId(), delegate.getTenantId());

    // Copy all variables
    delegate.getVariables().forEach(newContext::setVariable);

    return newContext;
  }

  /** Get the underlying WorkflowContext. */
  public WorkflowContext unwrap() {
    return delegate;
  }

  /** Builder for creating WorkflowContext instances. */
  public static class Builder {
    private String workflowId;
    private String tenantId;
    private final Map<String, Object> variables = new HashMap<>();

    public Builder workflowId(String workflowId) {
      this.workflowId = workflowId;
      return this;
    }

    public Builder tenantId(String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder put(String key, Object value) {
      variables.put(key, value);
      return this;
    }

    public Builder putAll(Map<String, Object> data) {
      variables.putAll(data);
      return this;
    }

    public WorkflowContext build() {
      Objects.requireNonNull(workflowId, "workflowId must be set");
      Objects.requireNonNull(tenantId, "tenantId must be set");

      WorkflowContext context = WorkflowContext.forWorkflow(workflowId, tenantId);
      variables.forEach(context::setVariable);
      return context;
    }
  }

  /** Create a new builder. */
  public static Builder builder() {
    return new Builder();
  }
}
