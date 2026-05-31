package com.ghatana.yappc.services.phase;

import com.ghatana.core.runtime.PreviewRuntimeService;
import com.ghatana.yappc.api.PhasePacket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhaseHealthSignalProviderTest {

    @Mock
    private PreviewRuntimeService previewRuntimeService;

    private String originalRuntimeProfile;

    @BeforeEach
    void setUp() {
        originalRuntimeProfile = System.getProperty("ghatana.runtime.profile");
    }

    @AfterEach
    void tearDown() {
        if (originalRuntimeProfile == null) {
            System.clearProperty("ghatana.runtime.profile");
        } else {
            System.setProperty("ghatana.runtime.profile", originalRuntimeProfile);
        }
    }

    @Test
    void buildUsesDerivedIdsInNonProductionRuntime() {
        System.clearProperty("ghatana.runtime.profile");
        when(previewRuntimeService.getHealth("project-1-generate"))
                .thenReturn(new PreviewRuntimeService.PreviewHealthStatus(true, "healthy", List.of()));
        when(previewRuntimeService.getGenerationHealth("project-1-generate-gen"))
                .thenReturn(new PreviewRuntimeService.GenerationHealthStatus(true, "healthy", "gen-1", List.of()));
        when(previewRuntimeService.getRuntimeHealth("project-1-generate-runtime"))
                .thenReturn(new PreviewRuntimeService.RuntimeHealthStatus(true, "healthy", "run-1", List.of()));

        PhaseHealthSignalProvider provider = new PhaseHealthSignalProvider(previewRuntimeService);
        PhasePacket.HealthSignals signals = provider.build("generate", "project-1", Map.of());

        assertThat(signals.preview().isHealthy()).isTrue();
        assertThat(signals.generation().isHealthy()).isTrue();
        assertThat(signals.runtime().isHealthy()).isTrue();
        assertThat(signals.agentGovernance()).isNotNull();
        assertThat(signals.agentGovernance().status()).isEqualTo("healthy");
        assertThat(signals.agentGovernance().governanceState()).isEqualTo("approved");
        verify(previewRuntimeService).getHealth("project-1-generate");
    }

    @Test
    void buildFailsClosedInProductionWhenRuntimeIdentifiersAreMissing() {
        System.setProperty("ghatana.runtime.profile", "production");
        PhaseHealthSignalProvider provider = new PhaseHealthSignalProvider(previewRuntimeService);

        PhasePacket.HealthSignals signals = provider.build("generate", "project-1", Map.of());

        assertThat(signals.preview().status()).isEqualTo("degraded");
        assertThat(signals.generation().status()).isEqualTo("degraded");
        assertThat(signals.runtime().status()).isEqualTo("degraded");
        assertThat(signals.preview().issues()).anyMatch(issue -> issue.contains("Missing previewId"));
        verifyNoInteractions(previewRuntimeService);
    }
}
