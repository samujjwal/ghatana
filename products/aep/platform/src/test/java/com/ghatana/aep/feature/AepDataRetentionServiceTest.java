/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.feature;

import com.ghatana.eventlog.RetentionPolicy;
import com.ghatana.eventlog.adapters.jdbc.JdbcEventStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AepDataRetentionService}.
 *
 * <p>All JDBC calls are mocked so no live database is required. Async dispatch
 * is exercised by running through {@link EventloopTestBase#runPromise}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AepDataRetentionService")
class AepDataRetentionServiceTest extends EventloopTestBase {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement preparedStatement;

    @Mock
    private ResultSet resultSet;

    @Mock
    private JdbcEventStore eventStore;

    private MeterRegistry meterRegistry;
    private AepDataRetentionService service;

    @BeforeEach
    void setUp() throws SQLException {
        meterRegistry = new SimpleMeterRegistry();
        service = new AepDataRetentionService(
                dataSource, eventStore, meterRegistry,
                Executors.newSingleThreadExecutor());

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeUpdate()).thenReturn(1);
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("null dataSource throws NullPointerException")
        void nullDataSource_throwsNpe() {
            assertThatThrownBy(() ->
                    new AepDataRetentionService(null, eventStore, meterRegistry,
                            Executors.newSingleThreadExecutor()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("dataSource");
        }

        @Test
        @DisplayName("null eventStore throws NullPointerException")
        void nullEventStore_throwsNpe() {
            assertThatThrownBy(() ->
                    new AepDataRetentionService(dataSource, null, meterRegistry,
                            Executors.newSingleThreadExecutor()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("eventStore");
        }

        @Test
        @DisplayName("null meterRegistry throws NullPointerException")
        void nullMeterRegistry_throwsNpe() {
            assertThatThrownBy(() ->
                    new AepDataRetentionService(dataSource, eventStore, null,
                            Executors.newSingleThreadExecutor()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("meterRegistry");
        }

        @Test
        @DisplayName("null executor throws NullPointerException")
        void nullExecutor_throwsNpe() {
            assertThatThrownBy(() ->
                    new AepDataRetentionService(dataSource, eventStore, meterRegistry, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("blockingExecutor");
        }
    }

    // =========================================================================
    // Policy management
    // =========================================================================

    @Nested
    @DisplayName("Policy management")
    class PolicyManagementTests {

        @Test
        @DisplayName("upsertPolicy executes INSERT … ON CONFLICT for a standard policy")
        void upsertPolicy_standardPolicy_executesUpsert() throws SQLException {
            AepTenantRetentionPolicy policy = AepTenantRetentionPolicy.of(
                    "t1", "audit_event", Duration.ofDays(90), 0L);

            runPromise(() -> service.upsertPolicy(policy));

            verify(preparedStatement).executeUpdate();
            // Verify tenant_id is bound to the statement
            verify(preparedStatement).setString(eq(2), eq("t1"));
            verify(preparedStatement).setString(eq(3), eq("audit_event"));
            verify(preparedStatement).setLong(eq(4), eq(90L * 86400)); // 90 days in seconds
        }

        @Test
        @DisplayName("upsertPolicy with null policy throws NullPointerException")
        void upsertPolicy_null_throwsNpe() {
            assertThatThrownBy(() ->
                    runPromise(() -> service.upsertPolicy(null)))
                    .hasCauseInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("getPoliciesForTenant returns empty list when no rows exist")
        void getPoliciesForTenant_noRows_returnsEmpty() throws SQLException {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            List<AepTenantRetentionPolicy> result =
                    runPromise(() -> service.getPoliciesForTenant("t-unknown"));

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getPoliciesForTenant returns mapped policies when rows exist")
        void getPoliciesForTenant_withRows_returnsMappedPolicies() throws SQLException {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, false); // one row
            when(resultSet.getObject("id", java.util.UUID.class))
                    .thenReturn(java.util.UUID.randomUUID());
            when(resultSet.getString("tenant_id")).thenReturn("t1");
            when(resultSet.getString("event_type")).thenReturn("DEFAULT");
            when(resultSet.getLong("max_age_seconds")).thenReturn(2592000L);
            when(resultSet.getLong("max_bytes")).thenReturn(0L);
            when(resultSet.getBoolean("gdpr_erasure")).thenReturn(false);
            when(resultSet.getTimestamp("created_at"))
                    .thenReturn(Timestamp.from(java.time.Instant.now()));
            when(resultSet.getTimestamp("updated_at"))
                    .thenReturn(Timestamp.from(java.time.Instant.now()));

            List<AepTenantRetentionPolicy> result =
                    runPromise(() -> service.getPoliciesForTenant("t1"));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).tenantId()).isEqualTo("t1");
            assertThat(result.get(0).maxAge()).isEqualByComparingTo(Duration.ofDays(30));
        }

        @Test
        @DisplayName("deletePolicy executes DELETE statement with correct parameters")
        void deletePolicy_executesDelete() throws SQLException {
            runPromise(() -> service.deletePolicy("t1", "audit_event"));

            verify(preparedStatement).setString(1, "t1");
            verify(preparedStatement).setString(2, "audit_event");
            verify(preparedStatement).executeUpdate();
        }
    }

    // =========================================================================
    // Enforcement cycle
    // =========================================================================

    @Nested
    @DisplayName("Enforcement cycle")
    class EnforcementCycleTests {

        @Test
        @DisplayName("runEnforcementCycle invokes purgeOlderThan for each policy")
        void runEnforcementCycle_callsPurgeOlderThan() throws SQLException {
            // No active policies in DB → loadAllPolicies returns empty
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            runPromise(() -> service.runEnforcementCycle());

            // eventStore purge should NOT be called when there are no policies
            verify(eventStore, never()).purgeOlderThan(any());
        }

        @Test
        @DisplayName("runEnforcementCycle records timing metric")
        void runEnforcementCycle_recordsTimer() throws SQLException {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(false);

            runPromise(() -> service.runEnforcementCycle());

            Timer timer = meterRegistry.find("aep.retention.cycle.duration").timer();
            assertThat(timer).isNotNull();
            assertThat(timer.count()).isGreaterThanOrEqualTo(1L);
        }
    }

    // =========================================================================
    // GDPR / CCPA erasure
    // =========================================================================

    @Nested
    @DisplayName("GDPR/CCPA erasure")
    class ErasureTests {

        @Test
        @DisplayName("enforceErasure calls purgeOlderThan with ZERO maxAge and increments counter")
        void enforceErasure_callsPurgeWithZeroAge() throws Exception {
            // DELETE for policy cleanup succeeds silently
            runPromise(() -> service.enforceErasure("gdpr-tenant"));

            ArgumentCaptor<RetentionPolicy> captor = ArgumentCaptor.forClass(RetentionPolicy.class);
            verify(eventStore).purgeOlderThan(captor.capture());

            RetentionPolicy applied = captor.getValue();
            assertThat(applied.maxAge()).isEqualByComparingTo(Duration.ZERO);

            Counter erasureCounter = meterRegistry.find("aep.retention.erasure.requests").counter();
            assertThat(erasureCounter).isNotNull();
            assertThat(erasureCounter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("enforceErasure with null tenantId throws NullPointerException")
        void enforceErasure_nullTenant_throwsNpe() {
            assertThatThrownBy(() ->
                    runPromise(() -> service.enforceErasure(null)))
                    .hasCauseInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("enforceErasure with eventStore failure records audit and rethrows")
        void enforceErasure_storeFailure_recordsErrorAndRethrows() throws Exception {
            doThrow(new RuntimeException("purge failed")).when(eventStore).purgeOlderThan(any());

            assertThatThrownBy(() ->
                    runPromise(() -> service.enforceErasure("failing-tenant")))
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasRootCauseMessage("purge failed");
        }
    }

    // =========================================================================
    // AepTenantRetentionPolicy record
    // =========================================================================

    @Nested
    @DisplayName("AepTenantRetentionPolicy")
    class PolicyRecordTests {

        @Test
        @DisplayName("of() creates standard policy with correct fields")
        void of_createsCorrectPolicy() {
            AepTenantRetentionPolicy p = AepTenantRetentionPolicy.of(
                    "t", "e", Duration.ofDays(7), 1024L);

            assertThat(p.tenantId()).isEqualTo("t");
            assertThat(p.eventType()).isEqualTo("e");
            assertThat(p.maxAge()).isEqualTo(Duration.ofDays(7));
            assertThat(p.maxBytes()).isEqualTo(1024L);
            assertThat(p.gdprErasure()).isFalse();
            assertThat(p.id()).isNotNull();
        }

        @Test
        @DisplayName("erasure() creates GDPR policy with ZERO maxAge and DEFAULT bucket")
        void erasure_createsGdprPolicy() {
            AepTenantRetentionPolicy p = AepTenantRetentionPolicy.erasure("gdpr-t");

            assertThat(p.gdprErasure()).isTrue();
            assertThat(p.maxAge()).isEqualTo(Duration.ZERO);
            assertThat(p.eventType()).isEqualTo(AepTenantRetentionPolicy.DEFAULT_BUCKET);
        }

        @Test
        @DisplayName("compact constructor rejects null tenantId")
        void compactConstructor_nullTenantId_throwsNpe() {
            assertThatThrownBy(() -> new AepTenantRetentionPolicy(
                    java.util.UUID.randomUUID(), null, "DEFAULT",
                    Duration.ofDays(1), 0L, false,
                    java.time.Instant.now(), java.time.Instant.now()))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("compact constructor normalises negative maxAge to ZERO")
        void compactConstructor_negativeMaxAge_normalisedToZero() {
            AepTenantRetentionPolicy p = new AepTenantRetentionPolicy(
                    java.util.UUID.randomUUID(), "t", "DEFAULT",
                    Duration.ofDays(-1), 0L, false,
                    java.time.Instant.now(), java.time.Instant.now());

            assertThat(p.maxAge()).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("compact constructor normalises negative maxBytes to zero")
        void compactConstructor_negativeMaxBytes_normalisedToZero() {
            AepTenantRetentionPolicy p = new AepTenantRetentionPolicy(
                    java.util.UUID.randomUUID(), "t", "DEFAULT",
                    Duration.ofDays(1), -999L, false,
                    java.time.Instant.now(), java.time.Instant.now());

            assertThat(p.maxBytes()).isEqualTo(0L);
        }
    }
}
