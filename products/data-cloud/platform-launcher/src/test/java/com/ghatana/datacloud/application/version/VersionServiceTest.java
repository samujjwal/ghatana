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
@DisplayName("VersionService Tests")
@ExtendWith(MockitoExtension.class)
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
    void setUp() {
        service = new VersionService(versionRepository, versionComparator);
        entityId = UUID.randomUUID();
        sampleEntity = Entity.builder()
                .id(entityId)
                .tenantId("tenant-1")
                .collectionName("products")
                .data(new HashMap<>(Map.of("name", "Widget")))
                .createdAt(Instant.now())
                .build();
        VersionMetadata meta = new VersionMetadata("user-1", Instant.now(), "initial version");
        sampleVersion = new EntityVersion(UUID.randomUUID(), "tenant-1", entityId, sampleEntity, 1, meta, Instant.now());
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should throw NullPointerException for null repository")
        void shouldThrowForNullRepository() {
            assertThatThrownBy(() -> new VersionService(null, versionComparator))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException for null comparator")
        void shouldThrowForNullComparator() {
            assertThatThrownBy(() -> new VersionService(versionRepository, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // CREATE VERSION
    // =========================================================================

    @Nested
    @DisplayName("createVersion")
    class CreateVersion {

        @Test
        @DisplayName("should create and return new version on save")
        void shouldCreateVersion() {
            when(versionRepository.saveVersion(eq("tenant-1"), eq(sampleEntity), any()))
                    .thenReturn(Promise.of(sampleVersion));

            EntityVersion result = runPromise(() -> service.createVersion("tenant-1", sampleEntity, "user-1", "initial"));

            assertThat(result).isNotNull();
            assertThat(result.getVersionNumber()).isEqualTo(1);
            verify(versionRepository).saveVersion(eq("tenant-1"), eq(sampleEntity), any());
        }

        @Test
        @DisplayName("should throw NullPointerException for null entity")
        void shouldThrowForNullEntity() {
            assertThatThrownBy(() -> runPromise(() -> service.createVersion("tenant-1", null, "user-1", "reason")))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should throw NullPointerException for null tenantId")
        void shouldThrowForNullTenantId() {
            assertThatThrownBy(() -> runPromise(() -> service.createVersion(null, sampleEntity, "user-1", "reason")))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // GET VERSION HISTORY
    // =========================================================================

    @Nested
    @DisplayName("getVersionHistory")
    class GetVersionHistory {

        @Test
        @DisplayName("should return version history for entity")
        void shouldReturnVersionHistory() {
            when(versionRepository.getVersionHistory("tenant-1", entityId))
                    .thenReturn(Promise.of(List.of(sampleVersion)));

            List<EntityVersion> history = runPromise(() -> service.getVersionHistory("tenant-1", entityId));
            assertThat(history).hasSize(1);
        }

        @Test
        @DisplayName("should return empty list when no versions exist")
        void shouldReturnEmptyListWhenNoVersions() {
            when(versionRepository.getVersionHistory("tenant-1", entityId))
                    .thenReturn(Promise.of(List.of()));

            List<EntityVersion> history = runPromise(() -> service.getVersionHistory("tenant-1", entityId));
            assertThat(history).isEmpty();
        }
    }

    // =========================================================================
    // GET VERSION
    // =========================================================================

    @Nested
    @DisplayName("getVersion")
    class GetVersion {

        @Test
        @DisplayName("should return specific version by version number")
        void shouldReturnSpecificVersion() {
            when(versionRepository.getVersion("tenant-1", entityId, 1))
                    .thenReturn(Promise.of(sampleVersion));

            EntityVersion result = runPromise(() -> service.getVersion("tenant-1", entityId, 1));
            assertThat(result).isNotNull();
            assertThat(result.getVersionNumber()).isEqualTo(1);
        }
    }

    // =========================================================================
    // GET LATEST VERSION
    // =========================================================================

    @Nested
    @DisplayName("getLatestVersion")
    class GetLatestVersion {

        @Test
        @DisplayName("should return latest version for entity")
        void shouldReturnLatestVersion() {
            when(versionRepository.getLatestVersion("tenant-1", entityId))
                    .thenReturn(Promise.of(sampleVersion));

            EntityVersion result = runPromise(() -> service.getLatestVersion("tenant-1", entityId));
            assertThat(result).isNotNull();
        }
    }

    // =========================================================================
    // COMPARE VERSIONS
    // =========================================================================

    @Nested
    @DisplayName("compareVersions")
    class CompareVersions {

        @Test
        @DisplayName("should delegate version comparison to repository")
        void shouldCompareVersions() {
            VersionDiff diff = mock(VersionDiff.class);
            when(versionRepository.computeDiff("tenant-1", entityId, 1, 2))
                    .thenReturn(Promise.of(diff));

            VersionDiff result = runPromise(() -> service.compareVersions("tenant-1", entityId, 1, 2));
            assertThat(result).isNotNull();
        }
    }

    // =========================================================================
    // COUNT / HAS VERSIONS
    // =========================================================================

    @Nested
    @DisplayName("countVersions and hasVersions")
    class CountHasVersions {

        @Test
        @DisplayName("should return count of versions for entity")
        void shouldReturnVersionCount() {
            when(versionRepository.countVersions("tenant-1", entityId))
                    .thenReturn(Promise.of(3));

            int count = runPromise(() -> service.countVersions("tenant-1", entityId));
            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("should return true when entity has versions")
        void shouldReturnTrueWhenHasVersions() {
            when(versionRepository.countVersions("tenant-1", entityId))
                    .thenReturn(Promise.of(2));

            boolean hasVersions = runPromise(() -> service.hasVersions("tenant-1", entityId));
            assertThat(hasVersions).isTrue();
        }

        @Test
        @DisplayName("should return false when entity has no versions")
        void shouldReturnFalseWhenNoVersions() {
            when(versionRepository.countVersions("tenant-1", entityId))
                    .thenReturn(Promise.of(0));

            boolean hasVersions = runPromise(() -> service.hasVersions("tenant-1", entityId));
            assertThat(hasVersions).isFalse();
        }
    }

    // =========================================================================
    // DELETE VERSION HISTORY
    // =========================================================================

    @Nested
    @DisplayName("deleteVersionHistory")
    class DeleteVersionHistory {

        @Test
        @DisplayName("should delete all versions and return deleted count")
        void shouldDeleteVersionHistory() {
            when(versionRepository.deleteVersionHistory("tenant-1", entityId))
                    .thenReturn(Promise.of(5));

            int deleted = runPromise(() -> service.deleteVersionHistory("tenant-1", entityId));
            assertThat(deleted).isEqualTo(5);
        }
    }
}
