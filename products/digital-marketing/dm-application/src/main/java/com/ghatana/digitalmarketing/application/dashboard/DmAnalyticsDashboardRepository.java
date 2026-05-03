package com.ghatana.digitalmarketing.application.dashboard;

import com.ghatana.digitalmarketing.domain.dashboard.DmAnalyticsDashboard;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for analytics dashboard persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for analytics dashboard configuration storage (DMOS-F2-018)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmAnalyticsDashboardRepository {

    Promise<DmAnalyticsDashboard> save(DmAnalyticsDashboard dashboard);

    Promise<DmAnalyticsDashboard> update(DmAnalyticsDashboard dashboard);

    Promise<Optional<DmAnalyticsDashboard>> findById(String id);

    Promise<List<DmAnalyticsDashboard>> listByTenant(String tenantId);
}
