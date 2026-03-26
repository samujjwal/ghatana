package com.ghatana.phr.repository;

import com.ghatana.phr.model.TenantConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * Data access layer for TenantConfig
 *
 * @doc.type class
 * @doc.purpose Data access layer for TenantConfig
 * @doc.layer product
 * @doc.pattern Repository
 */
public class TenantConfigRepository {
    private final Map<String, TenantConfig> configs = new HashMap<>();

    public TenantConfig findById(String tenantId) {
        return configs.get(tenantId);
    }

    public void save(TenantConfig config) {
        configs.put(config.getTenantId(), config);
    }

    public void delete(String tenantId) {
        configs.remove(tenantId);
    }

    public boolean exists(String tenantId) {
        return configs.containsKey(tenantId);
    }
}
