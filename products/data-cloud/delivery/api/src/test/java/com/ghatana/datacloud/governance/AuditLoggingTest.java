/*
 * Copyright (c) 2026 Ghatana Inc. 
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

    record AuditEntry( 
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
    void setUp() { 
        auditLog = new InMemoryAuditLog(); 
    }

    // ── Audit log creation ────────────────────────────────────────────────────

    @Test
    @DisplayName("log returns an entry with all required fields populated")
    void logCreatesEntryWithAllRequiredFields() { 
        AuditEntry entry = auditLog.log( 
                "tenant-audit", "user-001",
                "data.purge", "Collection", "col-xyz", true, "Purge executed successfully");

        assertThat(entry.entryId()).isNotNull().isNotBlank(); 
        assertThat(entry.timestamp()).isNotNull().isBefore(Instant.now().plusSeconds(1)); 
        assertThat(entry.tenantId()).isEqualTo("tenant-audit");
        assertThat(entry.userId()).isEqualTo("user-001");
        assertThat(entry.action()).isEqualTo("data.purge");
        assertThat(entry.resourceType()).isEqualTo("Collection");
        assertThat(entry.resourceId()).isEqualTo("col-xyz");
        assertThat(entry.success()).isTrue(); 
        assertThat(entry.details()).isEqualTo("Purge executed successfully");
    }

    @Test
    @DisplayName("log assigns unique entry IDs to each audit event")
    void logAssignsUniqueEntryIds() { 
        AuditEntry a = auditLog.log("tenant-a", "u1", "read", "Dataset", "ds-1", true, null); 
        AuditEntry b = auditLog.log("tenant-a", "u1", "read", "Dataset", "ds-2", true, null); 

        assertThat(a.entryId()).isNotEqualTo(b.entryId()); 
    }

    @Test
    @DisplayName("failed operations are logged with success=false")
    void failedOperationsLoggedWithSuccessFalse() { 
        AuditEntry entry = auditLog.log( 
                "tenant-fail", "user-002",
                "data.delete", "Feature", "feature-1", false, "Insufficient permissions");

        assertThat(entry.success()).isFalse(); 
        assertThat(entry.details()).contains("Insufficient permissions");
    }

    // ── Audit log querying ────────────────────────────────────────────────────

    @Test
    @DisplayName("queryByTenant returns only entries for the specified tenant")
    void queryByTenantReturnsOnlyMatchingEntries() { 
        auditLog.log("tenant-Q", "u1", "read", "X", "r1", true, null); 
        auditLog.log("tenant-Q", "u2", "write", "X", "r2", true, null); 
        auditLog.log("tenant-other", "u3", "read", "X", "r3", true, null); 

        List<AuditEntry> results = auditLog.queryByTenant("tenant-Q");

        assertThat(results).hasSize(2); 
        assertThat(results).allMatch(e -> e.tenantId().equals("tenant-Q"));
    }

    @Test
    @DisplayName("queryByAction returns only entries matching the specified action")
    void queryByActionReturnsMatchingEntries() { 
        auditLog.log("tenant-R", "u1", "data.purge", "Col", "c1", true, null); 
        auditLog.log("tenant-R", "u2", "data.read", "Col", "c2", true, null); 
        auditLog.log("tenant-R", "u3", "data.purge", "Col", "c3", true, null); 

        List<AuditEntry> results = auditLog.queryByAction("data.purge");

        assertThat(results).hasSize(2); 
        assertThat(results).allMatch(e -> e.action().equals("data.purge"));
    }

    @Test
    @DisplayName("queryByTimeRange returns only entries within the specified window")
    void queryByTimeRangeReturnsBoundedEntries() throws InterruptedException { 
        Instant before = Instant.now().minusSeconds(1); 
        auditLog.log("tenant-T", "u1", "write", "X", "x1", true, null); 
        Thread.sleep(10); 
        Instant after = Instant.now(); 

        List<AuditEntry> results = auditLog.queryByTimeRange("tenant-T", before, after); 

        assertThat(results).isNotEmpty(); 
        assertThat(results).allMatch(e -> 
                !e.timestamp().isBefore(before) && !e.timestamp().isAfter(after)); 
    }

    @Test
    @DisplayName("queryByTenant returns empty list when no entries exist for that tenant")
    void queryByTenantReturnsEmptyWhenNoEntries() { 
        List<AuditEntry> results = auditLog.queryByTenant("tenant-ghost");
        assertThat(results).isEmpty(); 
    }

    // ── Retention ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("pruneOlderThan removes entries before the cutoff timestamp")
    void pruneOlderThanRemovesOldEntries() throws InterruptedException { 
        auditLog.log("tenant-P", "u1", "write", "X", "x1", true, null); 
        Thread.sleep(20); 
        Instant cutoff = Instant.now(); 
        Thread.sleep(5); 
        auditLog.log("tenant-P", "u2", "write", "X", "x2", true, null); 

        int removed = auditLog.pruneOlderThan(cutoff); 

        assertThat(removed).isEqualTo(1); 
        List<AuditEntry> remaining = auditLog.queryByTenant("tenant-P");
        assertThat(remaining).hasSize(1); 
        assertThat(remaining.get(0).userId()).isEqualTo("u2");
    }

    @Test
    @DisplayName("pruneOlderThan returns 0 when all entries are newer than cutoff")
    void pruneOlderThanReturnZeroWhenAllNewer() { 
        auditLog.log("tenant-P2", "u1", "read", "X", "x1", true, null); 

        int removed = auditLog.pruneOlderThan(Instant.EPOCH); 
        assertThat(removed).isEqualTo(0); 
    }

    // ── Audit log export ──────────────────────────────────────────────────────

    @Test
    @DisplayName("export produces JSON-like string containing required fields")
    void exportProducesStructuredOutput() { 
        auditLog.log("tenant-E", "u1", "data.export", "Dataset", "ds-export", true, "Export succeeded"); 

        String exported = auditLog.exportAsJson("tenant-E");

        assertThat(exported).contains("tenant-E");
        assertThat(exported).contains("data.export");
        assertThat(exported).contains("Dataset");
        assertThat(exported).contains("ds-export");
    }

    // ── In-memory audit log implementation (for tests) ──────────────────────── 

    static class InMemoryAuditLog {
        private final Deque<AuditEntry> entries = new ConcurrentLinkedDeque<>(); 

        AuditEntry log(String tenantId, String userId, String action, 
                       String resourceType, String resourceId,
                       boolean success, String details) {
            AuditEntry entry = new AuditEntry( 
                    UUID.randomUUID().toString(), 
                    Instant.now(), 
                    tenantId, userId, action,
                    resourceType, resourceId,
                    success, details);
            entries.addFirst(entry); 
            return entry;
        }

        List<AuditEntry> queryByTenant(String tenantId) { 
            return entries.stream() 
                    .filter(e -> e.tenantId().equals(tenantId)) 
                    .collect(Collectors.toList()); 
        }

        List<AuditEntry> queryByAction(String action) { 
            return entries.stream() 
                    .filter(e -> e.action().equals(action)) 
                    .collect(Collectors.toList()); 
        }

        List<AuditEntry> queryByTimeRange(String tenantId, Instant from, Instant to) { 
            return entries.stream() 
                    .filter(e -> e.tenantId().equals(tenantId)) 
                    .filter(e -> !e.timestamp().isBefore(from) && !e.timestamp().isAfter(to)) 
                    .collect(Collectors.toList()); 
        }

        int pruneOlderThan(Instant cutoff) { 
            List<AuditEntry> toRemove = entries.stream() 
                    .filter(e -> e.timestamp().isBefore(cutoff)) 
                    .collect(Collectors.toList()); 
            toRemove.forEach(entries::remove); 
            return toRemove.size(); 
        }

        String exportAsJson(String tenantId) { 
            StringBuilder sb = new StringBuilder("[");
            queryByTenant(tenantId).forEach(e -> { 
                sb.append("{\"entryId\":\"").append(e.entryId()).append("\"") 
                  .append(",\"tenantId\":\"").append(e.tenantId()).append("\"") 
                  .append(",\"action\":\"").append(e.action()).append("\"") 
                  .append(",\"resourceType\":\"").append(e.resourceType()).append("\"") 
                  .append(",\"resourceId\":\"").append(e.resourceId()).append("\"") 
                  .append(",\"success\":").append(e.success()) 
                  .append("},");
            });
            if (!queryByTenant(tenantId).isEmpty()) { 
                sb.setLength(sb.length() - 1); 
            }
            sb.append("]");
            return sb.toString(); 
        }
    }
}
