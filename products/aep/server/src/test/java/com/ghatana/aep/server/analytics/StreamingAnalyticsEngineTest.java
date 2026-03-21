/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.analytics;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.Event;
import com.ghatana.datacloud.DataCloudClient.Subscription;
import com.ghatana.datacloud.DataCloudClient.TailRequest;
import com.ghatana.platform.observability.MetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.ghatana.aep.server.analytics.StreamingAnalyticsEngine.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StreamingAnalyticsEngine}.
 *
 * <p>All Data-Cloud interactions are mocked with synchronous stubs so that
 * the event handler lambda can be captured and invoked inline on the test thread
 * without running an ActiveJ Eventloop.
 *
 * @doc.type class
 * @doc.purpose Unit tests for StreamingAnalyticsEngine — subscriptions, filtering, lifecycle
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StreamingAnalyticsEngine")
class StreamingAnalyticsEngineTest {

    private static final String TENANT = "tenant-stream";

    @Mock
    private DataCloudClient dataCloud;

    @Mock
    private MetricsCollector metrics;

    @Mock
    private Subscription dcSubscription;

    private StreamingAnalyticsEngine engine;

    @BeforeEach
    void setUp() {
        engine = new StreamingAnalyticsEngine(dataCloud, metrics);
        // Default: tailEvents returns our controlled stub subscription
        lenient().when(dataCloud.tailEvents(anyString(), any(TailRequest.class), any()))
                .thenReturn(dcSubscription);
        lenient().when(dcSubscription.isCancelled()).thenReturn(false);
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        @DisplayName("null dataCloud throws NullPointerException")
        void nullDataCloud_throwsNpe() {
            assertThatThrownBy(() -> new StreamingAnalyticsEngine(null, metrics))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("dataCloud");
        }

        @Test
        @DisplayName("null metrics throws NullPointerException")
        void nullMetrics_throwsNpe() {
            assertThatThrownBy(() -> new StreamingAnalyticsEngine(dataCloud, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("metrics");
        }

        @Test
        @DisplayName("getActiveSubscriptions returns empty on fresh engine")
        void freshEngine_noActiveSubscriptions() {
            assertThat(engine.getActiveSubscriptions()).isEmpty();
        }
    }

    // =========================================================================
    // subscribeToAnomalies
    // =========================================================================

    @Nested
    @DisplayName("subscribeToAnomalies()")
    class SubscribeToAnomaliesTests {

        @Test
        @DisplayName("creates subscription and returns non-null ID")
        void subscribe_returnsNonNullId() {
            AnomalyFilter filter = AnomalyFilter.allFor(TENANT);
            String id = engine.subscribeToAnomalies(filter, anomaly -> {});

            assertThat(id).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("calls tailEvents on DataCloudClient with LATEST offset")
        void subscribe_callsTailEvents() {
            AnomalyFilter filter = AnomalyFilter.allFor(TENANT);
            engine.subscribeToAnomalies(filter, anomaly -> {});

            verify(dataCloud).tailEvents(eq(TENANT), any(TailRequest.class), any());
        }

        @Test
        @DisplayName("increments streaming.subscriptions.active counter for ANOMALY")
        void subscribe_incrementsCounter() {
            AnomalyFilter filter = AnomalyFilter.allFor(TENANT);
            engine.subscribeToAnomalies(filter, anomaly -> {});

            verify(metrics).incrementCounter("streaming.subscriptions.active", "type", "ANOMALY");
        }

        @Test
        @DisplayName("subscription appears in getActiveSubscriptions")
        void subscribe_appearsInActiveList() {
            AnomalyFilter filter = AnomalyFilter.allFor(TENANT);
            String id = engine.subscribeToAnomalies(filter, anomaly -> {});

            List<SubscriptionInfo> active = engine.getActiveSubscriptions();
            assertThat(active).hasSize(1);
            assertThat(active.get(0).id()).isEqualTo(id);
            assertThat(active.get(0).tenantId()).isEqualTo(TENANT);
            assertThat(active.get(0).type()).isEqualTo("ANOMALY");
            assertThat(active.get(0).createdAt()).isNotNull();
        }

        @Test
        @DisplayName("null filter throws NullPointerException")
        void nullFilter_throwsNpe() {
            assertThatThrownBy(() -> engine.subscribeToAnomalies(null, a -> {}))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null handler throws NullPointerException")
        void nullHandler_throwsNpe() {
            assertThatThrownBy(() -> engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("event with matching type is parsed and delivered to handler")
        void subscribe_matchingAnomalyEvent_deliveredToHandler() {
            // Capture the event handler installed on DataCloud
            ArgumentCaptor<Consumer<Event>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
            AnomalyFilter filter = AnomalyFilter.allFor(TENANT);

            List<DataCloudAnalyticsStore.AnomalyRecord> received = new ArrayList<>();
            engine.subscribeToAnomalies(filter, received::add);

            verify(dataCloud).tailEvents(eq(TENANT), any(), handlerCaptor.capture());

            // Simulate DataCloud delivering an anomaly event
            Event event = Event.of(EVENT_TYPE_ANOMALY, Map.of(
                    "id", "anomaly-1",
                    "anomalyType", "FREQUENCY_SPIKE",
                    "severity", "HIGH",
                    "score", 0.92,
                    "description", "Frequency spike detected"
            ));
            handlerCaptor.getValue().accept(event);

            assertThat(received).hasSize(1);
            assertThat(received.get(0).id()).isEqualTo("anomaly-1");
            assertThat(received.get(0).anomalyType()).isEqualTo("FREQUENCY_SPIKE");
            assertThat(received.get(0).severity()).isEqualTo("HIGH");
            assertThat(received.get(0).score()).isEqualTo(0.92);
        }

        @Test
        @DisplayName("event with wrong type is silently ignored")
        void subscribe_wrongTypeEvent_ignored() {
            ArgumentCaptor<Consumer<Event>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
            engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> fail("should not be called"));

            verify(dataCloud).tailEvents(any(), any(), handlerCaptor.capture());

            // Wrong event type — should not hit the handler
            Event kpiEvent = Event.of(EVENT_TYPE_KPI, Map.of("kpiName", "cpu.usage", "value", 0.5));
            handlerCaptor.getValue().accept(kpiEvent);
            // No assertion failure means the handler was not called — test passes
        }

        @Test
        @DisplayName("score filter drops events below minimum score")
        void scoreFilter_dropsLowScoreEvents() {
            ArgumentCaptor<Consumer<Event>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
            AnomalyFilter filter = AnomalyFilter.of(TENANT, null, 0.8); // minimum score 0.8

            List<DataCloudAnalyticsStore.AnomalyRecord> received = new ArrayList<>();
            engine.subscribeToAnomalies(filter, received::add);
            verify(dataCloud).tailEvents(any(), any(), handlerCaptor.capture());

            // Score 0.5 — below threshold
            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_ANOMALY, Map.of(
                    "severity", "HIGH", "score", 0.5, "anomalyType", "T", "description", "D")));
            // Score 0.9 — above threshold
            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_ANOMALY, Map.of(
                    "severity", "HIGH", "score", 0.9, "anomalyType", "T2", "description", "D2")));

            assertThat(received).hasSize(1);
            assertThat(received.get(0).anomalyType()).isEqualTo("T2");
        }

        @Test
        @DisplayName("severity filter drops events below minimum severity")
        void severityFilter_dropsLowSeverityEvents() {
            ArgumentCaptor<Consumer<Event>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
            AnomalyFilter filter = AnomalyFilter.of(TENANT, "HIGH", 0.0); // minimum severity HIGH

            List<DataCloudAnalyticsStore.AnomalyRecord> received = new ArrayList<>();
            engine.subscribeToAnomalies(filter, received::add);
            verify(dataCloud).tailEvents(any(), any(), handlerCaptor.capture());

            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_ANOMALY, Map.of(
                    "severity", "LOW", "score", 0.9, "anomalyType", "T1", "description", "D")));
            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_ANOMALY, Map.of(
                    "severity", "CRITICAL", "score", 0.9, "anomalyType", "T2", "description", "D")));

            assertThat(received).hasSize(1);
            assertThat(received.get(0).severity()).isEqualTo("CRITICAL");
        }

        @Test
        @DisplayName("multiple subscribers receive independent event streams")
        void multipleSubscribers_receiveIndependentEvents() {
            engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {});
            engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {});

            assertThat(engine.getActiveSubscriptions()).hasSize(2);
            verify(dataCloud, times(2)).tailEvents(any(), any(), any());
        }
    }

    // =========================================================================
    // subscribeToKPIs
    // =========================================================================

    @Nested
    @DisplayName("subscribeToKPIs()")
    class SubscribeToKpisTests {

        @Test
        @DisplayName("creates subscription and returns non-null ID")
        void subscribe_returnsNonNullId() {
            String id = engine.subscribeToKPIs(KpiFilter.allFor(TENANT), snap -> {});

            assertThat(id).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("calls tailEvents on DataCloudClient")
        void subscribe_callsTailEvents() {
            engine.subscribeToKPIs(KpiFilter.allFor(TENANT), snap -> {});

            verify(dataCloud).tailEvents(eq(TENANT), any(TailRequest.class), any());
        }

        @Test
        @DisplayName("increments streaming.subscriptions.active counter for KPI")
        void subscribe_incrementsCounter() {
            engine.subscribeToKPIs(KpiFilter.allFor(TENANT), snap -> {});

            verify(metrics).incrementCounter("streaming.subscriptions.active", "type", "KPI");
        }

        @Test
        @DisplayName("subscription appears in getActiveSubscriptions as KPI type")
        void subscribe_appearsInActiveListAsKPI() {
            String id = engine.subscribeToKPIs(KpiFilter.allFor(TENANT), snap -> {});

            List<SubscriptionInfo> active = engine.getActiveSubscriptions();
            assertThat(active).hasSize(1);
            assertThat(active.get(0).id()).isEqualTo(id);
            assertThat(active.get(0).type()).isEqualTo("KPI");
        }

        @Test
        @DisplayName("KPI event is parsed and forwarded to handler")
        void kpiEvent_parsedAndDelivered() {
            ArgumentCaptor<Consumer<Event>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
            List<DataCloudAnalyticsStore.KpiSnapshot> received = new ArrayList<>();
            engine.subscribeToKPIs(KpiFilter.allFor(TENANT), received::add);

            verify(dataCloud).tailEvents(eq(TENANT), any(), handlerCaptor.capture());

            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_KPI, Map.of(
                    "id", "kpi-1",
                    "kpiName", "event.throughput",
                    "value", 1500.0,
                    "unit", "events/s"
            )));

            assertThat(received).hasSize(1);
            assertThat(received.get(0).kpiName()).isEqualTo("event.throughput");
            assertThat(received.get(0).value()).isEqualTo(1500.0);
            assertThat(received.get(0).unit()).isEqualTo("events/s");
        }

        @Test
        @DisplayName("kpiName filter drops events for other KPIs")
        void kpiNameFilter_dropsNonMatchingKpis() {
            ArgumentCaptor<Consumer<Event>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
            KpiFilter filter = KpiFilter.forKpi(TENANT, "cpu.usage");
            List<DataCloudAnalyticsStore.KpiSnapshot> received = new ArrayList<>();
            engine.subscribeToKPIs(filter, received::add);

            verify(dataCloud).tailEvents(any(), any(), handlerCaptor.capture());

            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_KPI, Map.of(
                    "kpiName", "memory.usage", "value", 0.7, "unit", "%")));
            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_KPI, Map.of(
                    "kpiName", "cpu.usage", "value", 0.4, "unit", "%")));

            assertThat(received).hasSize(1);
            assertThat(received.get(0).kpiName()).isEqualTo("cpu.usage");
        }

        @Test
        @DisplayName("anomaly event is ignored by KPI subscriber")
        void anomalyEvent_ignoredByKpiSubscriber() {
            ArgumentCaptor<Consumer<Event>> handlerCaptor = ArgumentCaptor.forClass(Consumer.class);
            engine.subscribeToKPIs(KpiFilter.allFor(TENANT), s -> fail("should not be called"));

            verify(dataCloud).tailEvents(any(), any(), handlerCaptor.capture());
            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_ANOMALY, Map.of("severity", "HIGH")));
        }
    }

    // =========================================================================
    // unsubscribe
    // =========================================================================

    @Nested
    @DisplayName("unsubscribe()")
    class UnsubscribeTests {

        @Test
        @DisplayName("valid subscription → cancels DataCloud subscription and removes from active list")
        void validSubscription_cancelledAndRemoved() {
            String id = engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {});

            engine.unsubscribe(id);

            verify(dcSubscription).cancel();
            assertThat(engine.getActiveSubscriptions()).isEmpty();
        }

        @Test
        @DisplayName("unknown subscription ID → no-op (no exception)")
        void unknownId_noOp() {
            assertThatCode(() -> engine.unsubscribe("nonexistent-id"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null subscription ID throws NullPointerException")
        void nullId_throwsNpe() {
            assertThatThrownBy(() -> engine.unsubscribe(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("unsubscribing one does not affect others")
        void unsubscribeOne_othersRemain() {
            String id1 = engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {});
            String id2 = engine.subscribeToKPIs(KpiFilter.allFor(TENANT), k -> {});

            engine.unsubscribe(id1);

            assertThat(engine.getActiveSubscriptions()).hasSize(1);
            assertThat(engine.getActiveSubscriptions().get(0).id()).isEqualTo(id2);
        }
    }

    // =========================================================================
    // close()
    // =========================================================================

    @Nested
    @DisplayName("close()")
    class CloseTests {

        @Test
        @DisplayName("cancels all active subscriptions")
        void close_cancelsAll() {
            Subscription sub1 = mock(Subscription.class);
            Subscription sub2 = mock(Subscription.class);
            when(dataCloud.tailEvents(any(), any(), any()))
                    .thenReturn(sub1).thenReturn(sub2);

            engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {});
            engine.subscribeToKPIs(KpiFilter.allFor(TENANT), k -> {});

            engine.close();

            verify(sub1).cancel();
            verify(sub2).cancel();
            assertThat(engine.getActiveSubscriptions()).isEmpty();
        }

        @Test
        @DisplayName("safe to call close() on fresh engine (no subscriptions)")
        void closeFreshEngine_noException() {
            assertThatCode(() -> engine.close()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("safe to call close() multiple times")
        void closeMultipleTimes_noException() {
            engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {});
            engine.close();
            assertThatCode(() -> engine.close()).doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // AnomalyFilter
    // =========================================================================

    @Nested
    @DisplayName("AnomalyFilter")
    class AnomalyFilterTests {

        @Test
        @DisplayName("allFor creates filter accepting all severities and scores")
        void allFor_acceptsAll() {
            AnomalyFilter f = AnomalyFilter.allFor(TENANT);
            assertThat(f.minimumSeverity()).isNull();
            assertThat(f.minimumScore()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException")
        void nullTenant_throwsNpe() {
            assertThatThrownBy(() -> AnomalyFilter.allFor(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("minimumScore outside [0,1] throws IllegalArgumentException")
        void invalidScore_throwsIae() {
            assertThatThrownBy(() -> AnomalyFilter.of(TENANT, null, 1.5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minimumScore");
        }

        @Test
        @DisplayName("matches returns false for null record")
        void nullRecord_returnsFalse() {
            assertThat(AnomalyFilter.allFor(TENANT).matches(null)).isFalse();
        }

        @Test
        @DisplayName("severity matching: CRITICAL passes HIGH filter")
        void criticalPassesHighFilter() {
            AnomalyFilter f = AnomalyFilter.of(TENANT, "HIGH", 0.0);
            DataCloudAnalyticsStore.AnomalyRecord critical = DataCloudAnalyticsStore.AnomalyRecord.of(
                    "T", "CRITICAL", 0.9, "D");
            assertThat(f.matches(critical)).isTrue();
        }

        @Test
        @DisplayName("severity matching: LOW blocked by HIGH filter")
        void lowBlockedByHighFilter() {
            AnomalyFilter f = AnomalyFilter.of(TENANT, "HIGH", 0.0);
            DataCloudAnalyticsStore.AnomalyRecord low = DataCloudAnalyticsStore.AnomalyRecord.of(
                    "T", "LOW", 0.9, "D");
            assertThat(f.matches(low)).isFalse();
        }
    }

    // =========================================================================
    // KpiFilter
    // =========================================================================

    @Nested
    @DisplayName("KpiFilter")
    class KpiFilterTests {

        @Test
        @DisplayName("allFor accepts all KPIs")
        void allFor_acceptsAll() {
            KpiFilter f = KpiFilter.allFor(TENANT);
            DataCloudAnalyticsStore.KpiSnapshot snap =
                    DataCloudAnalyticsStore.KpiSnapshot.of("any.kpi", 1.0, "unit");
            assertThat(f.matches(snap)).isTrue();
        }

        @Test
        @DisplayName("forKpi filter accepts matching KPI name")
        void forKpi_acceptsMatchingName() {
            KpiFilter f = KpiFilter.forKpi(TENANT, "cpu.usage");
            DataCloudAnalyticsStore.KpiSnapshot snap =
                    DataCloudAnalyticsStore.KpiSnapshot.of("cpu.usage", 0.75, "%");
            assertThat(f.matches(snap)).isTrue();
        }

        @Test
        @DisplayName("forKpi filter rejects non-matching KPI name")
        void forKpi_rejectsNonMatchingName() {
            KpiFilter f = KpiFilter.forKpi(TENANT, "cpu.usage");
            DataCloudAnalyticsStore.KpiSnapshot snap =
                    DataCloudAnalyticsStore.KpiSnapshot.of("memory.usage", 0.5, "%");
            assertThat(f.matches(snap)).isFalse();
        }

        @Test
        @DisplayName("matches returns false for null snapshot")
        void nullSnapshot_returnsFalse() {
            assertThat(KpiFilter.allFor(TENANT).matches(null)).isFalse();
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException")
        void nullTenant_throwsNpe() {
            assertThatThrownBy(() -> KpiFilter.allFor(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
