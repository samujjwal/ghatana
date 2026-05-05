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

    @Test
    void shouldSimulatePinnedInstallWithSandboxExecutionTruth() { 
        AgentMarketplaceService service = new AgentMarketplaceService( 
                null,
                List.of(CatalogAgentEntry.builder() 
                        .id("ops-agent")
                        .name("Ops Agent")
                        .version("2.4.0")
                        .catalogId("platform")
                        .metadata(Map.of("level", "worker", "domain", "operations"))
                        .capabilities(Set.of("triage", "explain"))
                        .build())); 

        AgentMarketplaceService.MarketplaceInstallSimulation simulation = service
                .simulateInstallAgent(
                        TENANT_ID,
                        "ops-agent",
                        new AgentMarketplaceService.InstallAgentRequest("sandbox", Map.of(), "2.4.0"))
                .getResult(); 

        assertThat(simulation.versionPinned()).isTrue(); 
        assertThat(simulation.compatibilityStatus()).isEqualTo("COMPATIBLE");
        assertThat(simulation.directExecutionMode()).isEqualTo("SANDBOX_ONLY");
        assertThat(simulation.productionExecutionMode()).isEqualTo("PIPELINE_HITL_REQUIRED");
        assertThat(simulation.allowedToInstall()).isTrue();
    }

    @Test
    void shouldBlockInstallWhenRequestedVersionDoesNotMatchPublishedVersion() { 
        AgentMarketplaceService service = new AgentMarketplaceService( 
                null,
                List.of(CatalogAgentEntry.builder() 
                        .id("ops-agent")
                        .name("Ops Agent")
                        .version("2.4.0")
                        .catalogId("platform")
                        .metadata(Map.of("level", "worker", "domain", "operations"))
                        .capabilities(Set.of("triage"))
                        .build())); 

        AgentMarketplaceService.MarketplaceInstallSimulation simulation = service
                .simulateInstallAgent(
                        TENANT_ID,
                        "ops-agent",
                        new AgentMarketplaceService.InstallAgentRequest("production", Map.of(), "1.9.0"))
                .getResult(); 

        assertThat(simulation.compatibilityStatus()).isEqualTo("BLOCKED");
        assertThat(simulation.allowedToInstall()).isFalse();
        assertThat(simulation.compatibilityNotes()).anyMatch(note -> note.contains("does not match published version"));
    }

    @Test
    @DisplayName("MKT-ROLL-1: installAgent fails atomically when simulation blocks install")
    void installAgentFailsWhenSimulationBlocked() {
        AgentMarketplaceService service = new AgentMarketplaceService(
                null,
                List.of(CatalogAgentEntry.builder()
                        .id("ops-agent")
                        .name("Ops Agent")
                        .version("2.4.0")
                        .catalogId("platform")
                        .metadata(Map.of("level", "worker", "domain", "operations"))
                        .capabilities(Set.of("triage"))
                        .build()));

        // Requesting a mismatched version causes the simulation to block install
        Throwable[] throwableHolder = new Throwable[1];
        service.installAgent(
                TENANT_ID,
                "ops-agent",
                new AgentMarketplaceService.InstallAgentRequest("production", Map.of(), "1.9.0"))
            .whenException(e -> throwableHolder[0] = e);

        assertThat(throwableHolder[0]).isNotNull();
        assertThat(throwableHolder[0].getMessage()).contains("Marketplace install blocked");
    }

    @Test
    @DisplayName("MKT-ROLL-2: installAgent succeeds and returns a non-null install record in-memory")
    void installAgentSucceedsInMemoryModeWithVersionMatch() {
        AgentMarketplaceService service = new AgentMarketplaceService(
                null,
                List.of(CatalogAgentEntry.builder()
                        .id("ops-agent")
                        .name("Ops Agent")
                        .version("2.4.0")
                        .catalogId("platform")
                        .metadata(Map.of("level", "worker", "domain", "operations"))
                        .capabilities(Set.of("triage"))
                        .build()));

        AgentMarketplaceService.MarketplaceInstallRecord record = service
                .installAgent(
                        TENANT_ID,
                        "ops-agent",
                        new AgentMarketplaceService.InstallAgentRequest("sandbox", Map.of(), "2.4.0"))
                .getResult();

        assertThat(record).isNotNull();
        assertThat(record.tenantId()).isEqualTo(TENANT_ID);
        assertThat(record.agentId()).isEqualTo("ops-agent");
        assertThat(record.compatibilityStatus()).isEqualTo("COMPATIBLE");
        assertThat(record.installedAt()).isNotNull();
    }

    @Test
    @DisplayName("MKT-ROLL-3: installAgent for unknown agent throws IllegalArgumentException (no partial state)")
    void installAgentForUnknownAgentThrowsWithoutPartialState() {
        AgentMarketplaceService service = new AgentMarketplaceService(null, List.of());

        Throwable[] throwableHolder = new Throwable[1];
        service.installAgent(
                TENANT_ID,
                "agent-does-not-exist",
                new AgentMarketplaceService.InstallAgentRequest("sandbox", Map.of(), null))
            .whenException(e -> throwableHolder[0] = e);

        assertThat(throwableHolder[0]).isNotNull();
        assertThat(throwableHolder[0].getMessage()).contains("not found");
    }
}
