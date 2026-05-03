package com.ghatana.digitalmarketing.application.preflight;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.preflight.DmPreflightChecklist;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for preflight campaign safety checks.
 *
 * @doc.type interface
 * @doc.purpose Evaluates campaign safety checklist before launch (DMOS-F2-013)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmPreflightChecklistService {

    Promise<DmPreflightChecklist> evaluate(DmOperationContext ctx, EvaluatePreflightCommand command);

    Promise<Optional<DmPreflightChecklist>> findById(DmOperationContext ctx, String checklistId);

    Promise<List<DmPreflightChecklist>> listByCampaign(DmOperationContext ctx, String campaignId);

    Promise<Optional<DmPreflightChecklist>> findLatestByCampaign(DmOperationContext ctx, String campaignId);

    /**
     * Command to evaluate a new preflight checklist for a campaign.
     */
    record EvaluatePreflightCommand(
        String campaignId,
        List<DmPreflightChecklist.DmPreflightCheckItem> items
    ) {
        public EvaluatePreflightCommand {
            Objects.requireNonNull(campaignId, "campaignId must not be null");
            Objects.requireNonNull(items, "items must not be null");
            if (campaignId.isBlank()) throw new IllegalArgumentException("campaignId must not be blank");
            if (items.isEmpty()) throw new IllegalArgumentException("items must not be empty");
            items = List.copyOf(items);
        }
    }
}
