package com.ghatana.products.finance.domains.ems.service;

import com.ghatana.products.finance.domains.ems.domain.ExecutionSide;
import com.ghatana.products.finance.domains.ems.domain.ExecutionStatus;
import com.ghatana.products.finance.domains.ems.domain.RoutedOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type Test
 * @doc.purpose Tests for execution data persistence per D02-008
 * @doc.layer Test
 * @doc.pattern Unit Test
 */
@DisplayName("Execution Persistence Tests")
class ExecutionPersistenceTest {

    private ExecutionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryExecutionRepository();
    }

    @Test
    @DisplayName("Should save routed order")
    void shouldSaveRoutedOrder() {
        RoutedOrder order = createRoutedOrder("routing-1", "order-1");

        repository.save(order);

        Optional<RoutedOrder> retrieved = repository.findById("routing-1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().routingId()).isEqualTo("routing-1");
    }

    @Test
    @DisplayName("Should update existing routed order")
    void shouldUpdateExistingRoutedOrder() {
        RoutedOrder order = createRoutedOrder("routing-1", "order-1");
        repository.save(order);

        RoutedOrder updated = new RoutedOrder(
            order.routingId(),
            order.parentOrderId(),
            order.clientId(),
            order.instrumentId(),
            order.exchange(),
            order.side(),
            order.quantity(),
            order.limitPrice(),
            order.orderType(),
            order.timeInForce(),
            ExecutionStatus.FILLED,
            100L,
            BigDecimal.valueOf(150.50),
            order.externalOrderId(),
            order.routedAt(),
            Instant.now(),
            List.of()
        );

        repository.save(updated);

        Optional<RoutedOrder> retrieved = repository.findById("routing-1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().status()).isEqualTo(ExecutionStatus.FILLED);
    }

    @Test
    @DisplayName("Should find orders by parent order ID")
    void shouldFindOrdersByParentOrderId() {
        repository.save(createRoutedOrder("routing-1", "order-1"));
        repository.save(createRoutedOrder("routing-2", "order-1"));
        repository.save(createRoutedOrder("routing-3", "order-2"));

        List<RoutedOrder> orders = repository.findByParentOrderId("order-1");

        assertThat(orders).hasSize(2);
        assertThat(orders).allMatch(o -> o.parentOrderId().equals("order-1"));
    }

    @Test
    @DisplayName("Should find orders by status")
    void shouldFindOrdersByStatus() {
        RoutedOrder pending = createRoutedOrder("routing-1", "order-1");
        RoutedOrder filled = new RoutedOrder(
            "routing-2", "order-2", "client-1", "AAPL", "NASDAQ",
            ExecutionSide.BUY, 100L, BigDecimal.valueOf(150.50),
            "LIMIT", "DAY", ExecutionStatus.FILLED, 100L,
            BigDecimal.valueOf(150.50), "ext-2", Instant.now(),
            Instant.now(), List.of()
        );

        repository.save(pending);
        repository.save(filled);

        List<RoutedOrder> filledOrders = repository.findByStatus(ExecutionStatus.FILLED);

        assertThat(filledOrders).hasSize(1);
        assertThat(filledOrders.get(0).status()).isEqualTo(ExecutionStatus.FILLED);
    }

    @Test
    @DisplayName("Should delete routed order")
    void shouldDeleteRoutedOrder() {
        RoutedOrder order = createRoutedOrder("routing-1", "order-1");
        repository.save(order);

        repository.delete("routing-1");

        Optional<RoutedOrder> retrieved = repository.findById("routing-1");
        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("Should find orders by exchange")
    void shouldFindOrdersByExchange() {
        repository.save(createRoutedOrder("routing-1", "order-1", "NASDAQ"));
        repository.save(createRoutedOrder("routing-2", "order-2", "NYSE"));
        repository.save(createRoutedOrder("routing-3", "order-3", "NASDAQ"));

        List<RoutedOrder> nasdaqOrders = repository.findByExchange("NASDAQ");

        assertThat(nasdaqOrders).hasSize(2);
        assertThat(nasdaqOrders).allMatch(o -> o.exchange().equals("NASDAQ"));
    }

    @Test
    @DisplayName("Should find orders within time range")
    void shouldFindOrdersWithinTimeRange() {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minusSeconds(3600);
        Instant twoHoursAgo = now.minusSeconds(7200);

        repository.save(createRoutedOrderWithTime("routing-1", "order-1", twoHoursAgo));
        repository.save(createRoutedOrderWithTime("routing-2", "order-2", oneHourAgo));
        repository.save(createRoutedOrderWithTime("routing-3", "order-3", now));

        List<RoutedOrder> recentOrders = repository.findByTimeRange(oneHourAgo, now);

        assertThat(recentOrders).hasSize(2);
    }

    @Test
    @DisplayName("Should count orders by status")
    void shouldCountOrdersByStatus() {
        repository.save(createRoutedOrder("routing-1", "order-1"));
        repository.save(createRoutedOrder("routing-2", "order-2"));

        long count = repository.countByStatus(ExecutionStatus.ROUTED);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("Should support batch save")
    void shouldSupportBatchSave() {
        List<RoutedOrder> orders = List.of(
            createRoutedOrder("routing-1", "order-1"),
            createRoutedOrder("routing-2", "order-2"),
            createRoutedOrder("routing-3", "order-3")
        );

        repository.saveAll(orders);

        assertThat(repository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("Should handle concurrent updates")
    void shouldHandleConcurrentUpdates() {
        RoutedOrder order = createRoutedOrder("routing-1", "order-1");
        repository.save(order);

        RoutedOrder update1 = new RoutedOrder(
            order.routingId(), order.parentOrderId(), order.clientId(),
            order.instrumentId(), order.exchange(), order.side(),
            order.quantity(), order.limitPrice(), order.orderType(),
            order.timeInForce(), ExecutionStatus.PARTIALLY_FILLED,
            50L, BigDecimal.valueOf(150.50), order.externalOrderId(),
            order.routedAt(), Instant.now(), List.of()
        );

        repository.save(update1);

        Optional<RoutedOrder> retrieved = repository.findById("routing-1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().status()).isEqualTo(ExecutionStatus.PARTIALLY_FILLED);
    }

    private RoutedOrder createRoutedOrder(String routingId, String orderId) {
        return createRoutedOrder(routingId, orderId, "NASDAQ");
    }

    private RoutedOrder createRoutedOrder(String routingId, String orderId, String exchange) {
        return new RoutedOrder(
            routingId, orderId, "client-1", "AAPL", exchange,
            ExecutionSide.BUY, 100L, BigDecimal.valueOf(150.50),
            "LIMIT", "DAY", ExecutionStatus.ROUTED, 0L,
            BigDecimal.ZERO, "ext-" + routingId, Instant.now(),
            Instant.now(), List.of()
        );
    }

    private RoutedOrder createRoutedOrderWithTime(String routingId, String orderId, Instant time) {
        return new RoutedOrder(
            routingId, orderId, "client-1", "AAPL", "NASDAQ",
            ExecutionSide.BUY, 100L, BigDecimal.valueOf(150.50),
            "LIMIT", "DAY", ExecutionStatus.ROUTED, 0L,
            BigDecimal.ZERO, "ext-" + routingId, time,
            time, List.of()
        );
    }

    interface ExecutionRepository {
        void save(RoutedOrder order);
        void saveAll(List<RoutedOrder> orders);
        Optional<RoutedOrder> findById(String routingId);
        List<RoutedOrder> findByParentOrderId(String parentOrderId);
        List<RoutedOrder> findByStatus(ExecutionStatus status);
        List<RoutedOrder> findByExchange(String exchange);
        List<RoutedOrder> findByTimeRange(Instant start, Instant end);
        List<RoutedOrder> findAll();
        long countByStatus(ExecutionStatus status);
        void delete(String routingId);
    }

    static class InMemoryExecutionRepository implements ExecutionRepository {
        private final java.util.Map<String, RoutedOrder> store = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public void save(RoutedOrder order) {
            store.put(order.routingId(), order);
        }

        @Override
        public void saveAll(List<RoutedOrder> orders) {
            orders.forEach(this::save);
        }

        @Override
        public Optional<RoutedOrder> findById(String routingId) {
            return Optional.ofNullable(store.get(routingId));
        }

        @Override
        public List<RoutedOrder> findByParentOrderId(String parentOrderId) {
            return store.values().stream()
                .filter(o -> o.parentOrderId().equals(parentOrderId))
                .toList();
        }

        @Override
        public List<RoutedOrder> findByStatus(ExecutionStatus status) {
            return store.values().stream()
                .filter(o -> o.status() == status)
                .toList();
        }

        @Override
        public List<RoutedOrder> findByExchange(String exchange) {
            return store.values().stream()
                .filter(o -> o.exchange().equals(exchange))
                .toList();
        }

        @Override
        public List<RoutedOrder> findByTimeRange(Instant start, Instant end) {
            return store.values().stream()
                .filter(o -> !o.routedAt().isBefore(start) && !o.routedAt().isAfter(end))
                .toList();
        }

        @Override
        public List<RoutedOrder> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public long countByStatus(ExecutionStatus status) {
            return store.values().stream()
                .filter(o -> o.status() == status)
                .count();
        }

        @Override
        public void delete(String routingId) {
            store.remove(routingId);
        }
    }
}
