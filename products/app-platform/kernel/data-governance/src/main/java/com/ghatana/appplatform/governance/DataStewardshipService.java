package com.ghatana.appplatform.governance;

import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.appplatform.governance.port.StewardshipStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @doc.type    DomainService
 * @doc.purpose Data steward assignment and accountability management. Links data stewards to
 *              data domains and individual assets. Each assignment carries an SLA (days) for
 *              acting on quality breaks, classification reviews, and lineage validations.
 *              Stewards who miss their SLA window are escalated to the domain owner.
 *              Integrates with K-01 RBAC to verify steward role. Satisfies STORY-K08-013.
 * @doc.layer   Kernel
 * @doc.pattern Role-based steward assignment; SLA monitoring with automatic escalation;
 *              domain-level and asset-level assignment scopes; K-07 audit trail;
 *              overwdueCount Gauge.
 */
public class DataStewardshipService {

    private final StewardshipStore stewardshipStore;
    private final Executor           executor;
    private final RbacPort           rbacPort;
    private final NotificationPort   notificationPort;
    private final AuditBusPort          auditPort;
    private final AtomicLong         overdueCount = new AtomicLong(0);

    public DataStewardshipService(StewardshipStore stewardshipStore, Executor executor,
                                   RbacPort rbacPort,
                                   NotificationPort notificationPort,
                                   AuditBusPort auditPort,
                                   MeterRegistry registry) {
        this.stewardshipStore = stewardshipStore;
        this.executor         = executor;
        this.rbacPort         = rbacPort;
        this.notificationPort = notificationPort;
        this.auditPort        = auditPort;

        Gauge.builder("governance.stewardship.overdue_count", overdueCount, AtomicLong::get).register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-01 RBAC: check whether a user has the required role. */
    public interface RbacPort {
        boolean hasRole(String userId, String role);
        String domainOwner(String domainId);
    }

    /** Steward and escalation notifications. */
    public interface NotificationPort {
        void notifySteward(String stewardId, String message);
        void notifyOwner(String ownerId, String message);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum AssignmentScope { DOMAIN, ASSET }
    public enum ActionStatus { PENDING, IN_PROGRESS, COMPLETED }

    public record StewardAssignment(
        String assignmentId, AssignmentScope scope,
        String domainId, String assetId, String stewardId,
        int slaDays, Instant assignedAt, Instant slaDeadline
    ) {}

    public record StewardAction(
        String actionId, String assignmentId, String stewardId,
        String actionType, ActionStatus status, Instant createdAt, Instant dueAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Assign a steward to a data domain. Only users with DATA_STEWARD role may be assigned.
     * Existing assignment for the domain is replaced (ON CONFLICT DO UPDATE).
     */
    public Promise<StewardAssignment> assignDomainSteward(String domainId, String stewardId,
                                                            int slaDays, String assignedBy) {
        return Promise.ofBlocking(executor, () -> {
            if (!rbacPort.hasRole(stewardId, "DATA_STEWARD")) {
                throw new IllegalArgumentException("User " + stewardId + " lacks DATA_STEWARD role");
            }

            String assignmentId = UUID.randomUUID().toString();
            Instant now         = Instant.now();
            Instant slaDeadline = now.plusSeconds((long) slaDays * 86400);

            stewardshipStore.upsertDomainAssignment(assignmentId, domainId, stewardId,
                    slaDays, slaDeadline);

            notificationPort.notifySteward(stewardId,
                "You have been assigned as steward for domain " + domainId +
                " with a " + slaDays + "-day SLA.");

            auditPort.emit(AuditEvent.builder().eventType("STEWARD_ASSIGNED").resourceType("Domain").resourceId(domainId).details(Map.of("stewardId", stewardId, "slaDays", slaDays, "assignedBy", assignedBy)).build());

            return new StewardAssignment(assignmentId, AssignmentScope.DOMAIN,
                domainId, null, stewardId, slaDays, now, slaDeadline);
        });
    }

    /**
     * Assign a steward to a specific data asset (overrides domain-level assignment for this asset).
     */
    public Promise<StewardAssignment> assignAssetSteward(String assetId, String domainId,
                                                          String stewardId, int slaDays,
                                                          String assignedBy) {
        return Promise.ofBlocking(executor, () -> {
            if (!rbacPort.hasRole(stewardId, "DATA_STEWARD")) {
                throw new IllegalArgumentException("User " + stewardId + " lacks DATA_STEWARD role");
            }

            String assignmentId = UUID.randomUUID().toString();
            Instant now         = Instant.now();
            Instant slaDeadline = now.plusSeconds((long) slaDays * 86400);

            stewardshipStore.upsertAssetAssignment(assignmentId, domainId, assetId,
                    stewardId, slaDays, slaDeadline);

            auditPort.emit(AuditEvent.builder().eventType("STEWARD_ASSIGNED").resourceType("DataAsset").resourceId(assetId).details(Map.of("stewardId", stewardId, "slaDays", slaDays, "assignedBy", assignedBy)).build());

            return new StewardAssignment(assignmentId, AssignmentScope.ASSET,
                domainId, assetId, stewardId, slaDays, now, slaDeadline);
        });
    }

    /**
     * Record a steward action taken on a quality break, classification review, or lineage task.
     */
    public Promise<StewardAction> recordAction(String assignmentId, String stewardId,
                                                String actionType) {
        return Promise.ofBlocking(executor, () -> {
            String actionId = UUID.randomUUID().toString();
            Instant now     = Instant.now();

            stewardshipStore.insertAction(actionId, assignmentId, stewardId, actionType);

            auditPort.emit(AuditEvent.builder().eventType("STEWARD_ACTION_RECORDED").resourceType("StewardAssignment").resourceId(assignmentId).details(Map.of("actionId", actionId, "stewardId", stewardId, "actionType", actionType)).build());

            return new StewardAction(actionId, assignmentId, stewardId, actionType,
                ActionStatus.IN_PROGRESS, now, now.plusSeconds(86400));
        });
    }

    /**
     * Scan all assignments for SLA breaches and escalate overdue ones to domain owners.
     * Returns the number of escalations triggered.
     */
    public Promise<Integer> escalateSlaBreaches() {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, String>> overdueAssignments = stewardshipStore.fetchOverdueAssignments();
            overdueCount.set(overdueAssignments.size());

            for (Map<String, String> assignment : overdueAssignments) {
                String stewardId  = assignment.get("steward_id");
                String domainId   = assignment.get("domain_id");
                String ownerId    = rbacPort.domainOwner(domainId);
                String assignmentId = assignment.get("assignment_id");

                notificationPort.notifyOwner(ownerId,
                    String.format("Steward %s has exceeded SLA for domain %s. Escalating.",
                        stewardId, domainId));

                stewardshipStore.escalateAssignment(assignmentId);

                auditPort.emit(AuditEvent.builder().eventType("STEWARD_SLA_ESCALATED").resourceType("StewardAssignment").resourceId(assignmentId).details(Map.of("stewardId", stewardId, "domainId", domainId, "escalatedTo", ownerId)).build());
            }

            return overdueAssignments.size();
        });
    }

    /**
     * Retrieve the effective steward for a given asset (asset-level beats domain-level).
     */
    public Promise<Optional<String>> resolveEffectiveSteward(String assetId, String domainId) {
        return Promise.ofBlocking(executor, () -> stewardshipStore.resolveEffectiveSteward(assetId, domainId));
    }
}
