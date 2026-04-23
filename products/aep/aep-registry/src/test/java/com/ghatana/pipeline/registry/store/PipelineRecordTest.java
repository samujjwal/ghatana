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

    private static PipelineRecord sampleRecord() { // GH-90000
        return PipelineRecord.builder() // GH-90000
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .tenantId(TenantId.of("tenant-a"))
                .name("my-pipeline")
                .description("A test pipeline")
                .version(3) // GH-90000
                .active(true) // GH-90000
                .config("{\"key\":\"value\"}") // GH-90000
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .updatedAt(Instant.parse("2026-01-02T00:00:00Z"))
                .createdBy("alice")
                .updatedBy("bob")
                .tags("env:prod,team:data")
                .versionControl(42L) // GH-90000
                .build(); // GH-90000
    }

    // ── toFieldMap ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("toFieldMap: all fields are present and correctly mapped")
    void toFieldMap_allFields_presentAndCorrect() { // GH-90000
        PipelineRecord record = sampleRecord(); // GH-90000
        Map<String, Object> map = record.toFieldMap(); // GH-90000

        assertThat(map).containsEntry("id", "00000000-0000-0000-0000-000000000001"); // GH-90000
        assertThat(map).containsEntry("tenantId", "TenantId[value=tenant-a]"); // GH-90000
        assertThat(map).containsEntry("name", "my-pipeline"); // GH-90000
        assertThat(map).containsEntry("description", "A test pipeline"); // GH-90000
        assertThat(map).containsEntry("version", 3); // GH-90000
        assertThat(map).containsEntry("active", true); // GH-90000
        assertThat(map).containsEntry("config", "{\"key\":\"value\"}"); // GH-90000
        assertThat(map).containsEntry("createdAt", "2026-01-01T00:00:00Z"); // GH-90000
        assertThat(map).containsEntry("updatedAt", "2026-01-02T00:00:00Z"); // GH-90000
        assertThat(map).containsEntry("createdBy", "alice"); // GH-90000
        assertThat(map).containsEntry("updatedBy", "bob"); // GH-90000
        assertThat(map).containsEntry("tags", "env:prod,team:data"); // GH-90000
        assertThat(map).containsEntry("versionControl", 42L); // GH-90000
    }

    @Test
    @DisplayName("toFieldMap: null UUID → null id entry")
    void toFieldMap_nullId_mapsToNull() { // GH-90000
        PipelineRecord record = PipelineRecord.builder() // GH-90000
                .id(null).name("x").tenantId(null).build();
        Map<String, Object> map = record.toFieldMap(); // GH-90000
        assertThat(map.get("id")).isNull();
        assertThat(map.get("tenantId")).isNull();
    }

    @Test
    @DisplayName("toFieldMap: returned map is unmodifiable")
    void toFieldMap_isUnmodifiable() { // GH-90000
        PipelineRecord record = sampleRecord(); // GH-90000
        Map<String, Object> map = record.toFieldMap(); // GH-90000

        assertThatThrownBy(() -> map.put("extra", "not-allowed")) // GH-90000
                .isInstanceOf(UnsupportedOperationException.class); // GH-90000
    }

    // ── toWireBytes / fromWireBytes ─────────────────────────────────────────

    @Test
    @DisplayName("round-trip: toWireBytes then fromWireBytes restores all fields")
    void wireBytes_roundTrip_restoresAllFields() { // GH-90000
        PipelineRecord original = sampleRecord(); // GH-90000
        byte[] bytes = original.toWireBytes(); // GH-90000
        PipelineRecord recovered = PipelineRecord.fromWireBytes(bytes); // GH-90000

        assertThat(recovered.getId()).isEqualTo(original.getId()); // GH-90000
        assertThat(recovered.getName()).isEqualTo(original.getName()); // GH-90000
        assertThat(recovered.getVersion()).isEqualTo(original.getVersion()); // GH-90000
        assertThat(recovered.isActive()).isEqualTo(original.isActive()); // GH-90000
        assertThat(recovered.getConfig()).isEqualTo(original.getConfig()); // GH-90000
        assertThat(recovered.getCreatedAt()).isEqualTo(original.getCreatedAt()); // GH-90000
        assertThat(recovered.getUpdatedAt()).isEqualTo(original.getUpdatedAt()); // GH-90000
        assertThat(recovered.getCreatedBy()).isEqualTo(original.getCreatedBy()); // GH-90000
        assertThat(recovered.getUpdatedBy()).isEqualTo(original.getUpdatedBy()); // GH-90000
        assertThat(recovered.getTags()).isEqualTo(original.getTags()); // GH-90000
        assertThat(recovered.getVersionControl()).isEqualTo(original.getVersionControl()); // GH-90000
    }

    @Test
    @DisplayName("toWireBytes: produces non-empty byte array")
    void wireBytes_producesNonEmpty() { // GH-90000
        byte[] bytes = sampleRecord().toWireBytes(); // GH-90000
        assertThat(bytes).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("fromWireBytes: invalid bytes throw IllegalStateException")
    void fromWireBytes_invalidBytes_throwsIllegalState() { // GH-90000
        assertThatThrownBy(() -> PipelineRecord.fromWireBytes(new byte[]{0x00, 0x01, 0x02})) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("deserialize");
    }
}
