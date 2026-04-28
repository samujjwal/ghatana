package com.ghatana.aep.server.marketplace;

import com.ghatana.agent.catalog.CatalogAgentEntry;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type test
 * @doc.purpose Regression coverage for marketplace listing, publishing, and reviews
 * @doc.layer product
 */
@DisplayName("AgentMarketplaceService")
class AgentMarketplaceServiceTest {

    private static final String TENANT_ID = "tenant-a";

    @Test
    void shouldMergeCatalogEntriesWithTenantPublishedListings() { // GH-90000
        AgentMarketplaceService service = new AgentMarketplaceService( // GH-90000
                null,
                List.of(CatalogAgentEntry.builder() // GH-90000
                        .id("catalog-agent")
                        .name("Catalog Agent")
                        .description("Built into the central catalog")
                        .catalogId("platform")
                        .metadata(Map.of("level", "expert", "domain", "governance", "tags", List.of("trusted")))
                        .capabilities(Set.of("triage", "explain")) // GH-90000
                        .build())); // GH-90000

        AgentMarketplaceService.PublishAgentRequest publishRequest = new AgentMarketplaceService.PublishAgentRequest( // GH-90000
                "tenant-agent",
                "Tenant Agent",
                "Published from the tenant workspace",
                "1.1.0",
                "operations",
                "worker",
                List.of("deploy"),
                List.of("beta"),
                "tenant-a");

        service.publishAgent(TENANT_ID, publishRequest); // GH-90000
        List<AgentMarketplaceService.MarketplaceAgentListing> listings = service
                .listAgents(TENANT_ID, null, null, 20) // GH-90000
                .getResult(); // GH-90000

        assertThat(listings).extracting(AgentMarketplaceService.MarketplaceAgentListing::id) // GH-90000
                .contains("catalog-agent", "tenant-agent"); // GH-90000
        assertThat(listings).filteredOn(listing -> listing.id().equals("tenant-agent"))
                .singleElement() // GH-90000
                .satisfies(listing -> { // GH-90000
                    assertThat(listing.source()).isEqualTo("tenant");
                    assertThat(listing.owner()).isEqualTo("tenant-a");
                });
    }

    @Test
    void shouldAggregateMarketplaceReviews() { // GH-90000
        AgentMarketplaceService service = new AgentMarketplaceService( // GH-90000
                null,
                List.of(CatalogAgentEntry.builder() // GH-90000
                        .id("reviewed-agent")
                        .name("Reviewed Agent")
                        .catalogId("platform")
                        .capabilities(Set.of("triage"))
                        .build())); // GH-90000

        Promise<AgentMarketplaceService.MarketplaceReview> first = service.addReview( // GH-90000
                TENANT_ID,
                "reviewed-agent",
                new AgentMarketplaceService.CreateReviewRequest("sam", 5, "Excellent", "Strong operator fit")); // GH-90000
        Promise<AgentMarketplaceService.MarketplaceReview> second = service.addReview( // GH-90000
                TENANT_ID,
                "reviewed-agent",
                new AgentMarketplaceService.CreateReviewRequest("alex", 3, "Good", "Needs more docs")); // GH-90000

        first.getResult(); // GH-90000
        second.getResult(); // GH-90000

        AgentMarketplaceService.MarketplaceAgentDetail detail = service
                .getAgent(TENANT_ID, "reviewed-agent") // GH-90000
                .getResult() // GH-90000
                .orElseThrow(); // GH-90000

        assertThat(detail.reviews()).hasSize(2); // GH-90000
        assertThat(detail.listing().averageRating()).isEqualTo(4.0); // GH-90000
        assertThat(detail.listing().reviewCount()).isEqualTo(2); // GH-90000
    }

    @Test
    void shouldSimulatePinnedInstallWithSandboxExecutionTruth() { // GH-90000
        AgentMarketplaceService service = new AgentMarketplaceService( // GH-90000
                null,
                List.of(CatalogAgentEntry.builder() // GH-90000
                        .id("ops-agent")
                        .name("Ops Agent")
                        .version("2.4.0")
                        .catalogId("platform")
                        .metadata(Map.of("level", "worker", "domain", "operations"))
                        .capabilities(Set.of("triage", "explain"))
                        .build())); // GH-90000

        AgentMarketplaceService.MarketplaceInstallSimulation simulation = service
                .simulateInstallAgent(
                        TENANT_ID,
                        "ops-agent",
                        new AgentMarketplaceService.InstallAgentRequest("sandbox", Map.of(), "2.4.0"))
                .getResult(); // GH-90000

        assertThat(simulation.versionPinned()).isTrue(); // GH-90000
        assertThat(simulation.compatibilityStatus()).isEqualTo("COMPATIBLE");
        assertThat(simulation.directExecutionMode()).isEqualTo("SANDBOX_ONLY");
        assertThat(simulation.productionExecutionMode()).isEqualTo("PIPELINE_HITL_REQUIRED");
        assertThat(simulation.allowedToInstall()).isTrue();
    }

    @Test
    void shouldBlockInstallWhenRequestedVersionDoesNotMatchPublishedVersion() { // GH-90000
        AgentMarketplaceService service = new AgentMarketplaceService( // GH-90000
                null,
                List.of(CatalogAgentEntry.builder() // GH-90000
                        .id("ops-agent")
                        .name("Ops Agent")
                        .version("2.4.0")
                        .catalogId("platform")
                        .metadata(Map.of("level", "worker", "domain", "operations"))
                        .capabilities(Set.of("triage"))
                        .build())); // GH-90000

        AgentMarketplaceService.MarketplaceInstallSimulation simulation = service
                .simulateInstallAgent(
                        TENANT_ID,
                        "ops-agent",
                        new AgentMarketplaceService.InstallAgentRequest("production", Map.of(), "1.9.0"))
                .getResult(); // GH-90000

        assertThat(simulation.compatibilityStatus()).isEqualTo("BLOCKED");
        assertThat(simulation.allowedToInstall()).isFalse();
        assertThat(simulation.compatibilityNotes()).anyMatch(note -> note.contains("does not match published version"));
    }
}
