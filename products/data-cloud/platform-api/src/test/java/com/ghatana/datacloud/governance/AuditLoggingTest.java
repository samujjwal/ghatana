/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.governance;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for governance audit logging — creation, querying, retention,
 * and structured output of audit trail entries.
 *
 * @doc.type    class
 * @doc.purpose Tests for data-cloud governance audit log behavior
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("Governance Audit Logging Tests")
class AuditLoggingTest extends EventloopTestBase {

    // ── Audit model ───────────────────────────────────────────────────────────

    record AuditEntry( // GH-90000
            String entryId,
            Instant timestamp,
            String tenantId,
            String userId,
            String action,
            String resourceType,
            String resourceId,
            boolean success,
            String details
    ) {}

    private InMemoryAuditLog auditLog;

    @BeforeEach
    void setUp() { // GH-90000
        auditLog = new InMemoryAuditLog(); // GH-90000
    }

    // ── Audit log creation ────────────────────────────────────────────────────

    @Test
    @DisplayName("log returns an entry with all required fields populated")
    void logCreatesEntryWithAllRequiredFields() { // GH-90000
        AuditEntry entry = auditLog.log( // GH-90000
                "tenant-audit", "user-001",
                "data.purge", "Collection", "col-xyz", true, "Purge executed successfully");

        assertThat(entry.entryId()).isNotNull().isNotBlank(); // GH-90000
        assertThat(entry.timestamp()).isNotNull().isBefore(Instant.now().plusSeconds(1)); // GH-90000
        assertThat(entry.tenantId()).isEqualTo("tenant-audit");
        assertThat(entry.userId()).isEqualTo("user-001");
        assertThat(entry.action()).isEqualTo("data.purge");
        assertThat(entry.resourceType()).isEqualTo("Collection");
        assertThat(entry.resourceId()).isEqualTo("col-xyz");
        assertThat(entry.success()).isTrue(); // GH-90000
        assertThat(entry.details()).isEqualTo("Purge executed successfully");
    }

    @Test
    @DisplayName("log assigns unique entry IDs to each audit event")
    void logAssignsUniqueEntryIds() { // GH-90000
        AuditEntry a = auditLog.log("tenant-a", "u1", "read", "Dataset", "ds-1", true, null); // GH-90000
        AuditEntry b = auditLog.log("tenant-a", "u1", "read", "Dataset", "ds-2", true, null); // GH-90000

        assertThat(a.entryId()).isNotEqualTo(b.entryId()); // GH-90000
    }

    @Test
    @DisplayName("failed operations are logged with success=false")
    void failedOperationsLoggedWithSuccessFalse() { // GH-90000
        AuditEntry entry = auditLog.log( // GH-90000
                "tenant-fail", "user-002",
                "data.delete", "Feature", "feature-1", false, "Insufficient permissions");

        assertThat(entry.success()).isFalse(); // GH-90000
        assertThat(entry.details()).contains("Insufficient permissions");
    }

    // ── Audit log querying ────────────────────────────────────────────────────

    @Test
    @DisplayName("queryByTenant returns only entries for the specified tenant")
    void queryByTenantReturnsOnlyMatchingEntries() { // GH-90000
        auditLog.log("tenant-Q", "u1", "read", "X", "r1", true, null); // GH-90000
        auditLog.log("tenant-Q", "u2", "write", "X", "r2", true, null); // GH-90000
        auditLog.log("tenant-other", "u3", "read", "X", "r3", true, null); // GH-90000

        List<AuditEntry> results = auditLog.queryByTenant("tenant-Q");

        assertThat(results).hasSize(2); // GH-90000
        assertThat(results).allMatch(e -> e.tenantId().equals("tenant-Q"));
    }

    @Test
    @DisplayName("queryByAction returns only entries matching the specified action")
    void queryByActionReturnsMatchingEntries() { // GH-90000
        auditLog.log("tenant-R", "u1", "data.purge", "Col", "c1", true, null); // GH-90000
        auditLog.log("tenant-R", "u2", "data.read", "Col", "c2", true, null); // GH-90000
        auditLog.log("tenant-R", "u3", "data.purge", "Col", "c3", true, null); // GH-90000

        List<AuditEntry> results = auditLog.queryByAction("data.purge");

        assertThat(results).hasSize(2); // GH-90000
        assertThat(results).allMatch(e -> e.action().equals("data.purge"));
    }

    @Test
    @DisplayName("queryByTimeRange returns only entries within the specified window")
    void queryByTimeRangeReturnsBoundedEntries() throws InterruptedException { // GH-90000
        Instant before = Instant.now().minusSeconds(1); // GH-90000
        auditLog.log("tenant-T", "u1", "write", "X", "x1", true, null); // GH-90000
        Thread.sleep(10); // GH-90000
        Instant after = Instant.now(); // GH-90000

        List<AuditEntry> results = auditLog.queryByTimeRange("tenant-T", before, after); // GH-90000

        assertThat(results).isNotEmpty(); // GH-90000
        assertThat(results).allMatch(e -> // GH-90000
                !e.timestamp().isBefore(before) && !e.timestamp().isAfter(after)); // GH-90000
    }

