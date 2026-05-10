package com.ghatana.datacloud.entity;

import com.ghatana.datacloud.EntityRecord;
import com.ghatana.datacloud.spi.DeletionMode;
import com.ghatana.datacloud.spi.DeletionTombstone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DC-BE-004: Deletion lifecycle tests for EntityRecord.
 *
 * <p>Tests the standardized deletion lifecycle with DeletionMode,
 * DeletionTombstone, and retention policy support.
 *
 * @doc.type class
 * @doc.purpose Deletion lifecycle tests for EntityRecord
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Entity Deletion Lifecycle Tests")
class EntityDeletionLifecycleTest {

    @Nested
    @DisplayName("Soft Delete Tests")
    class SoftDeleteTests {

        @Test
        @DisplayName("Should soft delete entity with reason")
        void shouldSoftDeleteEntityWithReason() {
            EntityRecord entity = EntityRecord.builder()
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.softDelete("user-456", "User requested deletion");

            assertThat(entity.isDeleted()).isTrue();
            assertThat(entity.getActive()).isFalse();
            assertThat(entity.getDeletionMode()).isEqualTo(DeletionMode.SOFT_DELETE);
            assertThat(entity.getDeletedAt()).isNotNull();
            assertThat(entity.getDeletedBy()).isEqualTo("user-456");
            assertThat(entity.getDeletionReason()).isEqualTo("User requested deletion");
        }

        @Test
        @DisplayName("Should soft delete entity without reason (legacy method)")
        void shouldSoftDeleteEntityWithoutReason() {
            EntityRecord entity = EntityRecord.builder()
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.softDelete("user-456");

            assertThat(entity.isDeleted()).isTrue();
            assertThat(entity.getActive()).isFalse();
            assertThat(entity.getDeletionMode()).isEqualTo(DeletionMode.SOFT_DELETE);
            assertThat(entity.getDeletedAt()).isNotNull();
            assertThat(entity.getDeletedBy()).isEqualTo("user-456");
            assertThat(entity.getDeletionReason()).isNull();
        }

        @Test
        @DisplayName("Should restore soft-deleted entity")
        void shouldRestoreSoftDeletedEntity() {
            EntityRecord entity = EntityRecord.builder()
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.softDelete("user-456", "User requested deletion");
            entity.restore("user-789");

            assertThat(entity.isDeleted()).isFalse();
            assertThat(entity.getActive()).isTrue();
            assertThat(entity.getDeletionMode()).isNull();
            assertThat(entity.getDeletedAt()).isNull();
            assertThat(entity.getDeletedBy()).isNull();
            assertThat(entity.getDeletionReason()).isNull();
        }
    }

    @Nested
    @DisplayName("Archive Tests")
    class ArchiveTests {

        @Test
        @DisplayName("Should archive entity with reason")
        void shouldArchiveEntityWithReason() {
            EntityRecord entity = EntityRecord.builder()
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.archive("system-archive", "Retention policy expired");

            assertThat(entity.isDeleted()).isTrue();
            assertThat(entity.isArchived()).isTrue();
            assertThat(entity.getActive()).isFalse();
            assertThat(entity.getDeletionMode()).isEqualTo(DeletionMode.ARCHIVE);
            assertThat(entity.getDeletedAt()).isNotNull();
            assertThat(entity.getDeletedBy()).isEqualTo("system-archive");
            assertThat(entity.getDeletionReason()).isEqualTo("Retention policy expired");
        }

        @Test
        @DisplayName("Should not be marked for purge when archived")
        void shouldNotBeMarkedForPurgeWhenArchived() {
            EntityRecord entity = EntityRecord.builder()
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.archive("system-archive", "Retention policy expired");

            assertThat(entity.isMarkedForPurge()).isFalse();
        }
    }

    @Nested
    @DisplayName("Retention Purge Tests")
    class RetentionPurgeTests {

        @Test
        @DisplayName("Should mark entity for retention purge")
        void shouldMarkEntityForRetentionPurge() {
            EntityRecord entity = EntityRecord.builder()
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.markForRetentionPurge("retention-job", "7-year compliance retention expired");

            assertThat(entity.isMarkedForPurge()).isTrue();
            assertThat(entity.getDeletionMode()).isEqualTo(DeletionMode.RETENTION_PURGE);
            assertThat(entity.getDeletedAt()).isNotNull();
            assertThat(entity.getDeletedBy()).isEqualTo("retention-job");
            assertThat(entity.getDeletionReason()).isEqualTo("7-year compliance retention expired");
        }

        @Test
        @DisplayName("Should not be archived when marked for purge")
        void shouldNotBeArchivedWhenMarkedForPurge() {
            EntityRecord entity = EntityRecord.builder()
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.markForRetentionPurge("retention-job", "7-year compliance retention expired");

            assertThat(entity.isArchived()).isFalse();
        }
    }

    @Nested
    @DisplayName("Tombstone Tests")
    class TombstoneTests {

