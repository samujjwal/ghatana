package com.ghatana.yappc.domain.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies ArtifactGraphIngestRequest carries full residualIslands payload through JSON serialisation
 * @doc.layer test
 * @doc.pattern RoundTripTest
 */
@DisplayName("ArtifactGraphIngestRequest Round-Trip Tests")
class ArtifactGraphIngestRequestRoundTripTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("residualIslands list survives round-trip with all fields")
    void residualIslandsRoundTrip() throws Exception {
        ResidualIslandDto island = new ResidualIslandDto(
            "ri-1", "css_module", "Stylesheet fragment",
            ".btn { color: red; }", "src/App.css:1:0-10:0", "sha256:abc", null, "no_css_model",
            0.9, false, 0.3, Map.of("lang", "css"), 1,
            "t1", "p1", "w1", "snap-1"
        );

        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            "p1", "t1", List.of(), List.of(),
            "refs/heads/main", "snap-1", "ver-1", "csum-abc",
            List.of(), List.of(), List.of(island)
        );

        String json = mapper.writeValueAsString(request);
        ArtifactGraphIngestRequest restored = mapper.readValue(json, ArtifactGraphIngestRequest.class);

        assertThat(restored.projectId()).isEqualTo("p1");
        assertThat(restored.residualIslands()).hasSize(1);
        ResidualIslandDto restoredIsland = restored.residualIslands().get(0);
        assertThat(restoredIsland.id()).isEqualTo("ri-1");
        assertThat(restoredIsland.sourceSpan()).isEqualTo("src/App.css:1:0-10:0");
        assertThat(restoredIsland.checksum()).isEqualTo("sha256:abc");
        assertThat(restoredIsland.reason()).isEqualTo("no_css_model");
    }

    @Test
    @DisplayName("empty residualIslands list survives round-trip as empty list")
    void emptyResidualIslandsRoundTrip() throws Exception {
        ArtifactGraphIngestRequest request = new ArtifactGraphIngestRequest(
            "p1", "t1", List.of(), List.of(),
            null, null, "ver-2", null,
            List.of(), List.of(), List.of()
        );

        String json = mapper.writeValueAsString(request);
        ArtifactGraphIngestRequest restored = mapper.readValue(json, ArtifactGraphIngestRequest.class);

        assertThat(restored.residualIslands()).isNotNull().isEmpty();
    }
}
