package com.ghatana.digitalmarketing.domain.agency;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AgencyClient (DMOS-P3-003).
 *
 * @doc.type test
 * @doc.purpose Verify agency client domain entity behavior
 * @doc.layer domain
 */
@DisplayName("AgencyClient")
class AgencyClientTest {

    @Test
    @DisplayName("builder creates valid agency client")
    void builder_createsValidAgencyClient() {
        DmTenantId tenantId = new DmTenantId("tenant-123");
        DmWorkspaceId workspaceId = new DmWorkspaceId("workspace-456");
        Instant now = Instant.now();

        AgencyClient client = AgencyClient.builder()
            .clientId("client-789")
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .clientName("Acme Corp")
            .contactEmail("contact@acme.com")
            .contactPhone("555-1234")
            .brandingTheme("blue")
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThat(client.getClientId()).isEqualTo("client-789");
        assertThat(client.getTenantId()).isEqualTo(tenantId);
        assertThat(client.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(client.getClientName()).isEqualTo("Acme Corp");
        assertThat(client.isActive()).isTrue();
    }

    @Test
    @DisplayName("builder creates inactive client")
    void builder_createsInactiveClient() {
        AgencyClient client = AgencyClient.builder()
            .clientId("client-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .clientName("Acme Corp")
            .active(false)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        assertThat(client.isActive()).isFalse();
    }

    @Test
    @DisplayName("builder handles null optional fields")
    void builder_handlesNullOptionalFields() {
        AgencyClient client = AgencyClient.builder()
            .clientId("client-789")
            .tenantId(new DmTenantId("tenant-123"))
            .workspaceId(new DmWorkspaceId("workspace-456"))
            .clientName("Acme Corp")
            .active(true)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        assertThat(client.getContactEmail()).isNull();
        assertThat(client.getContactPhone()).isNull();
        assertThat(client.getBrandingTheme()).isNull();
    }
}
