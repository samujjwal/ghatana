/*
 * Copyright (c) 2026 Ghatana Inc. 
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
        void createGeneratesIds() { 
            MediaArtifactRecord record = MediaArtifactRecord.create( 
                    "tenant-1", "agent-1", "audio/wav",
                    "gs://bucket/file.wav", 1024L, "sha256abc",
                    3000L, "av.speech-to-text", "corr-1", Map.of()); 
            assertThat(record.artifactId()).isNotBlank(); 
            assertThat(record.createdAt()).isNotNull(); 
            assertThat(record.tenantId()).isEqualTo("tenant-1");
            assertThat(record.mediaType()).isEqualTo("audio/wav");
        }

        @Test
        @DisplayName("metadata is immutable after creation")
        void metadataIsImmutable() { 
            MediaArtifactRecord record = MediaArtifactRecord.create( 
                    "tenant-1", "agent-1", "image/jpeg",
                    "s3://bucket/img.jpg", 512L, null,
                    0, null, null, Map.of("key", "val")); 
            assertThatThrownBy(() -> record.metadata().put("extra", "val")) 
                    .isInstanceOf(UnsupportedOperationException.class); 
        }

        @Test
        @DisplayName("null metadata defaults to empty map")
        void nullMetadataDefaultsToEmpty() { 
            MediaArtifactRecord record = MediaArtifactRecord.create( 
                    "t1", "a1", "video/mp4", "uri", 100L, null, 0, null, null, null);
            assertThat(record.metadata()).isEmpty(); 
        }

        @Test
        @DisplayName("negative sizeBytes throws")
        void negativeSizeBytesThrows() { 
            assertThatThrownBy(() -> MediaArtifactRecord.create( 
                    "t1", "a1", "audio/wav", "uri", -1L, null, 0, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("sizeBytes");
        }

        @Test
        @DisplayName("blank mediaType throws")
        void blankMediaTypeThrows() { 
            assertThatThrownBy(() -> MediaArtifactRecord.create( 
                    "t1", "a1", "  ", "uri", 100L, null, 0, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class) 
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
        void setUp() { 
            repo = new DataCloudMediaArtifactRepository(); 
        }

        private MediaArtifactRecord buildRecord(String tenantId, String agentId, String mediaType) { 
            return MediaArtifactRecord.create( 
                    tenantId, agentId, mediaType,
                    "gs://bucket/test.wav", 100L, null, 0, "av.stt", "corr-1", Map.of()); 
        }

        @Test
        @DisplayName("save and findById returns saved record")
        void saveAndFindById() { 
            MediaArtifactRecord record = buildRecord("t1", "agent-1", "audio/wav"); 
            MediaArtifactRecord saved = repo.save(record).getResult(); 
            assertThat(saved).isEqualTo(record); 
            Optional<MediaArtifactRecord> found = repo.findById(record.artifactId(), "t1").getResult(); 
            assertThat(found).contains(record); 
        }

        @Test
        @DisplayName("findById returns empty when not found")
        void findByIdReturnsEmpty() { 
            Optional<MediaArtifactRecord> result = repo.findById("nonexistent", "t1").getResult(); 
            assertThat(result).isEmpty(); 
        }

        @Test
        @DisplayName("tenant isolation: different tenants cannot see each other's artifacts")
        void tenantIsolation() { 
            MediaArtifactRecord t1 = buildRecord("tenant-A", "agent-1", "audio/wav"); 
            MediaArtifactRecord t2 = buildRecord("tenant-B", "agent-1", "audio/wav"); 
            repo.save(t1).getResult(); 
            repo.save(t2).getResult(); 

            assertThat(repo.findById(t1.artifactId(), "tenant-B").getResult()).isEmpty(); 
            assertThat(repo.findById(t2.artifactId(), "tenant-A").getResult()).isEmpty(); 
            assertThat(repo.findById(t1.artifactId(), "tenant-A").getResult()).contains(t1); 
        }

        @Test
        @DisplayName("findByAgent returns matching records within limit")
        void findByAgent() { 
            repo.save(buildRecord("t1", "agent-x", "audio/wav")).getResult(); 
            repo.save(buildRecord("t1", "agent-x", "video/mp4")).getResult(); 
            repo.save(buildRecord("t1", "agent-y", "image/jpeg")).getResult(); 

            List<MediaArtifactRecord> results = repo.findByAgent("agent-x", "t1", 10).getResult(); 
            assertThat(results).hasSize(2); 
        }

        @Test
        @DisplayName("findByMediaType filters by MIME type")
        void findByMediaType() { 
            repo.save(buildRecord("t1", "a1", "audio/wav")).getResult(); 
            repo.save(buildRecord("t1", "a1", "audio/wav")).getResult(); 
            repo.save(buildRecord("t1", "a1", "video/mp4")).getResult(); 

            List<MediaArtifactRecord> results = repo.findByMediaType("audio/wav", "t1", 10).getResult(); 
            assertThat(results).hasSize(2); 
        }

        @Test
        @DisplayName("delete returns true and removes record")
        void deleteRemovesRecord() { 
            MediaArtifactRecord record = buildRecord("t1", "a1", "audio/wav"); 
            repo.save(record).getResult(); 
            Boolean deleted = repo.delete(record.artifactId(), "t1").getResult(); 
            assertThat(deleted).isTrue(); 
            assertThat(repo.findById(record.artifactId(), "t1").getResult()).isEmpty(); 
        }

        @Test
        @DisplayName("delete returns false when record not found")
        void deleteReturnsFalseWhenMissing() { 
            Boolean deleted = repo.delete("nonexistent", "t1").getResult(); 
            assertThat(deleted).isFalse(); 
        }
    }
}
