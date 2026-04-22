package com.ghatana.datacloud.application;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.datacloud.entity.MetaField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SchemaDiffService}.
 *
 * <p>Validates schema comparison, version bump recommendation, and semantic version
 * incrementing across all branching paths.
 *
 * @doc.type test
 * @doc.purpose Validate schema diff computation, versioning recommendations, and version string increments
 * @doc.layer application
 */
@DisplayName("SchemaDiffService Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class SchemaDiffServiceTest {

    @Mock
    private MetricsCollector metrics;

    private SchemaDiffService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new SchemaDiffService(metrics); // GH-90000
    }

    private MetaCollection buildCollection(String version, MetaField... fields) { // GH-90000
        return MetaCollection.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("orders [GH-90000]")
                .schemaVersion(version) // GH-90000
                .fields(List.of(fields)) // GH-90000
                .build(); // GH-90000
    }

    private MetaField field(String name, DataType type) { // GH-90000
        return MetaField.builder().name(name).type(type).build(); // GH-90000
    }

    // =========================================================================
    // COMPARE SCHEMAS
    // =========================================================================

    @Nested
    @DisplayName("compareSchemas [GH-90000]")
    class CompareSchemas {

        @Test
        @DisplayName("should detect no changes when schemas are identical [GH-90000]")
        void shouldDetectNoChanges() { // GH-90000
            MetaField f = field("name", DataType.STRING); // GH-90000
            MetaCollection old = buildCollection("1.0.0", f); // GH-90000
            MetaCollection now = buildCollection("1.0.0", f); // GH-90000

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); // GH-90000

            assertThat(diff.getAllChanges()).isEmpty(); // GH-90000
            assertThat(diff.hasBreakingChanges()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should detect field addition as non-breaking change [GH-90000]")
        void shouldDetectAddedField() { // GH-90000
            MetaCollection old = buildCollection("1.0.0", field("id", DataType.STRING)); // GH-90000
            MetaCollection now = buildCollection("1.0.0", // GH-90000
                    field("id", DataType.STRING), // GH-90000
                    field("email", DataType.STRING)); // GH-90000

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); // GH-90000

            assertThat(diff.getNonBreakingChanges()).isNotEmpty(); // GH-90000
            assertThat(diff.hasBreakingChanges()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should detect field removal as breaking change [GH-90000]")
        void shouldDetectRemovedField() { // GH-90000
            MetaCollection old = buildCollection("1.0.0", // GH-90000
                    field("id", DataType.STRING), // GH-90000
                    field("name", DataType.STRING)); // GH-90000
            MetaCollection now = buildCollection("1.0.0", field("id", DataType.STRING)); // GH-90000

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); // GH-90000

            assertThat(diff.getBreakingChanges()).isNotEmpty(); // GH-90000
            assertThat(diff.hasBreakingChanges()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should detect field type change as breaking [GH-90000]")
        void shouldDetectTypeChange() { // GH-90000
            MetaCollection old = buildCollection("1.0.0", field("count", DataType.STRING)); // GH-90000
            MetaCollection now = buildCollection("1.0.0", field("count", DataType.NUMBER)); // GH-90000

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); // GH-90000

            assertThat(diff.hasBreakingChanges()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should return old and new version in diff [GH-90000]")
        void shouldCaptureVersions() { // GH-90000
            MetaCollection old = buildCollection("1.0.0", field("id", DataType.STRING)); // GH-90000
            MetaCollection now = buildCollection("2.0.0", field("id", DataType.STRING)); // GH-90000

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); // GH-90000

            assertThat(diff.getOldVersion()).isEqualTo("1.0.0 [GH-90000]");
            assertThat(diff.getNewVersion()).isEqualTo("2.0.0 [GH-90000]");
        }

        @Test
        @DisplayName("should throw NullPointerException for null old schema [GH-90000]")
        void shouldHandleNullOldSchema() { // GH-90000
            MetaCollection now = buildCollection("1.0.0", field("id", DataType.STRING)); // GH-90000
            assertThatThrownBy(() -> service.compareSchemas(null, now)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // RECOMMEND VERSION BUMP
    // =========================================================================

    @Nested
    @DisplayName("recommendVersionBump [GH-90000]")
    class RecommendVersionBump {

        @Test
        @DisplayName("should recommend MAJOR when diff has breaking changes [GH-90000]")
        void shouldRecommendMajorForBreakingChanges() { // GH-90000
            MetaCollection old = buildCollection("1.0.0", // GH-90000
                    field("id", DataType.STRING), field("name", DataType.STRING)); // GH-90000
            MetaCollection now = buildCollection("1.0.0", field("id", DataType.STRING)); // GH-90000

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); // GH-90000
            SchemaDiffService.VersionBump bump = service.recommendVersionBump(diff); // GH-90000

            assertThat(bump).isEqualTo(SchemaDiffService.VersionBump.MAJOR); // GH-90000
        }

        @Test
        @DisplayName("should recommend MINOR when diff has non-breaking additions [GH-90000]")
        void shouldRecommendMinorForNonBreakingChanges() { // GH-90000
            MetaCollection old = buildCollection("1.0.0", field("id", DataType.STRING)); // GH-90000
            MetaCollection now = buildCollection("1.0.0", // GH-90000
                    field("id", DataType.STRING), field("email", DataType.STRING)); // GH-90000

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); // GH-90000
            SchemaDiffService.VersionBump bump = service.recommendVersionBump(diff); // GH-90000

            assertThat(bump).isEqualTo(SchemaDiffService.VersionBump.MINOR); // GH-90000
        }

        @Test
        @DisplayName("should recommend PATCH when diff has metadata-only changes (label change) [GH-90000]")
        void shouldRecommendPatchForMetadataChanges() { // GH-90000
            MetaField old = field("name", DataType.STRING); // GH-90000
            MetaField updated = MetaField.builder().name("name [GH-90000]").type(DataType.STRING).label("Full Name [GH-90000]").build();
            SchemaDiffService.SchemaDiff metaOnlyDiff = new SchemaDiffService.SchemaDiff("1.0.0", "1.0.1"); // GH-90000
            metaOnlyDiff.addChange(SchemaDiffService.FieldChange.labelChanged(old, updated)); // GH-90000
            SchemaDiffService.VersionBump bump = service.recommendVersionBump(metaOnlyDiff); // GH-90000
            assertThat(bump).isEqualTo(SchemaDiffService.VersionBump.PATCH); // GH-90000
        }
    }

    // =========================================================================
    // INCREMENT VERSION
    // =========================================================================

    @Nested
    @DisplayName("incrementVersion [GH-90000]")
    class IncrementVersion {

        @ParameterizedTest
        @DisplayName("should increment version correctly [GH-90000]")
        @CsvSource({ // GH-90000
            "1.2.3, MAJOR, 2.0.0",
            "1.2.3, MINOR, 1.3.0",
            "1.2.3, PATCH, 1.2.4",
            "0.0.0, MAJOR, 1.0.0",
            "10.20.30, PATCH, 10.20.31"
        })
        void shouldIncrementCorrectly(String current, String bump, String expected) { // GH-90000
            SchemaDiffService.VersionBump versionBump = SchemaDiffService.VersionBump.valueOf(bump); // GH-90000
            String result = service.incrementVersion(current, versionBump); // GH-90000
            assertThat(result).isEqualTo(expected); // GH-90000
        }

        @Test
        @DisplayName("should throw for invalid version format [GH-90000]")
        void shouldThrowForInvalidFormat() { // GH-90000
            assertThatThrownBy(() -> service.incrementVersion("invalid", SchemaDiffService.VersionBump.PATCH)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null version [GH-90000]")
        void shouldThrowForNullVersion() { // GH-90000
            assertThatThrownBy(() -> service.incrementVersion(null, SchemaDiffService.VersionBump.PATCH)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
