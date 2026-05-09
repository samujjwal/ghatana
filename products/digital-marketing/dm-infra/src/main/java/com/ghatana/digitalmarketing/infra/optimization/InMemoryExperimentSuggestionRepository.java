package com.ghatana.digitalmarketing.infra.optimization;

import com.ghatana.digitalmarketing.application.optimization.ExperimentSuggestionRepository;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestion;
import com.ghatana.digitalmarketing.domain.optimization.ExperimentSuggestionStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory {@link ExperimentSuggestionRepository} backed by a {@link ConcurrentHashMap}.
 *
 * <p>Stores experiment suggestions by ID and supports filtering by tenant, workspace,
 * campaign, and status. Used for local development and test deployments where a
 * full PostgreSQL database is not available.</p>
 *
 * @doc.type class
 * @doc.purpose In-memory experiment suggestion repository for DMOS local and test deployments
 * @doc.layer product
 * @doc.pattern Adapter, Repository
 */
public final class InMemoryExperimentSuggestionRepository implements ExperimentSuggestionRepository {

    private final ConcurrentHashMap<String, ExperimentSuggestion> store = new ConcurrentHashMap<>();

    @Override
    public Promise<ExperimentSuggestion> save(ExperimentSuggestion suggestion) {
        Objects.requireNonNull(suggestion, "suggestion must not be null");
        store.put(suggestion.getId(), suggestion);
        return Promise.of(suggestion);
    }

    @Override
    public Promise<ExperimentSuggestion> update(ExperimentSuggestion suggestion) {
        Objects.requireNonNull(suggestion, "suggestion must not be null");
        if (!store.containsKey(suggestion.getId())) {
            return Promise.ofException(new IllegalArgumentException(
                "Suggestion not found: " + suggestion.getId()));
        }
        store.put(suggestion.getId(), suggestion);
        return Promise.of(suggestion);
    }

    @Override
    public Promise<Optional<ExperimentSuggestion>> findById(String id) {
        Objects.requireNonNull(id, "id must not be null");
        return Promise.of(Optional.ofNullable(store.get(id)));
    }

    @Override
    public Promise<List<ExperimentSuggestion>> listByTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        List<ExperimentSuggestion> results = store.values().stream()
            .filter(s -> s.getTenantId().equals(tenantId))
            .collect(Collectors.toList());
        return Promise.of(results);
    }

    @Override
    public Promise<List<ExperimentSuggestion>> listByWorkspace(String tenantId, String workspaceId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        List<ExperimentSuggestion> results = store.values().stream()
            .filter(s -> s.getTenantId().equals(tenantId) && s.getWorkspaceId().equals(workspaceId))
            .collect(Collectors.toList());
        return Promise.of(results);
    }

    @Override
    public Promise<List<ExperimentSuggestion>> listByCampaign(String tenantId, String campaignId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(campaignId, "campaignId must not be null");
        List<ExperimentSuggestion> results = store.values().stream()
            .filter(s -> s.getTenantId().equals(tenantId) && s.getCampaignId().equals(campaignId))
            .collect(Collectors.toList());
        return Promise.of(results);
    }

    @Override
    public Promise<List<ExperimentSuggestion>> listByStatus(String tenantId, ExperimentSuggestionStatus status) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        List<ExperimentSuggestion> results = store.values().stream()
            .filter(s -> s.getTenantId().equals(tenantId) && s.getStatus().equals(status))
            .collect(Collectors.toList());
        return Promise.of(results);
    }
}
