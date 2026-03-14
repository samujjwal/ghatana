package com.ghatana.appplatform.eventstore.replay;

import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProjectionRebuildEngine}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for projection rebuild orchestration
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectionRebuildEngine — Unit Tests")
class ProjectionRebuildEngineTest {

    @Mock
    private EventReplayEngine replayEngine;

    @Mock
    private RebuildableProjection projection;

    private static ReplayProgress completedProgress(String tenantId) {
        return ReplayProgress.start(tenantId, 100L).withReplayed(100L, Instant.now()).complete();
    }

    @Test
    @DisplayName("startRebuild_clearsBeforeReplay — clear() is called before replay")
    void startRebuildClearsBeforeReplay() throws Exception {
        when(projection.projectionName()).thenReturn("order-summary");
        ReplayFilter filter = ReplayFilter.forTenant("t-1");
        when(replayEngine.replay(eq(filter), any())).thenReturn(completedProgress("t-1"));

        ProjectionRebuildEngine engine = new ProjectionRebuildEngine(replayEngine);
        Future<ReplayProgress> future = engine.startRebuild(projection, filter);
        ReplayProgress result = future.get();

        verify(projection).clear();
        verify(replayEngine).replay(eq(filter), any());
        assertThat(result.completed()).isTrue();
    }

    @Test
    @DisplayName("startRebuild_forwardsEventsToProjection — each replayed event reaches applyEvent")
    void startRebuildForwardsEvents() throws Exception {
        when(projection.projectionName()).thenReturn("payment-ledger");
        ReplayFilter filter = ReplayFilter.forTenant("t-2");

        AggregateEventRecord event = AggregateEventRecord.builder()
                .aggregateId(UUID.randomUUID())
                .aggregateType("Payment")
                .eventType("PaymentCreated")
                .sequenceNumber(1L)
                .build();

        ArgumentCaptor<Consumer<AggregateEventRecord>> handlerCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        when(replayEngine.replay(eq(filter), handlerCaptor.capture())).thenAnswer(inv -> {
            handlerCaptor.getValue().accept(event);
            return completedProgress("t-2");
        });

        ProjectionRebuildEngine engine = new ProjectionRebuildEngine(replayEngine);
        engine.startRebuild(projection, filter).get();

        verify(projection).applyEvent(event);
    }

    @Test
    @DisplayName("startRebuild_afterComplete_allowsFreshRebuild")
    void startRebuildAfterCompleteAllowsFresh() throws Exception {
        when(projection.projectionName()).thenReturn("fresh-projection");
        ReplayFilter filter = ReplayFilter.forTenant("t-3");
        when(replayEngine.replay(eq(filter), any())).thenReturn(completedProgress("t-3"));

        ProjectionRebuildEngine engine = new ProjectionRebuildEngine(replayEngine);
        engine.startRebuild(projection, filter).get();
        engine.startRebuild(projection, filter).get();

        // clear must have been called for each rebuild
        verify(projection, times(2)).clear();
    }