    @Test
    @DisplayName("queryByTenant returns empty list when no entries exist for that tenant")
    void queryByTenantReturnsEmptyWhenNoEntries() { // GH-90000
        List<AuditEntry> results = auditLog.queryByTenant("tenant-ghost");
        assertThat(results).isEmpty(); // GH-90000
    }

    // ── Retention ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pruneOlderThan removes entries before the cutoff timestamp")
    void pruneOlderThanRemovesOldEntries() throws InterruptedException { // GH-90000
        auditLog.log("tenant-P", "u1", "write", "X", "x1", true, null); // GH-90000
        Thread.sleep(20); // GH-90000
        Instant cutoff = Instant.now(); // GH-90000
        Thread.sleep(5); // GH-90000
        auditLog.log("tenant-P", "u2", "write", "X", "x2", true, null); // GH-90000

        int removed = auditLog.pruneOlderThan(cutoff); // GH-90000

        assertThat(removed).isEqualTo(1); // GH-90000
        List<AuditEntry> remaining = auditLog.queryByTenant("tenant-P");
        assertThat(remaining).hasSize(1); // GH-90000
        assertThat(remaining.get(0).userId()).isEqualTo("u2");
    }

    @Test
    @DisplayName("pruneOlderThan returns 0 when all entries are newer than cutoff")
    void pruneOlderThanReturnZeroWhenAllNewer() { // GH-90000
        auditLog.log("tenant-P2", "u1", "read", "X", "x1", true, null); // GH-90000

        int removed = auditLog.pruneOlderThan(Instant.EPOCH); // GH-90000
        assertThat(removed).isEqualTo(0); // GH-90000
    }

    // ── Audit log export ──────────────────────────────────────────────────────

    @Test
    @DisplayName("export produces JSON-like string containing required fields")
    void exportProducesStructuredOutput() { // GH-90000
        auditLog.log("tenant-E", "u1", "data.export", "Dataset", "ds-export", true, "Export succeeded"); // GH-90000

        String exported = auditLog.exportAsJson("tenant-E");

        assertThat(exported).contains("tenant-E");
        assertThat(exported).contains("data.export");
        assertThat(exported).contains("Dataset");
        assertThat(exported).contains("ds-export");
    }

    // ── In-memory audit log implementation (for tests) ──────────────────────── // GH-90000

    static class InMemoryAuditLog {
        private final Deque<AuditEntry> entries = new ConcurrentLinkedDeque<>(); // GH-90000

        AuditEntry log(String tenantId, String userId, String action, // GH-90000
                       String resourceType, String resourceId,
                       boolean success, String details) {
            AuditEntry entry = new AuditEntry( // GH-90000
                    UUID.randomUUID().toString(), // GH-90000
                    Instant.now(), // GH-90000
                    tenantId, userId, action,
                    resourceType, resourceId,
                    success, details);
            entries.addFirst(entry); // GH-90000
            return entry;
        }

        List<AuditEntry> queryByTenant(String tenantId) { // GH-90000
            return entries.stream() // GH-90000
                    .filter(e -> e.tenantId().equals(tenantId)) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }

        List<AuditEntry> queryByAction(String action) { // GH-90000
            return entries.stream() // GH-90000
                    .filter(e -> e.action().equals(action)) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }

        List<AuditEntry> queryByTimeRange(String tenantId, Instant from, Instant to) { // GH-90000
            return entries.stream() // GH-90000
                    .filter(e -> e.tenantId().equals(tenantId)) // GH-90000
                    .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to)) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
        }

        int pruneOlderThan(Instant cutoff) { // GH-90000
            List<AuditEntry> toRemove = entries.stream() // GH-90000
                    .filter(e -> e.timestamp().isBefore(cutoff)) // GH-90000
                    .collect(Collectors.toList()); // GH-90000
            toRemove.forEach(entries::remove); // GH-90000
            return toRemove.size(); // GH-90000
        }

        String exportAsJson(String tenantId) { // GH-90000
            StringBuilder sb = new StringBuilder("[");
            queryByTenant(tenantId).forEach(e -> { // GH-90000
                sb.append("{\"entryId\":\"").append(e.entryId()).append("\"") // GH-90000
                  .append(",\"tenantId\":\"").append(e.tenantId()).append("\"") // GH-90000
                  .append(",\"action\":\"").append(e.action()).append("\"") // GH-90000
                  .append(",\"resourceType\":\"").append(e.resourceType()).append("\"") // GH-90000
                  .append(",\"resourceId\":\"").append(e.resourceId()).append("\"") // GH-90000
                  .append(",\"success\":").append(e.success()) // GH-90000
                  .append("},");
            });
            if (!queryByTenant(tenantId).isEmpty()) { // GH-90000
                sb.setLength(sb.length() - 1); // GH-90000
            }
            sb.append("]");
            return sb.toString(); // GH-90000
        }
    }
}
