package com.ghatana.digitalmarketing.domain.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DmTikTokAdsConnector}.
 *
 * @doc.type class
 * @doc.purpose Tests TikTok Ads connector domain model (P3-003)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmTikTokAdsConnector Tests")
class DmTikTokAdsConnectorTest {

    @Test
    @DisplayName("Should build valid connector")
    void shouldBuildValidConnector() {
        Instant now = Instant.now();
        DmTikTokAdsConnector connector = DmTikTokAdsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My TikTok Ads")
            .advertiserId("advertiser-123")
            .accessToken("token-abc123")
            .status(DmTikTokAdsConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertEquals("connector-1", connector.getId());
        assertEquals("tenant-1", connector.getTenantId());
        assertEquals("workspace-1", connector.getWorkspaceId());
        assertEquals("My TikTok Ads", connector.getDisplayName());
        assertEquals("advertiser-123", connector.getAdvertiserId());
        assertEquals("token-abc123", connector.getAccessToken());
        assertEquals(DmTikTokAdsConnectorStatus.PENDING, connector.getStatus());
    }

    @Test
    @DisplayName("Should activate pending connector")
    void shouldActivatePendingConnector() {
        Instant now = Instant.now();
        DmTikTokAdsConnector connector = DmTikTokAdsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My TikTok Ads")
            .advertiserId("advertiser-123")
            .accessToken("token-abc123")
            .status(DmTikTokAdsConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmTikTokAdsConnector activated = connector.activate();

        assertEquals(DmTikTokAdsConnectorStatus.ACTIVE, activated.getStatus());
        assertNull(activated.getFailureReason());
        assertNotEquals(now, activated.getUpdatedAt());
    }

    @Test
    @DisplayName("Should fail to activate non-pending connector")
    void shouldFailToActivateNonPendingConnector() {
        Instant now = Instant.now();
        DmTikTokAdsConnector connector = DmTikTokAdsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My TikTok Ads")
            .advertiserId("advertiser-123")
            .accessToken("token-abc123")
            .status(DmTikTokAdsConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThrows(IllegalStateException.class, connector::activate);
    }

    @Test
    @DisplayName("Should mark connector as failed")
    void shouldMarkConnectorAsFailed() {
        Instant now = Instant.now();
        DmTikTokAdsConnector connector = DmTikTokAdsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My TikTok Ads")
            .advertiserId("advertiser-123")
            .accessToken("token-abc123")
            .status(DmTikTokAdsConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmTikTokAdsConnector failed = connector.markFailed("Invalid credentials");

        assertEquals(DmTikTokAdsConnectorStatus.FAILED, failed.getStatus());
        assertEquals("Invalid credentials", failed.getFailureReason());
    }

    @Test
    @DisplayName("Should suspend active connector")
    void shouldSuspendActiveConnector() {
        Instant now = Instant.now();
        DmTikTokAdsConnector connector = DmTikTokAdsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My TikTok Ads")
            .advertiserId("advertiser-123")
            .accessToken("token-abc123")
            .status(DmTikTokAdsConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmTikTokAdsConnector suspended = connector.suspend();

        assertEquals(DmTikTokAdsConnectorStatus.SUSPENDED, suspended.getStatus());
    }

    @Test
    @DisplayName("Should reactivate suspended connector")
    void shouldReactivateSuspendedConnector() {
        Instant now = Instant.now();
        DmTikTokAdsConnector connector = DmTikTokAdsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My TikTok Ads")
            .advertiserId("advertiser-123")
            .accessToken("token-abc123")
            .status(DmTikTokAdsConnectorStatus.SUSPENDED)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmTikTokAdsConnector reactivated = connector.reactivate();

        assertEquals(DmTikTokAdsConnectorStatus.ACTIVE, reactivated.getStatus());
    }

    @Test
    @DisplayName("Should disable connector")
    void shouldDisableConnector() {
        Instant now = Instant.now();
        DmTikTokAdsConnector connector = DmTikTokAdsConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My TikTok Ads")
            .advertiserId("advertiser-123")
            .accessToken("token-abc123")
            .status(DmTikTokAdsConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmTikTokAdsConnector disabled = connector.disable();

        assertEquals(DmTikTokAdsConnectorStatus.DISABLED, disabled.getStatus());
    }

    @Test
    @DisplayName("Should fail to build connector with blank id")
    void shouldFailToBuildConnectorWithBlankId() {
        assertThrows(IllegalArgumentException.class, () -> 
            DmTikTokAdsConnector.builder()
                .id("")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .displayName("My TikTok Ads")
                .advertiserId("advertiser-123")
                .accessToken("token-abc123")
                .status(DmTikTokAdsConnectorStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );
    }

    @Test
    @DisplayName("Should fail to build connector with null accessToken")
    void shouldFailToBuildConnectorWithNullAccessToken() {
        assertThrows(NullPointerException.class, () -> 
            DmTikTokAdsConnector.builder()
                .id("connector-1")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .displayName("My TikTok Ads")
                .advertiserId("advertiser-123")
                .accessToken(null)
                .status(DmTikTokAdsConnectorStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );
    }
}
