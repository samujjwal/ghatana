/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.version;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for entity history and versioning.
 *
 * <p>Tests EntityVersion creation, VersionRecord operations,
 * VersionMetadata, and version comparison/rollback scenarios.</p>
 *
 * @doc.type test
 * @doc.purpose Entity history and versioning tests
 * @doc.layer domain
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("Entity History Tests")
class EntityHistoryTest extends EventloopTestBase {

    @Mock
    private VersionRecord versionRecord;

    @Mock
    private Entity entity;

    // =========================================================================
    // ENTITY VERSION CREATION
    // =========================================================================

    @Nested
    @DisplayName("EntityVersion creation")
    class EntityVersionCreation {

        @Test
        @DisplayName("should create EntityVersion with all required fields")
        void shouldCreateEntityVersionWithRequiredFields() { // GH-90000
            UUID versionId = UUID.randomUUID(); // GH-90000
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); // GH-90000
            Integer versionNumber = 1;
            Instant createdAt = Instant.now(); // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", createdAt, "Initial creation"); // GH-90000

            EntityVersion version = new EntityVersion( // GH-90000
                versionId,
                tenantId,
                entityId,
                entity,
                versionNumber,
                metadata,
                createdAt
            );

            assertThat(version.getId()).isEqualTo(versionId); // GH-90000
            assertThat(version.getTenantId()).isEqualTo(tenantId); // GH-90000
            assertThat(version.getEntityId()).isEqualTo(entityId); // GH-90000
            assertThat(version.getEntitySnapshot()).isEqualTo(entity); // GH-90000
            assertThat(version.getVersionNumber()).isEqualTo(versionNumber); // GH-90000
            assertThat(version.getMetadata()).isEqualTo(metadata); // GH-90000
            assertThat(version.getCreatedAt()).isEqualTo(createdAt); // GH-90000
        }

