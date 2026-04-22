/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("StreamingAnalyticsEngine [GH-90000]")
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
    void setUp() { // GH-90000
        engine = new StreamingAnalyticsEngine(dataCloud, metrics); // GH-90000
        // Default: tailEvents returns our controlled stub subscription
        lenient().when(dataCloud.tailEvents(anyString(), any(TailRequest.class), any())) // GH-90000
                .thenReturn(dcSubscription); // GH-90000
        lenient().when(dcSubscription.isCancelled()).thenReturn(false); // GH-90000
    }

    // =========================================================================
    // Construction
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class ConstructionTests {

        @Test
        @DisplayName("null dataCloud throws NullPointerException [GH-90000]")
        void nullDataCloud_throwsNpe() { // GH-90000
            assertThatThrownBy(() -> new StreamingAnalyticsEngine(null, metrics)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("dataCloud [GH-90000]");
        }

        @Test
        @DisplayName("null metrics throws NullPointerException [GH-90000]")
        void nullMetrics_throwsNpe() { // GH-90000
            assertThatThrownBy(() -> new StreamingAnalyticsEngine(dataCloud, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("metrics [GH-90000]");
        }

        @Test
        @DisplayName("getActiveSubscriptions returns empty on fresh engine [GH-90000]")
        void freshEngine_noActiveSubscriptions() { // GH-90000
            assertThat(engine.getActiveSubscriptions()).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // subscribeToAnomalies
    // =========================================================================

    @Nested
    @DisplayName("subscribeToAnomalies() [GH-90000]")
    class SubscribeToAnomaliesTests {

        @Test
        @DisplayName("creates subscription and returns non-null ID [GH-90000]")
        void subscribe_returnsNonNullId() { // GH-90000
            AnomalyFilter filter = AnomalyFilter.allFor(TENANT); // GH-90000
            String id = engine.subscribeToAnomalies(filter, anomaly -> {}); // GH-90000

            assertThat(id).isNotNull().isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("calls tailEvents on DataCloudClient with LATEST offset [GH-90000]")
        void subscribe_callsTailEvents() { // GH-90000
            AnomalyFilter filter = AnomalyFilter.allFor(TENANT); // GH-90000
            engine.subscribeToAnomalies(filter, anomaly -> {}); // GH-90000

            verify(dataCloud).tailEvents(eq(TENANT), any(TailRequest.class), any()); // GH-90000
        }

        @Test
        @DisplayName("increments streaming.subscriptions.active counter for ANOMALY [GH-90000]")
        void subscribe_incrementsCounter() { // GH-90000
            AnomalyFilter filter = AnomalyFilter.allFor(TENANT); // GH-90000
            engine.subscribeToAnomalies(filter, anomaly -> {}); // GH-90000

            verify(metrics).incrementCounter("streaming.subscriptions.active", "type", "ANOMALY"); // GH-90000
        }

        @Test
        @DisplayName("subscription appears in getActiveSubscriptions [GH-90000]")
        void subscribe_appearsInActiveList() { // GH-90000
            AnomalyFilter filter = AnomalyFilter.allFor(TENANT); // GH-90000
            String id = engine.subscribeToAnomalies(filter, anomaly -> {}); // GH-90000

            List<SubscriptionInfo> active = engine.getActiveSubscriptions(); // GH-90000
            assertThat(active).hasSize(1); // GH-90000
            assertThat(active.get(0).id()).isEqualTo(id); // GH-90000
            assertThat(active.get(0).tenantId()).isEqualTo(TENANT); // GH-90000
            assertThat(active.get(0).type()).isEqualTo("ANOMALY [GH-90000]");
            assertThat(active.get(0).createdAt()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("null filter throws NullPointerException [GH-90000]")
        void nullFilter_throwsNpe() { // GH-90000
            assertThatThrownBy(() -> engine.subscribeToAnomalies(null, a -> {})) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null handler throws NullPointerException [GH-90000]")
        void nullHandler_throwsNpe() { // GH-90000
            assertThatThrownBy(() -> engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("event with matching type is parsed and delivered to handler [GH-90000]")
        void subscribe_matchingAnomalyEvent_deliveredToHandler() { // GH-90000
            // Capture the event handler installed on DataCloud
            ArgumentCaptor<Consumer<Event>> handlerCaptor = eventConsumerCaptor(); // GH-90000
            AnomalyFilter filter = AnomalyFilter.allFor(TENANT); // GH-90000

            List<DataCloudAnalyticsStore.AnomalyRecord> received = new ArrayList<>(); // GH-90000
            engine.subscribeToAnomalies(filter, received::add); // GH-90000

            verify(dataCloud).tailEvents(eq(TENANT), any(), handlerCaptor.capture()); // GH-90000

            // Simulate DataCloud delivering an anomaly event
            Event event = Event.of(EVENT_TYPE_ANOMALY, Map.of( // GH-90000
                    "id", "anomaly-1",
                    "anomalyType", "FREQUENCY_SPIKE",
                    "severity", "HIGH",
                    "score", 0.92,
                    "description", "Frequency spike detected"
            ));
            handlerCaptor.getValue().accept(event); // GH-90000

            assertThat(received).hasSize(1); // GH-90000
            assertThat(received.get(0).id()).isEqualTo("anomaly-1 [GH-90000]");
            assertThat(received.get(0).anomalyType()).isEqualTo("FREQUENCY_SPIKE [GH-90000]");
            assertThat(received.get(0).severity()).isEqualTo("HIGH [GH-90000]");
            assertThat(received.get(0).score()).isEqualTo(0.92); // GH-90000
        }

        @Test
        @DisplayName("event with wrong type is silently ignored [GH-90000]")
        void subscribe_wrongTypeEvent_ignored() { // GH-90000
            ArgumentCaptor<Consumer<Event>> handlerCaptor = eventConsumerCaptor(); // GH-90000
            engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> fail("should not be called [GH-90000]"));

            verify(dataCloud).tailEvents(any(), any(), handlerCaptor.capture()); // GH-90000

            // Wrong event type — should not hit the handler
            Event kpiEvent = Event.of(EVENT_TYPE_KPI, Map.of("kpiName", "cpu.usage", "value", 0.5)); // GH-90000
            handlerCaptor.getValue().accept(kpiEvent); // GH-90000
            // No assertion failure means the handler was not called — test passes
        }

        @Test
        @DisplayName("score filter drops events below minimum score [GH-90000]")
        void scoreFilter_dropsLowScoreEvents() { // GH-90000
            ArgumentCaptor<Consumer<Event>> handlerCaptor = eventConsumerCaptor(); // GH-90000
            AnomalyFilter filter = AnomalyFilter.of(TENANT, null, 0.8); // minimum score 0.8 // GH-90000

            List<DataCloudAnalyticsStore.AnomalyRecord> received = new ArrayList<>(); // GH-90000
            engine.subscribeToAnomalies(filter, received::add); // GH-90000
            verify(dataCloud).tailEvents(any(), any(), handlerCaptor.capture()); // GH-90000

            // Score 0.5 — below threshold
            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_ANOMALY, Map.of( // GH-90000
                    "severity", "HIGH", "score", 0.5, "anomalyType", "T", "description", "D")));
            // Score 0.9 — above threshold
            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_ANOMALY, Map.of( // GH-90000
                    "severity", "HIGH", "score", 0.9, "anomalyType", "T2", "description", "D2")));

            assertThat(received).hasSize(1); // GH-90000
            assertThat(received.get(0).anomalyType()).isEqualTo("T2 [GH-90000]");
        }

        @Test
        @DisplayName("severity filter drops events below minimum severity [GH-90000]")
        void severityFilter_dropsLowSeverityEvents() { // GH-90000
            ArgumentCaptor<Consumer<Event>> handlerCaptor = eventConsumerCaptor(); // GH-90000
            AnomalyFilter filter = AnomalyFilter.of(TENANT, "HIGH", 0.0); // minimum severity HIGH // GH-90000

            List<DataCloudAnalyticsStore.AnomalyRecord> received = new ArrayList<>(); // GH-90000
            engine.subscribeToAnomalies(filter, received::add); // GH-90000
            verify(dataCloud).tailEvents(any(), any(), handlerCaptor.capture()); // GH-90000

            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_ANOMALY, Map.of( // GH-90000
                    "severity", "LOW", "score", 0.9, "anomalyType", "T1", "description", "D")));
            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_ANOMALY, Map.of( // GH-90000
                    "severity", "CRITICAL", "score", 0.9, "anomalyType", "T2", "description", "D")));

            assertThat(received).hasSize(1); // GH-90000
            assertThat(received.get(0).severity()).isEqualTo("CRITICAL [GH-90000]");
        }

        @Test
        @DisplayName("multiple subscribers receive independent event streams [GH-90000]")
        void multipleSubscribers_receiveIndependentEvents() { // GH-90000
            engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {}); // GH-90000
            engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {}); // GH-90000

            assertThat(engine.getActiveSubscriptions()).hasSize(2); // GH-90000
            verify(dataCloud, times(2)).tailEvents(any(), any(), any()); // GH-90000
        }
    }

    // =========================================================================
    // subscribeToKPIs
    // =========================================================================

    @Nested
    @DisplayName("subscribeToKPIs() [GH-90000]")
    class SubscribeToKpisTests {

        @Test
        @DisplayName("creates subscription and returns non-null ID [GH-90000]")
        void subscribe_returnsNonNullId() { // GH-90000
            String id = engine.subscribeToKPIs(KpiFilter.allFor(TENANT), snap -> {}); // GH-90000

            assertThat(id).isNotNull().isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("calls tailEvents on DataCloudClient [GH-90000]")
        void subscribe_callsTailEvents() { // GH-90000
            engine.subscribeToKPIs(KpiFilter.allFor(TENANT), snap -> {}); // GH-90000

            verify(dataCloud).tailEvents(eq(TENANT), any(TailRequest.class), any()); // GH-90000
        }

        @Test
        @DisplayName("increments streaming.subscriptions.active counter for KPI [GH-90000]")
        void subscribe_incrementsCounter() { // GH-90000
            engine.subscribeToKPIs(KpiFilter.allFor(TENANT), snap -> {}); // GH-90000

            verify(metrics).incrementCounter("streaming.subscriptions.active", "type", "KPI"); // GH-90000
        }

        @Test
        @DisplayName("subscription appears in getActiveSubscriptions as KPI type [GH-90000]")
        void subscribe_appearsInActiveListAsKPI() { // GH-90000
            String id = engine.subscribeToKPIs(KpiFilter.allFor(TENANT), snap -> {}); // GH-90000

            List<SubscriptionInfo> active = engine.getActiveSubscriptions(); // GH-90000
            assertThat(active).hasSize(1); // GH-90000
            assertThat(active.get(0).id()).isEqualTo(id); // GH-90000
            assertThat(active.get(0).type()).isEqualTo("KPI [GH-90000]");
        }

        @Test
        @DisplayName("KPI event is parsed and forwarded to handler [GH-90000]")
        void kpiEvent_parsedAndDelivered() { // GH-90000
            ArgumentCaptor<Consumer<Event>> handlerCaptor = eventConsumerCaptor(); // GH-90000
            List<DataCloudAnalyticsStore.KpiSnapshot> received = new ArrayList<>(); // GH-90000
            engine.subscribeToKPIs(KpiFilter.allFor(TENANT), received::add); // GH-90000

            verify(dataCloud).tailEvents(eq(TENANT), any(), handlerCaptor.capture()); // GH-90000

            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_KPI, Map.of( // GH-90000
                    "id", "kpi-1",
                    "kpiName", "event.throughput",
                    "value", 1500.0,
                    "unit", "events/s"
            )));

            assertThat(received).hasSize(1); // GH-90000
            assertThat(received.get(0).kpiName()).isEqualTo("event.throughput [GH-90000]");
            assertThat(received.get(0).value()).isEqualTo(1500.0); // GH-90000
            assertThat(received.get(0).unit()).isEqualTo("events/s [GH-90000]");
        }

        @Test
        @DisplayName("kpiName filter drops events for other KPIs [GH-90000]")
        void kpiNameFilter_dropsNonMatchingKpis() { // GH-90000
            ArgumentCaptor<Consumer<Event>> handlerCaptor = eventConsumerCaptor(); // GH-90000
            KpiFilter filter = KpiFilter.forKpi(TENANT, "cpu.usage"); // GH-90000
            List<DataCloudAnalyticsStore.KpiSnapshot> received = new ArrayList<>(); // GH-90000
            engine.subscribeToKPIs(filter, received::add); // GH-90000

            verify(dataCloud).tailEvents(any(), any(), handlerCaptor.capture()); // GH-90000

            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_KPI, Map.of( // GH-90000
                    "kpiName", "memory.usage", "value", 0.7, "unit", "%")));
            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_KPI, Map.of( // GH-90000
                    "kpiName", "cpu.usage", "value", 0.4, "unit", "%")));

            assertThat(received).hasSize(1); // GH-90000
            assertThat(received.get(0).kpiName()).isEqualTo("cpu.usage [GH-90000]");
        }

        @Test
        @DisplayName("anomaly event is ignored by KPI subscriber [GH-90000]")
        void anomalyEvent_ignoredByKpiSubscriber() { // GH-90000
            ArgumentCaptor<Consumer<Event>> handlerCaptor = eventConsumerCaptor(); // GH-90000
            engine.subscribeToKPIs(KpiFilter.allFor(TENANT), s -> fail("should not be called [GH-90000]"));

            verify(dataCloud).tailEvents(any(), any(), handlerCaptor.capture()); // GH-90000
            handlerCaptor.getValue().accept(Event.of(EVENT_TYPE_ANOMALY, Map.of("severity", "HIGH"))); // GH-90000
        }
    }

    // =========================================================================
    // unsubscribe
    // =========================================================================

    @Nested
    @DisplayName("unsubscribe() [GH-90000]")
    class UnsubscribeTests {

        @Test
        @DisplayName("valid subscription → cancels DataCloud subscription and removes from active list [GH-90000]")
        void validSubscription_cancelledAndRemoved() { // GH-90000
            String id = engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {}); // GH-90000

            engine.unsubscribe(id); // GH-90000

            verify(dcSubscription).cancel(); // GH-90000
            assertThat(engine.getActiveSubscriptions()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("unknown subscription ID → no-op (no exception) [GH-90000]")
        void unknownId_noOp() { // GH-90000
            assertThatCode(() -> engine.unsubscribe("nonexistent-id [GH-90000]"))
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("null subscription ID throws NullPointerException [GH-90000]")
        void nullId_throwsNpe() { // GH-90000
            assertThatThrownBy(() -> engine.unsubscribe(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("unsubscribing one does not affect others [GH-90000]")
        void unsubscribeOne_othersRemain() { // GH-90000
            String id1 = engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {}); // GH-90000
            String id2 = engine.subscribeToKPIs(KpiFilter.allFor(TENANT), k -> {}); // GH-90000

            engine.unsubscribe(id1); // GH-90000

            assertThat(engine.getActiveSubscriptions()).hasSize(1); // GH-90000
            assertThat(engine.getActiveSubscriptions().get(0).id()).isEqualTo(id2); // GH-90000
        }
    }

    // =========================================================================
    // close() // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("close() [GH-90000]")
    class CloseTests {

        @Test
        @DisplayName("cancels all active subscriptions [GH-90000]")
        void close_cancelsAll() { // GH-90000
            Subscription sub1 = mock(Subscription.class); // GH-90000
            Subscription sub2 = mock(Subscription.class); // GH-90000
            when(dataCloud.tailEvents(any(), any(), any())) // GH-90000
                    .thenReturn(sub1).thenReturn(sub2); // GH-90000

            engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {}); // GH-90000
            engine.subscribeToKPIs(KpiFilter.allFor(TENANT), k -> {}); // GH-90000

            engine.close(); // GH-90000

            verify(sub1).cancel(); // GH-90000
            verify(sub2).cancel(); // GH-90000
            assertThat(engine.getActiveSubscriptions()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("safe to call close() on fresh engine (no subscriptions) [GH-90000]")
        void closeFreshEngine_noException() { // GH-90000
            assertThatCode(() -> engine.close()).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("safe to call close() multiple times [GH-90000]")
        void closeMultipleTimes_noException() { // GH-90000
            engine.subscribeToAnomalies(AnomalyFilter.allFor(TENANT), a -> {}); // GH-90000
            engine.close(); // GH-90000
            assertThatCode(() -> engine.close()).doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // AnomalyFilter
    // =========================================================================

    @Nested
    @DisplayName("AnomalyFilter [GH-90000]")
    class AnomalyFilterTests {

        @Test
        @DisplayName("allFor creates filter accepting all severities and scores [GH-90000]")
        void allFor_acceptsAll() { // GH-90000
            AnomalyFilter f = AnomalyFilter.allFor(TENANT); // GH-90000
            assertThat(f.minimumSeverity()).isNull(); // GH-90000
            assertThat(f.minimumScore()).isEqualTo(0.0); // GH-90000
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException [GH-90000]")
        void nullTenant_throwsNpe() { // GH-90000
            assertThatThrownBy(() -> AnomalyFilter.allFor(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("minimumScore outside [0,1] throws IllegalArgumentException [GH-90000]")
        void invalidScore_throwsIae() { // GH-90000
            assertThatThrownBy(() -> AnomalyFilter.of(TENANT, null, 1.5)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("minimumScore [GH-90000]");
        }

        @Test
        @DisplayName("matches returns false for null record [GH-90000]")
        void nullRecord_returnsFalse() { // GH-90000
            assertThat(AnomalyFilter.allFor(TENANT).matches(null)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("severity matching: CRITICAL passes HIGH filter [GH-90000]")
        void criticalPassesHighFilter() { // GH-90000
            AnomalyFilter f = AnomalyFilter.of(TENANT, "HIGH", 0.0); // GH-90000
            DataCloudAnalyticsStore.AnomalyRecord critical = DataCloudAnalyticsStore.AnomalyRecord.of( // GH-90000
                    "T", "CRITICAL", 0.9, "D");
            assertThat(f.matches(critical)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("severity matching: LOW blocked by HIGH filter [GH-90000]")
        void lowBlockedByHighFilter() { // GH-90000
            AnomalyFilter f = AnomalyFilter.of(TENANT, "HIGH", 0.0); // GH-90000
            DataCloudAnalyticsStore.AnomalyRecord low = DataCloudAnalyticsStore.AnomalyRecord.of( // GH-90000
                    "T", "LOW", 0.9, "D");
            assertThat(f.matches(low)).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // KpiFilter
    // =========================================================================

    @Nested
    @DisplayName("KpiFilter [GH-90000]")
    class KpiFilterTests {

        @Test
        @DisplayName("allFor accepts all KPIs [GH-90000]")
        void allFor_acceptsAll() { // GH-90000
            KpiFilter f = KpiFilter.allFor(TENANT); // GH-90000
            DataCloudAnalyticsStore.KpiSnapshot snap =
                    DataCloudAnalyticsStore.KpiSnapshot.of("any.kpi", 1.0, "unit"); // GH-90000
            assertThat(f.matches(snap)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("forKpi filter accepts matching KPI name [GH-90000]")
        void forKpi_acceptsMatchingName() { // GH-90000
            KpiFilter f = KpiFilter.forKpi(TENANT, "cpu.usage"); // GH-90000
            DataCloudAnalyticsStore.KpiSnapshot snap =
                    DataCloudAnalyticsStore.KpiSnapshot.of("cpu.usage", 0.75, "%"); // GH-90000
            assertThat(f.matches(snap)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("forKpi filter rejects non-matching KPI name [GH-90000]")
        void forKpi_rejectsNonMatchingName() { // GH-90000
            KpiFilter f = KpiFilter.forKpi(TENANT, "cpu.usage"); // GH-90000
            DataCloudAnalyticsStore.KpiSnapshot snap =
                    DataCloudAnalyticsStore.KpiSnapshot.of("memory.usage", 0.5, "%"); // GH-90000
            assertThat(f.matches(snap)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("matches returns false for null snapshot [GH-90000]")
        void nullSnapshot_returnsFalse() { // GH-90000
            assertThat(KpiFilter.allFor(TENANT).matches(null)).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("null tenantId throws NullPointerException [GH-90000]")
        void nullTenant_throwsNpe() { // GH-90000
            assertThatThrownBy(() -> KpiFilter.allFor(null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // GH-90000
    private static ArgumentCaptor<Consumer<Event>> eventConsumerCaptor() { // GH-90000
        return (ArgumentCaptor) ArgumentCaptor.forClass(Consumer.class); // GH-90000
    }
}
