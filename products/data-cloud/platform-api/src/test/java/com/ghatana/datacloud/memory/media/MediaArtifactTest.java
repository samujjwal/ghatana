/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link MediaArtifactRecord} and {@link DataCloudMediaArtifactRepository}.
 */
@DisplayName("MediaArtifact (P7-T4)")
class MediaArtifactTest {

    // =========================================================================
    // MediaArtifactRecord
    // =========================================================================

    @Nested
    @DisplayName("MediaArtifactRecord")
    class RecordTests {

        @Test
        @DisplayName("create() generates non-null artifactId and current timestamp")
        void createGeneratesIds() { // GH-90000
            MediaArtifactRecord record = MediaArtifactRecord.create( // GH-90000
                    "tenant-1", "agent-1", "audio/wav",
                    "gs://bucket/file.wav", 1024L, "sha256abc",
                    3000L, "av.speech-to-text", "corr-1", Map.of()); // GH-90000
            assertThat(record.artifactId()).isNotBlank(); // GH-90000
            assertThat(record.createdAt()).isNotNull(); // GH-90000
            assertThat(record.tenantId()).isEqualTo("tenant-1");
            assertThat(record.mediaType()).isEqualTo("audio/wav");
        }

        @Test
        @DisplayName("metadata is immutable after creation")
        void metadataIsImmutable() { // GH-90000
            MediaArtifactRecord record = MediaArtifactRecord.create( // GH-90000
                    "tenant-1", "agent-1", "image/jpeg",
                    "s3://bucket/img.jpg", 512L, null,
                    0, null, null, Map.of("key", "val")); // GH-90000
            assertThatThrownBy(() -> record.metadata().put("extra", "val")) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("null metadata defaults to empty map")
        void nullMetadataDefaultsToEmpty() { // GH-90000
            MediaArtifactRecord record = MediaArtifactRecord.create( // GH-90000
                    "t1", "a1", "video/mp4", "uri", 100L, null, 0, null, null, null);
            assertThat(record.metadata()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("negative sizeBytes throws")
        void negativeSizeBytesThrows() { // GH-90000
            assertThatThrownBy(() -> MediaArtifactRecord.create( // GH-90000
                    "t1", "a1", "audio/wav", "uri", -1L, null, 0, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("sizeBytes");
        }

        @Test
        @DisplayName("blank mediaType throws")
        void blankMediaTypeThrows() { // GH-90000
            assertThatThrownBy(() -> MediaArtifactRecord.create( // GH-90000
                    "t1", "a1", "  ", "uri", 100L, null, 0, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("mediaType");
        }
    }

    // =========================================================================
    // DataCloudMediaArtifactRepository
    // =========================================================================

    @Nested
    @DisplayName("DataCloudMediaArtifactRepository")
    class RepositoryTests {

        private DataCloudMediaArtifactRepository repo;

        @BeforeEach
        void setUp() { // GH-90000
            repo = new DataCloudMediaArtifactRepository(); // GH-90000
        }

        private MediaArtifactRecord buildRecord(String tenantId, String agentId, String mediaType) { // GH-90000
            return MediaArtifactRecord.create( // GH-90000
                    tenantId, agentId, mediaType,
                    "gs://bucket/test.wav", 100L, null, 0, "av.stt", "corr-1", Map.of()); // GH-90000
        }

        @Test
        @DisplayName("save and findById returns saved record")
        void saveAndFindById() { // GH-90000
            MediaArtifactRecord record = buildRecord("t1", "agent-1", "audio/wav"); // GH-90000
            MediaArtifactRecord saved = repo.save(record).getResult(); // GH-90000
            assertThat(saved).isEqualTo(record); // GH-90000
            Optional<MediaArtifactRecord> found = repo.findById(record.artifactId(), "t1").getResult(); // GH-90000
            assertThat(found).contains(record); // GH-90000
        }

        @Test
        @DisplayName("findById returns empty when not found")
        void findByIdReturnsEmpty() { // GH-90000
            Optional<MediaArtifactRecord> result = repo.findById("nonexistent", "t1").getResult(); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("tenant isolation: different tenants cannot see each other's artifacts")
        void tenantIsolation() { // GH-90000
            MediaArtifactRecord t1 = buildRecord("tenant-A", "agent-1", "audio/wav"); // GH-90000
            MediaArtifactRecord t2 = buildRecord("tenant-B", "agent-1", "audio/wav"); // GH-90000
            repo.save(t1).getResult(); // GH-90000
            repo.save(t2).getResult(); // GH-90000

            assertThat(repo.findById(t1.artifactId(), "tenant-B").getResult()).isEmpty(); // GH-90000
            assertThat(repo.findById(t2.artifactId(), "tenant-A").getResult()).isEmpty(); // GH-90000
            assertThat(repo.findById(t1.artifactId(), "tenant-A").getResult()).contains(t1); // GH-90000
        }

        @Test
        @DisplayName("findByAgent returns matching records within limit")
        void findByAgent() { // GH-90000
            repo.save(buildRecord("t1", "agent-x", "audio/wav")).getResult(); // GH-90000
            repo.save(buildRecord("t1", "agent-x", "video/mp4")).getResult(); // GH-90000
            repo.save(buildRecord("t1", "agent-y", "image/jpeg")).getResult(); // GH-90000

            List<MediaArtifactRecord> results = repo.findByAgent("agent-x", "t1", 10).getResult(); // GH-90000
            assertThat(results).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("findByMediaType filters by MIME type")
        void findByMediaType() { // GH-90000
            repo.save(buildRecord("t1", "a1", "audio/wav")).getResult(); // GH-90000
            repo.save(buildRecord("t1", "a1", "audio/wav")).getResult(); // GH-90000
            repo.save(buildRecord("t1", "a1", "video/mp4")).getResult(); // GH-90000

            List<MediaArtifactRecord> results = repo.findByMediaType("audio/wav", "t1", 10).getResult(); // GH-90000
            assertThat(results).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("delete returns true and removes record")
        void deleteRemovesRecord() { // GH-90000
            MediaArtifactRecord record = buildRecord("t1", "a1", "audio/wav"); // GH-90000
            repo.save(record).getResult(); // GH-90000
            Boolean deleted = repo.delete(record.artifactId(), "t1").getResult(); // GH-90000
            assertThat(deleted).isTrue(); // GH-90000
            assertThat(repo.findById(record.artifactId(), "t1").getResult()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("delete returns false when record not found")
        void deleteReturnsFalseWhenMissing() { // GH-90000
            Boolean deleted = repo.delete("nonexistent", "t1").getResult(); // GH-90000
            assertThat(deleted).isFalse(); // GH-90000
        }
    }
}
