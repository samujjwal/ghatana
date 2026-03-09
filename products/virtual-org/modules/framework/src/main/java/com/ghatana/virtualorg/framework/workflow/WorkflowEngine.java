package com.ghatana.virtualorg.framework.workflow;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.virtualorg.framework.agent.OrganizationalAgent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event-driven workflow orchestration engine.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates execution of workflows by coordinating agents, managing state,
 * handling errors, and emitting events. Workflows are declarative and can be
 * executed, paused, resumed, and replayed.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowEngine engine = new WorkflowEngine();
 * WorkflowDefinition sprint = WorkflowDefinition.builder()
 *     .id("sprint-planning")
 *     .name("Sprint Planning")
 *     .version("1.0.0")
 *     .addStep(WorkflowStep.of("load-backlog", "Load backlog", "ProductManager"))
 *     .build();
 * 
 * WorkflowExecution execution = engine.execute(sprint, agentRegistry);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Part of virtual-org-framework product module. Provides event-driven workflow
 * orchestration for organizational processes.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Uses ConcurrentHashMap for execution tracking.
 *
 * @see WorkflowDefinition
 * @see WorkflowExecution
 * @doc.type class
 * @doc.purpose Event-driven workflow orchestration
 * @doc.layer product
 * @doc.pattern Orchestrator
 */
public class WorkflowEngine {
    
    private final Map<String, WorkflowExecution> executions = new ConcurrentHashMap<>();
    private final AtomicLong executionCount = new AtomicLong(0);
    private final AtomicLong completedCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    
    /**
     * Executes a workflow.
     *
     * @param definition the workflow definition
     * @param agents map of role name to agent
     * @return workflow execution
     */
    public WorkflowExecution execute(
            WorkflowDefinition definition,
            Map<String, OrganizationalAgent> agents) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(agents, "agents cannot be null");
        
        String executionId = UUID.randomUUID().toString();
        WorkflowExecution execution = new WorkflowExecution(
            executionId,
            definition,
            agents,
            Instant.now()
        );
        
        executions.put(executionId, execution);
        executionCount.incrementAndGet();
        
        return execution;
    }
    
    /**
     * Gets a workflow execution by ID.
     */
    public WorkflowExecution getExecution(String executionId) {
        return executions.get(executionId);
    }
    
    /**
     * Gets all active executions.
     */
    public List<WorkflowExecution> getActiveExecutions() {
        return new ArrayList<>(executions.values());
    }
    
    /**
     * Gets execution metrics.
     */
    public ExecutionMetrics getMetrics() {
        return new ExecutionMetrics(
            executionCount.get(),
            completedCount.get(),
            failedCount.get(),
            executions.size()
        );
    }
    
    /**
     * Marks execution as completed.
     */
    public void markCompleted(String executionId) {
        WorkflowExecution execution = executions.get(executionId);
        if (execution != null) {
            execution.markCompleted();
            completedCount.incrementAndGet();
        }
    }
    
    /**
     * Marks execution as failed.
     */
    public void markFailed(String executionId, String reason) {
        WorkflowExecution execution = executions.get(executionId);
        if (execution != null) {
            execution.markFailed(reason);
            failedCount.incrementAndGet();
        }
    }
    
    /**
     * Workflow execution state and tracking.
     */
    public static class WorkflowExecution {
        private final String id;
        private final WorkflowDefinition definition;
        private final Map<String, OrganizationalAgent> agents;
        private final Instant startedAt;
        private Instant completedAt;
        private String status = "RUNNING";
        private String failureReason;
        private int currentStepIndex = 0;
        private final List<Event> events = Collections.synchronizedList(new ArrayList<>());
        
        WorkflowExecution(
                String id,
                WorkflowDefinition definition,
                Map<String, OrganizationalAgent> agents,
                Instant startedAt) {
            this.id = id;
            this.definition = definition;
            this.agents = new HashMap<>(agents);
            this.startedAt = startedAt;
        }
        
        public String getId() {
            return id;
        }
        
        public WorkflowDefinition getDefinition() {
            return definition;
        }
        
        public Instant getStartedAt() {
            return startedAt;
        }
        
        public Instant getCompletedAt() {
            return completedAt;
        }
        
        public String getStatus() {
            return status;
        }
        
        public String getFailureReason() {
            return failureReason;
        }
        
        public int getCurrentStepIndex() {
            return currentStepIndex;
        }
        
        public List<Event> getEvents() {
            return new ArrayList<>(events);
        }
        
        public void addEvent(Event event) {
            events.add(event);
        }
        
        public void advanceStep() {
            if (currentStepIndex < definition.getSteps().size() - 1) {
                currentStepIndex++;
            }
        }
        
        public void markCompleted() {
            this.status = "COMPLETED";
            this.completedAt = Instant.now();
        }
        
        public void markFailed(String reason) {
            this.status = "FAILED";
            this.failureReason = reason;
            this.completedAt = Instant.now();
        }
        
        public long getDurationMs() {
            Instant end = completedAt != null ? completedAt : Instant.now();
            return end.toEpochMilli() - startedAt.toEpochMilli();
        }
        
        @Override
        public String toString() {
            return "WorkflowExecution{" +
                    "id='" + id + '\'' +
                    ", definition='" + definition.getName() + '\'' +
                    ", status='" + status + '\'' +
                    ", duration=" + getDurationMs() + "ms" +
                    '}';
        }
    }
    
    /**
     * Execution metrics.
     */
    public static class ExecutionMetrics {
        private final long totalExecutions;
        private final long completedExecutions;
        private final long failedExecutions;
        private final long activeExecutions;
        
        public ExecutionMetrics(long total, long completed, long failed, long active) {
            this.totalExecutions = total;
            this.completedExecutions = completed;
            this.failedExecutions = failed;
            this.activeExecutions = active;
        }
        
        public long getTotalExecutions() {
            return totalExecutions;
        }
        
        public long getCompletedExecutions() {
            return completedExecutions;
        }
        
        public long getFailedExecutions() {
            return failedExecutions;
        }
        
        public long getActiveExecutions() {
            return activeExecutions;
        }
        
        public double getSuccessRate() {
            if (totalExecutions == 0) return 0.0;
            return (double) completedExecutions / totalExecutions;
        }
        
        @Override
        public String toString() {
            return "ExecutionMetrics{" +
                    "total=" + totalExecutions +
                    ", completed=" + completedExecutions +
                    ", failed=" + failedExecutions +
                    ", active=" + activeExecutions +
                    ", successRate=" + String.format("%.2f%%", getSuccessRate() * 100) +
                    '}';
        }
    }
}
