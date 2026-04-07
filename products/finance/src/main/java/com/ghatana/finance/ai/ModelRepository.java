/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.finance.ai;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data access layer for Model
 *
 * @doc.type class
 * @doc.purpose Data access layer for Model
 * @doc.layer product
 * @doc.pattern Repository
 */
public class ModelRepository {

    private static final String UPSERT_SQL = """
        INSERT INTO finance_ai_model_registry (model_id, name, version, type, metadata_json, status)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (model_id) DO UPDATE SET
            name = EXCLUDED.name,
            version = EXCLUDED.version,
            type = EXCLUDED.type,
            metadata_json = EXCLUDED.metadata_json,
            status = EXCLUDED.status,
            updated_at = CURRENT_TIMESTAMP
        """;

    private static final String SELECT_SQL = """
        SELECT model_id, name, version, type, metadata_json, status
        FROM finance_ai_model_registry
        WHERE model_id = ?
        """;

    private static final String DELETE_SQL = "DELETE FROM finance_ai_model_registry WHERE model_id = ?";

    private final DataSource dataSource;
    private final Map<String, ModelRecord> models = new ConcurrentHashMap<>();

    public ModelRepository() {
        this.dataSource = null;
    }

    public ModelRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
        FinanceAiPersistenceSupport.migrate(dataSource);
    }

    public ModelRecord findByModelId(String modelId) {
        if (dataSource != null) {
            return findByModelIdJdbc(modelId);
        }
        return models.get(modelId);
    }

    public void save(ModelRecord record) {
        if (dataSource != null) {
            saveJdbc(record);
            return;
        }
        models.put(record.getModelId(), record);
    }

    public void delete(String modelId) {
        if (dataSource != null) {
            deleteJdbc(modelId);
            return;
        }
        models.remove(modelId);
    }

    private ModelRecord findByModelIdJdbc(String modelId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, modelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                ModelRecord record = new ModelRecord();
                record.setModelId(resultSet.getString("model_id"));
                record.setName(resultSet.getString("name"));
                record.setVersion(resultSet.getString("version"));
                record.setType(resultSet.getString("type"));
                record.setMetadata(FinanceAiPersistenceSupport.readJson(resultSet.getString("metadata_json")));
                record.setStatus(resultSet.getString("status"));
                return record;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find model record: " + modelId, exception);
        }
    }

    private void saveJdbc(ModelRecord record) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, record.getModelId());
            statement.setString(2, record.getName());
            statement.setString(3, record.getVersion());
            statement.setString(4, record.getType());
            statement.setString(5, FinanceAiPersistenceSupport.writeJson(record.getMetadata()));
            statement.setString(6, record.getStatus());
            statement.executeUpdate();
            FinanceAiPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save model record: " + record.getModelId(), exception);
        }
    }

    private void deleteJdbc(String modelId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, modelId);
            statement.executeUpdate();
            FinanceAiPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete model record: " + modelId, exception);
        }
    }
}
