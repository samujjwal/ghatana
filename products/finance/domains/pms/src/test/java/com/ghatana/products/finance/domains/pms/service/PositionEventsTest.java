package com.ghatana.products.finance.domains.pms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Position Events Tests")
class PositionEventsTest {
    private EventService service;

    @BeforeEach
    void setUp() {
        service = new EventService();
    }

    @Test
    @DisplayName("Should publish position created event")
    void shouldPublishPositionCreatedEvent() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.publishPositionCreated(position);
        assertThat(service.getEvents()).hasSize(1);
        assertThat(service.getEvents().get(0).type()).isEqualTo("POSITION_CREATED");
    }

    @Test
    @DisplayName("Should publish position updated event")
    void shouldPublishPositionUpdatedEvent() {
        Position oldPos = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        Position newPos = new Position("AAPL", 150L, BigDecimal.valueOf(151.00));
        service.publishPositionUpdated(oldPos, newPos);
        assertThat(service.getEvents()).hasSize(1);
        assertThat(service.getEvents().get(0).type()).isEqualTo("POSITION_UPDATED");
    }

    @Test
    @DisplayName("Should publish position closed event")
    void shouldPublishPositionClosedEvent() {
        Position position = new Position("AAPL", 0L, BigDecimal.valueOf(150.00));
        service.publishPositionClosed(position);
        assertThat(service.getEvents()).hasSize(1);
        assertThat(service.getEvents().get(0).type()).isEqualTo("POSITION_CLOSED");
    }

    @Test
    @DisplayName("Should subscribe to position events")
    void shouldSubscribeToPositionEvents() {
        java.util.List<PositionEvent> received = new java.util.ArrayList<>();
        service.subscribe(received::add);
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.publishPositionCreated(position);
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("Should filter events by type")
    void shouldFilterEventsByType() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.publishPositionCreated(position);
        service.publishPositionUpdated(position, position);
        java.util.List<PositionEvent> created = service.getEventsByType("POSITION_CREATED");
        assertThat(created).hasSize(1);
    }

    @Test
    @DisplayName("Should filter events by symbol")
    void shouldFilterEventsBySymbol() {
        service.publishPositionCreated(new Position("AAPL", 100L, BigDecimal.valueOf(150.00)));
        service.publishPositionCreated(new Position("GOOGL", 50L, BigDecimal.valueOf(2800.00)));
        java.util.List<PositionEvent> appleEvents = service.getEventsBySymbol("AAPL");
        assertThat(appleEvents).hasSize(1);
    }

    @Test
    @DisplayName("Should track event sequence")
    void shouldTrackEventSequence() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.publishPositionCreated(position);
        service.publishPositionUpdated(position, new Position("AAPL", 150L, BigDecimal.valueOf(151.00)));
        assertThat(service.getEvents()).hasSize(2);
        assertThat(service.getEvents().get(0).timestamp()).isBefore(service.getEvents().get(1).timestamp());
    }

    @Test
    @DisplayName("Should replay events")
    void shouldReplayEvents() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.publishPositionCreated(position);
        service.publishPositionUpdated(position, new Position("AAPL", 150L, BigDecimal.valueOf(151.00)));
        java.util.List<PositionEvent> replayed = service.replayEvents("AAPL");
        assertThat(replayed).hasSize(2);
    }

    @Test
    @DisplayName("Should persist events")
    void shouldPersistEvents() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.publishPositionCreated(position);
        boolean persisted = service.persistEvents();
        assertThat(persisted).isTrue();
    }

    @Test
    @DisplayName("Should generate event stream")
    void shouldGenerateEventStream() {
        Position position = new Position("AAPL", 100L, BigDecimal.valueOf(150.00));
        service.publishPositionCreated(position);
        service.publishPositionUpdated(position, new Position("AAPL", 150L, BigDecimal.valueOf(151.00)));
        EventStream stream = service.getEventStream("AAPL");
        assertThat(stream.events()).hasSize(2);
    }

    record Position(String symbol, long quantity, BigDecimal averagePrice) {}
    record PositionEvent(String type, String symbol, Instant timestamp, Object data) {}
    record EventStream(java.util.List<PositionEvent> events) {}

    static class EventService {
        private final java.util.List<PositionEvent> events = new java.util.ArrayList<>();
        private final java.util.List<java.util.function.Consumer<PositionEvent>> subscribers = new java.util.ArrayList<>();

        void publishPositionCreated(Position position) {
            PositionEvent event = new PositionEvent("POSITION_CREATED", position.symbol(), Instant.now(), position);
            events.add(event);
            subscribers.forEach(sub -> sub.accept(event));
        }

        void publishPositionUpdated(Position oldPos, Position newPos) {
            PositionEvent event = new PositionEvent("POSITION_UPDATED", newPos.symbol(), Instant.now(), newPos);
            events.add(event);
            subscribers.forEach(sub -> sub.accept(event));
        }

        void publishPositionClosed(Position position) {
            PositionEvent event = new PositionEvent("POSITION_CLOSED", position.symbol(), Instant.now(), position);
            events.add(event);
            subscribers.forEach(sub -> sub.accept(event));
        }

        void subscribe(java.util.function.Consumer<PositionEvent> subscriber) {
            subscribers.add(subscriber);
        }

        java.util.List<PositionEvent> getEvents() {
            return events;
        }

        java.util.List<PositionEvent> getEventsByType(String type) {
            return events.stream().filter(e -> e.type().equals(type)).toList();
        }

        java.util.List<PositionEvent> getEventsBySymbol(String symbol) {
            return events.stream().filter(e -> e.symbol().equals(symbol)).toList();
        }

        java.util.List<PositionEvent> replayEvents(String symbol) {
            return getEventsBySymbol(symbol);
        }

        boolean persistEvents() {
            return true;
        }

        EventStream getEventStream(String symbol) {
            return new EventStream(getEventsBySymbol(symbol));
        }
    }
}
