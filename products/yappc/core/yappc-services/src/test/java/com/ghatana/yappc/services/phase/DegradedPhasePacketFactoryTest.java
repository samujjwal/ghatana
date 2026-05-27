package com.ghatana.yappc.services.phase;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.yappc.api.PhasePacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies degraded phase packet construction is isolated from PhasePacketServiceImpl
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("DegradedPhasePacketFactory")
class DegradedPhasePacketFactoryTest {

    private final DegradedPhasePacketFactory factory = new DegradedPhasePacketFactory();

    @Test
    @DisplayName("build returns fail-closed Kernel degraded packet details")
    void build_kernelReason_returnsKernelDetails() {
        PhasePacket packet = factory.build(
                "RUN",
                "project-1",
                "workspace-1",
                new Principal("operator", List.of("ADMIN"), "tenant-1"),
                "corr-1",
                "KERNEL_LIFECYCLE_TRUTH_UNAVAILABLE");

        assertThat(packet.readiness().canAdvance()).isFalse();
        assertThat(packet.capabilities().canUpdate()).isFalse();
        assertThat(packet.degradedDetails().dependency()).isEqualTo("KERNEL");
        assertThat(packet.degradedDetails().truthSource()).isEqualTo("kernel_lifecycle_truth");
        assertThat(packet.dashboardActions().blockedActions()).containsExactly("all");
    }
}
