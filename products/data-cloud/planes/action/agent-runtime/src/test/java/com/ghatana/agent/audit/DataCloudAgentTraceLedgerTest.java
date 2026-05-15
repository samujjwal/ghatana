/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import com.ghatana.datacloud.spi.EventLogStoreAdapters;
import com.ghatana.datacloud.spi.provider.InMemoryEventLogStoreProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the Data Cloud-backed agent trace ledger.
 *
 * @doc.type class
 * @doc.purpose Verify governed agent traces persist through Data Cloud EventLogStore
 * @doc.layer product
 * @doc.pattern TestSuite
 */
@DisplayName("DataCloudAgentTraceLedger")
class DataCloudAgentTraceLedgerTest extends EventloopTestBase {

    private DataCloudAgentTraceLedger ledger;

    @Override
    protected Duration eventloopTimeout() {
        return Duration.ofSeconds(15);
    }

    @BeforeEach
    void setUp() {
        ledger = new DataCloudAgentTraceLedger(
                EventLogStoreAdapters.toPlatformStore(new InMemoryEventLogStoreProvider()));
    }

    @Test
    @DisplayName("persists and retrieves trace events through Data Cloud EventLogStore")
    void persistsAndRetrievesTraceEventsThroughDataCloud() {
        TraceEventBuilder builder = new TraceEventBuilder("trace-1", "agent-1", "tenant-1", ledger.getLastHash("tenant-1"));
        TraceEvent started = builder.build(TraceEventType.TURN_STARTED, "Turn started", Map.of("phase", "plan"));
        runPromise(() -> ledger.append(started));

        TraceEventBuilder nextBuilder = new TraceEventBuilder("trace-1", "agent-1", "tenant-1", ledger.getLastHash("tenant-1"));
        TraceEvent allowed = nextBuilder.build(TraceEventType.DISPATCH_ALLOWED, "Governance allowed dispatch");
        runPromise(() -> ledger.append(allowed));

        List<TraceEvent> byTrace = runPromise(() -> ledger.getByTrace("trace-1", "tenant-1"));
        List<TraceEvent> byAgent = runPromise(() -> ledger.getByAgent("agent-1", "tenant-1", null, null, 10));
        List<TraceEvent> allowedEvents = runPromise(
                () -> ledger.getByType(TraceEventType.DISPATCH_ALLOWED, "tenant-1", null, null, 10));

        assertThat(byTrace).extracting(TraceEvent::eventType)
                .containsExactly(TraceEventType.TURN_STARTED, TraceEventType.DISPATCH_ALLOWED);
        assertThat(byAgent).hasSize(2);
        assertThat(allowedEvents).singleElement().satisfies(event -> {
            assertThat(event.summary()).isEqualTo("Governance allowed dispatch");
            assertThat(event.previousHash()).isEqualTo(started.eventHash());
        });
        assertThat(ledger.verifyChain(byAgent)).isTrue();
        assertThat(ledger.getLastHash("tenant-1")).isEqualTo(allowed.eventHash());
    }

    @Test
    @DisplayName("enforces tenant isolation and event limits")
    void enforcesTenantIsolationAndLimits() {
        TraceEventBuilder tenantOne = new TraceEventBuilder("trace-1", "agent-1", "tenant-1", "");
        TraceEventBuilder tenantTwo = new TraceEventBuilder("trace-2", "agent-1", "tenant-2", "");
        runPromise(() -> ledger.append(tenantOne.build(TraceEventType.ACTION_EXECUTED, "Tenant one action")));
        runPromise(() -> ledger.append(tenantTwo.build(TraceEventType.ACTION_EXECUTED, "Tenant two action")));

        List<TraceEvent> tenantOneEvents = runPromise(
                () -> ledger.getByAgent("agent-1", "tenant-1", null, null, 1));
        List<TraceEvent> tenantTwoEvents = runPromise(
                () -> ledger.getByAgent("agent-1", "tenant-2", null, null, 10));

        assertThat(tenantOneEvents).singleElement()
                .satisfies(event -> assertThat(event.tenantId()).isEqualTo("tenant-1"));
        assertThat(tenantTwoEvents).singleElement()
                .satisfies(event -> assertThat(event.tenantId()).isEqualTo("tenant-2"));
    }

    @Test
    @DisplayName("applies time filtering for agent queries")
    void appliesTimeFilteringForAgentQueries() {
        Instant before = Instant.now().minusSeconds(5);
        TraceEventBuilder builder = new TraceEventBuilder("trace-1", "agent-1", "tenant-1", "");
        runPromise(() -> ledger.append(builder.build(TraceEventType.ACTION_EXECUTED, "Action")));

        List<TraceEvent> futureOnly = runPromise(
                () -> ledger.getByAgent("agent-1", "tenant-1", Instant.now().plusSeconds(5), null, 10));
        List<TraceEvent> fromPast = runPromise(
                () -> ledger.getByAgent("agent-1", "tenant-1", before, null, 10));

        assertThat(futureOnly).isEmpty();
        assertThat(fromPast).hasSize(1);
    }

    @Test
    @DisplayName("rejects broken hash chains before persisting")
    void rejectsBrokenHashChainsBeforePersisting() {
        TraceEventBuilder builder = new TraceEventBuilder("trace-1", "agent-1", "tenant-1", "wrong-hash");
        TraceEvent event = builder.build(TraceEventType.ACTION_DENIED, "Denied");

        assertThatThrownBy(() -> runPromise(() -> ledger.append(event)))
                .hasMessageContaining("Hash chain broken");

        List<TraceEvent> events = runPromise(() -> ledger.getByTrace("trace-1", "tenant-1"));
        assertThat(events).isEmpty();
    }
}
