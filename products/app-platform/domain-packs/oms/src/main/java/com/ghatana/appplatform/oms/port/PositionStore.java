package com.ghatana.appplatform.oms.port;

import com.ghatana.appplatform.oms.domain.Position;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * @doc.type    Port (Interface)
 * @doc.purpose Persistence contract for the CQRS position read model (D01-016).
 * @doc.layer   Port
 * @doc.pattern Hexagonal Architecture — Port, CQRS Read Side
 */
public interface PositionStore {

    Promise<Void> upsert(Position position);

    Promise<Optional<Position>> find(String clientId, String instrumentId, String accountId);

    Promise<List<Position>> findByClient(String clientId);
}
