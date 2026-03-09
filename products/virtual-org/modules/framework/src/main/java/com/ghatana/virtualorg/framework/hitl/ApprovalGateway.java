package com.ghatana.virtualorg.framework.hitl;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.List;

/**
 * Interface for human-in-the-loop approval system.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides agents with a mechanism to request human approval for critical
 * actions. Implements pause-and-wait semantics for governance and compliance.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ApprovalGateway gateway = new InMemoryApprovalGateway(metrics);
 *
 * // Agent requests approval
 * ApprovalRequest request = gateway.requestApproval(
 *     "Deploy to production",
 *     "devops-agent-001",
 *     ApprovalContext.builder()
 *         .reason("Fix critical bug")
 *         .riskLevel(RiskLevel.HIGH)
 *         .build(),
 *     Duration.ofHours(4)
 * ).getResult();
 *
 * // Human approves
 * gateway.approve(request.getId(), "john@company.com", "Looks good");
 *
 * // Agent checks status
 * ApprovalStatus status = gateway.checkStatus(request.getId()).getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose HITL approval gateway contract
 * @doc.layer product
 * @doc.pattern Gateway
 */
public interface ApprovalGateway {

    /**
     * Requests human approval for an action.
     *
     * @param action Description of the action
     * @param requestorAgentId ID of the requesting agent
     * @param context Context for the approval
     * @param timeout How long to wait for approval
     * @return Promise of the created request
     */
    Promise<ApprovalRequest> requestApproval(
            String action,
            String requestorAgentId,
            ApprovalContext context,
            Duration timeout
    );

    /**
     * Checks the status of an approval request.
     *
     * @param requestId The request ID
     * @return Promise of the current status
     */
    Promise<ApprovalStatus> checkStatus(String requestId);

    /**
     * Gets the full request details.
     *
     * @param requestId The request ID
     * @return Promise of the request
     */
    Promise<ApprovalRequest> getRequest(String requestId);

    /**
     * Approves a pending request.
     *
     * @param requestId The request ID
     * @param approver Who is approving
     * @param comment Approval comment
     * @return Promise completing when approved
     */
    Promise<Void> approve(String requestId, String approver, String comment);

    /**
     * Rejects a pending request.
     *
     * @param requestId The request ID
     * @param approver Who is rejecting
     * @param reason Rejection reason
     * @return Promise completing when rejected
     */
    Promise<Void> reject(String requestId, String approver, String reason);

    /**
     * Cancels a pending request (by the requesting agent).
     *
     * @param requestId The request ID
     * @return Promise completing when cancelled
     */
    Promise<Void> cancel(String requestId);

    /**
     * Gets all pending approvals.
     *
     * @return Promise of pending requests
     */
    Promise<List<ApprovalRequest>> getPendingApprovals();

    /**
     * Gets pending approvals for a specific role.
     *
     * @param approverRole The role that can approve
     * @return Promise of pending requests for that role
     */
    Promise<List<ApprovalRequest>> getPendingApprovals(String approverRole);

    /**
     * Waits for a request to be resolved (approved, rejected, or expired).
     *
     * @param requestId The request ID
     * @return Promise of the final status
     */
    Promise<ApprovalStatus> awaitResolution(String requestId);

    /**
     * Listener for approval events.
     */
    interface ApprovalListener {

        default void onApprovalRequested(ApprovalRequest request) {
        }

        default void onApproved(ApprovalRequest request, String approver) {
        }

        default void onRejected(ApprovalRequest request, String approver, String reason) {
        }

        default void onExpired(ApprovalRequest request) {
        }
    }

    /**
     * Adds an approval listener.
     */
    void addListener(ApprovalListener listener);

    /**
     * Removes an approval listener.
     */
    void removeListener(ApprovalListener listener);
}