        @Test
        @DisplayName("Should create tombstone from soft-deleted entity")
        void shouldCreateTombstoneFromSoftDeletedEntity() {
            EntityRecord entity = EntityRecord.builder()
                .id(java.util.UUID.randomUUID())
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.softDelete("user-456", "User requested deletion");

            DeletionTombstone tombstone = entity.toTombstone();

            assertThat(tombstone).isNotNull();
            assertThat(tombstone.resourceType()).isEqualTo("entity");
            assertThat(tombstone.resourceId()).isEqualTo(entity.getId().toString());
            assertThat(tombstone.tenantId()).isEqualTo("tenant-123");
            assertThat(tombstone.deletionMode()).isEqualTo(DeletionMode.SOFT_DELETE);
            assertThat(tombstone.deletedBy()).hasValue("user-456");
            assertThat(tombstone.reason()).hasValue("User requested deletion");
        }

        @Test
        @DisplayName("Should create tombstone from archived entity")
        void shouldCreateTombstoneFromArchivedEntity() {
            EntityRecord entity = EntityRecord.builder()
                .id(java.util.UUID.randomUUID())
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.archive("system-archive", "Retention policy expired");

            DeletionTombstone tombstone = entity.toTombstone();

            assertThat(tombstone.deletionMode()).isEqualTo(DeletionMode.ARCHIVE);
            assertThat(tombstone.deletedBy()).hasValue("system-archive");
            assertThat(tombstone.reason()).hasValue("Retention policy expired");
        }

        @Test
        @DisplayName("Should throw exception when creating tombstone from active entity")
        void shouldThrowExceptionWhenCreatingTombstoneFromActiveEntity() {
            EntityRecord entity = EntityRecord.builder()
                .id(java.util.UUID.randomUUID())
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            assertThatThrownBy(entity::toTombstone)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot create tombstone for active entity");
        }
    }

    @Nested
    @DisplayName("Retention Calculation Tests")
    class RetentionCalculationTests {

        @Test
        @DisplayName("Should calculate retention period for soft delete")
        void shouldCalculateRetentionPeriodForSoftDelete() {
            EntityRecord entity = EntityRecord.builder()
                .id(java.util.UUID.randomUUID())
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.softDelete("user-456", "User requested deletion");

            DeletionTombstone tombstone = entity.toTombstone();
            java.time.Duration retentionPeriod = java.time.Duration.ofDays(30);

            // Tombstone should not be expired immediately after creation
            assertThat(tombstone.isExpired(retentionPeriod)).isFalse();

            // A negative retention window guarantees expiry deterministically.
            assertThat(tombstone.isExpired(java.time.Duration.ofSeconds(-1))).isTrue();
        }

        @Test
        @DisplayName("Should calculate retention period for archive")
        void shouldCalculateRetentionPeriodForArchive() {
            EntityRecord entity = EntityRecord.builder()
                .id(java.util.UUID.randomUUID())
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.archive("system-archive", "Retention policy expired");

            DeletionTombstone tombstone = entity.toTombstone();
            java.time.Duration retentionPeriod = java.time.Duration.ofDays(365);

            assertThat(tombstone.isExpired(retentionPeriod)).isFalse();
        }
    }

    @Nested
    @DisplayName("Deletion Mode Transition Tests")
    class DeletionModeTransitionTests {

        @Test
        @DisplayName("Should transition from soft delete to archive")
        void shouldTransitionFromSoftDeleteToArchive() {
            EntityRecord entity = EntityRecord.builder()
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.softDelete("user-456", "User requested deletion");
            assertThat(entity.getDeletionMode()).isEqualTo(DeletionMode.SOFT_DELETE);

            entity.archive("system-archive", "Retention period expired");
            assertThat(entity.getDeletionMode()).isEqualTo(DeletionMode.ARCHIVE);
            assertThat(entity.isArchived()).isTrue();
        }

        @Test
        @DisplayName("Should transition from soft delete to retention purge")
        void shouldTransitionFromSoftDeleteToRetentionPurge() {
            EntityRecord entity = EntityRecord.builder()
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.softDelete("user-456", "User requested deletion");
            assertThat(entity.getDeletionMode()).isEqualTo(DeletionMode.SOFT_DELETE);

            entity.markForRetentionPurge("retention-job", "7-year compliance retention expired");
            assertThat(entity.getDeletionMode()).isEqualTo(DeletionMode.RETENTION_PURGE);
            assertThat(entity.isMarkedForPurge()).isTrue();
        }

        @Test
        @DisplayName("Should reset deletion state on restore")
        void shouldResetDeletionStateOnRestore() {
            EntityRecord entity = EntityRecord.builder()
                .tenantId("tenant-123")
                .collectionName("test-collection")
                .data(Map.of("name", "test"))
                .build();

            entity.softDelete("user-456", "User requested deletion");
            entity.archive("system-archive", "Retention period expired");
            entity.restore("user-789");

            assertThat(entity.getDeletionMode()).isNull();
            assertThat(entity.getDeletedAt()).isNull();
            assertThat(entity.getDeletedBy()).isNull();
            assertThat(entity.getDeletionReason()).isNull();
            assertThat(entity.isDeleted()).isFalse();
        }
    }
}