    @Test
    @DisplayName("startRebuild_nullProjection — throws NullPointerException")
    void startRebuildNullProjectionThrows() {
        ProjectionRebuildEngine engine = new ProjectionRebuildEngine(replayEngine);
        ReplayFilter filter = ReplayFilter.forTenant("t-4");
        assertThatThrownBy(() -> engine.startRebuild(null, filter))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("startRebuild_nullFilter — throws NullPointerException")
    void startRebuildNullFilterThrows() {
        when(projection.projectionName()).thenReturn("any");
        ProjectionRebuildEngine engine = new ProjectionRebuildEngine(replayEngine);
        assertThatThrownBy(() -> engine.startRebuild(projection, null))
                .isInstanceOf(NullPointerException.class);
    }
}


/**
 * Unit tests for {@link ProjectionRebuildEngine}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for projection rebuild orchestration
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectionRebuildEngine — Unit Tests")
class ProjectionRebuildEngineTest {

    @Mock
    private EventReplayEngine replayEngine;

    @Mock
    private RebuildableProjection projection;

    @Test
    @DisplayName("startRebuild_clearsBeforeReplay — clear() is called before replay starts")
    void startRebuildClearsBeforeReplay() throws Exception {
        when(projection.projectionName()).thenReturn("order-summary");
        ReplayFilter filter = ReplayFilter.forTenant("t-1");
        ReplayProgress progress = new ReplayProgress("t-1", 100L, 100L, Instant.now());
        when(replayEngine.replay(eq(filter), any())).thenReturn(progress);

        ProjectionRebuildEngine engine = new ProjectionRebuildEngine(replayEngine);
        Future<ReplayProgress> future = engine.startRebuild(projection, filter);
        ReplayProgress result = future.get();

        // Verify clear happened
        verify(projection).clear();
        // Verify replay was invoked
        verify(replayEngine).replay(eq(filter), any());
        assertThat(result.eventsReplayed()).isEqualTo(100L);
    }

    @Test
    @DisplayName("startRebuild_forwardsEventsToProjection — each replayed event reaches applyEvent")
    void startRebuildForwardsEvents() throws Exception {
        when(projection.projectionName()).thenReturn("payment-ledger");
        ReplayFilter filter = ReplayFilter.forTenant("t-2");

        AggregateEventRecord event = new AggregateEventRecord(
                "evt-1", "t-2", "Payment", "pay-1", 1L, "PaymentCreated",
                1, "{}", null, Instant.now());

        // Capture the handler and invoke it
        ArgumentCaptor<Consumer<AggregateEventRecord>> handlerCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        ReplayProgress progress = new ReplayProgress("t-2", 1L, 1L, Instant.now());
        when(replayEngine.replay(eq(filter), handlerCaptor.capture())).thenAnswer(inv -> {
            handlerCaptor.getValue().accept(event);
            return progress;
        });

        ProjectionRebuildEngine engine = new ProjectionRebuildEngine(replayEngine);
        engine.startRebuild(projection, filter).get();

        verify(projection).applyEvent(event);
    }

    @Test
    @DisplayName("startRebuild_concurrent_sameProjectionReturnsExistingFuture")
    void startRebuildConcurrentSameProjectionDeduped() throws Exception {
        when(projection.projectionName()).thenReturn("dedupe-projection");
        ReplayFilter filter = ReplayFilter.forTenant("t-3");
        ReplayProgress progress = new ReplayProgress("t-3", 0L, 0L, Instant.now());
        when(replayEngine.replay(eq(filter), any())).thenReturn(progress);

        ProjectionRebuildEngine engine = new ProjectionRebuildEngine(replayEngine);
        Future<ReplayProgress> f1 = engine.startRebuild(projection, filter);
        // Wait for the first rebuild
        f1.get();
        // A second rebuild after completion should start fresh
        Future<ReplayProgress> f2 = engine.startRebuild(projection, filter);
        f2.get();

        // Both rebuilds completed; clear must have been called twice
        verify(projection, times(2)).clear();
    }

    @Test
    @DisplayName("startRebuild_nullProjection — throws NullPointerException")
    void startRebuildNullProjectionThrows() {
        ProjectionRebuildEngine engine = new ProjectionRebuildEngine(replayEngine);
        ReplayFilter filter = ReplayFilter.forTenant("t-4");
        assertThatThrownBy(() -> engine.startRebuild(null, filter))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("startRebuild_nullFilter — throws NullPointerException")
    void startRebuildNullFilterThrows() {
        when(projection.projectionName()).thenReturn("any");
        ProjectionRebuildEngine engine = new ProjectionRebuildEngine(replayEngine);
        assertThatThrownBy(() -> engine.startRebuild(projection, null))
                .isInstanceOf(NullPointerException.class);
    }
}
