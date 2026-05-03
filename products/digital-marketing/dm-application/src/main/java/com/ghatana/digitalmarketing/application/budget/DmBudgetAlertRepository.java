package com.ghatana.digitalmarketing.application.budget;

import com.ghatana.digitalmarketing.domain.budget.DmBudgetAlert;
import com.ghatana.digitalmarketing.domain.budget.DmBudgetAlertLevel;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for budget alert persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for budget alert storage (DMOS-F3-002)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmBudgetAlertRepository {

    Promise<DmBudgetAlert> save(DmBudgetAlert alert);

    Promise<DmBudgetAlert> update(DmBudgetAlert alert);

    Promise<Optional<DmBudgetAlert>> findById(String id);

    Promise<List<DmBudgetAlert>> listByTenant(String tenantId);

    Promise<List<DmBudgetAlert>> listByCampaign(String tenantId, String campaignId);

    Promise<List<DmBudgetAlert>> listUnacknowledged(String tenantId);
}
