package com.ghatana.pipeline.registry.store;

import com.ghatana.platform.domain.auth.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PipelineRecord}.
 *
 * @doc.type class
 * @doc.purpose Tests toFieldMap, toWireBytes/fromWireBytes round-trip, and null safety
 * @doc.layer product
 * @doc.pattern TestCase
 */
@DisplayName("PipelineRecord")
class PipelineRecordTest {

    private static PipelineRecord sampleRecord() {
        return PipelineRecord.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .tenantId(TenantId.of("tenant-a"))
                .name("my-pipeline")
                .description("A test pipeline")
                .version(3)
                .active(true)
                .config("{\"key\":\"value\"}")
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .createdBy("alice")
                .updatedBy("bob")
                .tags("env:prod,team:data")
                .versionControl(42L)
                .build();
    }

    // ── toFieldMap ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("toFieldMap: all fields are present and correctly mapped")
    void toFieldMap_allFields_presentAndCorrect() {
        PipelineRecord record = sampleRecord();
        Map<String, Object> map = record.toFieldMap();

        assertThat(map).containsEntry("id", "00000000-0000-0000-0000-000000000001");
        assertThat(map).containsEntry("tenantId", "tenant-a");
        assertThat(map).containsEntry("name", "my-pipeline");
        assertThat(map).containsEntry("description", "A test pipeline");
        assertThat(map).containsEntry("version", 3);
        assertThat(map).containsEntry("active", true);
        assertThat(map).containsEntry("config", "{\"key\":\"value\"}");
        assertThat(map).containsEntry("createdAt", "2026-01-01T00:00:00Z");
        assertThat(map).containsEntry("updatedAt", "2026-01-02T00:00:00Z");
        assertThat(map).containsEntry("createdBy", "alice");
        assertThat(map).containsEntry("updatedBy", "bob");
        assertThat(map).containsEntry("tags", "env:prod,team:data");
        assertThat(map).containsEntry("versionControl", 42L);
    }

    @Test
    @DisplayName("toFieldMap: null UUID → null id entry")
    void toFieldMap_nullId_mapsToNull() {
        PipelineRecord record = PipelineRecord.builder()
                .id(null).name("x").tenantId(null).build();
        Map<String, Object> map = record.toFieldMap();
        assertThat(map.get("id")).isNull();
        assertThat(map.get("tenantId")).isNull();
    }

    @Test
    @DisplayName("toFieldMap: returned map is unmodifiable")
    void toFieldMap_isUnmodifiable() {
        PipelineRecord record = sampleRecord();
        Map<String, Object> map = record.toFieldMap();

        assertThatThrownBy(() -> map.put("extra", "not-allowed"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ── toWireBytes / fromWireBytes ─────────────────────────────────────────

    @Test
    @DisplayName("round-trip: toWireBytes then fromWireBytes restores all fields")
    void wireBytes_roundTrip_restoresAllFields() {
        PipelineRecord original = sampleRecord();
        byte[] bytes = original.toWireBytes();
        PipelineRecord recovered = PipelineRecord.fromWireBytes(bytes);

        assertThat(recovered.getId()).isEqualTo(original.getId());
        assertThat(recovered.getName()).isEqualTo(original.getName());
        assertThat(recovered.getVersion()).isEqualTo(original.getVersion());
        assertThat(recovered.isActive()).isEqualTo(original.isActive());
        assertThat(recovered.getConfig()).isEqualTo(original.getConfig());
        assertThat(recovered.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(recovered.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
        assertThat(recovered.getCreatedBy()).isEqualTo(original.getCreatedBy());
        assertThat(recovered.getUpdatedBy()).isEqualTo(original.getUpdatedBy());
        assertThat(recovered.getTags()).isEqualTo(original.getTags());
        assertThat(recovered.getVersionControl()).isEqualTo(original.getVersionControl());
    }

    @Test
    @DisplayName("toWireBytes: produces non-empty byte array")
    void wireBytes_producesNonEmpty() {
        byte[] bytes = sampleRecord().toWireBytes();
        assertThat(bytes).isNotEmpty();
    }

    @Test
    @DisplayName("fromWireBytes: invalid bytes throw IllegalStateException")
    void fromWireBytes_invalidBytes_throwsIllegalState() {
        assertThatThrownBy(() -> PipelineRecord.fromWireBytes(new byte[]{0x00, 0x01, 0x02}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("deserialize");
    }
}
