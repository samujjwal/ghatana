package com.ghatana.digitalmarketing.persistence.optimization;

import com.ghatana.digitalmarketing.application.optimization.AnomalyDetectionResultRepository;
import com.ghatana.digitalmarketing.domain.optimization.AnomalyDetectionResult;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PostgreSQL implementation of {@link AnomalyDetectionResultRepository}.
 *
 * <p>Currently uses in-memory storage for development. Production implementation
 * should use PostgreSQL with proper schema and connection pooling.</p>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL persistence for anomaly detection results (P3-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresAnomalyDetectionResultRepository implements AnomalyDetectionResultRepository {

    private final Map<String, AnomalyDetectionResult> storage = new ConcurrentHashMap<>();

    @Override
    public Promise<AnomalyDetectionResult> save(AnomalyDetectionResult result) {
        storage.put(result.getId(), result);
        return Promise.of(result);
    }

    @Override
    public Promise<AnomalyDetectionResult> update(AnomalyDetectionResult result) {
        if (!storage.containsKey(result.getId())) {
            return Promise.ofException(new IllegalArgumentException("Anomaly detection result not found: " + result.getId()));
        }
        storage.put(result.getId(), result);
        return Promise.of(result);
    }

    @Override
    public Promise<Optional<AnomalyDetectionResult>> findById(String id) {
        return Promise.of(Optional.ofNullable(storage.get(id)));
    }

    @Override
    public Promise<List<AnomalyDetectionResult>> listByTenant(String tenantId) {
        List<AnomalyDetectionResult> result = new ArrayList<>();
        for (AnomalyDetectionResult rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }

    @Override
    public Promise<List<AnomalyDetectionResult>> listByWorkspace(String tenantId, String workspaceId) {
        List<AnomalyDetectionResult> result = new ArrayList<>();
        for (AnomalyDetectionResult rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId) && rec.getWorkspaceId().equals(workspaceId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }

    @Override
    public Promise<List<AnomalyDetectionResult>> listByCampaign(String tenantId, String campaignId) {
        List<AnomalyDetectionResult> result = new ArrayList<>();
        for (AnomalyDetectionResult rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId) && rec.getCampaignId().equals(campaignId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }
}
