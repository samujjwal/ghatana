package com.ghatana.yappc.domain.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies ResidualIslandDto serialises and deserialises without data loss
 * @doc.layer test
 * @doc.pattern RoundTripTest
 */
@DisplayName("ResidualIslandDto Round-Trip Serialisation Tests")
class ResidualIslandDtoRoundTripTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("all fields survive JSON round-trip")
    void allFieldsSurviveRoundTrip() throws Exception {
        ResidualIslandDto dto = new ResidualIslandDto(
            "island-abc123",
            "imperative_logic",
            "Complex mutation chain that cannot be represented as a node",
            "const x = mutate(state);",
            "src/service/OrderService.ts:42:0-68:1",
            "sha256:deadbeef",
            "blobs/deadbeef",
            "too_complex_for_graph",
            0.82,
            true,
            0.75,
            Map.of("phase", "extraction", "extractor", "ts-v2"),
            3,
            "tenant-1",
            "project-42",
            "ws-99",
            "snap-001"
        );

        String json = mapper.writeValueAsString(dto);
        ResidualIslandDto restored = mapper.readValue(json, ResidualIslandDto.class);

        assertThat(restored.id()).isEqualTo(dto.id());
        assertThat(restored.islandType()).isEqualTo(dto.islandType());
        assertThat(restored.summary()).isEqualTo(dto.summary());
        assertThat(restored.sourceSpan()).isEqualTo(dto.sourceSpan());
        assertThat(restored.checksum()).isEqualTo(dto.checksum());
        assertThat(restored.rawFragmentRef()).isEqualTo(dto.rawFragmentRef());
        assertThat(restored.reason()).isEqualTo(dto.reason());
        assertThat(restored.confidence()).isEqualTo(dto.confidence());
        assertThat(restored.reviewRequired()).isEqualTo(dto.reviewRequired());
        assertThat(restored.riskScore()).isEqualTo(dto.riskScore());
        assertThat(restored.metadata()).isEqualTo(dto.metadata());
        assertThat(restored.fileCount()).isEqualTo(dto.fileCount());
        assertThat(restored.tenantId()).isEqualTo(dto.tenantId());
        assertThat(restored.projectId()).isEqualTo(dto.projectId());
        assertThat(restored.workspaceId()).isEqualTo(dto.workspaceId());
        assertThat(restored.snapshotId()).isEqualTo(dto.snapshotId());
    }

    @Test
    @DisplayName("null optional fields survive round-trip as null")
    void nullOptionalFieldsRoundTrip() throws Exception {
        ResidualIslandDto dto = new ResidualIslandDto(
            "island-min",
            "unknown",
            "Minimal island",
            null,
            "src/a.ts:1:0-2:0",
            null,
            null,
            null,
            0.5,
            false,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        String json = mapper.writeValueAsString(dto);
        ResidualIslandDto restored = mapper.readValue(json, ResidualIslandDto.class);

        assertThat(restored.id()).isEqualTo("island-min");
        assertThat(restored.checksum()).isNull();
        assertThat(restored.rawFragmentRef()).isNull();
        assertThat(restored.riskScore()).isNull();
    }
}
