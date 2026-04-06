package com.ghatana.yappc.ai.cost;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * JDBC-backed persistence for AI cost audit events.
 *
 * <p>Writes are offloaded to a blocking thread pool via {@link Promise#ofBlocking}
 * so the ActiveJ event loop is never blocked.
 *
 * @doc.type class
 * @doc.purpose Durable persistence of AI cost events to the YAPPC PostgreSQL database
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class JdbcCostRepository implements CostRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcCostRepository.class);

    private static final String INSERT_SQL =
        "INSERT INTO ai_cost_events " +
        "(id, call_id, tenant_id, user_id, model, provider, feature_id, " +
        " tokens_input, tokens_output, cost_usd, occurred_at) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final DataSource dataSource;
    private final Eventloop eventloop;

    public JdbcCostRepository(DataSource dataSource, Eventloop eventloop) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource is required");
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop is required");
    }

    @Override
    public Promise<Void> save(CostEvent event) {
        Objects.requireNonNull(event, "event is required");
        return Promise.ofBlocking(eventloop, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(INSERT_SQL)) {
                stmt.setString(1, event.id());
                stmt.setString(2, event.callId());
                stmt.setString(3, event.tenantId());
                stmt.setString(4, event.userId());
                stmt.setString(5, event.model());
                stmt.setString(6, event.provider());
                stmt.setString(7, event.featureId());
                stmt.setInt(8, event.tokensInput());
                stmt.setInt(9, event.tokensOutput());
                stmt.setBigDecimal(10, java.math.BigDecimal.valueOf(event.costUsd()));
                stmt.setTimestamp(11, Timestamp.from(event.occurredAt()));
                stmt.executeUpdate();
                logger.debug("Persisted cost event id={} tenant={} model={} cost={}",
                    event.id(), event.tenantId(), event.model(), event.costUsd());
            } catch (Exception ex) {
                logger.error("Failed to persist cost event id={}: {}", event.id(), ex.getMessage(), ex);
                throw ex;
            }
            return null;
        });
    }
}
