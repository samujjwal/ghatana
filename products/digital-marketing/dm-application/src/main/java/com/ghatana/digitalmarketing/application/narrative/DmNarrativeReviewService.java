package com.ghatana.digitalmarketing.application.narrative;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.narrative.DmNarrativeReview;
import com.ghatana.digitalmarketing.domain.narrative.DmNarrativePeriodType;
import com.ghatana.digitalmarketing.domain.narrative.DmNarrativeReviewStatus;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for narrative review generation.
 *
 * @doc.type interface
 * @doc.purpose Generate and track AI narrative review documents for reporting periods (DMOS-F3-006)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmNarrativeReviewService {

    Promise<DmNarrativeReview> generate(DmOperationContext ctx, GenerateNarrativeReviewCommand command);

    Promise<DmNarrativeReview> markReady(DmOperationContext ctx, String reviewId, String narrativeText,
                                          List<String> keyInsights, List<String> recommendations);

    Promise<DmNarrativeReview> markFailed(DmOperationContext ctx, String reviewId);

    Promise<Optional<DmNarrativeReview>> findById(DmOperationContext ctx, String reviewId);

    Promise<List<DmNarrativeReview>> listByTenant(DmOperationContext ctx);

    /**
     * Command to generate a narrative review for a period.
     */
    record GenerateNarrativeReviewCommand(
        DmNarrativePeriodType periodType,
        Instant periodStart,
        Instant periodEnd
    ) {
        public GenerateNarrativeReviewCommand {
            Objects.requireNonNull(periodType, "periodType must not be null");
            Objects.requireNonNull(periodStart, "periodStart must not be null");
            Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        }
    }
}
