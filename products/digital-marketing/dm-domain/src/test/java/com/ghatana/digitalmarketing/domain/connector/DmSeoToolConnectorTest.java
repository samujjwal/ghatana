package com.ghatana.digitalmarketing.domain.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DmSeoToolConnector}.
 *
 * @doc.type class
 * @doc.purpose Tests SEO tool connector domain model (P3-003)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmSeoToolConnector Tests")
class DmSeoToolConnectorTest {

    @Test
    @DisplayName("Should build valid connector")
    void shouldBuildValidConnector() {
        Instant now = Instant.now();
        DmSeoToolConnector connector = DmSeoToolConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My SEO Tool")
            .seoToolType("SEMrush")
            .apiUrl("https://api.semrush.com")
            .accessToken("token-abc123")
            .status(DmSeoToolConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertEquals("connector-1", connector.getId());
        assertEquals("tenant-1", connector.getTenantId());
        assertEquals("workspace-1", connector.getWorkspaceId());
        assertEquals("My SEO Tool", connector.getDisplayName());
        assertEquals("SEMrush", connector.getSeoToolType());
        assertEquals("https://api.semrush.com", connector.getApiUrl());
        assertEquals("token-abc123", connector.getAccessToken());
        assertEquals(DmSeoToolConnectorStatus.PENDING, connector.getStatus());
    }

    @Test
    @DisplayName("Should activate pending connector")
    void shouldActivatePendingConnector() {
        Instant now = Instant.now();
        DmSeoToolConnector connector = DmSeoToolConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My SEO Tool")
            .seoToolType("SEMrush")
            .apiUrl("https://api.semrush.com")
            .accessToken("token-abc123")
            .status(DmSeoToolConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmSeoToolConnector activated = connector.activate();

        assertEquals(DmSeoToolConnectorStatus.ACTIVE, activated.getStatus());
        assertNull(activated.getFailureReason());
        assertNotEquals(now, activated.getUpdatedAt());
    }

    @Test
    @DisplayName("Should fail to activate non-pending connector")
    void shouldFailToActivateNonPendingConnector() {
        Instant now = Instant.now();
        DmSeoToolConnector connector = DmSeoToolConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My SEO Tool")
            .seoToolType("SEMrush")
            .apiUrl("https://api.semrush.com")
            .accessToken("token-abc123")
            .status(DmSeoToolConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThrows(IllegalStateException.class, connector::activate);
    }

    @Test
    @DisplayName("Should mark connector as failed")
    void shouldMarkConnectorAsFailed() {
        Instant now = Instant.now();
        DmSeoToolConnector connector = DmSeoToolConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My SEO Tool")
            .seoToolType("SEMrush")
            .apiUrl("https://api.semrush.com")
            .accessToken("token-abc123")
            .status(DmSeoToolConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmSeoToolConnector failed = connector.markFailed("Invalid credentials");

        assertEquals(DmSeoToolConnectorStatus.FAILED, failed.getStatus());
        assertEquals("Invalid credentials", failed.getFailureReason());
    }

    @Test
    @DisplayName("Should suspend active connector")
    void shouldSuspendActiveConnector() {
        Instant now = Instant.now();
        DmSeoToolConnector connector = DmSeoToolConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My SEO Tool")
            .seoToolType("SEMrush")
            .apiUrl("https://api.semrush.com")
            .accessToken("token-abc123")
            .status(DmSeoToolConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmSeoToolConnector suspended = connector.suspend();

        assertEquals(DmSeoToolConnectorStatus.SUSPENDED, suspended.getStatus());
    }

    @Test
    @DisplayName("Should reactivate suspended connector")
    void shouldReactivateSuspendedConnector() {
        Instant now = Instant.now();
        DmSeoToolConnector connector = DmSeoToolConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My SEO Tool")
            .seoToolType("SEMrush")
            .apiUrl("https://api.semrush.com")
            .accessToken("token-abc123")
            .status(DmSeoToolConnectorStatus.SUSPENDED)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmSeoToolConnector reactivated = connector.reactivate();

        assertEquals(DmSeoToolConnectorStatus.ACTIVE, reactivated.getStatus());
    }

    @Test
    @DisplayName("Should disable connector")
    void shouldDisableConnector() {
        Instant now = Instant.now();
        DmSeoToolConnector connector = DmSeoToolConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My SEO Tool")
            .seoToolType("SEMrush")
            .apiUrl("https://api.semrush.com")
            .accessToken("token-abc123")
            .status(DmSeoToolConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmSeoToolConnector disabled = connector.disable();

        assertEquals(DmSeoToolConnectorStatus.DISABLED, disabled.getStatus());
    }

    @Test
    @DisplayName("Should fail to build connector with blank id")
    void shouldFailToBuildConnectorWithBlankId() {
        assertThrows(IllegalArgumentException.class, () -> 
            DmSeoToolConnector.builder()
                .id("")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .displayName("My SEO Tool")
                .seoToolType("SEMrush")
                .apiUrl("https://api.semrush.com")
                .accessToken("token-abc123")
                .status(DmSeoToolConnectorStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );
    }

    @Test
    @DisplayName("Should fail to build connector with null accessToken")
    void shouldFailToBuildConnectorWithNullAccessToken() {
        assertThrows(NullPointerException.class, () -> 
            DmSeoToolConnector.builder()
                .id("connector-1")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .displayName("My SEO Tool")
                .seoToolType("SEMrush")
                .apiUrl("https://api.semrush.com")
                .accessToken(null)
                .status(DmSeoToolConnectorStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );
    }

    @Test
    @DisplayName("Should build connector with configuration")
    void shouldBuildConnectorWithConfiguration() {
        Instant now = Instant.now();
        Map<String, String> config = Map.of("apiKey", "key123", "projectId", "proj456");
        DmSeoToolConnector connector = DmSeoToolConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My SEO Tool")
            .seoToolType("SEMrush")
            .apiUrl("https://api.semrush.com")
            .accessToken("token-abc123")
            .configuration(config)
            .status(DmSeoToolConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertEquals(config, connector.getConfiguration());
    }
}
