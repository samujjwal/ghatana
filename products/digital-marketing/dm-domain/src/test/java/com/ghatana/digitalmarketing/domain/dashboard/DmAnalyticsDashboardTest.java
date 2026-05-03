package com.ghatana.digitalmarketing.domain.dashboard;

import com.ghatana.digitalmarketing.domain.dashboard.DmAnalyticsDashboard;
import com.ghatana.digitalmarketing.domain.dashboard.DmAnalyticsDashboard.DmDashboardWidget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmAnalyticsDashboard domain entity")
class DmAnalyticsDashboardTest {

    private DmAnalyticsDashboard valid() {
        return DmAnalyticsDashboard.builder()
            .id("dash-1").tenantId("t1").workspaceId("ws1").name("My Dashboard")
            .widgets(List.of(new DmDashboardWidget("w1", "Clicks", "clicks", "BAR")))
            .createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmAnalyticsDashboard d = valid();
        assertThat(d.getId()).isEqualTo("dash-1");
        assertThat(d.getName()).isEqualTo("My Dashboard");
        assertThat(d.getWidgets()).hasSize(1);
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAnalyticsDashboard.builder().id("").tenantId("t").name("n")
                .widgets(List.of()).createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test @DisplayName("builder rejects blank name")
    void shouldRejectBlankName() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAnalyticsDashboard.builder().id("x").tenantId("t").name("")
                .widgets(List.of()).createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }

    @Test @DisplayName("widgets list is immutable")
    void shouldHaveImmutableWidgets() {
        assertThat(valid().getWidgets()).isUnmodifiable();
    }

    @Test @DisplayName("DmDashboardWidget rejects null widgetId")
    void shouldRejectNullWidgetId() {
        assertThatNullPointerException().isThrownBy(() ->
            new DmDashboardWidget(null, "title", "key", "BAR"));
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        assertThat(valid()).isEqualTo(valid());
        assertThat(valid().hashCode()).isEqualTo(valid().hashCode());
    }

    @Test @DisplayName("equals returns false for null")
    void shouldNotEqualNull() { assertThat(valid()).isNotEqualTo(null); }

    @Test @DisplayName("equals returns false for different type")
    void shouldNotEqualDifferentType() { assertThat(valid()).isNotEqualTo("x"); }

    @Test @DisplayName("all getters return expected values")
    void shouldExposeAllGetters() {
        DmAnalyticsDashboard d = valid();
        assertThat(d.getTenantId()).isEqualTo("t1");
        assertThat(d.getWorkspaceId()).isEqualTo("ws1");
        assertThat(d.getName()).isEqualTo("My Dashboard");
        assertThat(d.getUpdatedAt()).isNotNull();
        assertThat(d.toString()).contains("dash-1");
    }

    @Test @DisplayName("builder rejects null createdAt")
    void shouldRejectNullCreatedAt() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAnalyticsDashboard.builder().id("x").tenantId("t").name("n")
                .widgets(List.of()).createdAt(null).build());
    }

    @Test @DisplayName("builder rejects null widgets")
    void shouldRejectNullWidgets() {
        assertThatNullPointerException().isThrownBy(() ->
            DmAnalyticsDashboard.builder().id("x").tenantId("t").name("n")
                .widgets(null).createdAt(Instant.now()).build());
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAnalyticsDashboard.builder().id(null).tenantId("t").name("n")
                .widgets(java.util.List.of())
                .createdAt(java.time.Instant.now()).updatedAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmAnalyticsDashboard.builder().id("x").tenantId("").name("n")
                .widgets(java.util.List.of())
                .createdAt(java.time.Instant.now()).updatedAt(java.time.Instant.now()).build());
    }
}
