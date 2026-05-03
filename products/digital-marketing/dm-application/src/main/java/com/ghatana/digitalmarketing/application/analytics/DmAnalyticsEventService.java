package com.ghatana.digitalmarketing.application.analytics;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.analytics.DmAnalyticsEvent;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for analytics event ingestion and querying.
 *
 * @doc.type interface
 * @doc.purpose MVP analytics event collection service (DMOS-F2-016)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmAnalyticsEventService {

    Promise<DmAnalyticsEvent> ingest(DmOperationContext ctx, IngestAnalyticsEventCommand command);

    Promise<Optional<DmAnalyticsEvent>> findById(DmOperationContext ctx, String eventId);

    Promise<List<DmAnalyticsEvent>> listBySession(DmOperationContext ctx, String sessionId);

    Promise<List<DmAnalyticsEvent>> listByTenant(DmOperationContext ctx, int limit);

    /**
     * Command to ingest a single analytics event.
     */
    record IngestAnalyticsEventCommand(
        String sessionId,
        String eventType,
        String sourceUrl,
        String utmSource,
        String utmMedium,
        String utmCampaign,
        String utmContent,
        String utmTerm,
        String visitorId,
        Map<String, String> properties
    ) {
        public IngestAnalyticsEventCommand {
            Objects.requireNonNull(sessionId, "sessionId must not be null");
            Objects.requireNonNull(eventType, "eventType must not be null");
            Objects.requireNonNull(properties, "properties must not be null");
            if (sessionId.isBlank()) throw new IllegalArgumentException("sessionId must not be blank");
            if (eventType.isBlank()) throw new IllegalArgumentException("eventType must not be blank");
        }
    }
}
