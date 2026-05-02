package com.ghatana.digitalmarketing.application.audit;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.audit.AuditSeverity;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditFinding;
import com.ghatana.digitalmarketing.domain.audit.WebsiteAuditReport;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link WebsiteAuditService}.
 *
 * @doc.type class
 * @doc.purpose DMOS MVP website diagnostics service with evidence-backed findings
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class WebsiteAuditServiceImpl implements WebsiteAuditService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final WebsiteAuditReportRepository repository;

    public WebsiteAuditServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            WebsiteAuditReportRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<WebsiteAuditReport> runAudit(DmOperationContext ctx, RunWebsiteAuditCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "audits/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to run website audit"));
                }

                List<WebsiteAuditFinding> findings = generateFindings(command);
                WebsiteAuditReport report = WebsiteAuditReport.builder()
                    .reportId(UUID.randomUUID().toString())
                    .workspaceId(ctx.getWorkspaceId())
                    .websiteUrl(command.websiteUrl())
                    .findings(findings)
                    .generatedAt(Instant.now())
                    .generatedBy(ctx.getActor().getPrincipalId())
                    .build();

                return repository.save(report)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx,
                        "audits/" + saved.getReportId(),
                        "website-audit-generated",
                        Map.of(
                            "websiteUrl", saved.getWebsiteUrl(),
                            "findingCount", Integer.toString(saved.getFindings().size())
                        )
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<WebsiteAuditReport> getLatestAudit(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "audits/*", "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read website audit"));
                }
                return repository.findLatestByWorkspace(ctx.getWorkspaceId())
                    .then(report -> report
                        .map(Promise::of)
                        .orElseGet(() -> Promise.ofException(new NoSuchElementException("Website audit report not found"))));
            });
    }

    private static List<WebsiteAuditFinding> generateFindings(RunWebsiteAuditCommand command) {
        List<WebsiteAuditFinding> findings = new ArrayList<>();

        if (!command.reachable()) {
            findings.add(new WebsiteAuditFinding(
                AuditSeverity.CRITICAL,
                "AVAILABILITY",
                "Website was unreachable during audit run",
                "Unavailable websites block conversion and ad quality objectives.",
                "Fix hosting/DNS uptime and retry audit.",
                command.websiteUrl()
            ));
            return findings;
        }

        if (command.responseTimeMs() > 3000) {
            findings.add(new WebsiteAuditFinding(
                AuditSeverity.WARNING,
                "PERFORMANCE",
                "Response time observed: " + command.responseTimeMs() + "ms",
                "Slow pages reduce conversion and quality scores.",
                "Optimize render path and media payloads.",
                command.websiteUrl()
            ));
        }

        if (command.title() == null || command.title().isBlank()) {
            findings.add(new WebsiteAuditFinding(
                AuditSeverity.WARNING,
                "SEO",
                "Missing page title",
                "Search engines rely on title for relevance and CTR.",
                "Add concise, intent-aligned title tag.",
                command.websiteUrl()
            ));
        }

        if (command.metaDescription() == null || command.metaDescription().isBlank()) {
            findings.add(new WebsiteAuditFinding(
                AuditSeverity.INFO,
                "SEO",
                "Missing meta description",
                "Meta descriptions help communicate value in search snippets.",
                "Add meta description with clear value proposition.",
                command.websiteUrl()
            ));
        }

        if (command.h1() == null || command.h1().isBlank()) {
            findings.add(new WebsiteAuditFinding(
                AuditSeverity.WARNING,
                "MESSAGING",
                "Missing H1 headline",
                "Pages need a clear primary message for visitors and search context.",
                "Add a clear H1 aligned to offer and target intent.",
                command.websiteUrl()
            ));
        }

        if (!command.trackingTagDetected()) {
            findings.add(new WebsiteAuditFinding(
                AuditSeverity.WARNING,
                "TRACKING",
                "No tracking tag detected",
                "Without measurement tags, attribution and optimization are limited.",
                "Install baseline analytics/ads tracking tags.",
                command.websiteUrl()
            ));
        }

        if (!command.hasLeadForm()) {
            findings.add(new WebsiteAuditFinding(
                AuditSeverity.WARNING,
                "CONVERSION",
                "No lead capture form detected",
                "Lead generation pages require a clear conversion path.",
                "Add a visible lead form with clear call to action.",
                command.websiteUrl()
            ));
        }

        if (findings.isEmpty()) {
            findings.add(new WebsiteAuditFinding(
                AuditSeverity.INFO,
                "SUMMARY",
                "No high-priority gaps detected from baseline checks",
                "Current MVP checks did not find blocking technical issues.",
                "Proceed to deeper audit and strategy generation.",
                command.websiteUrl()
            ));
        }

        return List.copyOf(findings);
    }
}
