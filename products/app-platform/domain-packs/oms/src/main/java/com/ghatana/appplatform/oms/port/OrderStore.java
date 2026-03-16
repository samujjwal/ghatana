package com.ghatana.appplatform.oms.port;

import com.ghatana.appplatform.oms.domain.Order;
import com.ghatana.appplatform.oms.domain.OrderStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * @doc.type    Port (Interface)
 * @doc.purpose Persistence contract for the Order aggregate (D01-001, D01-004).
 * @doc.layer   Port
 * @doc.pattern Hexagonal Architecture — Port
 */
public interface OrderStore {

    Promise<Void> save(Order order);

    Promise<Void> update(Order order);

    Promise<Optional<Order>> findById(String orderId);

    Promise<Optional<Order>> findByIdempotencyKey(String idempotencyKey);

    Promise<List<Order>> findByClientId(String clientId, OrderStatus status,
                                         String instrumentId, Instant from, Instant to,
                                         int page, int size);

    Promise<List<Order>> findPendingApproval(String assigneeId);

    Promise<List<Order>> findExpiredOrders(Instant before);
}
