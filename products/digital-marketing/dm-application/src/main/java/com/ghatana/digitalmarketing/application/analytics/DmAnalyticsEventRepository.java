package com.ghatana.digitalmarketing.application.analytics;

import com.ghatana.digitalmarketing.domain.analytics.DmAnalyticsEvent;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for analytics event persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for analytics event storage (DMOS-F2-016)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmAnalyticsEventRepository {

    Promise<DmAnalyticsEvent> save(DmAnalyticsEvent event);

    Promise<Optional<DmAnalyticsEvent>> findById(String id);

    Promise<List<DmAnalyticsEvent>> listByTenant(String tenantId, int limit);

    Promise<List<DmAnalyticsEvent>> listBySession(String tenantId, String sessionId);
}
