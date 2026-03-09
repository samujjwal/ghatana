package com.ghatana.virtualorg.framework.hitl;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ApprovalGateway.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides fast, in-memory approval tracking for development and testing. For
 * production, use a persistent implementation backed by a database.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. Uses ConcurrentHashMap for storage.
 *
 * @doc.type class
 * @doc.purpose In-memory approval gateway implementation
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class InMemoryApprovalGateway implements ApprovalGateway {

    private final Map<String, ApprovalRequest> requests;
    private final Map<String, ApprovalResult> results;
    private final List<ApprovalListener> listeners;
    private final MetricsCollector metrics;

    public InMemoryApprovalGateway(MetricsCollector metrics) {
        this.requests = new ConcurrentHashMap<>();
        this.results = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.metrics = Objects.requireNonNull(metrics, "metrics required");
    }

    @Override
    public Promise<ApprovalRequest> requestApproval(
            String action,
            String requestorAgentId,
            ApprovalContext context,
            Duration timeout) {

        String id = "approval-" + UUID.randomUUID().toString().substring(0, 8);

        ApprovalRequest request = ApprovalRequest.builder()
                .id(id)
                .action(action)
                .requestorAgentId(requestorAgentId)
                .context(context)
                .timeout(timeout)
                .status(ApprovalStatus.PENDING)
                .build();

        requests.put(id, request);

        metrics.incrementCounter("approval.requested",
                "agent_id", requestorAgentId,
                "priority", request.getPriority().name());

        notifyRequested(request);

        return Promise.of(request);
    }

    @Override
    public Promise<ApprovalStatus> checkStatus(String requestId) {
        ApprovalRequest request = requests.get(requestId);
        if (request == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Approval request not found: " + requestId));
        }

        // Check for expiration
        if (request.getStatus() == ApprovalStatus.PENDING && request.isExpired()) {
            ApprovalRequest expired = request.withStatus(ApprovalStatus.EXPIRED);
            requests.put(requestId, expired);
            notifyExpired(expired);
            return Promise.of(ApprovalStatus.EXPIRED);
        }

        return Promise.of(request.getStatus());
    }

    @Override
    public Promise<ApprovalRequest> getRequest(String requestId) {
        ApprovalRequest request = requests.get(requestId);
        if (request == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Approval request not found: " + requestId));
        }
        return Promise.of(request);
    }

    @Override
    public Promise<Void> approve(String requestId, String approver, String comment) {
        ApprovalRequest request = requests.get(requestId);
        if (request == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Approval request not found: " + requestId));
        }

        if (request.getStatus() != ApprovalStatus.PENDING) {
            return Promise.ofException(new IllegalStateException(
                    "Request is not pending: " + request.getStatus()));
        }

        if (request.isExpired()) {
            return Promise.ofException(new IllegalStateException(
                    "Request has expired"));
        }

        ApprovalRequest approved = request.withStatus(ApprovalStatus.APPROVED);
        requests.put(requestId, approved);

        results.put(requestId, new ApprovalResult(
                ApprovalStatus.APPROVED, approver, comment, Instant.now()));

        metrics.incrementCounter("approval.approved",
                "agent_id", request.getRequestorAgentId(),
                "approver", approver);

        notifyApproved(approved, approver);

        return Promise.complete();
    }

    @Override
    public Promise<Void> reject(String requestId, String approver, String reason) {
        ApprovalRequest request = requests.get(requestId);
        if (request == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Approval request not found: " + requestId));
        }

        if (request.getStatus() != ApprovalStatus.PENDING) {
            return Promise.ofException(new IllegalStateException(
                    "Request is not pending: " + request.getStatus()));
        }

        ApprovalRequest rejected = request.withStatus(ApprovalStatus.REJECTED);
        requests.put(requestId, rejected);

        results.put(requestId, new ApprovalResult(
                ApprovalStatus.REJECTED, approver, reason, Instant.now()));

        metrics.incrementCounter("approval.rejected",
                "agent_id", request.getRequestorAgentId(),
                "approver", approver);

        notifyRejected(rejected, approver, reason);

        return Promise.complete();
    }

    @Override
    public Promise<Void> cancel(String requestId) {
        ApprovalRequest request = requests.get(requestId);
        if (request == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Approval request not found: " + requestId));
        }

        if (request.getStatus() != ApprovalStatus.PENDING) {
            return Promise.ofException(new IllegalStateException(
                    "Request is not pending: " + request.getStatus()));
        }

        requests.put(requestId, request.withStatus(ApprovalStatus.CANCELLED));

        metrics.incrementCounter("approval.cancelled",
                "agent_id", request.getRequestorAgentId());

        return Promise.complete();
    }

    @Override
    public Promise<List<ApprovalRequest>> getPendingApprovals() {
        return Promise.of(requests.values().stream()
                .filter(ApprovalRequest::isPending)
                .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ApprovalRequest>> getPendingApprovals(String approverRole) {
        return Promise.of(requests.values().stream()
                .filter(ApprovalRequest::isPending)
                .filter(r -> approverRole == null
                || approverRole.equals(r.getRequiredRole()))
                .collect(Collectors.toList()));
    }

    @Override
    public Promise<ApprovalStatus> awaitResolution(String requestId) {
        // In a real implementation, this would use async polling or callbacks
        // For now, just check the current status
        return checkStatus(requestId);
    }

    // ========== Test Helpers ==========
    /**
     * Gets pending approvals synchronously (useful for tests).
     *
     * @param approverRole The approver role or agent to filter by
     * @return List of pending approval requests
     */
    public List<ApprovalRequest> getPendingApprovalsSync(String approverRole) {
        return requests.values().stream()
                .filter(ApprovalRequest::isPending)
                .filter(r -> approverRole == null
                || approverRole.equals(r.getRequiredRole())
                || approverRole.equals(r.getRequestorAgentId()))
                .collect(Collectors.toList());
    }

    /**
     * Gets all requests (useful for tests).
     *
     * @return All approval requests
     */
    public List<ApprovalRequest> getAllRequests() {
        return new ArrayList<>(requests.values());
    }

    /**
     * Clears all requests (useful for tests).
     */
    public void clear() {
        requests.clear();
        results.clear();
    }

    // ========== Listeners ==========
    @Override
    public void addListener(ApprovalListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ApprovalListener listener) {
        listeners.remove(listener);
    }

    private void notifyRequested(ApprovalRequest request) {
        for (ApprovalListener listener : listeners) {
            try {
                listener.onApprovalRequested(request);
            } catch (Exception e) {
                metrics.incrementCounter("approval.listener.error", "type", "requested");
            }
        }
    }

    private void notifyApproved(ApprovalRequest request, String approver) {
        for (ApprovalListener listener : listeners) {
            try {
                listener.onApproved(request, approver);
            } catch (Exception e) {
                metrics.incrementCounter("approval.listener.error", "type", "approved");
            }
        }
    }

    private void notifyRejected(ApprovalRequest request, String approver, String reason) {
        for (ApprovalListener listener : listeners) {
            try {
                listener.onRejected(request, approver, reason);
            } catch (Exception e) {
                metrics.incrementCounter("approval.listener.error", "type", "rejected");
            }
        }
    }

    private void notifyExpired(ApprovalRequest request) {
        for (ApprovalListener listener : listeners) {
            try {
                listener.onExpired(request);
            } catch (Exception e) {
                metrics.incrementCounter("approval.listener.error", "type", "expired");
            }
        }
    }

    // ========== Helper Classes ==========
    private record ApprovalResult(
            ApprovalStatus status,
            String approver,
            String comment,
            Instant timestamp
    ) {
    }
}
