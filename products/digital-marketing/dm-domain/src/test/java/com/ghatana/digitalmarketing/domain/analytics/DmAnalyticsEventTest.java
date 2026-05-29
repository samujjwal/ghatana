package com.ghatana.digitalmarketing.domain.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmAnalyticsEvent domain entity")
class DmAnalyticsEventTest {

    private DmAnalyticsEvent valid() {
        return DmAnalyticsEvent.builder()
            .id("evt-1").tenantId("t1").workspaceId("ws1")
            .sessionId("sess-1").eventType("PAGE_VIEW").sourceUrl("https://example.com")
            .visitorId("v1").properties(Map.of("key", "value")).occurredAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmAnalyticsEvent e = valid();
        assertThat(e.getId()).isEqualTo("evt-1");
        assertThat(e.getEventType()).isEqualTo("PAGE_VIEW");
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAnalyticsEvent.builder().id("").tenantId("t").eventType("e")
                .properties(Map.of()).occurredAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank eventType")
    void shouldRejectBlankEventType() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAnalyticsEvent.builder().id("x").tenantId("t").eventType("")
                .properties(Map.of()).occurredAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects null occurredAt")
    void shouldRejectNullOccurredAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAnalyticsEvent.builder().id("x").tenantId("t").eventType("e")
                .properties(Map.of()).occurredAt(null).build());
    }

    @Test @DisplayName("properties map is immutable")
    void shouldHaveImmutableProperties() {
        assertThat(valid().getProperties()).isUnmodifiable();
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() {
        assertThat(valid()).isNotEqualTo(null);
    }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() {
        assertThat(valid()).isNotEqualTo(42);
    }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmAnalyticsEvent e = valid();
        assertThat(e.getTenantId()).isEqualTo("t1");
        assertThat(e.getWorkspaceId()).isEqualTo("ws1");
        assertThat(e.getSessionId()).isEqualTo("sess-1");
        assertThat(e.getSourceUrl()).isEqualTo("https://example.com");
        assertThat(e.getVisitorId()).isEqualTo("v1");
        assertThat(e.getOccurredAt()).isNotNull();
        assertThat(e.toString()).contains("evt-1");
    }

    @Test @DisplayName("builder rejects null properties")
    void shouldRejectNullProperties() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAnalyticsEvent.builder().id("x").tenantId("t").eventType("e")
                .properties(null).occurredAt(Instant.now()).build());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAnalyticsEvent.builder().id(null).tenantId("t").eventType("e")
                .properties(Map.of()).occurredAt(Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAnalyticsEvent.builder().id("x").tenantId("").eventType("e")
                .properties(Map.of()).occurredAt(Instant.now()).build());
    }

    @Test @DisplayName("null eventType throws")
    void shouldRejectNullEventType() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAnalyticsEvent.builder().id("x").tenantId("t").eventType(null)
                .properties(Map.of()).occurredAt(Instant.now()).build());
    }
}
