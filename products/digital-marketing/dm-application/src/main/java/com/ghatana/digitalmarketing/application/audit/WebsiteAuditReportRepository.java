package com.ghatana.digitalmarketing.application.audit;

import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditReport;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository contract for website audit report persistence.
 *
 * @doc.type interface
 * @doc.purpose DMOS website audit report repository contract
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface WebsiteAuditReportRepository {

    Promise<WebsiteAuditReport> save(WebsiteAuditReport report);

    Promise<Optional<WebsiteAuditReport>> findLatestByWorkspace(DmWorkspaceId workspaceId);
}
