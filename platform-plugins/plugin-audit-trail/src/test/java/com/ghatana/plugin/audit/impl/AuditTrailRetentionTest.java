package com.ghatana.plugin.audit.impl;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.plugin.audit.AuditTrailPlugin;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retention policy tests for audit trail plugins.
 * <p>
 * Verifies that {@link AuditTrailPlugin#purgeEntriesOlderThan} correctly enforces
 * time-based data retention: entries older than the cutoff are removed, newer ones
 * are preserved, and the operation is correctly scoped to matching entries.
 *
 * @doc.type class
 * @doc.purpose Retention policy contract tests for StandardAuditTrailPlugin and DurableAuditTrailPlugin
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Audit Trail Retention Policy Tests")
class AuditTrailRetentionTest extends EventloopTestBase {

    @Nested
    @DisplayName("StandardAuditTrailPlugin")
    class StandardImpl {

        private StandardAuditTrailPlugin plugin;

        @BeforeEach
        void setUp() {
            plugin = new StandardAuditTrailPlugin();
        }

        @Test
        @DisplayName("purgeEntriesOlderThan removes entries before cutoff and keeps newer ones")
        void purgesOldEntriesKeepsNew() {
            runPromise(() -> plugin.logEvent("entity-1", "CREATE", Map.of("v", "old")));
            // Record timestamp before adding the new entry
            long cutoff = Instant.now().plusMillis(10).toEpochMilli();

            try {
                Thread.sleep(20); // ensure the next event is clearly after cutoff
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            runPromise(() -> plugin.logEvent("entity-1", "UPDATE", Map.of("v", "new")));

            Integer deleted = runPromise(() -> plugin.purgeEntriesOlderThan(cutoff));
            assertThat(deleted).isEqualTo(1);

            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("entity-1"));
            assertThat(trail).hasSize(1);
            assertThat(trail.get(0).action()).isEqualTo("UPDATE");
        }

        @Test
        @DisplayName("purgeEntriesOlderThan returns 0 when no entries match cutoff")
        void purgeReturnsZeroForNoMatch() {
            runPromise(() -> plugin.logEvent("entity-1", "CREATE", Map.of()));

            // Cutoff in the past — no entries are older than this
            long cutoffInPast = Instant.now().minusSeconds(3600).toEpochMilli();

            Integer deleted = runPromise(() -> plugin.purgeEntriesOlderThan(cutoffInPast));
            assertThat(deleted).isEqualTo(0);

            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("entity-1"));
            assertThat(trail).hasSize(1);
        }

        @Test
        @DisplayName("purgeEntriesOlderThan removes all entries when cutoff is in the future")
        void purgeAllWhenCutoffInFuture() {
            runPromise(() -> plugin.logEvent("entity-1", "CREATE", Map.of()));
            runPromise(() -> plugin.logEvent("entity-1", "UPDATE", Map.of()));

            long cutoffFuture = Instant.now().plusSeconds(3600).toEpochMilli();

            Integer deleted = runPromise(() -> plugin.purgeEntriesOlderThan(cutoffFuture));
            assertThat(deleted).isEqualTo(2);

            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("entity-1"));
            assertThat(trail).isEmpty();
        }

        @Test
        @DisplayName("purgeEntriesOlderThan operates across all entity trails")
        void purgeAcrossMultipleEntities() {
            runPromise(() -> plugin.logEvent("entity-a", "CREATE", Map.of()));
            runPromise(() -> plugin.logEvent("entity-b", "CREATE", Map.of()));

            long cutoffFuture = Instant.now().plusSeconds(3600).toEpochMilli();

            Integer deleted = runPromise(() -> plugin.purgeEntriesOlderThan(cutoffFuture));
            assertThat(deleted).isEqualTo(2);

            assertThat(runPromise(() -> plugin.getTrail("entity-a"))).isEmpty();
            assertThat(runPromise(() -> plugin.getTrail("entity-b"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("DurableAuditTrailPlugin")
    class DurableImpl {

        private DurableAuditTrailPlugin plugin;

        @BeforeEach
        void setUp() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:audit_retention_test_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
            ds.setUser("sa");
            ds.setPassword("");
            plugin = new DurableAuditTrailPlugin(ds);
            plugin.ensureSchema();
        }

        @Test
        @DisplayName("purgeEntriesOlderThan removes entries before cutoff and keeps newer ones")
        void purgesOldEntriesKeepsNew() {
            runPromise(() -> plugin.logEvent("entity-1", "CREATE", Map.of("v", "old")));
            long cutoff = Instant.now().plusMillis(10).toEpochMilli();

            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            runPromise(() -> plugin.logEvent("entity-1", "UPDATE", Map.of("v", "new")));

            Integer deleted = runPromise(() -> plugin.purgeEntriesOlderThan(cutoff));
            assertThat(deleted).isEqualTo(1);

            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("entity-1"));
            assertThat(trail).hasSize(1);
            assertThat(trail.get(0).action()).isEqualTo("UPDATE");
        }

        @Test
        @DisplayName("purgeEntriesOlderThan returns 0 when no entries match cutoff")
        void purgeReturnsZeroForNoMatch() {
            runPromise(() -> plugin.logEvent("entity-1", "CREATE", Map.of()));
            long cutoffInPast = Instant.now().minusSeconds(3600).toEpochMilli();

            Integer deleted = runPromise(() -> plugin.purgeEntriesOlderThan(cutoffInPast));
            assertThat(deleted).isEqualTo(0);

            List<AuditTrailPlugin.AuditEntry> trail = runPromise(() -> plugin.getTrail("entity-1"));
            assertThat(trail).hasSize(1);
        }

        @Test
        @DisplayName("purgeEntriesOlderThan removes all entries when cutoff is in the future")
        void purgeAllWhenCutoffInFuture() {
            runPromise(() -> plugin.logEvent("entity-1", "CREATE", Map.of()));
            runPromise(() -> plugin.logEvent("entity-2", "CREATE", Map.of()));

            long cutoffFuture = Instant.now().plusSeconds(3600).toEpochMilli();

            Integer deleted = runPromise(() -> plugin.purgeEntriesOlderThan(cutoffFuture));
            assertThat(deleted).isEqualTo(2);

            assertThat(runPromise(() -> plugin.getTrail("entity-1"))).isEmpty();
            assertThat(runPromise(() -> plugin.getTrail("entity-2"))).isEmpty();
        }
    }
}
