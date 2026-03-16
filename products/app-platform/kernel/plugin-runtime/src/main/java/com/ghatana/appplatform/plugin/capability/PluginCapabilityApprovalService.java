/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.capability;

import com.ghatana.appplatform.plugin.domain.PluginCapability;
import com.ghatana.appplatform.plugin.domain.PluginManifest;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Manages the security-team approval process for high-risk plugin capabilities
 * (STORY-K04-008).
 *
 * <p>High-risk capabilities ({@code EXECUTE_NETWORK}, {@code WRITE_DATA}) declared by T3
 * plugins require an explicit approval from a security team member before the plugin may
 * enter {@code ACTIVE} status. This service tracks the approval lifecycle and emits a
 * record when approval is granted or denied.
 *
 * @doc.type  class
 * @doc.purpose Tracks security-team approval for high-risk plugin capabilities (K04-008)
 * @doc.layer kernel
 * @doc.pattern Service
 */
public final class PluginCapabilityApprovalService {

    private static final Logger log = LoggerFactory.getLogger(PluginCapabilityApprovalService.class);

    private final Map<String, CapabilityApprovalRequest> requests = new ConcurrentHashMap<>();
    private final Executor executor;

    public PluginCapabilityApprovalService(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Opens an approval request for the high-risk capabilities declared by {@code manifest}.
     *
     * @param manifest      plugin manifest containing the high-risk capabilities to approve
     * @param requestedBy   security officer who will review (or the submitting team)
     * @return promise resolving to the approval request ID
     */
    public Promise<String> submit(PluginManifest manifest, String requestedBy) {
        Objects.requireNonNull(manifest,    "manifest");
        Objects.requireNonNull(requestedBy, "requestedBy");

        List<PluginCapability> highRisk = manifest.highRiskCapabilities();
        if (highRisk.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException(
                    "No high-risk capabilities found in manifest for plugin: " + manifest.name()));
        }

        return Promise.ofBlocking(executor, () -> {
            String requestId = UUID.randomUUID().toString();
            CapabilityApprovalRequest request = new CapabilityApprovalRequest(
                    requestId,
                    manifest.name(),
                    manifest.version().toString(),
                    List.copyOf(highRisk),
                    requestedBy,
                    ApprovalStatus.PENDING,
                    Instant.now(),
                    null,
                    null,
                    null
            );
            requests.put(requestId, request);
            log.info("Capability approval requested: id={} plugin={} caps={}", requestId, manifest.name(), highRisk);
            return requestId;
        });
    }

    /**
     * Approves a pending capability approval request.
     *
     * @param requestId  the approval request to approve
     * @param approvedBy security team member granting approval
     * @return promise resolving to the updated request
     */
    public Promise<CapabilityApprovalRequest> approve(String requestId, String approvedBy) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(approvedBy, "approvedBy");

        return Promise.ofBlocking(executor, () -> {
            CapabilityApprovalRequest req = requirePending(requestId);
            if (req.requestedBy().equals(approvedBy)) {
                throw new IllegalArgumentException(
                        "Approver must differ from requester for requestId=" + requestId);
            }
            CapabilityApprovalRequest approved = req.withStatus(ApprovalStatus.APPROVED, approvedBy, Instant.now(), null);
            requests.put(requestId, approved);
            log.info("Capability approved: id={} plugin={} approvedBy={}", requestId, req.pluginName(), approvedBy);
            return approved;
        });
    }

    /**
     * Denies a pending capability approval request.
     *
     * @param requestId  the approval request to deny
     * @param deniedBy   security team member denying the request
     * @param reason     denial reason (required)
     */
    public Promise<CapabilityApprovalRequest> deny(String requestId, String deniedBy, String reason) {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(deniedBy,  "deniedBy");
        Objects.requireNonNull(reason,    "reason");

        return Promise.ofBlocking(executor, () -> {
            CapabilityApprovalRequest req = requirePending(requestId);
            CapabilityApprovalRequest denied = req.withStatus(ApprovalStatus.DENIED, deniedBy, Instant.now(), reason);
            requests.put(requestId, denied);
            log.warn("Capability denied: id={} plugin={} deniedBy={} reason={}", requestId, req.pluginName(), deniedBy, reason);
            return denied;
        });
    }

    /** Returns a request by ID. */
    public Promise<CapabilityApprovalRequest> get(String requestId) {
        return Promise.ofBlocking(executor, () -> {
            CapabilityApprovalRequest req = requests.get(requestId);
            if (req == null) throw new IllegalArgumentException("Approval request not found: " + requestId);
            return req;
        });
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private CapabilityApprovalRequest requirePending(String requestId) {
        CapabilityApprovalRequest req = requests.get(requestId);
        if (req == null) throw new IllegalArgumentException("Approval request not found: " + requestId);
        if (req.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Request already " + req.status() + ": " + requestId);
        }
        return req;
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    public enum ApprovalStatus { PENDING, APPROVED, DENIED }

    public record CapabilityApprovalRequest(
            String requestId,
            String pluginName,
            String pluginVersion,
            List<PluginCapability> capabilities,
            String requestedBy,
            ApprovalStatus status,
            Instant requestedAt,
            String reviewedBy,
            Instant reviewedAt,
            String denialReason
    ) {
        CapabilityApprovalRequest withStatus(ApprovalStatus s, String reviewer,
                                              Instant at, String reason) {
            return new CapabilityApprovalRequest(requestId, pluginName, pluginVersion,
                    capabilities, requestedBy, s, requestedAt, reviewer, at, reason);
        }
    }
}
