package com.ghatana.digitalmarketing.application.audit;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditReport;
import io.activej.promise.Promise;

/**
 * Application service for deterministic MVP website diagnostics.
 *
 * @doc.type interface
 * @doc.purpose DMOS website audit generation service for F1-010
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface WebsiteAuditService {

    Promise<WebsiteAuditReport> runAudit(DmOperationContext ctx, RunWebsiteAuditCommand command);

    Promise<WebsiteAuditReport> getLatestAudit(DmOperationContext ctx);

    record RunWebsiteAuditCommand(
        String websiteUrl,
        boolean reachable,
        int responseTimeMs,
        String title,
        String metaDescription,
        String h1,
        boolean trackingTagDetected,
        boolean hasLeadForm
    ) {
        public RunWebsiteAuditCommand {
            if (websiteUrl == null || websiteUrl.isBlank()) {
                throw new IllegalArgumentException("websiteUrl must not be blank");
            }
            if (responseTimeMs < 0) {
                throw new IllegalArgumentException("responseTimeMs must not be negative");
            }
        }
    }
}
