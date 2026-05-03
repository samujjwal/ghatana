package com.ghatana.digitalmarketing.application.budget;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.budget.DmBudgetAlert;
import com.ghatana.digitalmarketing.domain.budget.DmBudgetAlertLevel;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for budget alert management.
 *
 * @doc.type interface
 * @doc.purpose Fire and acknowledge budget pacing alerts for campaigns (DMOS-F3-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmBudgetAlertService {

    Promise<DmBudgetAlert> fire(DmOperationContext ctx, FireBudgetAlertCommand command);

    Promise<DmBudgetAlert> acknowledge(DmOperationContext ctx, String alertId);

    Promise<Optional<DmBudgetAlert>> findById(DmOperationContext ctx, String alertId);

    Promise<List<DmBudgetAlert>> listByCampaign(DmOperationContext ctx, String campaignId);

    Promise<List<DmBudgetAlert>> listUnacknowledged(DmOperationContext ctx);

    /**
     * Command to fire a budget pacing alert.
     */
    record FireBudgetAlertCommand(
        String campaignId,
        long totalBudgetMicros,
        long spentMicros,
        double pacingRatio,
        DmBudgetAlertLevel level,
        String message
    ) {
        public FireBudgetAlertCommand {
            Objects.requireNonNull(campaignId, "campaignId must not be null");
            Objects.requireNonNull(level, "level must not be null");
            if (campaignId.isBlank()) throw new IllegalArgumentException("campaignId must not be blank");
            if (totalBudgetMicros < 0) throw new IllegalArgumentException("totalBudgetMicros must be non-negative");
        }
    }
}
