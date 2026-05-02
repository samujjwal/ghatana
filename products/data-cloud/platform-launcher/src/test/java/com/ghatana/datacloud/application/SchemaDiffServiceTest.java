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
@DisplayName("SchemaDiffService Tests")
@ExtendWith(MockitoExtension.class) 
class SchemaDiffServiceTest {

    @Mock
    private MetricsCollector metrics;

    private SchemaDiffService service;

    @BeforeEach
    void setUp() { 
        service = new SchemaDiffService(metrics); 
    }

    private MetaCollection buildCollection(String version, MetaField... fields) { 
        return MetaCollection.builder() 
                .id(UUID.randomUUID()) 
                .tenantId("tenant-1")
                .name("orders")
                .schemaVersion(version) 
                .fields(List.of(fields)) 
                .build(); 
    }

    private MetaField field(String name, DataType type) { 
        return MetaField.builder().name(name).type(type).build(); 
    }

    // =========================================================================
    // COMPARE SCHEMAS
    // =========================================================================

    @Nested
    @DisplayName("compareSchemas")
    class CompareSchemas {

        @Test
        @DisplayName("should detect no changes when schemas are identical")
        void shouldDetectNoChanges() { 
            MetaField f = field("name", DataType.STRING); 
            MetaCollection old = buildCollection("1.0.0", f); 
            MetaCollection now = buildCollection("1.0.0", f); 

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); 

            assertThat(diff.getAllChanges()).isEmpty(); 
            assertThat(diff.hasBreakingChanges()).isFalse(); 
        }

        @Test
        @DisplayName("should detect field addition as non-breaking change")
        void shouldDetectAddedField() { 
            MetaCollection old = buildCollection("1.0.0", field("id", DataType.STRING)); 
            MetaCollection now = buildCollection("1.0.0", 
                    field("id", DataType.STRING), 
                    field("email", DataType.STRING)); 

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); 

            assertThat(diff.getNonBreakingChanges()).isNotEmpty(); 
            assertThat(diff.hasBreakingChanges()).isFalse(); 
        }

        @Test
        @DisplayName("should detect field removal as breaking change")
        void shouldDetectRemovedField() { 
            MetaCollection old = buildCollection("1.0.0", 
                    field("id", DataType.STRING), 
                    field("name", DataType.STRING)); 
            MetaCollection now = buildCollection("1.0.0", field("id", DataType.STRING)); 

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); 

            assertThat(diff.getBreakingChanges()).isNotEmpty(); 
            assertThat(diff.hasBreakingChanges()).isTrue(); 
        }

        @Test
        @DisplayName("should detect field type change as breaking")
        void shouldDetectTypeChange() { 
            MetaCollection old = buildCollection("1.0.0", field("count", DataType.STRING)); 
            MetaCollection now = buildCollection("1.0.0", field("count", DataType.NUMBER)); 

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); 

            assertThat(diff.hasBreakingChanges()).isTrue(); 
        }

        @Test
        @DisplayName("should return old and new version in diff")
        void shouldCaptureVersions() { 
            MetaCollection old = buildCollection("1.0.0", field("id", DataType.STRING)); 
            MetaCollection now = buildCollection("2.0.0", field("id", DataType.STRING)); 

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); 

            assertThat(diff.getOldVersion()).isEqualTo("1.0.0");
            assertThat(diff.getNewVersion()).isEqualTo("2.0.0");
        }

        @Test
        @DisplayName("should throw NullPointerException for null old schema")
        void shouldHandleNullOldSchema() { 
            MetaCollection now = buildCollection("1.0.0", field("id", DataType.STRING)); 
            assertThatThrownBy(() -> service.compareSchemas(null, now)) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }

    // =========================================================================
    // RECOMMEND VERSION BUMP
    // =========================================================================

    @Nested
    @DisplayName("recommendVersionBump")
    class RecommendVersionBump {

        @Test
        @DisplayName("should recommend MAJOR when diff has breaking changes")
        void shouldRecommendMajorForBreakingChanges() { 
            MetaCollection old = buildCollection("1.0.0", 
                    field("id", DataType.STRING), field("name", DataType.STRING)); 
            MetaCollection now = buildCollection("1.0.0", field("id", DataType.STRING)); 

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); 
            SchemaDiffService.VersionBump bump = service.recommendVersionBump(diff); 

            assertThat(bump).isEqualTo(SchemaDiffService.VersionBump.MAJOR); 
        }

        @Test
        @DisplayName("should recommend MINOR when diff has non-breaking additions")
        void shouldRecommendMinorForNonBreakingChanges() { 
            MetaCollection old = buildCollection("1.0.0", field("id", DataType.STRING)); 
            MetaCollection now = buildCollection("1.0.0", 
                    field("id", DataType.STRING), field("email", DataType.STRING)); 

            SchemaDiffService.SchemaDiff diff = service.compareSchemas(old, now); 
            SchemaDiffService.VersionBump bump = service.recommendVersionBump(diff); 

            assertThat(bump).isEqualTo(SchemaDiffService.VersionBump.MINOR); 
        }

        @Test
        @DisplayName("should recommend PATCH when diff has metadata-only changes (label change)")
        void shouldRecommendPatchForMetadataChanges() { 
            MetaField old = field("name", DataType.STRING); 
            MetaField updated = MetaField.builder().name("name").type(DataType.STRING).label("Full Name").build();
            SchemaDiffService.SchemaDiff metaOnlyDiff = new SchemaDiffService.SchemaDiff("1.0.0", "1.0.1"); 
            metaOnlyDiff.addChange(SchemaDiffService.FieldChange.labelChanged(old, updated)); 
            SchemaDiffService.VersionBump bump = service.recommendVersionBump(metaOnlyDiff); 
            assertThat(bump).isEqualTo(SchemaDiffService.VersionBump.PATCH); 
        }
    }

    // =========================================================================
    // INCREMENT VERSION
    // =========================================================================

    @Nested
    @DisplayName("incrementVersion")
    class IncrementVersion {

        @ParameterizedTest
        @DisplayName("should increment version correctly")
        @CsvSource({ 
            "1.2.3, MAJOR, 2.0.0",
            "1.2.3, MINOR, 1.3.0",
            "1.2.3, PATCH, 1.2.4",
            "0.0.0, MAJOR, 1.0.0",
            "10.20.30, PATCH, 10.20.31"
        })
        void shouldIncrementCorrectly(String current, String bump, String expected) { 
            SchemaDiffService.VersionBump versionBump = SchemaDiffService.VersionBump.valueOf(bump); 
            String result = service.incrementVersion(current, versionBump); 
            assertThat(result).isEqualTo(expected); 
        }

        @Test
        @DisplayName("should throw for invalid version format")
        void shouldThrowForInvalidFormat() { 
            assertThatThrownBy(() -> service.incrementVersion("invalid", SchemaDiffService.VersionBump.PATCH)) 
                    .isInstanceOf(IllegalArgumentException.class); 
        }

        @Test
        @DisplayName("should throw NullPointerException for null version")
        void shouldThrowForNullVersion() { 
            assertThatThrownBy(() -> service.incrementVersion(null, SchemaDiffService.VersionBump.PATCH)) 
                    .isInstanceOf(NullPointerException.class); 
        }
    }
}
