package com.ghatana.digitalmarketing.infra.audit;

import com.ghatana.digitalmarketing.application.audit.WebsiteAuditReportRepository;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditReport;
import io.activej.promise.Promise;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory website audit report repository for development and E2E smoke tests.
 *
 * @doc.type class
 * @doc.purpose Development adapter for website audit report persistence
 * @doc.layer infra
 * @doc.pattern Repository
 */
public final class EphemeralWebsiteAuditReportRepository implements WebsiteAuditReportRepository {

    private final Map<String, WebsiteAuditReport> byId = new ConcurrentHashMap<>();

    @Override
    public Promise<WebsiteAuditReport> save(WebsiteAuditReport report) {
        byId.put(report.getReportId(), report);
        return Promise.of(report);
    }

    @Override
    public Promise<Optional<WebsiteAuditReport>> findLatestByWorkspace(DmWorkspaceId workspaceId) {
        return Promise.of(byId.values().stream()
            .filter(report -> report.getWorkspaceId().equals(workspaceId))
            .max(Comparator.comparing(WebsiteAuditReport::getGeneratedAt)));
    }
}
