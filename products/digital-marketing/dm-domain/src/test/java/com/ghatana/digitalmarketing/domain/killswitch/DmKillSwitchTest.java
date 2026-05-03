package com.ghatana.digitalmarketing.domain.killswitch;

import com.ghatana.digitalmarketing.domain.killswitch.DmKillSwitch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("DmKillSwitch domain entity")
class DmKillSwitchTest {

    private DmKillSwitch active() {
        return DmKillSwitch.builder()
            .id("ks-1").tenantId("t1").workspaceId("ws1")
            .scope("workspace").active(true).reason("safety")
            .activatedBy("admin").activatedAt(Instant.now()).createdAt(Instant.now()).build();
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
                .activatedBy("a").activatedAt(Instant.now()).createdAt(Instant.now()).build());
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
        assertThat(active()).isEqualTo(active());
    }

    @Test @DisplayName("toString contains id")
    void shouldContainId() {
        assertThat(active().toString()).contains("ks-1");
    }

    @Test @DisplayName("null id throws")
    void shouldRejectNullId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmKillSwitch.builder().id(null).tenantId("t").scope("global")
                .createdAt(java.time.Instant.now()).build());
    }

    @Test @DisplayName("blank tenantId throws")
    void shouldRejectBlankTenantId() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            DmKillSwitch.builder().id("x").tenantId("").scope("global")
                .createdAt(java.time.Instant.now()).build());
    }
}
