package com.ghatana.virtualorg.framework.collaboration;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of DelegationManager.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides task delegation between agents with: - Capability-based routing -
 * Workload balancing - Progress tracking
 *
 * @doc.type class
 * @doc.purpose Default delegation manager
 * @doc.layer product
 * @doc.pattern Mediator
 */
public class DefaultDelegationManager implements DelegationManager {

    private final ConversationManager conversationManager;
    private final VirtualOrgAgentDirectory agentDirectory;
    private final ConcurrentHashMap<String, DelegationRecord> delegations = new ConcurrentHashMap<>();

    public DefaultDelegationManager(ConversationManager conversationManager, VirtualOrgAgentDirectory agentDirectory) {
        this.conversationManager = conversationManager;
        this.agentDirectory = agentDirectory;
    }

    @Override
    public Promise<DelegationResult> delegate(DelegationRequest request) {
        return findCapableAgents(request.taskType())
                .then(agents -> {
                    if (agents.isEmpty()) {
                        return Promise.of(DelegationResult.failure(
                                "No agents capable of handling task type: " + request.taskType()));
                    }

                    // Find agent with lowest workload
                    String selectedAgent = selectBestAgent(agents);
                    return delegateTo(request, selectedAgent);
                });
    }

    @Override
    public Promise<DelegationResult> delegateTo(DelegationRequest request, String targetAgentId) {
        String delegationId = UUID.randomUUID().toString();

        // Create delegation record
        DelegationRecord record = new DelegationRecord(
                delegationId,
                request.fromAgentId(),
                targetAgentId,
                request.taskType(),
                request.taskDescription(),
                request.taskData(),
                request.priority(),
                DelegationStatus.PENDING,
                Instant.now(),
                null,
                request.timeoutMs()
        );
        delegations.put(delegationId, record);

        // Send delegation message
        AgentMessage message = AgentMessage.builder()
                .from(request.fromAgentId())
                .to(targetAgentId)
                .type(AgentMessage.MessageType.DELEGATION)
                .subject("Task: " + request.taskType())
                .content(request.taskDescription())
                .payload(Map.of(
                        "delegation_id", delegationId,
                        "task_type", request.taskType(),
                        "task_data", request.taskData()
                ))
                .priority(request.priority())
                .ttl(java.time.Duration.ofMillis(request.timeoutMs()))
                .build();

        return conversationManager.send(message)
                .map(sent -> {
                    // Update status to assigned
                    record.status = DelegationStatus.ASSIGNED;
                    return DelegationResult.success(delegationId, targetAgentId, request.timeoutMs());
                });
    }

    @Override
    public Promise<List<String>> findCapableAgents(String taskType) {
        return Promise.of(agentDirectory.findByCapability(taskType));
    }

    @Override
    public Promise<AgentWorkload> getWorkload(String agentId) {
        long active = delegations.values().stream()
                .filter(d -> d.assignedTo.equals(agentId))
                .filter(d -> d.status == DelegationStatus.IN_PROGRESS)
                .count();

        long queued = delegations.values().stream()
                .filter(d -> d.assignedTo.equals(agentId))
                .filter(d -> d.status == DelegationStatus.ASSIGNED
                || d.status == DelegationStatus.PENDING)
                .count();

        double utilization = Math.min(100.0, (active + queued * 0.5) * 20);
        boolean available = utilization < 80;

        return Promise.of(new AgentWorkload(
                agentId,
                (int) active,
                (int) queued,
                utilization,
                available
        ));
    }

    @Override
    public Promise<Boolean> cancel(String delegationId, String reason) {
        DelegationRecord record = delegations.get(delegationId);
        if (record == null) {
            return Promise.of(false);
        }

        record.status = DelegationStatus.CANCELLED;

        // Notify assigned agent
        AgentMessage cancellation = AgentMessage.builder()
                .from(record.requestedBy)
                .to(record.assignedTo)
                .type(AgentMessage.MessageType.NOTIFICATION)
                .subject("Task Cancelled: " + record.taskType)
                .content("Reason: " + reason)
                .payload(Map.of("delegation_id", delegationId))
                .build();

        return conversationManager.send(cancellation)
                .map(sent -> true);
    }

