package com.ghatana.digitalmarketing.application.research;

import com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter;
import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.research.CompetitorFinding;
import com.ghatana.digitalmarketing.domain.research.CompetitorResearchSnapshot;
import com.ghatana.digitalmarketing.domain.research.KeywordFinding;
import com.ghatana.digitalmarketing.domain.research.KeywordIntent;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of {@link CompetitorResearchService}.
 *
 * <p>MVP research is deterministic and inferred from the provided competitor domains and service area.
 * All findings are marked with a source to maintain data provenance per acceptance criteria.
 *
 * @doc.type class
 * @doc.purpose DMOS competitor and keyword research workflow service for F1-011
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public final class CompetitorResearchServiceImpl implements CompetitorResearchService {

    private final DigitalMarketingKernelAdapter kernelAdapter;
    private final CompetitorResearchRepository repository;

    public CompetitorResearchServiceImpl(
            DigitalMarketingKernelAdapter kernelAdapter,
            CompetitorResearchRepository repository) {
        this.kernelAdapter = Objects.requireNonNull(kernelAdapter, "kernelAdapter must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Promise<CompetitorResearchSnapshot> runResearch(
            DmOperationContext ctx,
            RunCompetitorResearchCommand command) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        Objects.requireNonNull(command, "command must not be null");

        return kernelAdapter.isAuthorized(ctx, "research/*", "write")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to run competitor research"));
                }

                List<CompetitorFinding> competitorFindings = generateCompetitorFindings(command);
                List<KeywordFinding> keywordFindings = generateKeywordFindings(command);
                String opportunitySummary = buildOpportunitySummary(command, competitorFindings, keywordFindings);

                CompetitorResearchSnapshot snapshot = CompetitorResearchSnapshot.builder()
                    .snapshotId(UUID.randomUUID().toString())
                    .workspaceId(ctx.getWorkspaceId())
                    .competitorFindings(competitorFindings)
                    .keywordFindings(keywordFindings)
                    .opportunitySummary(opportunitySummary)
                    .generatedAt(Instant.now())
                    .generatedBy(ctx.getActor().getPrincipalId())
                    .build();

                return repository.save(snapshot)
                    .then(saved -> kernelAdapter.recordAudit(
                        ctx,
                        "research/" + saved.getSnapshotId(),
                        "competitor-research-generated",
                        Map.of(
                            "competitorCount", Integer.toString(saved.getCompetitorFindings().size()),
                            "keywordCount", Integer.toString(saved.getKeywordFindings().size()),
                            "serviceArea", command.serviceArea()
                        )
                    ).map(__ -> saved));
            });
    }

    @Override
    public Promise<CompetitorResearchSnapshot> getLatestResearch(DmOperationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");

        return kernelAdapter.isAuthorized(ctx, "research/*", "read")
            .then(allowed -> {
                if (!allowed) {
                    return Promise.ofException(new SecurityException("Actor not authorized to read competitor research"));
                }
                return repository.findLatestByWorkspace(ctx.getWorkspaceId())
                    .then(result -> result
                        .map(Promise::of)
                        .orElseGet(() -> Promise.ofException(
                            new NoSuchElementException("Competitor research snapshot not found"))));
            });
    }

    private static List<CompetitorFinding> generateCompetitorFindings(RunCompetitorResearchCommand command) {
        List<CompetitorFinding> findings = new ArrayList<>();
        for (String domain : command.competitorDomains()) {
            if (domain == null || domain.isBlank()) {
                continue;
            }
            findings.add(new CompetitorFinding(
                domain,
                "Competitor domain provided by user: " + domain,
                "Competitor is active in the " + command.serviceArea() + " market. "
                    + "Recommend reviewing their offer, messaging, and ad presence.",
                true,
                "user-provided"
            ));
        }
        if (findings.isEmpty()) {
            findings.add(new CompetitorFinding(
                "unknown",
                "No competitor domains were provided by the user",
                "Without known competitors, keyword strategy defaults to service-area generic terms. "
                    + "Consider adding competitor domains in workspace settings.",
                true,
                "inferred-mvp"
            ));
        }
        return findings;
    }

    private static List<KeywordFinding> generateKeywordFindings(RunCompetitorResearchCommand command) {
        List<KeywordFinding> findings = new ArrayList<>();
        String area = command.serviceArea();
        String offer = command.primaryOffer();

        findings.add(new KeywordFinding(
            offer + " " + area,
            KeywordIntent.TRANSACTIONAL,
            0.92,
            "Search campaign — exact match head term",
            "Core transactional intent combining offer and geography",
            "inferred-mvp"
        ));

        findings.add(new KeywordFinding(
            "best " + offer + " in " + area,
            KeywordIntent.COMMERCIAL_INVESTIGATION,
            0.78,
            "Search campaign — commercial investigation",
            "Comparison intent typically used before contacting a provider",
            "inferred-mvp"
        ));

        findings.add(new KeywordFinding(
            offer + " near me",
            KeywordIntent.TRANSACTIONAL,
            0.88,
            "Search campaign — local near-me variation",
            "High-volume local search modifier for service area businesses",
            "inferred-mvp"
        ));

        findings.add(new KeywordFinding(
            "how to choose " + offer,
            KeywordIntent.INFORMATIONAL,
            0.55,
            "Content / display campaign — top-of-funnel education",
            "Informational intent for buyers early in decision process",
            "inferred-mvp"
        ));

        findings.add(new KeywordFinding(
            offer + " " + area + " cost",
            KeywordIntent.COMMERCIAL_INVESTIGATION,
            0.70,
            "Search campaign — budget-aware searchers",
            "Cost/price queries indicate high purchase intent but price sensitivity",
            "inferred-mvp"
        ));

        return findings;
    }

    private static String buildOpportunitySummary(
            RunCompetitorResearchCommand command,
            List<CompetitorFinding> competitorFindings,
            List<KeywordFinding> keywordFindings) {
        int competitorCount = (int) competitorFindings.stream()
            .filter(f -> !"unknown".equals(f.competitorDomain()))
            .count();

        long transactionalCount = keywordFindings.stream()
            .filter(k -> k.intent() == KeywordIntent.TRANSACTIONAL)
            .count();

        if (competitorCount == 0) {
            return "No direct competitors identified. " + transactionalCount
                + " transactional keywords generated for '" + command.primaryOffer()
                + "' in '" + command.serviceArea() + "'. Add competitors in settings for deeper gap analysis.";
        }

        return competitorCount + " competitor(s) analysed for '" + command.primaryOffer()
            + "' in '" + command.serviceArea() + "'. "
            + transactionalCount + " transactional keywords identified. "
            + "All findings are inferred from user-provided data; validate before campaign launch.";
    }
}
