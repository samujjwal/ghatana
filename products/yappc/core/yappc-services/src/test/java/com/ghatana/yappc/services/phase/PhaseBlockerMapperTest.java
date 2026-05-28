package com.ghatana.yappc.services.phase;

import com.ghatana.yappc.api.PhasePacket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PhaseBlockerMapper")
class PhaseBlockerMapperTest {

    private final PhaseBlockerMapper mapper = new PhaseBlockerMapper();

    @Test
    @DisplayName("maps missing artifact blockers as critical artifact blockers")
    void mapsMissingArtifactBlockers() {
        List<PhasePacket.PhaseBlocker> blockers = mapper.map(List.of("missing-artifact: shape-model"));

        assertThat(blockers).singleElement().satisfies(blocker -> {
            assertThat(blocker.type()).isEqualTo("ARTIFACT");
            assertThat(blocker.severity()).isEqualTo("CRITICAL");
            assertThat(blocker.title()).isEqualTo("shape-model");
        });
    }

    @Test
    @DisplayName("maps criterion blockers as warning criterion blockers")
    void mapsCriterionBlockers() {
        List<PhasePacket.PhaseBlocker> blockers = mapper.map(List.of("entry-criterion: policy approval"));

        assertThat(blockers).singleElement().satisfies(blocker -> {
            assertThat(blocker.type()).isEqualTo("CRITERION");
            assertThat(blocker.severity()).isEqualTo("WARNING");
            assertThat(blocker.title()).isEqualTo("policy approval");
        });
    }

    @Test
    @DisplayName("maps policy and dependency degraded blockers to critical")
    void mapsPolicyAndDependencyBlockers() {
        List<PhasePacket.PhaseBlocker> blockers = mapper.map(List.of(
                "policy-denied: tenant-tier",
                "dependency-degraded: feature-flags"
        ));

        assertThat(blockers).hasSize(2);
        assertThat(blockers.get(0).type()).isEqualTo("POLICY");
        assertThat(blockers.get(0).severity()).isEqualTo("CRITICAL");
        assertThat(blockers.get(1).type()).isEqualTo("DEPENDENCY");
        assertThat(blockers.get(1).severity()).isEqualTo("CRITICAL");
    }
}
