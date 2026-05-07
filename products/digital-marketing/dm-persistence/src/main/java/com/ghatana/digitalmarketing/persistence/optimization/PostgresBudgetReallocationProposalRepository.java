package com.ghatana.digitalmarketing.persistence.optimization;

import com.ghatana.digitalmarketing.application.optimization.BudgetReallocationProposalRepository;
import com.ghatana.digitalmarketing.domain.optimization.BudgetReallocationProposal;
import io.activej.promise.Promise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PostgreSQL implementation of {@link BudgetReallocationProposalRepository}.
 *
 * <p>Currently uses in-memory storage for development. Production implementation
 * should use PostgreSQL with proper schema and connection pooling.</p>
 *
 * @doc.type class
 * @doc.purpose PostgreSQL persistence for budget reallocation proposals (P3-004)
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class PostgresBudgetReallocationProposalRepository implements BudgetReallocationProposalRepository {

    private final Map<String, BudgetReallocationProposal> storage = new ConcurrentHashMap<>();

    @Override
    public Promise<BudgetReallocationProposal> save(BudgetReallocationProposal proposal) {
        storage.put(proposal.getId(), proposal);
        return Promise.of(proposal);
    }

    @Override
    public Promise<BudgetReallocationProposal> update(BudgetReallocationProposal proposal) {
        if (!storage.containsKey(proposal.getId())) {
            return Promise.ofException(new IllegalArgumentException("Budget reallocation proposal not found: " + proposal.getId()));
        }
        storage.put(proposal.getId(), proposal);
        return Promise.of(proposal);
    }

    @Override
    public Promise<Optional<BudgetReallocationProposal>> findById(String id) {
        return Promise.of(Optional.ofNullable(storage.get(id)));
    }

    @Override
    public Promise<List<BudgetReallocationProposal>> listByTenant(String tenantId) {
        List<BudgetReallocationProposal> result = new ArrayList<>();
        for (BudgetReallocationProposal rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }

    @Override
    public Promise<List<BudgetReallocationProposal>> listByWorkspace(String tenantId, String workspaceId) {
        List<BudgetReallocationProposal> result = new ArrayList<>();
        for (BudgetReallocationProposal rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId) && rec.getWorkspaceId().equals(workspaceId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }

    @Override
    public Promise<List<BudgetReallocationProposal>> listByCampaign(String tenantId, String campaignId) {
        List<BudgetReallocationProposal> result = new ArrayList<>();
        for (BudgetReallocationProposal rec : storage.values()) {
            if (rec.getTenantId().equals(tenantId) && rec.getCampaignId().equals(campaignId)) {
                result.add(rec);
            }
        }
        return Promise.of(result);
    }
}
