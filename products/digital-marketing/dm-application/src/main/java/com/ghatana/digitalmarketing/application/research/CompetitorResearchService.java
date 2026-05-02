package com.ghatana.digitalmarketing.application.research;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.research.CompetitorResearchSnapshot;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Application service for competitor and keyword research workflow.
 *
 * @doc.type interface
 * @doc.purpose DMOS competitor and keyword research service for F1-011
 * @doc.layer product
 * @doc.pattern ApplicationService
 */
public interface CompetitorResearchService {

    /**
     * Runs competitor and keyword research using user-provided domains and inferred service area keywords.
     *
     * @param ctx     operation context carrying tenant, workspace, and actor
     * @param command inputs for the research run
     * @return promise resolving to the generated research snapshot
     */
    Promise<CompetitorResearchSnapshot> runResearch(DmOperationContext ctx, RunCompetitorResearchCommand command);

    /**
     * Retrieves the most recent research snapshot for the caller's workspace.
     *
     * @param ctx operation context
     * @return promise resolving to the latest snapshot
     */
    Promise<CompetitorResearchSnapshot> getLatestResearch(DmOperationContext ctx);

    /**
     * Command for triggering a competitor and keyword research run.
     *
     * @param competitorDomains  list of competitor domains supplied by the user; may be empty
     * @param serviceArea        geographic area or service type for keyword context
     * @param primaryOffer       the main offer or service being promoted
     */
    record RunCompetitorResearchCommand(
        List<String> competitorDomains,
        String serviceArea,
        String primaryOffer
    ) {
        public RunCompetitorResearchCommand {
            competitorDomains = competitorDomains != null ? List.copyOf(competitorDomains) : List.of();
            if (serviceArea == null || serviceArea.isBlank()) {
                throw new IllegalArgumentException("serviceArea must not be blank");
            }
            if (primaryOffer == null || primaryOffer.isBlank()) {
                throw new IllegalArgumentException("primaryOffer must not be blank");
            }
        }
    }
}
