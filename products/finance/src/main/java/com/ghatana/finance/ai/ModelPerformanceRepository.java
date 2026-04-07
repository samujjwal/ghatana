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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Data access layer for ModelPerformance
 *
 * @doc.type class
 * @doc.purpose Data access layer for ModelPerformance
 * @doc.layer product
 * @doc.pattern Repository
 */
public class ModelPerformanceRepository {

    private static final String INSERT_SQL = """
        INSERT INTO finance_ai_model_performance (model_id, confidence, accuracy, latency, recorded_at)
        VALUES (?, ?, ?, ?, ?)
        """;

    private static final String SELECT_SQL = """
        SELECT model_id, confidence, accuracy, latency, recorded_at
        FROM finance_ai_model_performance
        WHERE model_id = ?
        ORDER BY recorded_at ASC
        """;

    private final DataSource dataSource;
    private final Map<String, List<ModelPerformanceRecord>> performanceRecords = new ConcurrentHashMap<>();

    public ModelPerformanceRepository() {
        this.dataSource = null;
    }

    public ModelPerformanceRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
        FinanceAiPersistenceSupport.migrate(dataSource);
    }

    public void save(ModelPerformanceRecord record) {
        if (dataSource != null) {
            saveJdbc(record);
            return;
        }
        performanceRecords.computeIfAbsent(record.getModelId(), k -> new CopyOnWriteArrayList<>())
            .add(record);
    }

    public List<ModelPerformanceRecord> findByModelId(String modelId) {
        if (dataSource != null) {
            return findByModelIdJdbc(modelId);
        }
        return performanceRecords.getOrDefault(modelId, List.of());
    }

    private void saveJdbc(ModelPerformanceRecord record) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, record.getModelId());
            statement.setDouble(2, record.getConfidence());
            statement.setDouble(3, record.getAccuracy());
            statement.setLong(4, record.getLatency());
            statement.setTimestamp(5, FinanceAiPersistenceSupport.toTimestamp(record.getTimestamp()));
            statement.executeUpdate();
            FinanceAiPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save model performance: " + record.getModelId(), exception);
        }
    }

    private List<ModelPerformanceRecord> findByModelIdJdbc(String modelId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, modelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                java.util.List<ModelPerformanceRecord> records = new java.util.ArrayList<>();
                while (resultSet.next()) {
                    ModelPerformanceRecord record = new ModelPerformanceRecord();
                    record.setModelId(resultSet.getString("model_id"));
                    record.setConfidence(resultSet.getDouble("confidence"));
                    record.setAccuracy(resultSet.getDouble("accuracy"));
                    record.setLatency(resultSet.getLong("latency"));
                    record.setTimestamp(FinanceAiPersistenceSupport.toInstant(resultSet.getTimestamp("recorded_at")));
                    records.add(record);
                }
                return List.copyOf(records);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read model performance for: " + modelId, exception);
        }
    }
}
