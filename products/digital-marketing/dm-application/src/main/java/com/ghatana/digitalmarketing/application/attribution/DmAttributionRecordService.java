package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.attribution.DmAttributionModel;
import com.ghatana.digitalmarketing.domain.attribution.DmAttributionRecord;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for attribution record management.
 *
 * @doc.type interface
 * @doc.purpose MVP last-click attribution recording and querying (DMOS-F2-017)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmAttributionRecordService {

    Promise<DmAttributionRecord> record(DmOperationContext ctx, RecordAttributionCommand command);

    Promise<Optional<DmAttributionRecord>> findById(DmOperationContext ctx, String recordId);

    Promise<List<DmAttributionRecord>> listByVisitor(DmOperationContext ctx, String visitorId);

    Promise<Optional<DmAttributionRecord>> findByConversionEvent(DmOperationContext ctx, String conversionEventId);

    /**
     * Command to create an attribution record.
     */
    record RecordAttributionCommand(
        String visitorId,
        String sessionId,
        String conversionEventId,
        String attributedSource,
        String attributedMedium,
        String attributedCampaign,
        String attributedContent,
        String attributedTerm,
        DmAttributionModel model,
        double attributionWeight
    ) {
        public RecordAttributionCommand {
            Objects.requireNonNull(visitorId, "visitorId must not be null");
            Objects.requireNonNull(conversionEventId, "conversionEventId must not be null");
            Objects.requireNonNull(model, "model must not be null");
            if (visitorId.isBlank()) throw new IllegalArgumentException("visitorId must not be blank");
            if (conversionEventId.isBlank()) throw new IllegalArgumentException("conversionEventId must not be blank");
            if (attributionWeight < 0.0 || attributionWeight > 1.0) {
                throw new IllegalArgumentException("attributionWeight must be between 0.0 and 1.0");
            }
        }
    }
}