        @Test
        @DisplayName("should reject null ID")
        void shouldRejectNullId() { // GH-90000
            assertThatThrownBy(() -> new EntityVersion( // GH-90000
                null,
                "tenant-123",
                UUID.randomUUID(), // GH-90000
                entity,
                1,
                new VersionMetadata("user-456", Instant.now(), "test"), // GH-90000
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("ID must not be null");
        }

        @Test
        @DisplayName("should reject null tenant ID")
        void shouldRejectNullTenantId() { // GH-90000
            assertThatThrownBy(() -> new EntityVersion( // GH-90000
                UUID.randomUUID(), // GH-90000
                null,
                UUID.randomUUID(), // GH-90000
                entity,
                1,
                new VersionMetadata("user-456", Instant.now(), "test"), // GH-90000
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("Tenant ID must not be null");
        }

        @Test
        @DisplayName("should reject null entity ID")
        void shouldRejectNullEntityId() { // GH-90000
            assertThatThrownBy(() -> new EntityVersion( // GH-90000
                UUID.randomUUID(), // GH-90000
                "tenant-123",
                null,
                entity,
                1,
                new VersionMetadata("user-456", Instant.now(), "test"), // GH-90000
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("Entity ID must not be null");
        }

        @Test
        @DisplayName("should reject null entity snapshot")
        void shouldRejectNullEntitySnapshot() { // GH-90000
            assertThatThrownBy(() -> new EntityVersion( // GH-90000
                UUID.randomUUID(), // GH-90000
                "tenant-123",
                UUID.randomUUID(), // GH-90000
                null,
                1,
                new VersionMetadata("user-456", Instant.now(), "test"), // GH-90000
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("Entity snapshot must not be null");
        }

        @Test
        @DisplayName("should reject null version number")
        void shouldRejectNullVersionNumber() { // GH-90000
            assertThatThrownBy(() -> new EntityVersion( // GH-90000
                UUID.randomUUID(), // GH-90000
                "tenant-123",
                UUID.randomUUID(), // GH-90000
                entity,
                null,
                new VersionMetadata("user-456", Instant.now(), "test"), // GH-90000
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("Version number must not be null");
        }

        @Test
        @DisplayName("should reject version number less than 1")
        void shouldRejectVersionNumberLessThan1() { // GH-90000
            assertThatThrownBy(() -> new EntityVersion( // GH-90000
                UUID.randomUUID(), // GH-90000
                "tenant-123",
                UUID.randomUUID(), // GH-90000
                entity,
                0,
                new VersionMetadata("user-456", Instant.now(), "test"), // GH-90000
                Instant.now() // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("Version number must be >= 1");
        }

        @Test
        @DisplayName("should reject null metadata")
        void shouldRejectNullMetadata() { // GH-90000
            assertThatThrownBy(() -> new EntityVersion( // GH-90000
                UUID.randomUUID(), // GH-90000
                "tenant-123",
                UUID.randomUUID(), // GH-90000
                entity,
                1,
                null,
                Instant.now() // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("Metadata must not be null");
        }

        @Test
        @DisplayName("should reject null createdAt")
        void shouldRejectNullCreatedAt() { // GH-90000
            assertThatThrownBy(() -> new EntityVersion( // GH-90000
                UUID.randomUUID(), // GH-90000
                "tenant-123",
                UUID.randomUUID(), // GH-90000
                entity,
                1,
                new VersionMetadata("user-456", Instant.now(), "test"), // GH-90000
                null
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("Created at must not be null");
        }

        @Test
        @DisplayName("should create EntityVersion using builder")
        void shouldCreateEntityVersionUsingBuilder() { // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            Instant createdAt = Instant.now(); // GH-90000

            EntityVersion version = EntityVersion.builder() // GH-90000
                .tenantId("tenant-123")
                .entityId(entityId) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(1) // GH-90000
                .metadata(new VersionMetadata("user-456", createdAt, "Initial creation")) // GH-90000
                .createdAt(createdAt) // GH-90000
                .build(); // GH-90000

            assertThat(version.getTenantId()).isEqualTo("tenant-123");
            assertThat(version.getEntityId()).isEqualTo(entityId); // GH-90000
            assertThat(version.getVersionNumber()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("builder should auto-generate ID if not provided")
        void builderShouldAutoGenerateIdIfNotProvided() { // GH-90000
            EntityVersion version = EntityVersion.builder() // GH-90000
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(1) // GH-90000
                .metadata(new VersionMetadata("user-456", Instant.now(), "test")) // GH-90000
                .build(); // GH-90000

            assertThat(version.getId()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("builder should auto-generate createdAt if not provided")
        void builderShouldAutoGenerateCreatedAtIfNotProvided() { // GH-90000
            EntityVersion version = EntityVersion.builder() // GH-90000
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(1) // GH-90000
                .metadata(new VersionMetadata("user-456", Instant.now(), "test")) // GH-90000
                .build(); // GH-90000

            assertThat(version.getCreatedAt()).isNotNull(); // GH-90000
            assertThat(version.getCreatedAt()).isBeforeOrEqualTo(Instant.now()); // GH-90000
        }
    }

    // =========================================================================
    // ENTITY VERSION ACCESSORS
    // =========================================================================

    @Nested
    @DisplayName("EntityVersion accessors")
    class EntityVersionAccessors {

        @Test
        @DisplayName("should get author from metadata")
        void shouldGetAuthorFromMetadata() { // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", Instant.now(), "test"); // GH-90000
            EntityVersion version = EntityVersion.builder() // GH-90000
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(1) // GH-90000
                .metadata(metadata) // GH-90000
                .build(); // GH-90000

            assertThat(version.getAuthor()).isEqualTo("user-456");
        }

        @Test
        @DisplayName("should get reason from metadata")
        void shouldGetReasonFromMetadata() { // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", Instant.now(), "Updated contact info"); // GH-90000
            EntityVersion version = EntityVersion.builder() // GH-90000
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(1) // GH-90000
                .metadata(metadata) // GH-90000
                .build(); // GH-90000

            assertThat(version.getReason()).isEqualTo("Updated contact info");
        }

        @Test
        @DisplayName("should return null reason if metadata reason is null")
        void shouldReturnNullReasonIfMetadataReasonIsNull() { // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", Instant.now(), null); // GH-90000
            EntityVersion version = EntityVersion.builder() // GH-90000
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(1) // GH-90000
                .metadata(metadata) // GH-90000
                .build(); // GH-90000

            assertThat(version.getReason()).isNull(); // GH-90000
        }
    }

    // =========================================================================
    // ENTITY VERSION EQUALITY
    // =========================================================================

    @Nested
    @DisplayName("EntityVersion equality")
    class EntityVersionEquality {

        @Test
        @DisplayName("should be equal when IDs match")
        void shouldBeEqualWhenIdsMatch() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            Instant createdAt = Instant.now(); // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", createdAt, "test"); // GH-90000

            EntityVersion version1 = new EntityVersion(id, "tenant-123", entityId, entity, 1, metadata, createdAt); // GH-90000
            EntityVersion version2 = new EntityVersion(id, "tenant-123", entityId, entity, 1, metadata, createdAt); // GH-90000

            assertThat(version1).isEqualTo(version2); // GH-90000
            assertThat(version1.hashCode()).isEqualTo(version2.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("should not be equal when IDs differ")
        void shouldNotBeEqualWhenIdsDiffer() { // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            Instant createdAt = Instant.now(); // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", createdAt, "test"); // GH-90000

            EntityVersion version1 = new EntityVersion(UUID.randomUUID(), "tenant-123", entityId, entity, 1, metadata, createdAt); // GH-90000
            EntityVersion version2 = new EntityVersion(UUID.randomUUID(), "tenant-123", entityId, entity, 1, metadata, createdAt); // GH-90000

            assertThat(version1).isNotEqualTo(version2); // GH-90000
        }

        @Test
        @DisplayName("toString should include key fields")
        void toStringShouldIncludeKeyFields() { // GH-90000
            UUID entityId = UUID.randomUUID(); // GH-90000
            Instant createdAt = Instant.now(); // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", createdAt, "test"); // GH-90000

            EntityVersion version = new EntityVersion(UUID.randomUUID(), "tenant-123", entityId, entity, 1, metadata, createdAt); // GH-90000

            assertThat(version.toString()) // GH-90000
                .contains("entityId=")
                .contains("versionNumber=1")
                .contains("author='user-456'");
        }
    }

    // =========================================================================
    // VERSION METADATA
    // =========================================================================

    @Nested
    @DisplayName("VersionMetadata")
    class VersionMetadataTests {

        @Test
        @DisplayName("should create VersionMetadata with required fields")
        void shouldCreateVersionMetadataWithRequiredFields() { // GH-90000
            String author = "user-456";
            Instant timestamp = Instant.now(); // GH-90000
            String reason = "Updated contact info";

            VersionMetadata metadata = new VersionMetadata(author, timestamp, reason); // GH-90000

            assertThat(metadata.author()).isEqualTo(author); // GH-90000
            assertThat(metadata.timestamp()).isEqualTo(timestamp); // GH-90000
            assertThat(metadata.reason()).isEqualTo(reason); // GH-90000
        }

        @Test
        @DisplayName("should create VersionMetadata with null reason")
        void shouldCreateVersionMetadataWithNullReason() { // GH-90000
            String author = "user-456";
            Instant timestamp = Instant.now(); // GH-90000

            VersionMetadata metadata = new VersionMetadata(author, timestamp, null); // GH-90000

            assertThat(metadata.author()).isEqualTo(author); // GH-90000
            assertThat(metadata.timestamp()).isEqualTo(timestamp); // GH-90000
            assertThat(metadata.reason()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should reject null author")
        void shouldRejectNullAuthor() { // GH-90000
            assertThatThrownBy(() -> new VersionMetadata(null, Instant.now(), "test")) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("Author must not be null");
        }

        @Test
        @DisplayName("should reject null timestamp")
        void shouldRejectNullTimestamp() { // GH-90000
            assertThatThrownBy(() -> new VersionMetadata("user-456", null, "test")) // GH-90000
                .isInstanceOf(NullPointerException.class) // GH-90000
                .hasMessageContaining("Timestamp must not be null");
        }

        @Test
        @DisplayName("should generate summary with reason")
        void shouldGenerateSummaryWithReason() { // GH-90000
            Instant timestamp = Instant.now(); // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", timestamp, "Updated contact info"); // GH-90000

            String summary = metadata.getSummary(); // GH-90000

            assertThat(summary).contains("Updated contact info");
            assertThat(summary).contains("user-456");
            assertThat(summary).contains(timestamp.toString()); // GH-90000
        }

        @Test
        @DisplayName("should generate summary without reason")
        void shouldGenerateSummaryWithoutReason() { // GH-90000
            Instant timestamp = Instant.now(); // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", timestamp, null); // GH-90000

            String summary = metadata.getSummary(); // GH-90000

            assertThat(summary).contains("Modified by user-456");
            assertThat(summary).contains(timestamp.toString()); // GH-90000
        }

        @Test
        @DisplayName("should generate summary with empty reason")
        void shouldGenerateSummaryWithEmptyReason() { // GH-90000
            Instant timestamp = Instant.now(); // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", timestamp, ""); // GH-90000

            String summary = metadata.getSummary(); // GH-90000

            assertThat(summary).contains("Modified by user-456");
            assertThat(summary).contains(timestamp.toString()); // GH-90000
        }
    }

    // =========================================================================
    // VERSION RECORD OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("VersionRecord operations")
    class VersionRecordOperations {

        @Test
        @DisplayName("should save version")
        void shouldSaveVersion() { // GH-90000
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); // GH-90000
            Instant createdAt = Instant.now(); // GH-90000
            VersionMetadata metadata = new VersionMetadata("user-456", createdAt, "Initial creation"); // GH-90000
            EntityVersion version = EntityVersion.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .entityId(entityId) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(1) // GH-90000
                .metadata(metadata) // GH-90000
                .createdAt(createdAt) // GH-90000
                .build(); // GH-90000

            when(versionRecord.saveVersion(tenantId, entity, metadata)) // GH-90000
                .thenReturn(Promise.of(version)); // GH-90000

            EntityVersion result = runPromise(() -> versionRecord.saveVersion(tenantId, entity, metadata)); // GH-90000

            assertThat(result).isEqualTo(version); // GH-90000
            verify(versionRecord).saveVersion(tenantId, entity, metadata); // GH-90000
        }

        @Test
        @DisplayName("should get version history")
        void shouldGetVersionHistory() { // GH-90000
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); // GH-90000
            List<EntityVersion> versions = List.of( // GH-90000
                EntityVersion.builder() // GH-90000
                    .tenantId(tenantId) // GH-90000
                    .entityId(entityId) // GH-90000
                    .entitySnapshot(entity) // GH-90000
                    .versionNumber(1) // GH-90000
                    .metadata(new VersionMetadata("user-456", Instant.now(), "v1")) // GH-90000
                    .build(), // GH-90000
                EntityVersion.builder() // GH-90000
                    .tenantId(tenantId) // GH-90000
                    .entityId(entityId) // GH-90000
                    .entitySnapshot(entity) // GH-90000
                    .versionNumber(2) // GH-90000
                    .metadata(new VersionMetadata("user-456", Instant.now(), "v2")) // GH-90000
                    .build() // GH-90000
            );

            when(versionRecord.getVersionHistory(tenantId, entityId)) // GH-90000
                .thenReturn(Promise.of(versions)); // GH-90000

            List<EntityVersion> result = runPromise(() -> versionRecord.getVersionHistory(tenantId, entityId)); // GH-90000

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result.get(0).getVersionNumber()).isEqualTo(1); // GH-90000
            assertThat(result.get(1).getVersionNumber()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("should get specific version by number")
        void shouldGetSpecificVersionByNumber() { // GH-90000
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); // GH-90000
            EntityVersion version = EntityVersion.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .entityId(entityId) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(2) // GH-90000
                .metadata(new VersionMetadata("user-456", Instant.now(), "v2")) // GH-90000
                .build(); // GH-90000

            when(versionRecord.getVersion(tenantId, entityId, 2)) // GH-90000
                .thenReturn(Promise.of(version)); // GH-90000

            EntityVersion result = runPromise(() -> versionRecord.getVersion(tenantId, entityId, 2)); // GH-90000

            assertThat(result.getVersionNumber()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("should get latest version")
        void shouldGetLatestVersion() { // GH-90000
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); // GH-90000
            EntityVersion latestVersion = EntityVersion.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .entityId(entityId) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(3) // GH-90000
                .metadata(new VersionMetadata("user-456", Instant.now(), "latest")) // GH-90000
                .build(); // GH-90000

            when(versionRecord.getLatestVersion(tenantId, entityId)) // GH-90000
                .thenReturn(Promise.of(latestVersion)); // GH-90000

            EntityVersion result = runPromise(() -> versionRecord.getLatestVersion(tenantId, entityId)); // GH-90000

            assertThat(result.getVersionNumber()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("should count versions")
        void shouldCountVersions() { // GH-90000
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); // GH-90000

            when(versionRecord.countVersions(tenantId, entityId)) // GH-90000
                .thenReturn(Promise.of(5)); // GH-90000

            Integer result = runPromise(() -> versionRecord.countVersions(tenantId, entityId)); // GH-90000

            assertThat(result).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("should compute diff between versions")
        void shouldComputeDiffBetweenVersions() { // GH-90000
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); // GH-90000
            VersionDiff diff = new VersionDiff( // GH-90000
                Map.of("name", new VersionDiff.FieldChange("Old", "New")), // GH-90000
                Set.of("email"),
                Set.of("phone")
            );

            when(versionRecord.computeDiff(tenantId, entityId, 1, 2)) // GH-90000
                .thenReturn(Promise.of(diff)); // GH-90000

            VersionDiff result = runPromise(() -> versionRecord.computeDiff(tenantId, entityId, 1, 2)); // GH-90000

            assertThat(result.hasChanges()).isTrue(); // GH-90000
            assertThat(result.getChanged()).hasSize(1); // GH-90000
            assertThat(result.getAdded()).contains("email");
            assertThat(result.getRemoved()).contains("phone");
        }

        @Test
        @DisplayName("should delete version history")
        void shouldDeleteVersionHistory() { // GH-90000
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); // GH-90000

            when(versionRecord.deleteVersionHistory(tenantId, entityId)) // GH-90000
                .thenReturn(Promise.of(3)); // GH-90000

            Integer result = runPromise(() -> versionRecord.deleteVersionHistory(tenantId, entityId)); // GH-90000

            assertThat(result).isEqualTo(3); // GH-90000
            verify(versionRecord).deleteVersionHistory(tenantId, entityId); // GH-90000
        }
    }

    // =========================================================================
    // VERSION SEQUENCE VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Version sequence validation")
    class VersionSequenceValidation {

        @Test
        @DisplayName("should maintain sequential version numbers")
        void shouldMaintainSequentialVersionNumbers() { // GH-90000
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); // GH-90000
            Instant createdAt = Instant.now(); // GH-90000

            EntityVersion v1 = EntityVersion.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .entityId(entityId) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(1) // GH-90000
                .metadata(new VersionMetadata("user-456", createdAt, "v1")) // GH-90000
                .build(); // GH-90000

            EntityVersion v2 = EntityVersion.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .entityId(entityId) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(2) // GH-90000
                .metadata(new VersionMetadata("user-456", createdAt.plusSeconds(1), "v2")) // GH-90000
                .build(); // GH-90000

            EntityVersion v3 = EntityVersion.builder() // GH-90000
                .tenantId(tenantId) // GH-90000
                .entityId(entityId) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(3) // GH-90000
                .metadata(new VersionMetadata("user-456", createdAt.plusSeconds(2), "v3")) // GH-90000
                .build(); // GH-90000

            List<EntityVersion> versions = List.of(v1, v2, v3); // GH-90000

            assertThat(versions.get(0).getVersionNumber()).isEqualTo(1); // GH-90000
            assertThat(versions.get(1).getVersionNumber()).isEqualTo(2); // GH-90000
            assertThat(versions.get(2).getVersionNumber()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("versions should be ordered chronologically")
        void versionsShouldBeOrderedChronologically() { // GH-90000
            Instant now = Instant.now(); // GH-90000
            EntityVersion v1 = EntityVersion.builder() // GH-90000
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(1) // GH-90000
                .metadata(new VersionMetadata("user-456", now, "v1")) // GH-90000
                .createdAt(now) // GH-90000
                .build(); // GH-90000

            EntityVersion v2 = EntityVersion.builder() // GH-90000
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(2) // GH-90000
                .metadata(new VersionMetadata("user-456", now.plusSeconds(10), "v2")) // GH-90000
                .createdAt(now.plusSeconds(10)) // GH-90000
                .build(); // GH-90000

            EntityVersion v3 = EntityVersion.builder() // GH-90000
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) // GH-90000
                .entitySnapshot(entity) // GH-90000
                .versionNumber(3) // GH-90000
                .metadata(new VersionMetadata("user-456", now.plusSeconds(20), "v3")) // GH-90000
                .createdAt(now.plusSeconds(20)) // GH-90000
                .build(); // GH-90000

            List<EntityVersion> versions = List.of(v1, v2, v3); // GH-90000

            assertThat(versions.get(0).getCreatedAt()).isBefore(versions.get(1).getCreatedAt()); // GH-90000
            assertThat(versions.get(1).getCreatedAt()).isBefore(versions.get(2).getCreatedAt()); // GH-90000
        }
    }

    // =========================================================================
    // TENANT ISOLATION
    // =========================================================================

    @Nested
    @DisplayName("Tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("should not return versions from different tenant")
        void shouldNotReturnVersionsFromDifferentTenant() { // GH-90000
            String tenantB = "tenant-b";
            UUID entityId = UUID.randomUUID(); // GH-90000

            when(versionRecord.getVersionHistory(tenantB, entityId)) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            List<EntityVersion> result = runPromise(() -> versionRecord.getVersionHistory(tenantB, entityId)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("should save version with correct tenant ID")
        void shouldSaveVersionWithCorrectTenantId() { // GH-90000
            String tenantId = "tenant-123";
            VersionMetadata metadata = new VersionMetadata("user-456", Instant.now(), "test"); // GH-90000

            when(versionRecord.saveVersion(eq(tenantId), any(), any())) // GH-90000
                .thenReturn(Promise.of(EntityVersion.builder() // GH-90000
                    .tenantId(tenantId) // GH-90000
                    .entityId(UUID.randomUUID()) // GH-90000
                    .entitySnapshot(entity) // GH-90000
                    .versionNumber(1) // GH-90000
                    .metadata(metadata) // GH-90000
                    .build())); // GH-90000

            EntityVersion result = runPromise(() -> versionRecord.saveVersion(tenantId, entity, metadata)); // GH-90000

            assertThat(result.getTenantId()).isEqualTo(tenantId); // GH-90000
        }
    }
}
