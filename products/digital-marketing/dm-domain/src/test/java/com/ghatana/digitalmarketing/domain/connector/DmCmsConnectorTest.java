package com.ghatana.digitalmarketing.domain.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DmCmsConnector}.
 *
 * @doc.type class
 * @doc.purpose Tests CMS connector domain model (P3-003)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmCmsConnector Tests")
class DmCmsConnectorTest {

    @Test
    @DisplayName("Should build valid connector")
    void shouldBuildValidConnector() {
        Instant now = Instant.now();
        DmCmsConnector connector = DmCmsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My CMS")
            .cmsType("WordPress")
            .apiUrl("https://example.com/wp-json")
            .accessToken("token-abc123")
            .status(DmCmsConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertEquals("connector-1", connector.getId());
        assertEquals("tenant-1", connector.getTenantId());
        assertEquals("workspace-1", connector.getWorkspaceId());
        assertEquals("My CMS", connector.getDisplayName());
        assertEquals("WordPress", connector.getCmsType());
        assertEquals("https://example.com/wp-json", connector.getApiUrl());
        assertEquals("token-abc123", connector.getAccessToken());
        assertEquals(DmCmsConnectorStatus.PENDING, connector.getStatus());
    }

    @Test
    @DisplayName("Should activate pending connector")
    void shouldActivatePendingConnector() {
        Instant now = Instant.now();
        DmCmsConnector connector = DmCmsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My CMS")
            .cmsType("WordPress")
            .apiUrl("https://example.com/wp-json")
            .accessToken("token-abc123")
            .status(DmCmsConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmCmsConnector activated = connector.activate();

        assertEquals(DmCmsConnectorStatus.ACTIVE, activated.getStatus());
        assertNull(activated.getFailureReason());
        assertNotEquals(now, activated.getUpdatedAt());
    }

    @Test
    @DisplayName("Should fail to activate non-pending connector")
    void shouldFailToActivateNonPendingConnector() {
        Instant now = Instant.now();
        DmCmsConnector connector = DmCmsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My CMS")
            .cmsType("WordPress")
            .apiUrl("https://example.com/wp-json")
            .accessToken("token-abc123")
            .status(DmCmsConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThrows(IllegalStateException.class, connector::activate);
    }

    @Test
    @DisplayName("Should mark connector as failed")
    void shouldMarkConnectorAsFailed() {
        Instant now = Instant.now();
        DmCmsConnector connector = DmCmsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My CMS")
            .cmsType("WordPress")
            .apiUrl("https://example.com/wp-json")
            .accessToken("token-abc123")
            .status(DmCmsConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmCmsConnector failed = connector.markFailed("Invalid credentials");

        assertEquals(DmCmsConnectorStatus.FAILED, failed.getStatus());
        assertEquals("Invalid credentials", failed.getFailureReason());
    }

    @Test
    @DisplayName("Should suspend active connector")
    void shouldSuspendActiveConnector() {
        Instant now = Instant.now();
        DmCmsConnector connector = DmCmsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My CMS")
            .cmsType("WordPress")
            .apiUrl("https://example.com/wp-json")
            .accessToken("token-abc123")
            .status(DmCmsConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmCmsConnector suspended = connector.suspend();

        assertEquals(DmCmsConnectorStatus.SUSPENDED, suspended.getStatus());
    }

    @Test
    @DisplayName("Should reactivate suspended connector")
    void shouldReactivateSuspendedConnector() {
        Instant now = Instant.now();
        DmCmsConnector connector = DmCmsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My CMS")
            .cmsType("WordPress")
            .apiUrl("https://example.com/wp-json")
            .accessToken("token-abc123")
            .status(DmCmsConnectorStatus.SUSPENDED)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmCmsConnector reactivated = connector.reactivate();

        assertEquals(DmCmsConnectorStatus.ACTIVE, reactivated.getStatus());
    }

    @Test
    @DisplayName("Should disable connector")
    void shouldDisableConnector() {
        Instant now = Instant.now();
        DmCmsConnector connector = DmCmsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My CMS")
            .cmsType("WordPress")
            .apiUrl("https://example.com/wp-json")
            .accessToken("token-abc123")
            .status(DmCmsConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmCmsConnector disabled = connector.disable();

        assertEquals(DmCmsConnectorStatus.DISABLED, disabled.getStatus());
    }

    @Test
    @DisplayName("Should fail to build connector with blank id")
    void shouldFailToBuildConnectorWithBlankId() {
        assertThrows(IllegalArgumentException.class, () -> 
            DmCmsConnector.builder()
                .id("")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .displayName("My CMS")
                .cmsType("WordPress")
                .apiUrl("https://example.com/wp-json")
                .accessToken("token-abc123")
                .status(DmCmsConnectorStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );
    }

    @Test
    @DisplayName("Should fail to build connector with null accessToken")
    void shouldFailToBuildConnectorWithNullAccessToken() {
        assertThrows(NullPointerException.class, () -> 
            DmCmsConnector.builder()
                .id("connector-1")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .displayName("My CMS")
                .cmsType("WordPress")
                .apiUrl("https://example.com/wp-json")
                .accessToken(null)
                .status(DmCmsConnectorStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );
    }

    @Test
    @DisplayName("Should build connector with configuration")
    void shouldBuildConnectorWithConfiguration() {
        Instant now = Instant.now();
        Map<String, String> config = Map.of("apiKey", "key123", "siteId", "site456");
        DmCmsConnector connector = DmCmsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My CMS")
            .cmsType("WordPress")
            .apiUrl("https://example.com/wp-json")
            .accessToken("token-abc123")
            .configuration(config)
            .status(DmCmsConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertEquals(config, connector.getConfiguration());
    }
}
