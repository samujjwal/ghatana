package com.ghatana.digitalmarketing.application.dashboard;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.dashboard.DmAnalyticsDashboard;
import com.ghatana.digitalmarketing.domain.dashboard.DmAnalyticsDashboard.DmDashboardWidget;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service for analytics dashboard management.
 *
 * @doc.type interface
 * @doc.purpose MVP analytics dashboard creation and configuration (DMOS-F2-018)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmAnalyticsDashboardService {

    Promise<DmAnalyticsDashboard> create(DmOperationContext ctx, CreateDashboardCommand command);

    Promise<DmAnalyticsDashboard> update(DmOperationContext ctx, String dashboardId, UpdateDashboardCommand command);

    Promise<Optional<DmAnalyticsDashboard>> findById(DmOperationContext ctx, String dashboardId);

    Promise<List<DmAnalyticsDashboard>> listByTenant(DmOperationContext ctx);

    /**
     * Command to create an analytics dashboard.
     */
    record CreateDashboardCommand(
        String name,
        String description,
        List<DmDashboardWidget> widgets
    ) {
        public CreateDashboardCommand {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(widgets, "widgets must not be null");
            if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        }
    }

    /**
     * Command to update an existing analytics dashboard.
     */
    record UpdateDashboardCommand(
        String name,
        String description,
        List<DmDashboardWidget> widgets
    ) {
        public UpdateDashboardCommand {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(widgets, "widgets must not be null");
            if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        }
    }
}
