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
 * Data access layer for ModelApproval
 *
 * @doc.type class
 * @doc.purpose Data access layer for ModelApproval
 * @doc.layer product
 * @doc.pattern Repository
 */
public class ModelApprovalRepository {

    private static final String UPSERT_SQL = """
        INSERT INTO finance_ai_model_approval (model_id, approved, approver, approval_date, version, conditions_json)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (model_id) DO UPDATE SET
            approved = EXCLUDED.approved,
            approver = EXCLUDED.approver,
            approval_date = EXCLUDED.approval_date,
            version = EXCLUDED.version,
            conditions_json = EXCLUDED.conditions_json,
            updated_at = CURRENT_TIMESTAMP
        """;

    private static final String SELECT_SQL = """
        SELECT model_id, approved, approver, approval_date, version, conditions_json
        FROM finance_ai_model_approval
        WHERE model_id = ?
        """;

    private static final String DELETE_SQL = "DELETE FROM finance_ai_model_approval WHERE model_id = ?";

    private final DataSource dataSource;
    private final Map<String, ModelApprovalRecord> approvals = new ConcurrentHashMap<>();

    public ModelApprovalRepository() {
        this.dataSource = null;
    }

    public ModelApprovalRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
        FinanceAiPersistenceSupport.migrate(dataSource);
    }

    public ModelApprovalRecord findByModelId(String modelId) {
        if (dataSource != null) {
            return findByModelIdJdbc(modelId);
        }
        return approvals.get(modelId);
    }

    public void save(ModelApprovalRecord record) {
        if (dataSource != null) {
            saveJdbc(record);
            return;
        }
        approvals.put(record.getModelId(), record);
    }

    public void delete(String modelId) {
        if (dataSource != null) {
            deleteJdbc(modelId);
            return;
        }
        approvals.remove(modelId);
    }

    private ModelApprovalRecord findByModelIdJdbc(String modelId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setString(1, modelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                ModelApprovalRecord record = new ModelApprovalRecord();
                record.setModelId(resultSet.getString("model_id"));
                record.setApproved(resultSet.getBoolean("approved"));
                record.setApprover(resultSet.getString("approver"));
                record.setApprovalDate(FinanceAiPersistenceSupport.toInstant(resultSet.getTimestamp("approval_date")));
                record.setVersion(resultSet.getString("version"));
                record.setConditions(FinanceAiPersistenceSupport.readJson(resultSet.getString("conditions_json")));
                return record;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find model approval: " + modelId, exception);
        }
    }

    private void saveJdbc(ModelApprovalRecord record) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, record.getModelId());
            statement.setBoolean(2, record.isApproved());
            statement.setString(3, record.getApprover());
            statement.setTimestamp(4, FinanceAiPersistenceSupport.toTimestamp(record.getApprovalDate()));
            statement.setString(5, record.getVersion());
            statement.setString(6, FinanceAiPersistenceSupport.writeJson(record.getConditions()));
            statement.executeUpdate();
            FinanceAiPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save model approval: " + record.getModelId(), exception);
        }
    }

    private void deleteJdbc(String modelId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, modelId);
            statement.executeUpdate();
            FinanceAiPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete model approval: " + modelId, exception);
        }
    }
}
