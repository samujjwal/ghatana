package com.ghatana.datacloud.application.version;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.version.EntityVersion;
import com.ghatana.datacloud.entity.version.VersionDiff;
import com.ghatana.datacloud.entity.version.VersionMetadata;
import com.ghatana.datacloud.entity.version.VersionRecord;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link VersionService}.
 *
 * @doc.type test
 * @doc.purpose Validate entity versioning, version history retrieval, comparison, and deletion
 * @doc.layer application
 */
@DisplayName("VersionService Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class VersionServiceTest extends EventloopTestBase {

    @Mock
    private VersionRecord versionRepository;

    @Mock
    private VersionComparator versionComparator;

    private VersionService service;

    private Entity sampleEntity;
    private EntityVersion sampleVersion;
    private UUID entityId;

    @BeforeEach
    void setUp() { // GH-90000
        service = new VersionService(versionRepository, versionComparator); // GH-90000
        entityId = UUID.randomUUID(); // GH-90000
        sampleEntity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .data(new HashMap<>(Map.of("name", "Widget"))) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .build(); // GH-90000
        VersionMetadata meta = new VersionMetadata("user-1", Instant.now(), "initial version"); // GH-90000
        sampleVersion = new EntityVersion(UUID.randomUUID(), "tenant-1", entityId, sampleEntity, 1, meta, Instant.now()); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException for null repository [GH-90000]")
        void shouldThrowForNullRepository() { // GH-90000
            assertThatThrownBy(() -> new VersionService(null, versionComparator)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null comparator [GH-90000]")
        void shouldThrowForNullComparator() { // GH-90000
            assertThatThrownBy(() -> new VersionService(versionRepository, null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // CREATE VERSION
    // =========================================================================

    @Nested
    @DisplayName("createVersion [GH-90000]")
    class CreateVersion {

        @Test
        @DisplayName("should create and return new version on save [GH-90000]")
        void shouldCreateVersion() { // GH-90000
            when(versionRepository.saveVersion(eq("tenant-1 [GH-90000]"), eq(sampleEntity), any()))
                    .thenReturn(Promise.of(sampleVersion)); // GH-90000

            EntityVersion result = runPromise(() -> service.createVersion("tenant-1", sampleEntity, "user-1", "initial")); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getVersionNumber()).isEqualTo(1); // GH-90000
            verify(versionRepository).saveVersion(eq("tenant-1 [GH-90000]"), eq(sampleEntity), any());
        }

        @Test
        @DisplayName("should throw NullPointerException for null entity [GH-90000]")
        void shouldThrowForNullEntity() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> service.createVersion("tenant-1", null, "user-1", "reason"))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("should throw NullPointerException for null tenantId [GH-90000]")
        void shouldThrowForNullTenantId() { // GH-90000
            assertThatThrownBy(() -> runPromise(() -> service.createVersion(null, sampleEntity, "user-1", "reason"))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // =========================================================================
    // GET VERSION HISTORY
    // =========================================================================

    @Nested
    @DisplayName("getVersionHistory [GH-90000]")
    class GetVersionHistory {

        @Test
        @DisplayName("should return version history for entity [GH-90000]")
        void shouldReturnVersionHistory() { // GH-90000
            when(versionRepository.getVersionHistory("tenant-1", entityId)) // GH-90000
                    .thenReturn(Promise.of(List.of(sampleVersion))); // GH-90000

            List<EntityVersion> history = runPromise(() -> service.getVersionHistory("tenant-1", entityId)); // GH-90000
            assertThat(history).hasSize(1); // GH-90000
        }

        @Test
        @DisplayName("should return empty list when no versions exist [GH-90000]")
        void shouldReturnEmptyListWhenNoVersions() { // GH-90000
            when(versionRepository.getVersionHistory("tenant-1", entityId)) // GH-90000
                    .thenReturn(Promise.of(List.of())); // GH-90000

            List<EntityVersion> history = runPromise(() -> service.getVersionHistory("tenant-1", entityId)); // GH-90000
            assertThat(history).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // GET VERSION
    // =========================================================================

    @Nested
    @DisplayName("getVersion [GH-90000]")
    class GetVersion {

        @Test
        @DisplayName("should return specific version by version number [GH-90000]")
        void shouldReturnSpecificVersion() { // GH-90000
            when(versionRepository.getVersion("tenant-1", entityId, 1)) // GH-90000
                    .thenReturn(Promise.of(sampleVersion)); // GH-90000

            EntityVersion result = runPromise(() -> service.getVersion("tenant-1", entityId, 1)); // GH-90000
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.getVersionNumber()).isEqualTo(1); // GH-90000
        }
    }

    // =========================================================================
    // GET LATEST VERSION
    // =========================================================================

    @Nested
    @DisplayName("getLatestVersion [GH-90000]")
    class GetLatestVersion {

        @Test
        @DisplayName("should return latest version for entity [GH-90000]")
        void shouldReturnLatestVersion() { // GH-90000
            when(versionRepository.getLatestVersion("tenant-1", entityId)) // GH-90000
                    .thenReturn(Promise.of(sampleVersion)); // GH-90000

            EntityVersion result = runPromise(() -> service.getLatestVersion("tenant-1", entityId)); // GH-90000
            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // =========================================================================
    // COMPARE VERSIONS
    // =========================================================================

    @Nested
    @DisplayName("compareVersions [GH-90000]")
    class CompareVersions {

        @Test
        @DisplayName("should delegate version comparison to repository [GH-90000]")
        void shouldCompareVersions() { // GH-90000
            VersionDiff diff = mock(VersionDiff.class); // GH-90000
            when(versionRepository.computeDiff("tenant-1", entityId, 1, 2)) // GH-90000
                    .thenReturn(Promise.of(diff)); // GH-90000

            VersionDiff result = runPromise(() -> service.compareVersions("tenant-1", entityId, 1, 2)); // GH-90000
            assertThat(result).isNotNull(); // GH-90000
        }
    }

    // =========================================================================
    // COUNT / HAS VERSIONS
    // =========================================================================

    @Nested
    @DisplayName("countVersions and hasVersions [GH-90000]")
    class CountHasVersions {

        @Test
        @DisplayName("should return count of versions for entity [GH-90000]")
        void shouldReturnVersionCount() { // GH-90000
            when(versionRepository.countVersions("tenant-1", entityId)) // GH-90000
                    .thenReturn(Promise.of(3)); // GH-90000

            int count = runPromise(() -> service.countVersions("tenant-1", entityId)); // GH-90000
            assertThat(count).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("should return true when entity has versions [GH-90000]")
        void shouldReturnTrueWhenHasVersions() { // GH-90000
            when(versionRepository.countVersions("tenant-1", entityId)) // GH-90000
                    .thenReturn(Promise.of(2)); // GH-90000

            boolean hasVersions = runPromise(() -> service.hasVersions("tenant-1", entityId)); // GH-90000
            assertThat(hasVersions).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should return false when entity has no versions [GH-90000]")
        void shouldReturnFalseWhenNoVersions() { // GH-90000
            when(versionRepository.countVersions("tenant-1", entityId)) // GH-90000
                    .thenReturn(Promise.of(0)); // GH-90000

            boolean hasVersions = runPromise(() -> service.hasVersions("tenant-1", entityId)); // GH-90000
            assertThat(hasVersions).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // DELETE VERSION HISTORY
    // =========================================================================

    @Nested
    @DisplayName("deleteVersionHistory [GH-90000]")
    class DeleteVersionHistory {

        @Test
        @DisplayName("should delete all versions and return deleted count [GH-90000]")
        void shouldDeleteVersionHistory() { // GH-90000
            when(versionRepository.deleteVersionHistory("tenant-1", entityId)) // GH-90000
                    .thenReturn(Promise.of(5)); // GH-90000

            int deleted = runPromise(() -> service.deleteVersionHistory("tenant-1", entityId)); // GH-90000
            assertThat(deleted).isEqualTo(5); // GH-90000
        }
    }
}
