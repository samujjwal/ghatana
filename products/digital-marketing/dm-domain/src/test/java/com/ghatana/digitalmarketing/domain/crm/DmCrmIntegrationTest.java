package com.ghatana.digitalmarketing.domain.crm;

import com.ghatana.digitalmarketing.domain.crm.DmCrmIntegration;
import com.ghatana.digitalmarketing.domain.crm.DmCrmIntegrationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmCrmIntegration domain entity")
class DmCrmIntegrationTest {

    private DmCrmIntegration valid() {
        Instant now = Instant.now();
        return DmCrmIntegration.builder()
            .id("crm-1").tenantId("t1").workspaceId("ws1")
            .crmProvider("SALESFORCE").displayName("Salesforce CRM")
            .apiEndpoint("https://example.salesforce.com").credentialRef("cred-1")
            .status(DmCrmIntegrationStatus.PENDING)
            .createdAt(now).updatedAt(now).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmCrmIntegration c = valid();
        assertThat(c.getId()).isEqualTo("crm-1");
        assertThat(c.getStatus()).isEqualTo(DmCrmIntegrationStatus.PENDING);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCrmIntegration.builder().id("").tenantId("t").crmProvider("CRM")
                .displayName("n").apiEndpoint("e").credentialRef("c")
                .status(DmCrmIntegrationStatus.PENDING)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test @DisplayName("activate from PENDING succeeds")
    void shouldActivate() {
        assertThat(valid().activate().getStatus()).isEqualTo(DmCrmIntegrationStatus.ACTIVE);
    }

    @Test @DisplayName("recordSync updates lastSyncAt")
    void shouldRecordSync() {
        DmCrmIntegration synced = valid().activate().recordSync();
        assertThat(synced.getLastSyncAt()).isNotNull();
    }

    @Test @DisplayName("markFailed from PENDING")
    void shouldMarkFailed() {
        DmCrmIntegration failed = valid().markFailed("timeout");
        assertThat(failed.getStatus()).isEqualTo(DmCrmIntegrationStatus.FAILED);
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() { assertThat(valid()).isNotEqualTo(null); }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() { assertThat(valid()).isNotEqualTo(42); }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmCrmIntegration c = valid();
        assertThat(c.getTenantId()).isEqualTo("t1");
        assertThat(c.getWorkspaceId()).isEqualTo("ws1");
        assertThat(c.getCrmProvider()).isEqualTo("SALESFORCE");
        assertThat(c.getDisplayName()).isEqualTo("Salesforce CRM");
        assertThat(c.getApiEndpoint()).isEqualTo("https://example.salesforce.com");
        assertThat(c.getCredentialRef()).isEqualTo("cred-1");
        assertThat(c.getCreatedAt()).isNotNull();
        assertThat(c.getUpdatedAt()).isNotNull();
        assertThat(c.toString()).contains("crm-1");
    }

    @Test @DisplayName("builder rejects null status")
    void shouldRejectNullStatus() {
        assertThatNullPointerException().isThrownBy(() ->
            DmCrmIntegration.builder().id("x").tenantId("t").crmProvider("CRM")
                .status(null).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null createdAt")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmCrmIntegration.builder().id("x").tenantId("t").crmProvider("CRM")
                .status(DmCrmIntegrationStatus.PENDING).createdAt(null).build());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCrmIntegration.builder().id(null).tenantId("t").crmProvider("salesforce")
                .status(DmCrmIntegrationStatus.PENDING).createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmCrmIntegration.builder().id("x").tenantId("").crmProvider("salesforce")
                .status(DmCrmIntegrationStatus.PENDING).createdAt(java.time.Instant.now()).build());
    }
}
