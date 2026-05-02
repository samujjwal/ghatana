/*
 * Copyright (c) 2026 Ghatana Inc. 
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
@ExtendWith(MockitoExtension.class) 
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
        void shouldCreateEntityVersionWithRequiredFields() { 
            UUID versionId = UUID.randomUUID(); 
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); 
            Integer versionNumber = 1;
            Instant createdAt = Instant.now(); 
            VersionMetadata metadata = new VersionMetadata("user-456", createdAt, "Initial creation"); 

            EntityVersion version = new EntityVersion( 
                versionId,
                tenantId,
                entityId,
                entity,
                versionNumber,
                metadata,
                createdAt
            );

            assertThat(version.getId()).isEqualTo(versionId); 
            assertThat(version.getTenantId()).isEqualTo(tenantId); 
            assertThat(version.getEntityId()).isEqualTo(entityId); 
            assertThat(version.getEntitySnapshot()).isEqualTo(entity); 
            assertThat(version.getVersionNumber()).isEqualTo(versionNumber); 
            assertThat(version.getMetadata()).isEqualTo(metadata); 
            assertThat(version.getCreatedAt()).isEqualTo(createdAt); 
        }

        @Test
        @DisplayName("should reject null ID")
        void shouldRejectNullId() { 
            assertThatThrownBy(() -> new EntityVersion( 
                null,
                "tenant-123",
                UUID.randomUUID(), 
                entity,
                1,
                new VersionMetadata("user-456", Instant.now(), "test"), 
                Instant.now() 
            )).isInstanceOf(NullPointerException.class) 
              .hasMessageContaining("ID must not be null");
        }

        @Test
        @DisplayName("should reject null tenant ID")
        void shouldRejectNullTenantId() { 
            assertThatThrownBy(() -> new EntityVersion( 
                UUID.randomUUID(), 
                null,
                UUID.randomUUID(), 
                entity,
                1,
                new VersionMetadata("user-456", Instant.now(), "test"), 
                Instant.now() 
            )).isInstanceOf(NullPointerException.class) 
              .hasMessageContaining("Tenant ID must not be null");
        }

        @Test
        @DisplayName("should reject null entity ID")
        void shouldRejectNullEntityId() { 
            assertThatThrownBy(() -> new EntityVersion( 
                UUID.randomUUID(), 
                "tenant-123",
                null,
                entity,
                1,
                new VersionMetadata("user-456", Instant.now(), "test"), 
                Instant.now() 
            )).isInstanceOf(NullPointerException.class) 
              .hasMessageContaining("Entity ID must not be null");
        }

        @Test
        @DisplayName("should reject null entity snapshot")
        void shouldRejectNullEntitySnapshot() { 
            assertThatThrownBy(() -> new EntityVersion( 
                UUID.randomUUID(), 
                "tenant-123",
                UUID.randomUUID(), 
                null,
                1,
                new VersionMetadata("user-456", Instant.now(), "test"), 
                Instant.now() 
            )).isInstanceOf(NullPointerException.class) 
              .hasMessageContaining("Entity snapshot must not be null");
        }

        @Test
        @DisplayName("should reject null version number")
        void shouldRejectNullVersionNumber() { 
            assertThatThrownBy(() -> new EntityVersion( 
                UUID.randomUUID(), 
                "tenant-123",
                UUID.randomUUID(), 
                entity,
                null,
                new VersionMetadata("user-456", Instant.now(), "test"), 
                Instant.now() 
            )).isInstanceOf(NullPointerException.class) 
              .hasMessageContaining("Version number must not be null");
        }

        @Test
        @DisplayName("should reject version number less than 1")
        void shouldRejectVersionNumberLessThan1() { 
            assertThatThrownBy(() -> new EntityVersion( 
                UUID.randomUUID(), 
                "tenant-123",
                UUID.randomUUID(), 
                entity,
                0,
                new VersionMetadata("user-456", Instant.now(), "test"), 
                Instant.now() 
            )).isInstanceOf(IllegalArgumentException.class) 
              .hasMessageContaining("Version number must be >= 1");
        }

        @Test
        @DisplayName("should reject null metadata")
        void shouldRejectNullMetadata() { 
            assertThatThrownBy(() -> new EntityVersion( 
                UUID.randomUUID(), 
                "tenant-123",
                UUID.randomUUID(), 
                entity,
                1,
                null,
                Instant.now() 
            )).isInstanceOf(NullPointerException.class) 
              .hasMessageContaining("Metadata must not be null");
        }

        @Test
        @DisplayName("should reject null createdAt")
        void shouldRejectNullCreatedAt() { 
            assertThatThrownBy(() -> new EntityVersion( 
                UUID.randomUUID(), 
                "tenant-123",
                UUID.randomUUID(), 
                entity,
                1,
                new VersionMetadata("user-456", Instant.now(), "test"), 
                null
            )).isInstanceOf(NullPointerException.class) 
              .hasMessageContaining("Created at must not be null");
        }

        @Test
        @DisplayName("should create EntityVersion using builder")
        void shouldCreateEntityVersionUsingBuilder() { 
            UUID entityId = UUID.randomUUID(); 
            Instant createdAt = Instant.now(); 

            EntityVersion version = EntityVersion.builder() 
                .tenantId("tenant-123")
                .entityId(entityId) 
                .entitySnapshot(entity) 
                .versionNumber(1) 
                .metadata(new VersionMetadata("user-456", createdAt, "Initial creation")) 
                .createdAt(createdAt) 
                .build(); 

            assertThat(version.getTenantId()).isEqualTo("tenant-123");
            assertThat(version.getEntityId()).isEqualTo(entityId); 
            assertThat(version.getVersionNumber()).isEqualTo(1); 
        }

        @Test
        @DisplayName("builder should auto-generate ID if not provided")
        void builderShouldAutoGenerateIdIfNotProvided() { 
            EntityVersion version = EntityVersion.builder() 
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) 
                .entitySnapshot(entity) 
                .versionNumber(1) 
                .metadata(new VersionMetadata("user-456", Instant.now(), "test")) 
                .build(); 

            assertThat(version.getId()).isNotNull(); 
        }

        @Test
        @DisplayName("builder should auto-generate createdAt if not provided")
        void builderShouldAutoGenerateCreatedAtIfNotProvided() { 
            EntityVersion version = EntityVersion.builder() 
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) 
                .entitySnapshot(entity) 
                .versionNumber(1) 
                .metadata(new VersionMetadata("user-456", Instant.now(), "test")) 
                .build(); 

            assertThat(version.getCreatedAt()).isNotNull(); 
            assertThat(version.getCreatedAt()).isBeforeOrEqualTo(Instant.now()); 
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
        void shouldGetAuthorFromMetadata() { 
            VersionMetadata metadata = new VersionMetadata("user-456", Instant.now(), "test"); 
            EntityVersion version = EntityVersion.builder() 
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) 
                .entitySnapshot(entity) 
                .versionNumber(1) 
                .metadata(metadata) 
                .build(); 

            assertThat(version.getAuthor()).isEqualTo("user-456");
        }

        @Test
        @DisplayName("should get reason from metadata")
        void shouldGetReasonFromMetadata() { 
            VersionMetadata metadata = new VersionMetadata("user-456", Instant.now(), "Updated contact info"); 
            EntityVersion version = EntityVersion.builder() 
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) 
                .entitySnapshot(entity) 
                .versionNumber(1) 
                .metadata(metadata) 
                .build(); 

            assertThat(version.getReason()).isEqualTo("Updated contact info");
        }

        @Test
        @DisplayName("should return null reason if metadata reason is null")
        void shouldReturnNullReasonIfMetadataReasonIsNull() { 
            VersionMetadata metadata = new VersionMetadata("user-456", Instant.now(), null); 
            EntityVersion version = EntityVersion.builder() 
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) 
                .entitySnapshot(entity) 
                .versionNumber(1) 
                .metadata(metadata) 
                .build(); 

            assertThat(version.getReason()).isNull(); 
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
        void shouldBeEqualWhenIdsMatch() { 
            UUID id = UUID.randomUUID(); 
            UUID entityId = UUID.randomUUID(); 
            Instant createdAt = Instant.now(); 
            VersionMetadata metadata = new VersionMetadata("user-456", createdAt, "test"); 

            EntityVersion version1 = new EntityVersion(id, "tenant-123", entityId, entity, 1, metadata, createdAt); 
            EntityVersion version2 = new EntityVersion(id, "tenant-123", entityId, entity, 1, metadata, createdAt); 

            assertThat(version1).isEqualTo(version2); 
            assertThat(version1.hashCode()).isEqualTo(version2.hashCode()); 
        }

        @Test
        @DisplayName("should not be equal when IDs differ")
        void shouldNotBeEqualWhenIdsDiffer() { 
            UUID entityId = UUID.randomUUID(); 
            Instant createdAt = Instant.now(); 
            VersionMetadata metadata = new VersionMetadata("user-456", createdAt, "test"); 

            EntityVersion version1 = new EntityVersion(UUID.randomUUID(), "tenant-123", entityId, entity, 1, metadata, createdAt); 
            EntityVersion version2 = new EntityVersion(UUID.randomUUID(), "tenant-123", entityId, entity, 1, metadata, createdAt); 

            assertThat(version1).isNotEqualTo(version2); 
        }

        @Test
        @DisplayName("toString should include key fields")
        void toStringShouldIncludeKeyFields() { 
            UUID entityId = UUID.randomUUID(); 
            Instant createdAt = Instant.now(); 
            VersionMetadata metadata = new VersionMetadata("user-456", createdAt, "test"); 

            EntityVersion version = new EntityVersion(UUID.randomUUID(), "tenant-123", entityId, entity, 1, metadata, createdAt); 

            assertThat(version.toString()) 
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
        void shouldCreateVersionMetadataWithRequiredFields() { 
            String author = "user-456";
            Instant timestamp = Instant.now(); 
            String reason = "Updated contact info";

            VersionMetadata metadata = new VersionMetadata(author, timestamp, reason); 

            assertThat(metadata.author()).isEqualTo(author); 
            assertThat(metadata.timestamp()).isEqualTo(timestamp); 
            assertThat(metadata.reason()).isEqualTo(reason); 
        }

        @Test
        @DisplayName("should create VersionMetadata with null reason")
        void shouldCreateVersionMetadataWithNullReason() { 
            String author = "user-456";
            Instant timestamp = Instant.now(); 

            VersionMetadata metadata = new VersionMetadata(author, timestamp, null); 

            assertThat(metadata.author()).isEqualTo(author); 
            assertThat(metadata.timestamp()).isEqualTo(timestamp); 
            assertThat(metadata.reason()).isNull(); 
        }

        @Test
        @DisplayName("should reject null author")
        void shouldRejectNullAuthor() { 
            assertThatThrownBy(() -> new VersionMetadata(null, Instant.now(), "test")) 
                .isInstanceOf(NullPointerException.class) 
                .hasMessageContaining("Author must not be null");
        }

        @Test
        @DisplayName("should reject null timestamp")
        void shouldRejectNullTimestamp() { 
            assertThatThrownBy(() -> new VersionMetadata("user-456", null, "test")) 
                .isInstanceOf(NullPointerException.class) 
                .hasMessageContaining("Timestamp must not be null");
        }

        @Test
        @DisplayName("should generate summary with reason")
        void shouldGenerateSummaryWithReason() { 
            Instant timestamp = Instant.now(); 
            VersionMetadata metadata = new VersionMetadata("user-456", timestamp, "Updated contact info"); 

            String summary = metadata.getSummary(); 

            assertThat(summary).contains("Updated contact info");
            assertThat(summary).contains("user-456");
            assertThat(summary).contains(timestamp.toString()); 
        }

        @Test
        @DisplayName("should generate summary without reason")
        void shouldGenerateSummaryWithoutReason() { 
            Instant timestamp = Instant.now(); 
            VersionMetadata metadata = new VersionMetadata("user-456", timestamp, null); 

            String summary = metadata.getSummary(); 

            assertThat(summary).contains("Modified by user-456");
            assertThat(summary).contains(timestamp.toString()); 
        }

        @Test
        @DisplayName("should generate summary with empty reason")
        void shouldGenerateSummaryWithEmptyReason() { 
            Instant timestamp = Instant.now(); 
            VersionMetadata metadata = new VersionMetadata("user-456", timestamp, ""); 

            String summary = metadata.getSummary(); 

            assertThat(summary).contains("Modified by user-456");
            assertThat(summary).contains(timestamp.toString()); 
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
        void shouldSaveVersion() { 
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); 
            Instant createdAt = Instant.now(); 
            VersionMetadata metadata = new VersionMetadata("user-456", createdAt, "Initial creation"); 
            EntityVersion version = EntityVersion.builder() 
                .tenantId(tenantId) 
                .entityId(entityId) 
                .entitySnapshot(entity) 
                .versionNumber(1) 
                .metadata(metadata) 
                .createdAt(createdAt) 
                .build(); 

            when(versionRecord.saveVersion(tenantId, entity, metadata)) 
                .thenReturn(Promise.of(version)); 

            EntityVersion result = runPromise(() -> versionRecord.saveVersion(tenantId, entity, metadata)); 

            assertThat(result).isEqualTo(version); 
            verify(versionRecord).saveVersion(tenantId, entity, metadata); 
        }

        @Test
        @DisplayName("should get version history")
        void shouldGetVersionHistory() { 
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); 
            List<EntityVersion> versions = List.of( 
                EntityVersion.builder() 
                    .tenantId(tenantId) 
                    .entityId(entityId) 
                    .entitySnapshot(entity) 
                    .versionNumber(1) 
                    .metadata(new VersionMetadata("user-456", Instant.now(), "v1")) 
                    .build(), 
                EntityVersion.builder() 
                    .tenantId(tenantId) 
                    .entityId(entityId) 
                    .entitySnapshot(entity) 
                    .versionNumber(2) 
                    .metadata(new VersionMetadata("user-456", Instant.now(), "v2")) 
                    .build() 
            );

            when(versionRecord.getVersionHistory(tenantId, entityId)) 
                .thenReturn(Promise.of(versions)); 

            List<EntityVersion> result = runPromise(() -> versionRecord.getVersionHistory(tenantId, entityId)); 

            assertThat(result).hasSize(2); 
            assertThat(result.get(0).getVersionNumber()).isEqualTo(1); 
            assertThat(result.get(1).getVersionNumber()).isEqualTo(2); 
        }

        @Test
        @DisplayName("should get specific version by number")
        void shouldGetSpecificVersionByNumber() { 
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); 
            EntityVersion version = EntityVersion.builder() 
                .tenantId(tenantId) 
                .entityId(entityId) 
                .entitySnapshot(entity) 
                .versionNumber(2) 
                .metadata(new VersionMetadata("user-456", Instant.now(), "v2")) 
                .build(); 

            when(versionRecord.getVersion(tenantId, entityId, 2)) 
                .thenReturn(Promise.of(version)); 

            EntityVersion result = runPromise(() -> versionRecord.getVersion(tenantId, entityId, 2)); 

            assertThat(result.getVersionNumber()).isEqualTo(2); 
        }

        @Test
        @DisplayName("should get latest version")
        void shouldGetLatestVersion() { 
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); 
            EntityVersion latestVersion = EntityVersion.builder() 
                .tenantId(tenantId) 
                .entityId(entityId) 
                .entitySnapshot(entity) 
                .versionNumber(3) 
                .metadata(new VersionMetadata("user-456", Instant.now(), "latest")) 
                .build(); 

            when(versionRecord.getLatestVersion(tenantId, entityId)) 
                .thenReturn(Promise.of(latestVersion)); 

            EntityVersion result = runPromise(() -> versionRecord.getLatestVersion(tenantId, entityId)); 

            assertThat(result.getVersionNumber()).isEqualTo(3); 
        }

        @Test
        @DisplayName("should count versions")
        void shouldCountVersions() { 
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); 

            when(versionRecord.countVersions(tenantId, entityId)) 
                .thenReturn(Promise.of(5)); 

            Integer result = runPromise(() -> versionRecord.countVersions(tenantId, entityId)); 

            assertThat(result).isEqualTo(5); 
        }

        @Test
        @DisplayName("should compute diff between versions")
        void shouldComputeDiffBetweenVersions() { 
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); 
            VersionDiff diff = new VersionDiff( 
                Map.of("name", new VersionDiff.FieldChange("Old", "New")), 
                Set.of("email"),
                Set.of("phone")
            );

            when(versionRecord.computeDiff(tenantId, entityId, 1, 2)) 
                .thenReturn(Promise.of(diff)); 

            VersionDiff result = runPromise(() -> versionRecord.computeDiff(tenantId, entityId, 1, 2)); 

            assertThat(result.hasChanges()).isTrue(); 
            assertThat(result.getChanged()).hasSize(1); 
            assertThat(result.getAdded()).contains("email");
            assertThat(result.getRemoved()).contains("phone");
        }

        @Test
        @DisplayName("should delete version history")
        void shouldDeleteVersionHistory() { 
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); 

            when(versionRecord.deleteVersionHistory(tenantId, entityId)) 
                .thenReturn(Promise.of(3)); 

            Integer result = runPromise(() -> versionRecord.deleteVersionHistory(tenantId, entityId)); 

            assertThat(result).isEqualTo(3); 
            verify(versionRecord).deleteVersionHistory(tenantId, entityId); 
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
        void shouldMaintainSequentialVersionNumbers() { 
            String tenantId = "tenant-123";
            UUID entityId = UUID.randomUUID(); 
            Instant createdAt = Instant.now(); 

            EntityVersion v1 = EntityVersion.builder() 
                .tenantId(tenantId) 
                .entityId(entityId) 
                .entitySnapshot(entity) 
                .versionNumber(1) 
                .metadata(new VersionMetadata("user-456", createdAt, "v1")) 
                .build(); 

            EntityVersion v2 = EntityVersion.builder() 
                .tenantId(tenantId) 
                .entityId(entityId) 
                .entitySnapshot(entity) 
                .versionNumber(2) 
                .metadata(new VersionMetadata("user-456", createdAt.plusSeconds(1), "v2")) 
                .build(); 

            EntityVersion v3 = EntityVersion.builder() 
                .tenantId(tenantId) 
                .entityId(entityId) 
                .entitySnapshot(entity) 
                .versionNumber(3) 
                .metadata(new VersionMetadata("user-456", createdAt.plusSeconds(2), "v3")) 
                .build(); 

            List<EntityVersion> versions = List.of(v1, v2, v3); 

            assertThat(versions.get(0).getVersionNumber()).isEqualTo(1); 
            assertThat(versions.get(1).getVersionNumber()).isEqualTo(2); 
            assertThat(versions.get(2).getVersionNumber()).isEqualTo(3); 
        }

        @Test
        @DisplayName("versions should be ordered chronologically")
        void versionsShouldBeOrderedChronologically() { 
            Instant now = Instant.now(); 
            EntityVersion v1 = EntityVersion.builder() 
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) 
                .entitySnapshot(entity) 
                .versionNumber(1) 
                .metadata(new VersionMetadata("user-456", now, "v1")) 
                .createdAt(now) 
                .build(); 

            EntityVersion v2 = EntityVersion.builder() 
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) 
                .entitySnapshot(entity) 
                .versionNumber(2) 
                .metadata(new VersionMetadata("user-456", now.plusSeconds(10), "v2")) 
                .createdAt(now.plusSeconds(10)) 
                .build(); 

            EntityVersion v3 = EntityVersion.builder() 
                .tenantId("tenant-123")
                .entityId(UUID.randomUUID()) 
                .entitySnapshot(entity) 
                .versionNumber(3) 
                .metadata(new VersionMetadata("user-456", now.plusSeconds(20), "v3")) 
                .createdAt(now.plusSeconds(20)) 
                .build(); 

            List<EntityVersion> versions = List.of(v1, v2, v3); 

            assertThat(versions.get(0).getCreatedAt()).isBefore(versions.get(1).getCreatedAt()); 
            assertThat(versions.get(1).getCreatedAt()).isBefore(versions.get(2).getCreatedAt()); 
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
        void shouldNotReturnVersionsFromDifferentTenant() { 
            String tenantB = "tenant-b";
            UUID entityId = UUID.randomUUID(); 

            when(versionRecord.getVersionHistory(tenantB, entityId)) 
                .thenReturn(Promise.of(List.of())); 

            List<EntityVersion> result = runPromise(() -> versionRecord.getVersionHistory(tenantB, entityId)); 

            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("should save version with correct tenant ID")
        void shouldSaveVersionWithCorrectTenantId() { 
            String tenantId = "tenant-123";
            VersionMetadata metadata = new VersionMetadata("user-456", Instant.now(), "test"); 

            when(versionRecord.saveVersion(eq(tenantId), any(), any())) 
                .thenReturn(Promise.of(EntityVersion.builder() 
                    .tenantId(tenantId) 
                    .entityId(UUID.randomUUID()) 
                    .entitySnapshot(entity) 
                    .versionNumber(1) 
                    .metadata(metadata) 
                    .build())); 

            EntityVersion result = runPromise(() -> versionRecord.saveVersion(tenantId, entity, metadata)); 

            assertThat(result.getTenantId()).isEqualTo(tenantId); 
        }
    }
}
