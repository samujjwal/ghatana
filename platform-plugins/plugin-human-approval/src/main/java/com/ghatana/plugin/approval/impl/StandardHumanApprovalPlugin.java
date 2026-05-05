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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of {@link HumanApprovalPlugin} for development and testing.
 *
 * <p>Uses a {@link ConcurrentHashMap} — all state is lost on restart. Use
 * the durable JDBC implementation for production workloads that require durability.</p>
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
    /** requestId → required approvals (quorum); default 1. */
    private final Map<String, Integer> quorumByRequest = new ConcurrentHashMap<>();
    /** requestId → reviewers who have approved so far. */
    private final Map<String, Set<String>> approvalVotes = new ConcurrentHashMap<>();

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
        quorumByRequest.clear();
        approvalVotes.clear();
        this.state = PluginState.UNLOADED;
        LOG.info("HumanApprovalPlugin shutdown");
        return Promise.complete();
    }

    // -------------------------------------------------------------------------
    // HumanApprovalPlugin operations
    // -------------------------------------------------------------------------

    @Override
    public Promise<ApprovalRecord> requestApproval(ApprovalRequest request) {
        if (request == null) {
            return Promise.ofException(new NullPointerException("request must not be null"));
        }

        ApprovalRecord existing = records.get(request.requestId());
        if (existing != null) {
            LOG.debug("Idempotent requestApproval — request {} already exists with status {}",
                    request.requestId(), existing.status());
            return Promise.of(applyTimeoutEscalation(existing));
        }

        ApprovalRecord record = ApprovalRecord.pending(request);
        records.put(request.requestId(), record);
        quorumByRequest.put(request.requestId(), requiredApprovals(request.context()));

        LOG.info("Created approval request {} for subject {} action={}",
                request.requestId(), request.subjectId(), request.action());
        return Promise.of(record);
    }

    @Override
    public Promise<Optional<ApprovalRecord>> getApprovalStatus(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("requestId must not be blank"));
        }
        ApprovalRecord record = records.get(requestId);
        if (record == null) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.of(applyTimeoutEscalation(record)));
    }

    @Override
    public Promise<ApprovalRecord> completeApproval(String requestId, ApprovalDecision decision,
                                                     String reviewerId, String notes) {
        ApprovalRecord existing = records.get(requestId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Approval request not found: " + requestId));
        }

        existing = applyTimeoutEscalation(existing);

        if (existing.status() != ApprovalStatus.PENDING) {
            LOG.debug("completeApproval no-op — request {} already in status {}",
                    requestId, existing.status());
            return Promise.of(existing);
        }

        if (decision == ApprovalDecision.APPROVED) {
            int requiredApprovals = quorumByRequest.getOrDefault(requestId, 1);
            if (requiredApprovals > 1) {
                Set<String> reviewers = approvalVotes.computeIfAbsent(requestId, key -> ConcurrentHashMap.newKeySet());
                boolean newVote = reviewers.add(reviewerId);
                if (!newVote) {
                    LOG.debug("Ignoring duplicate approval vote — request {} reviewer {}", requestId, reviewerId);
                }
                if (reviewers.size() < requiredApprovals) {
                    LOG.info("Approval {} awaiting quorum {}/{}", requestId, reviewers.size(), requiredApprovals);
                    return Promise.of(existing);
                }
            }
        }

        ApprovalRecord decided = existing.withDecision(decision, reviewerId, notes, Instant.now());
        records.put(requestId, decided);
        approvalVotes.remove(requestId);
        quorumByRequest.remove(requestId);

        LOG.info("Approval {} decided {} by {}", requestId, decision, reviewerId);
        return Promise.of(decided);
    }

    @Override
    public Promise<List<ApprovalRecord>> listPendingForSubject(String subjectId) {
        List<ApprovalRecord> pending = records.values().stream()
                .map(this::applyTimeoutEscalation)
                .filter(r -> r.subjectId().equals(subjectId))
                .filter(r -> r.status() == ApprovalStatus.PENDING)
                .collect(Collectors.toUnmodifiableList());
        return Promise.of(pending);
    }

    @Override
    public Promise<List<ApprovalRecord>> listPendingForWorkspace(String workspaceId) {
        List<ApprovalRecord> pending = records.values().stream()
                .map(this::applyTimeoutEscalation)
                .filter(r -> {
                    Object ws = r.context().get("workspaceId");
                    return ws != null && ws.toString().equals(workspaceId);
                })
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

        existing = applyTimeoutEscalation(existing);

        if (existing.status() != ApprovalStatus.PENDING) {
            LOG.debug("cancelApproval no-op — request {} already in status {}",
                    requestId, existing.status());
            return Promise.complete();
        }

        ApprovalRecord cancelled = existing.cancelled(reason, Instant.now());
        records.put(requestId, cancelled);
        approvalVotes.remove(requestId);
        quorumByRequest.remove(requestId);

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

    private ApprovalRecord applyTimeoutEscalation(ApprovalRecord record) {
        if (record.status() != ApprovalStatus.PENDING || record.expiresAt() == null) {
            return record;
        }
        if (record.expiresAt().isAfter(Instant.now())) {
            return record;
        }

        ApprovalRecord expired = new ApprovalRecord(
            record.requestId(),
            record.subjectId(),
            record.requestedBy(),
            record.action(),
            ApprovalStatus.EXPIRED,
            record.requestedAt(),
            record.expiresAt(),
            Instant.now(),
            null,
            "Approval request expired before decision",
            record.context()
        );
        records.put(record.requestId(), expired);
        approvalVotes.remove(record.requestId());
        quorumByRequest.remove(record.requestId());
        LOG.info("Approval {} expired at {}", record.requestId(), record.expiresAt());
        return expired;
    }

    private static int requiredApprovals(Map<String, Object> context) {
        Object raw = context.get("quorum.requiredApprovals");
        if (raw instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        if (raw instanceof String text) {
            try {
                return Math.max(1, Integer.parseInt(text));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }
}
