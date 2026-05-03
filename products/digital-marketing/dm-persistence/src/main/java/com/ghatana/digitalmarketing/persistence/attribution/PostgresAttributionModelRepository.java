package com.ghatana.digitalmarketing.persistence.attribution;

import com.ghatana.digitalmarketing.application.attribution.AttributionModelRepository;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.attribution.AttributionModel;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * PostgreSQL adapter for AttributionModelRepository (DMOS-P3-005).
 *
 * @doc.type class
 * @doc.purpose PostgreSQL persistence adapter for attribution models
 * @doc.layer persistence
 * @doc.pattern Repository
 */
public final class PostgresAttributionModelRepository implements AttributionModelRepository {

    private static final Logger logger = LoggerFactory.getLogger(PostgresAttributionModelRepository.class);

    private final DataSource dataSource;
    private final Executor executor;

    public PostgresAttributionModelRepository(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    @Override
    public Promise<AttributionModel> save(AttributionModel model) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                INSERT INTO dmos_attribution_models
                (model_id, tenant_id, workspace_id, model_name, model_type, touchpoint_weights, confidence_interval_lower, confidence_interval_upper, active, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                ON CONFLICT (model_id) DO UPDATE SET
                model_name = EXCLUDED.model_name,
                model_type = EXCLUDED.model_type,
                touchpoint_weights = EXCLUDED.touchpoint_weights,
                confidence_interval_lower = EXCLUDED.confidence_interval_lower,
                confidence_interval_upper = EXCLUDED.confidence_interval_upper,
                active = EXCLUDED.active,
                updated_at = EXCLUDED.updated_at
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, model.getModelId());
                stmt.setString(2, model.getTenantId().getValue());
                stmt.setString(3, model.getWorkspaceId().getValue());
                stmt.setString(4, model.getModelName());
                stmt.setString(5, model.getModelType());
                stmt.setString(6, mapToJson(model.getTouchpointWeights()));
                stmt.setDouble(7, model.getConfidenceIntervalLower());
                stmt.setDouble(8, model.getConfidenceIntervalUpper());
                stmt.setBoolean(9, model.isActive());
                stmt.setTimestamp(10, Timestamp.from(model.getCreatedAt()));
                stmt.setTimestamp(11, Timestamp.from(model.getUpdatedAt()));

                stmt.executeUpdate();
                logger.info("Attribution model saved: {}", model.getModelId());
                return model;
            } catch (SQLException e) {
                logger.error("Failed to save attribution model", e);
                throw new RuntimeException("Failed to save attribution model", e);
            }
        });
    }

    @Override
    public Promise<Optional<AttributionModel>> findById(String modelId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM dmos_attribution_models WHERE model_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, modelId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            } catch (SQLException e) {
                logger.error("Failed to find attribution model by ID", e);
                throw new RuntimeException("Failed to find attribution model", e);
            }
        });
    }

    @Override
    public Promise<Optional<AttributionModel>> findActiveByWorkspace(DmWorkspaceId workspaceId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM dmos_attribution_models WHERE workspace_id = ? AND active = true";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, workspaceId.getValue());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            } catch (SQLException e) {
                logger.error("Failed to find active attribution model by workspace", e);
                throw new RuntimeException("Failed to find active attribution model", e);
            }
        });
    }

    @Override
    public Promise<List<AttributionModel>> findByTenant(DmTenantId tenantId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "SELECT * FROM dmos_attribution_models WHERE tenant_id = ? ORDER BY created_at DESC";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, tenantId.getValue());
                ResultSet rs = stmt.executeQuery();

                List<AttributionModel> models = new ArrayList<>();
                while (rs.next()) {
                    models.add(mapRow(rs));
                }
                return models;
            } catch (SQLException e) {
                logger.error("Failed to find attribution models by tenant", e);
                throw new RuntimeException("Failed to find attribution models by tenant", e);
            }
        });
    }

    @Override
    public Promise<AttributionModel> update(AttributionModel model) {
        return Promise.ofBlocking(executor, () -> {
            String sql = """
                UPDATE dmos_attribution_models
                SET model_name = ?, model_type = ?, touchpoint_weights = ?::jsonb, confidence_interval_lower = ?, confidence_interval_upper = ?, active = ?, updated_at = ?
                WHERE model_id = ?
                """;

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, model.getModelName());
                stmt.setString(2, model.getModelType());
                stmt.setString(3, mapToJson(model.getTouchpointWeights()));
                stmt.setDouble(4, model.getConfidenceIntervalLower());
                stmt.setDouble(5, model.getConfidenceIntervalUpper());
                stmt.setBoolean(6, model.isActive());
                stmt.setTimestamp(7, Timestamp.from(Instant.now()));
                stmt.setString(8, model.getModelId());

                stmt.executeUpdate();
                logger.info("Attribution model updated: {}", model.getModelId());
                return model;
            } catch (SQLException e) {
                logger.error("Failed to update attribution model", e);
                throw new RuntimeException("Failed to update attribution model", e);
            }
        });
    }

    @Override
    public Promise<Void> delete(String modelId) {
        return Promise.ofBlocking(executor, () -> {
            String sql = "DELETE FROM dmos_attribution_models WHERE model_id = ?";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, modelId);
                stmt.executeUpdate();
                logger.info("Attribution model deleted: {}", modelId);
                return null;
            } catch (SQLException e) {
                logger.error("Failed to delete attribution model", e);
                throw new RuntimeException("Failed to delete attribution model", e);
            }
        });
    }

    private AttributionModel mapRow(ResultSet rs) throws SQLException {
        return AttributionModel.builder()
            .modelId(rs.getString("model_id"))
            .tenantId(DmTenantId.of(rs.getString("tenant_id")))
            .workspaceId(DmWorkspaceId.of(rs.getString("workspace_id")))
            .modelName(rs.getString("model_name"))
            .modelType(rs.getString("model_type"))
            .touchpointWeights(parseJson(rs.getString("touchpoint_weights")))
            .confidenceIntervalLower(rs.getDouble("confidence_interval_lower"))
            .confidenceIntervalUpper(rs.getDouble("confidence_interval_upper"))
            .active(rs.getBoolean("active"))
            .createdAt(rs.getTimestamp("created_at").toInstant())
            .updatedAt(rs.getTimestamp("updated_at").toInstant())
            .build();
    }

    private String mapToJson(Map<String, Double> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, Double> parseJson(String json) {
        Map<String, Double> map = new HashMap<>();
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return map;
        }
        String content = json.substring(1, json.length() - 1);
        String[] pairs = content.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                map.put(kv[0].replace("\"", ""), Double.parseDouble(kv[1]));
            }
        }
        return map;
    }
}
