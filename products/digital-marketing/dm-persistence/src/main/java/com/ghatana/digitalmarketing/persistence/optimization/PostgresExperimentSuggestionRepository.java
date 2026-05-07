package com.ghatana.digitalmarketing.persistence.optimization;

import com.ghatana.digitalmarketing.application.optimization.ExperimentSuggestionRepository;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestion;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PostgreSQL implementation of {@link ExperimentSuggestionRepository}.
 *
 * <p>Currently uses in-memory storage for development. Production implementation
 * should use PostgreSQL with proper schema and connection pooling.</p>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL persistence for experiment suggestions (P3-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresExperimentSuggestionRepository implements ExperimentSuggestionRepository {

    private final Map<String, ExperimentSuggestion> storage = new ConcurrentHashMap<>();

    @Override
    public Promise<ExperimentSuggestion> save(ExperimentSuggestion suggestion) {
        storage.put(suggestion.getId(), suggestion);
        return Promise.of(suggestion);
    }

    @Override
    public Promise<ExperimentSuggestion> update(ExperimentSuggestion suggestion) {
        if (!storage.containsKey(suggestion.getId())) {
            return Promise.ofException(new IllegalArgumentException("Experiment suggestion not found: " + suggestion.getId()));
        }
        storage.put(suggestion.getId(), suggestion);
        return Promise.of(suggestion);
    }

    @Override
    public Promise<Optional<ExperimentSuggestion>> findById(String id) {
        return Promise.of(Optional.ofNullable(storage.get(id)));
    }

    @Override
    public Promise<List<ExperimentSuggestion>> listByTenant(String tenantId) {
        List<ExperimentSuggestion> result = new ArrayList<>();
        for (ExperimentSuggestion rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }

    @Override
    public Promise<List<ExperimentSuggestion>> listByWorkspace(String tenantId, String workspaceId) {
        List<ExperimentSuggestion> result = new ArrayList<>();
        for (ExperimentSuggestion rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId) && rec.getWorkspaceId().equals(workspaceId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }

    @Override
    public Promise<List<ExperimentSuggestion>> listByCampaign(String tenantId, String campaignId) {
        List<ExperimentSuggestion> result = new ArrayList<>();
        for (ExperimentSuggestion rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId) && rec.getCampaignId().equals(campaignId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }
}
