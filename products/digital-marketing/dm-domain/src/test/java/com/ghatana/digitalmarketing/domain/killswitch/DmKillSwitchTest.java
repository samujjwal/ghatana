package com.ghatana.digitalmarketing.domain.killswitch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmKillSwitch domain entity")
class DmKillSwitchTest {

    private static final Instant NOW = Instant.parse("2026-05-02T18:00:00Z");

    private DmKillSwitch active() {
        return DmKillSwitch.builder()
            .id("ks-1").tenantId("t1").workspaceId("ws1")
            .scope("workspace").scopeId("ws1").active(true).reason("safety")
            .activatedBy("admin").activatedAt(NOW).createdAt(NOW).build();
    }

    @Test @DisplayName("builder creates valid entity")
    void shouldBuildValid() {
        DmKillSwitch ks = active();
        assertThat(ks.getId()).isEqualTo("ks-1");
        assertThat(ks.isActive()).isTrue();
    }

    @Test @DisplayName("builder rejects blank id")
    void shouldRejectBlankId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmKillSwitch.builder().id("").tenantId("t").scope("s")
                .activatedBy("a").activatedAt(NOW).createdAt(NOW).build());
    }

    @Test @DisplayName("deactivate transitions from active")
    void shouldDeactivate() {
        DmKillSwitch deactivated = active().deactivate();
        assertThat(deactivated.isActive()).isFalse();
        assertThat(deactivated.getDeactivatedAt()).isNotNull();
    }

    @Test @DisplayName("deactivate rejects already inactive")
    void shouldNotDeactivateTwice() {
        DmKillSwitch deactivated = active().deactivate();
        assertThatIllegalStateException().isThrownBy(deactivated::deactivate);
    }

    @Test @DisplayName("equals and hashCode are id-based")
    void shouldEqualById() {
        DmKillSwitch left = active();
        DmKillSwitch sameId = active();
        DmKillSwitch differentId = DmKillSwitch.builder()
            .id("ks-9").tenantId("t1").scope("workspace").createdAt(NOW).build();

        assertThat(left).isEqualTo(sameId).hasSameHashCodeAs(sameId).isNotEqualTo(differentId);
        assertThat(left).isNotEqualTo("ks-1");
    }

    @Test @DisplayName("toString contains key state")
    void shouldContainState() {
        assertThat(active().toString()).contains("ks-1", "true", "workspace");
    }

    @Test @DisplayName("getters and toBuilder expose and preserve values")
    void shouldExposeState() {
        DmKillSwitch killSwitch = active();
        DmKillSwitch copy = killSwitch.toBuilder().scope("campaign").active(false).build();

        assertThat(killSwitch.getTenantId()).isEqualTo("t1");
        assertThat(killSwitch.getWorkspaceId()).isEqualTo("ws1");
        assertThat(killSwitch.getScope()).isEqualTo("workspace");
        assertThat(killSwitch.getScopeId()).isEqualTo("ws1");
        assertThat(killSwitch.getReason()).isEqualTo("safety");
        assertThat(killSwitch.getActivatedBy()).isEqualTo("admin");
        assertThat(killSwitch.getActivatedAt()).isEqualTo(NOW);
        assertThat(killSwitch.getCreatedAt()).isEqualTo(NOW);
        assertThat(killSwitch.getDeactivatedAt()).isNull();
        assertThat(copy.getScope()).isEqualTo("campaign");
        assertThat(copy.isActive()).isFalse();
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmKillSwitch.builder().id(null).tenantId("t").scope("global")
                .createdAt(NOW).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmKillSwitch.builder().id("x").tenantId("").scope("global")
                .createdAt(NOW).build());
    }

    @Test @DisplayName("blank scope and null createdAt throw")
    void shouldValidateRequiredFields() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmKillSwitch.builder().id("x").tenantId("t").scope(" ").createdAt(NOW).build());

        assertThatNullPointerException().isThrownBy(() ->
            DmKillSwitch.builder().id("x").tenantId("t").scope("global").createdAt(null).build())
            .withMessage("createdAt must not be null");
    }
}
