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
    void shouldMergeCatalogEntriesWithTenantPublishedListings() {
        AgentMarketplaceService service = new AgentMarketplaceService(
                null,
                List.of(CatalogAgentEntry.builder()
                        .id("catalog-agent")
                        .name("Catalog Agent")
                        .description("Built into the central catalog")
                        .catalogId("platform")
                        .metadata(Map.of("level", "expert", "domain", "governance", "tags", List.of("trusted")))
                        .capabilities(Set.of("triage", "explain"))
                        .build()));

        AgentMarketplaceService.PublishAgentRequest publishRequest = new AgentMarketplaceService.PublishAgentRequest(
                "tenant-agent",
                "Tenant Agent",
                "Published from the tenant workspace",
                "1.1.0",
                "operations",
                "worker",
                List.of("deploy"),
                List.of("beta"),
                "tenant-a");

        service.publishAgent(TENANT_ID, publishRequest);
        List<AgentMarketplaceService.MarketplaceAgentListing> listings = service
                .listAgents(TENANT_ID, null, null, 20)
                .getResult();

        assertThat(listings).extracting(AgentMarketplaceService.MarketplaceAgentListing::id)
                .contains("catalog-agent", "tenant-agent");
        assertThat(listings).filteredOn(listing -> listing.id().equals("tenant-agent"))
                .singleElement()
                .satisfies(listing -> {
                    assertThat(listing.source()).isEqualTo("tenant");
                    assertThat(listing.owner()).isEqualTo("tenant-a");
                });
    }

    @Test
    void shouldAggregateMarketplaceReviews() {
        AgentMarketplaceService service = new AgentMarketplaceService(
                null,
                List.of(CatalogAgentEntry.builder()
                        .id("reviewed-agent")
                        .name("Reviewed Agent")
                        .catalogId("platform")
                        .capabilities(Set.of("triage"))
                        .build()));

        Promise<AgentMarketplaceService.MarketplaceReview> first = service.addReview(
                TENANT_ID,
                "reviewed-agent",
                new AgentMarketplaceService.CreateReviewRequest("sam", 5, "Excellent", "Strong operator fit"));
        Promise<AgentMarketplaceService.MarketplaceReview> second = service.addReview(
                TENANT_ID,
                "reviewed-agent",
                new AgentMarketplaceService.CreateReviewRequest("alex", 3, "Good", "Needs more docs"));

        first.getResult();
        second.getResult();

        AgentMarketplaceService.MarketplaceAgentDetail detail = service
                .getAgent(TENANT_ID, "reviewed-agent")
                .getResult()
                .orElseThrow();

        assertThat(detail.reviews()).hasSize(2);
        assertThat(detail.listing().averageRating()).isEqualTo(4.0);
        assertThat(detail.listing().reviewCount()).isEqualTo(2);
    }
}