    @Override
    public Promise<DelegationResult> escalate(String delegationId, String reason) {
        DelegationRecord record = delegations.get(delegationId);
        if (record == null) {
            return Promise.of(DelegationResult.failure("Delegation not found: " + delegationId));
        }

        record.status = DelegationStatus.ESCALATED;

        // Find a senior/manager agent
        List<String> managers = agentDirectory.findByRole("manager");
        if (managers.isEmpty()) {
            return Promise.of(DelegationResult.failure("No managers available for escalation"));
        }

        String manager = managers.get(0);

        // Create new delegation to manager
        DelegationRequest escalationRequest = DelegationRequest.builder()
                .fromAgent(record.requestedBy)
                .taskType(record.taskType)
                .taskDescription("[ESCALATED] " + record.taskDescription + "\nReason: " + reason)
                .taskData(record.taskData)
                .priority(AgentMessage.Priority.URGENT)
                .allowEscalation(false)
                .build();

        return delegateTo(escalationRequest, manager);
    }

    @Override
    public Promise<Boolean> reportProgress(String delegationId, TaskProgress progress) {
        DelegationRecord record = delegations.get(delegationId);
        if (record == null) {
            return Promise.of(false);
        }

        record.status = DelegationStatus.IN_PROGRESS;
        record.progress = progress.percentComplete();

        // Notify requester
        AgentMessage update = AgentMessage.builder()
                .from(record.assignedTo)
                .to(record.requestedBy)
                .type(AgentMessage.MessageType.STATUS_UPDATE)
                .subject("Progress: " + record.taskType)
                .content(progress.statusMessage())
                .payload(Map.of(
                        "delegation_id", delegationId,
                        "percent_complete", progress.percentComplete(),
                        "data", progress.data()
                ))
                .build();

        return conversationManager.send(update)
                .map(sent -> true);
    }

    @Override
    public Promise<Boolean> complete(String delegationId, TaskResult result) {
        DelegationRecord record = delegations.get(delegationId);
        if (record == null) {
            return Promise.of(false);
        }

        record.status = result.success() ? DelegationStatus.COMPLETED : DelegationStatus.FAILED;
        record.completedAt = Instant.now();

        // Notify requester
        AgentMessage completion = AgentMessage.builder()
                .from(record.assignedTo)
                .to(record.requestedBy)
                .type(AgentMessage.MessageType.RESPONSE)
                .subject("Completed: " + record.taskType)
                .content(result.success() ? "Task completed successfully" : "Task failed: " + result.errorMessage())
                .payload(Map.of(
                        "delegation_id", delegationId,
                        "success", result.success(),
                        "output", result.output(),
                        "duration_ms", result.durationMs()
                ))
                .build();

        return conversationManager.send(completion)
                .map(sent -> true);
    }

    @Override
    public Promise<DelegationStatus> getStatus(String delegationId) {
        DelegationRecord record = delegations.get(delegationId);
        if (record == null) {
            return Promise.ofException(new IllegalArgumentException("Delegation not found: " + delegationId));
        }

        // Check for timeout
        if (record.status == DelegationStatus.ASSIGNED
                || record.status == DelegationStatus.IN_PROGRESS) {
            long elapsed = Instant.now().toEpochMilli() - record.createdAt.toEpochMilli();
            if (elapsed > record.timeoutMs) {
                record.status = DelegationStatus.TIMED_OUT;
            }
        }

        return Promise.of(record.status);
    }

    private String selectBestAgent(List<String> agents) {
        // Simple round-robin with workload consideration
        String best = agents.get(0);
        double lowestUtil = Double.MAX_VALUE;

        for (String agent : agents) {
            AgentWorkload workload = getWorkload(agent).getResult();
            if (workload.available() && workload.utilizationPercent() < lowestUtil) {
                lowestUtil = workload.utilizationPercent();
                best = agent;
            }
        }

        return best;
    }

    // ========== Internal Record ==========
    private static class DelegationRecord {

        final String id;
        final String requestedBy;
        final String assignedTo;
        final String taskType;
        final String taskDescription;
        final Map<String, Object> taskData;
        final AgentMessage.Priority priority;
        DelegationStatus status;
        final Instant createdAt;
        Instant completedAt;
        final long timeoutMs;
        int progress;

        DelegationRecord(String id, String requestedBy, String assignedTo, String taskType,
                String taskDescription, Map<String, Object> taskData,
                AgentMessage.Priority priority, DelegationStatus status,
                Instant createdAt, Instant completedAt, long timeoutMs) {
            this.id = id;
            this.requestedBy = requestedBy;
            this.assignedTo = assignedTo;
            this.taskType = taskType;
            this.taskDescription = taskDescription;
            this.taskData = taskData;
            this.priority = priority;
            this.status = status;
            this.createdAt = createdAt;
            this.completedAt = completedAt;
            this.timeoutMs = timeoutMs;
            this.progress = 0;
        }
    }
}
