package com.ghatana.digitalmarketing.domain.connector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DmEsignatureConnector}.
 *
 * @doc.type class
 * @doc.purpose Tests e-signature connector domain model (P3-003)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DmEsignatureConnector Tests")
class DmEsignatureConnectorTest {

    @Test
    @DisplayName("Should build valid connector")
    void shouldBuildValidConnector() {
        Instant now = Instant.now();
        DmEsignatureConnector connector = DmEsignatureConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My E-Signature")
            .esignatureProvider("DocuSign")
            .apiUrl("https://api.docusign.com")
            .accessToken("token-abc123")
            .status(DmEsignatureConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertEquals("connector-1", connector.getId());
        assertEquals("tenant-1", connector.getTenantId());
        assertEquals("workspace-1", connector.getWorkspaceId());
        assertEquals("My E-Signature", connector.getDisplayName());
        assertEquals("DocuSign", connector.getEsignatureProvider());
        assertEquals("https://api.docusign.com", connector.getApiUrl());
        assertEquals("token-abc123", connector.getAccessToken());
        assertEquals(DmEsignatureConnectorStatus.PENDING, connector.getStatus());
    }

    @Test
    @DisplayName("Should activate pending connector")
    void shouldActivatePendingConnector() {
        Instant now = Instant.now();
        DmEsignatureConnector connector = DmEsignatureConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My E-Signature")
            .esignatureProvider("DocuSign")
            .apiUrl("https://api.docusign.com")
            .accessToken("token-abc123")
            .status(DmEsignatureConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmEsignatureConnector activated = connector.activate();

        assertEquals(DmEsignatureConnectorStatus.ACTIVE, activated.getStatus());
        assertNull(activated.getFailureReason());
        assertNotEquals(now, activated.getUpdatedAt());
    }

    @Test
    @DisplayName("Should fail to activate non-pending connector")
    void shouldFailToActivateNonPendingConnector() {
        Instant now = Instant.now();
        DmEsignatureConnector connector = DmEsignatureConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My E-Signature")
            .esignatureProvider("DocuSign")
            .apiUrl("https://api.docusign.com")
            .accessToken("token-abc123")
            .status(DmEsignatureConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThrows(IllegalStateException.class, connector::activate);
    }

    @Test
    @DisplayName("Should mark connector as failed")
    void shouldMarkConnectorAsFailed() {
        Instant now = Instant.now();
        DmEsignatureConnector connector = DmEsignatureConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My E-Signature")
            .esignatureProvider("DocuSign")
            .apiUrl("https://api.docusign.com")
            .accessToken("token-abc123")
            .status(DmEsignatureConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmEsignatureConnector failed = connector.markAuthFailed("Invalid credentials");

        assertEquals(DmEsignatureConnectorStatus.FAILED, failed.getStatus());
        assertEquals("Invalid credentials", failed.getFailureReason());
    }

    @Test
    @DisplayName("Should suspend active connector")
    void shouldSuspendActiveConnector() {
        Instant now = Instant.now();
        DmEsignatureConnector connector = DmEsignatureConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My E-Signature")
            .esignatureProvider("DocuSign")
            .apiUrl("https://api.docusign.com")
            .accessToken("token-abc123")
            .status(DmEsignatureConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmEsignatureConnector suspended = connector.suspend();

        assertEquals(DmEsignatureConnectorStatus.SUSPENDED, suspended.getStatus());
    }

    @Test
    @DisplayName("Should reactivate suspended connector")
    void shouldReactivateSuspendedConnector() {
        Instant now = Instant.now();
        DmEsignatureConnector connector = DmEsignatureConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My E-Signature")
            .esignatureProvider("DocuSign")
            .apiUrl("https://api.docusign.com")
            .accessToken("token-abc123")
            .status(DmEsignatureConnectorStatus.SUSPENDED)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmEsignatureConnector reactivated = connector.reactivate();

        assertEquals(DmEsignatureConnectorStatus.ACTIVE, reactivated.getStatus());
    }

    @Test
    @DisplayName("Should disable connector")
    void shouldDisableConnector() {
        Instant now = Instant.now();
        DmEsignatureConnector connector = DmEsignatureConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My E-Signature")
            .esignatureProvider("DocuSign")
            .apiUrl("https://api.docusign.com")
            .accessToken("token-abc123")
            .status(DmEsignatureConnectorStatus.ACTIVE)
            .createdAt(now)
            .updatedAt(now)
            .build();

        DmEsignatureConnector disabled = connector.disable();

        assertEquals(DmEsignatureConnectorStatus.DISABLED, disabled.getStatus());
    }

    @Test
    @DisplayName("Should fail to build connector with blank id")
    void shouldFailToBuildConnectorWithBlankId() {
        assertThrows(IllegalArgumentException.class, () -> 
            DmEsignatureConnector.builder()
                .id("")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .displayName("My E-Signature")
                .esignatureProvider("DocuSign")
                .apiUrl("https://api.docusign.com")
                .accessToken("token-abc123")
                .status(DmEsignatureConnectorStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );
    }

    @Test
    @DisplayName("Should fail to build connector with null accessToken")
    void shouldFailToBuildConnectorWithNullAccessToken() {
        assertThrows(NullPointerException.class, () -> 
            DmEsignatureConnector.builder()
                .id("connector-1")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .displayName("My E-Signature")
                .esignatureProvider("DocuSign")
                .apiUrl("https://api.docusign.com")
                .accessToken(null)
                .status(DmEsignatureConnectorStatus.PENDING)
                .createdAt(Instant.now())
                .build()
        );
    }

    @Test
    @DisplayName("Should build connector with configuration")
    void shouldBuildConnectorWithConfiguration() {
        Instant now = Instant.now();
        Map<String, String> config = Map.of("accountId", "acc123", "userId", "user456");
        DmEsignatureConnector connector = DmEsignatureConnector.builder()
            .id("connector-1")
            .tenantId("tenant-1")
            .workspaceId("workspace-1")
            .displayName("My E-Signature")
            .esignatureProvider("DocuSign")
            .apiUrl("https://api.docusign.com")
            .accessToken("token-abc123")
            .configuration(config)
            .status(DmEsignatureConnectorStatus.PENDING)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertEquals(config, connector.getConfiguration());
    }
}
