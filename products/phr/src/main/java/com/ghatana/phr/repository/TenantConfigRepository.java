package com.ghatana.phr.repository;

import com.ghatana.phr.model.TenantConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data access layer for TenantConfig
 *
 * @doc.type class
 * @doc.purpose Data access layer for TenantConfig
 * @doc.layer product
 * @doc.pattern Repository
 */
public class TenantConfigRepository {
    private static final String SELECT_BY_ID_SQL = """
        SELECT tenant_id, tenant_name, allowed_regions_json, hipaa_compliant, mfa_required
        FROM phr_tenant_configs
        WHERE tenant_id = ?
        """;

    private static final String UPSERT_SQL = """
        INSERT INTO phr_tenant_configs (tenant_id, tenant_name, allowed_regions_json, hipaa_compliant, mfa_required)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (tenant_id) DO UPDATE SET
            tenant_name = EXCLUDED.tenant_name,
            allowed_regions_json = EXCLUDED.allowed_regions_json,
            hipaa_compliant = EXCLUDED.hipaa_compliant,
            mfa_required = EXCLUDED.mfa_required
        """;

    private static final String DELETE_SQL = "DELETE FROM phr_tenant_configs WHERE tenant_id = ?";
    private static final String EXISTS_SQL = "SELECT 1 FROM phr_tenant_configs WHERE tenant_id = ?";

    private final DataSource dataSource;
    private final Map<String, TenantConfig> configs = new ConcurrentHashMap<>();

    public TenantConfigRepository() {
        this.dataSource = null;
    }

    public TenantConfigRepository(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource cannot be null");
        PhrPersistenceSupport.migrate(dataSource);
    }

    public TenantConfig findById(String tenantId) {
        if (dataSource != null) {
            return findByIdJdbc(tenantId);
        }
        return configs.get(tenantId);
    }

    public void save(TenantConfig config) {
        if (dataSource != null) {
            saveJdbc(config);
            return;
        }
        configs.put(config.getTenantId(), config);
    }

    public void delete(String tenantId) {
        if (dataSource != null) {
            deleteJdbc(tenantId);
            return;
        }
        configs.remove(tenantId);
    }

    public boolean exists(String tenantId) {
        if (dataSource != null) {
            return existsJdbc(tenantId);
        }
        return configs.containsKey(tenantId);
    }

    private TenantConfig findByIdJdbc(String tenantId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setString(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                TenantConfig config = new TenantConfig();
                config.setTenantId(resultSet.getString("tenant_id"));
                config.setTenantName(resultSet.getString("tenant_name"));
                config.setAllowedRegions(PhrPersistenceSupport.readStringSet(resultSet.getString("allowed_regions_json")));
                config.setHipaaCompliant(resultSet.getBoolean("hipaa_compliant"));
                config.setMfaRequired(resultSet.getBoolean("mfa_required"));
                return config;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load tenant config: " + tenantId, exception);
        }
    }

    private void saveJdbc(TenantConfig config) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setString(1, config.getTenantId());
            statement.setString(2, config.getTenantName());
            statement.setString(3, PhrPersistenceSupport.writeStringSet(config.getAllowedRegions()));
            statement.setBoolean(4, config.isHipaaCompliant());
            statement.setBoolean(5, config.isMfaRequired());
            statement.executeUpdate();
            PhrPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save tenant config: " + config.getTenantId(), exception);
        }
    }

    private void deleteJdbc(String tenantId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(DELETE_SQL)) {
            statement.setString(1, tenantId);
            statement.executeUpdate();
            PhrPersistenceSupport.commitIfNeeded(connection);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete tenant config: " + tenantId, exception);
        }
    }

    private boolean existsJdbc(String tenantId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_SQL)) {
            statement.setString(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check tenant config existence: " + tenantId, exception);
        }
    }
}
