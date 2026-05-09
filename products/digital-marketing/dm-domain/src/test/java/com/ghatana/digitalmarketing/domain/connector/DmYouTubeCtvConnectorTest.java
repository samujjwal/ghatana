package com.ghatana.digitalmarketing.domain.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DmYouTubeCtvConnector}.
 *
 * @doc.type class
 * @doc.purpose Tests YouTube/CTV connector domain model (P3-003)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmYouTubeCtvConnector Tests")
class DmYouTubeCtvConnectorTest {

    @Test
    @DisplayName("Should build valid connector")
    void shouldBuildValidConnector() {
        Instant now = Instant.now();
        DmYouTubeCtvConnector connector = DmYouTubeCtvConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My YouTube CTV")
            .channelId("channel-123")
            .accessToken("token-abc123")
            .status(DmYouTubeCtvConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertEquals("connector-1", connector.getId());
        assertEquals("tenant-1", connector.getTenantId());
        assertEquals("workspace-1", connector.getWorkspaceId());
        assertEquals("My YouTube CTV", connector.getDisplayName());
        assertEquals("channel-123", connector.getChannelId());
        assertEquals("token-abc123", connector.getAccessToken());
        assertEquals(DmYouTubeCtvConnectorStatus.PENDING, connector.getStatus());
    }

    @Test
    @DisplayName("Should activate pending connector")
    void shouldActivatePendingConnector() {
        Instant now = Instant.now();
        DmYouTubeCtvConnector connector = DmYouTubeCtvConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My YouTube CTV")
            .channelId("channel-123")
            .accessToken("token-abc123")
            .status(DmYouTubeCtvConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmYouTubeCtvConnector activated = connector.activate();

        assertEquals(DmYouTubeCtvConnectorStatus.ACTIVE, activated.getStatus());
        assertNull(activated.getFailureReason());
        assertFalse(activated.getUpdatedAt().isBefore(now));
    }

    @Test
    @DisplayName("Should fail to activate non-pending connector")
    void shouldFailToActivateNonPendingConnector() {
        Instant now = Instant.now();
        DmYouTubeCtvConnector connector = DmYouTubeCtvConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My YouTube CTV")
            .channelId("channel-123")
            .accessToken("token-abc123")
            .status(DmYouTubeCtvConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThrows(IllegalStateException.class, connector::activate);
    }

    @Test
    @DisplayName("Should mark connector as failed")
    void shouldMarkConnectorAsFailed() {
        Instant now = Instant.now();
        DmYouTubeCtvConnector connector = DmYouTubeCtvConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My YouTube CTV")
            .channelId("channel-123")
            .accessToken("token-abc123")
            .status(DmYouTubeCtvConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmYouTubeCtvConnector failed = connector.markFailed("Invalid credentials");

        assertEquals(DmYouTubeCtvConnectorStatus.FAILED, failed.getStatus());
        assertEquals("Invalid credentials", failed.getFailureReason());
    }

    @Test
    @DisplayName("Should suspend active connector")
    void shouldSuspendActiveConnector() {
        Instant now = Instant.now();
        DmYouTubeCtvConnector connector = DmYouTubeCtvConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My YouTube CTV")
            .channelId("channel-123")
            .accessToken("token-abc123")
            .status(DmYouTubeCtvConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmYouTubeCtvConnector suspended = connector.suspend();

        assertEquals(DmYouTubeCtvConnectorStatus.SUSPENDED, suspended.getStatus());
    }

    @Test
    @DisplayName("Should reactivate suspended connector")
    void shouldReactivateSuspendedConnector() {
        Instant now = Instant.now();
        DmYouTubeCtvConnector connector = DmYouTubeCtvConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My YouTube CTV")
            .channelId("channel-123")
            .accessToken("token-abc123")
            .status(DmYouTubeCtvConnectorStatus.SUSPENDED)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmYouTubeCtvConnector reactivated = connector.reactivate();

        assertEquals(DmYouTubeCtvConnectorStatus.ACTIVE, reactivated.getStatus());
    }

    @Test
    @DisplayName("Should disable connector")
    void shouldDisableConnector() {
        Instant now = Instant.now();
        DmYouTubeCtvConnector connector = DmYouTubeCtvConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My YouTube CTV")
            .channelId("channel-123")
            .accessToken("token-abc123")
            .status(DmYouTubeCtvConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmYouTubeCtvConnector disabled = connector.disable();

        assertEquals(DmYouTubeCtvConnectorStatus.DISABLED, disabled.getStatus());
    }

    @Test
    @DisplayName("Should fail to build connector with blank id")
    void shouldFailToBuildConnectorWithBlankId() {
        assertThrows(IllegalArgumentException.class, () -> 
            DmYouTubeCtvConnector.builder()
                .id("")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .displayName("My YouTube CTV")
                .channelId("channel-123")
                .accessToken("token-abc123")
                .status(DmYouTubeCtvConnectorStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );
    }

    @Test
    @DisplayName("Should fail to build connector with null accessToken")
    void shouldFailToBuildConnectorWithNullAccessToken() {
        assertThrows(NullPointerException.class, () -> 
            DmYouTubeCtvConnector.builder()
                .id("connector-1")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .displayName("My YouTube CTV")
                .channelId("channel-123")
                .accessToken(null)
                .status(DmYouTubeCtvConnectorStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );
    }
}
