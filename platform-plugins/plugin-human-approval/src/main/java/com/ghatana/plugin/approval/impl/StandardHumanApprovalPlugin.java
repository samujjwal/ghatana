package com.ghatana.plugin.approval.impl;

import com.ghatana.platform.plugin.PluginContext;
import com.ghatana.platform.plugin.PluginMetadata;
import com.ghatana.platform.plugin.PluginState;
import com.ghatana.platform.plugin.PluginType;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import com.ghatana.plugin.approval.ApprovalRequest;
import com.ghatana.plugin.approval.ApprovalStatus;
import com.ghatana.plugin.approval.HumanApprovalPlugin;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link HumanApprovalPlugin} for development and testing.
 *
 * <p>Uses a {@link ConcurrentHashMap} — all state is lost on restart. Use
 * {@link DurableHumanApprovalPlugin} for production workloads that require durability.</p>
 *
 * <p>Idempotency guarantee: calling {@link #requestApproval(ApprovalRequest)} twice with
 * the same {@code requestId} returns the existing record unchanged.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory human-approval plugin for development and testing
 * @doc.layer platform
 * @doc.pattern Plugin Implementation
 * @since 1.0.0
 */
public class StandardHumanApprovalPlugin implements HumanApprovalPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(StandardHumanApprovalPlugin.class);

    private static final PluginMetadata METADATA = PluginMetadata.builder()
            .id("com.ghatana.plugin.human-approval")
            .name("Human Approval Plugin")
            .version("1.0.0")
            .description("Human-in-the-loop approval for regulated operations (in-memory)")
            .type(PluginType.GOVERNANCE)
            .author("Ghatana")
            .license("Proprietary")
            .capability("approval:request", "approval:complete", "approval:cancel", "approval:query")
            .build();

    /** requestId → latest record (includes both pending and decided). */
    private final Map<String, ApprovalRecord> records = new ConcurrentHashMap<>();

    private PluginState state = PluginState.UNLOADED;

    // -------------------------------------------------------------------------
    // Plugin lifecycle
    // -------------------------------------------------------------------------

    @Override
    public PluginMetadata metadata() {
        return METADATA;
    }

    @Override
    public PluginState getState() {
        return state;
    }

    @Override
    public Promise<Void> initialize(PluginContext context) {
        this.state = PluginState.INITIALIZED;
        LOG.info("HumanApprovalPlugin initialized");
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        this.state = PluginState.RUNNING;
        LOG.info("HumanApprovalPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        this.state = PluginState.STOPPED;
        LOG.info("HumanApprovalPlugin stopped");
        return Promise.complete();
    }

    @Override
    public Promise<Void> shutdown() {
        records.clear();
        this.state = PluginState.UNLOADED;
        LOG.info("HumanApprovalPlugin shutdown");
        return Promise.complete();
    }

    // -------------------------------------------------------------------------
    // HumanApprovalPlugin operations
    // -------------------------------------------------------------------------

    @Override
    public Promise<ApprovalRecord> requestApproval(ApprovalRequest request) {
        ApprovalRecord existing = records.get(request.requestId());
        if (existing != null) {
            LOG.debug("Idempotent requestApproval — request {} already exists with status {}",
                    request.requestId(), existing.status());
            return Promise.of(existing);
        }

        ApprovalRecord record = ApprovalRecord.pending(request);
        records.put(request.requestId(), record);

        LOG.info("Created approval request {} for subject {} action={}",
                request.requestId(), request.subjectId(), request.action());
        return Promise.of(record);
    }

    @Override
    public Promise<Optional<ApprovalRecord>> getApprovalStatus(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("requestId must not be blank"));
        }
        return Promise.of(Optional.ofNullable(records.get(requestId)));
    }

    @Override
    public Promise<ApprovalRecord> completeApproval(String requestId, ApprovalDecision decision,
                                                     String reviewerId, String notes) {
        ApprovalRecord existing = records.get(requestId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Approval request not found: " + requestId));
        }

        if (existing.status() != ApprovalStatus.PENDING) {
            LOG.debug("completeApproval no-op — request {} already in status {}",
                    requestId, existing.status());
            return Promise.of(existing);
        }

        ApprovalRecord decided = existing.withDecision(decision, reviewerId, notes, Instant.now());
        records.put(requestId, decided);

        LOG.info("Approval {} decided {} by {}", requestId, decision, reviewerId);
        return Promise.of(decided);
    }

    @Override
    public Promise<List<ApprovalRecord>> listPendingForSubject(String subjectId) {
        List<ApprovalRecord> pending = records.values().stream()
                .filter(r -> r.subjectId().equals(subjectId))
                .filter(r -> r.status() == ApprovalStatus.PENDING)
                .collect(Collectors.toUnmodifiableList());
        return Promise.of(pending);
    }

    @Override
    public Promise<Void> cancelApproval(String requestId, String reason) {
        ApprovalRecord existing = records.get(requestId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Approval request not found: " + requestId));
        }

        if (existing.status() != ApprovalStatus.PENDING) {
            LOG.debug("cancelApproval no-op — request {} already in status {}",
                    requestId, existing.status());
            return Promise.complete();
        }

        ApprovalRecord cancelled = existing.cancelled(reason, Instant.now());
        records.put(requestId, cancelled);

        LOG.info("Approval {} cancelled — reason: {}", requestId, reason);
        return Promise.complete();
    }

    @Override
    public String toString() {
        long pendingCount = records.values().stream()
                .filter(r -> r.status() == ApprovalStatus.PENDING)
                .count();
        return "StandardHumanApprovalPlugin{total=" + records.size()
                + ", pending=" + pendingCount + "}";
    }
}
