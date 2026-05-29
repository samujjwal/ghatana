package com.ghatana.digitalmarketing.domain.lead;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * @doc.type class
 * @doc.purpose Validates lead capture data integrity with campaign and consent referential integrity
 * @doc.layer product
 * @doc.pattern DomainTest
 */
@DisplayName("dm-003: Lead Capture Data Integrity Tests")
class DmLeadCaptureTest {

    private DmLeadCapture valid() {
        return DmLeadCapture.builder()
            .id("lead-1").tenantId("t1").workspaceId("ws1")
            .landingPageId("lp-1").email("user@example.com").name("Alice")
            .customFields(Map.of()).status(DmLeadStatus.NEW).capturedAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid lead")
    void shouldBuildValid() {
        DmLeadCapture lead = valid();
        assertThat(lead.getId()).isEqualTo("lead-1");
        assertThat(lead.getStatus()).isEqualTo(DmLeadStatus.NEW);
        assertThat(lead.getEmail()).isEqualTo("user@example.com");
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmLeadCapture.builder().id("").tenantId("t").workspaceId("w")
                .landingPageId("lp").email("e@e.com").customFields(Map.of())
                .status(DmLeadStatus.NEW).capturedAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank email")
    void shouldRejectBlankEmail() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmLeadCapture.builder().id("x").tenantId("t").workspaceId("w")
                .landingPageId("lp").email("").customFields(Map.of())
                .status(DmLeadStatus.NEW).capturedAt(Instant.now()).build());
    }

    @Test @DisplayName("customFields is immutable")
    void shouldHaveImmutableCustomFields() {
        assertThat(valid().getCustomFields()).isUnmodifiable();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmLeadCapture lead = valid();
        assertThat(lead.getTenantId()).isEqualTo("t1");
        assertThat(lead.getWorkspaceId()).isEqualTo("ws1");
        assertThat(lead.getLandingPageId()).isEqualTo("lp-1");
        assertThat(lead.getName()).isEqualTo("Alice");
        assertThat(lead.getCustomFields()).isEmpty();
        assertThat(lead.getCapturedAt()).isNotNull();
    }

    @Test @DisplayName("builder rejects null status")
    void shouldRejectNullStatus() {
        assertThatNullPointerException().isThrownBy(() ->
            DmLeadCapture.builder().id("x").tenantId("t").email("e@e.com")
                .customFields(Map.of()).status(null).capturedAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null customFields")
    void shouldRejectNullCustomFields() {
        assertThatNullPointerException().isThrownBy(() ->
            DmLeadCapture.builder().id("x").tenantId("t").email("e@e.com")
                .customFields(null).status(DmLeadStatus.NEW).capturedAt(Instant.now()).build());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmLeadCapture.builder().id(null).tenantId("t").email("a@b.com")
                .customFields(java.util.Map.of()).status(DmLeadStatus.NEW)
                .capturedAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmLeadCapture.builder().id("x").tenantId("").email("a@b.com")
                .customFields(java.util.Map.of()).status(DmLeadStatus.NEW)
                .capturedAt(java.time.Instant.now()).build());
    }
}
