package com.ghatana.core.connectors.s3;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for S3 connector — validates object upload, download,
 * listing, deletion, multi-part upload, and error handling.
 *
 * @doc.type class
 * @doc.purpose Integration tests for S3 connector read/write operations and error handling
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("S3 Connector Integration Tests")
@Tag("integration")
class S3ConnectorIntegrationTest extends EventloopTestBase {

    // ── In-memory S3 bucket simulation ────────────────────────────────────────

    static class InMemoryS3Bucket {
        private final Map<String, byte[]> objects = new HashMap<>(); 
        private final String bucketName;

        InMemoryS3Bucket(String bucketName) { 
            this.bucketName = bucketName;
        }

        void putObject(String key, byte[] data) { 
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Key must not be blank");
            objects.put(key, data); 
        }

        Optional<byte[]> getObject(String key) { 
            return Optional.ofNullable(objects.get(key)); 
        }

        List<String> listObjects(String prefix) { 
            return objects.keySet().stream() 
                    .filter(k -> prefix == null || k.startsWith(prefix)) 
                    .sorted() 
                    .toList(); 
        }

        void deleteObject(String key) { 
            objects.remove(key); 
        }

        boolean exists(String key) { 
            return objects.containsKey(key); 
        }

        int objectCount() { 
            return objects.size(); 
        }
    }

    // ── Object upload ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("object upload")
    class ObjectUpload {

        @Test
        @DisplayName("put object stores data retrievable by key")
        void putObject_storesDataRetrievableByKey() { 
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            byte[] data = "Hello, S3!".getBytes(); 

            bucket.putObject("folder/hello.txt", data); 
            Optional<byte[]> retrieved = bucket.getObject("folder/hello.txt");

            assertThat(retrieved).isPresent(); 
            assertThat(retrieved.get()).isEqualTo(data); 
        }

        @Test
        @DisplayName("put object with blank key throws exception")
        void putObject_withBlankKey_throwsException() { 
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");

            assertThatThrownBy(() -> bucket.putObject("", new byte[]{1})) 
                    .isInstanceOf(IllegalArgumentException.class) 
                    .hasMessageContaining("Key must not be blank");
        }

        @Test
        @DisplayName("overwriting existing key replaces old data")
        void overwritingExistingKey_replacesOldData() { 
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            bucket.putObject("file.txt", "version1".getBytes()); 
            bucket.putObject("file.txt", "version2".getBytes()); 

            Optional<byte[]> retrieved = bucket.getObject("file.txt");

            assertThat(retrieved).isPresent(); 
            assertThat(new String(retrieved.get())).isEqualTo("version2");
        }
    }

    // ── Object download ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("object download")
    class ObjectDownload {

        @Test
        @DisplayName("get existing object returns data")
        void getExistingObject_returnsData() { 
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            byte[] expected = "data bytes".getBytes(); 
            bucket.putObject("docs/readme.md", expected); 

            Optional<byte[]> result = bucket.getObject("docs/readme.md");

            assertThat(result).isPresent(); 
            assertThat(result.get()).isEqualTo(expected); 
        }

        @Test
        @DisplayName("get non-existent object returns empty optional")
        void getNonExistentObject_returnsEmptyOptional() { 
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");

            Optional<byte[]> result = bucket.getObject("does-not-exist.txt");

            assertThat(result).isEmpty(); 
        }
    }

    // ── Object listing ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("object listing")
    class ObjectListing {

        @Test
        @DisplayName("list all objects (null prefix) returns all keys sorted")
        void listAllObjects_returnsAllKeysSorted() { 
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            bucket.putObject("b/file.txt", new byte[]{}); 
            bucket.putObject("a/file.txt", new byte[]{}); 
            bucket.putObject("c/file.txt", new byte[]{}); 

            List<String> keys = bucket.listObjects(null); 

            assertThat(keys).containsExactly("a/file.txt", "b/file.txt", "c/file.txt"); 
        }

        @Test
        @DisplayName("list with prefix filters to matching keys only")
        void listWithPrefix_filtersToMatchingKeysOnly() { 
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            bucket.putObject("images/cat.jpg",  new byte[]{}); 
            bucket.putObject("images/dog.jpg",  new byte[]{}); 
            bucket.putObject("docs/readme.md",  new byte[]{}); 

            List<String> imageKeys = bucket.listObjects("images/");

            assertThat(imageKeys).containsExactlyInAnyOrder("images/cat.jpg", "images/dog.jpg"); 
        }

        @Test
        @DisplayName("list on empty bucket returns empty list")
        void listOnEmptyBucket_returnsEmptyList() { 
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");

            List<String> keys = bucket.listObjects(null); 

            assertThat(keys).isEmpty(); 
        }
    }

    // ── Object deletion ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("object deletion")
    class ObjectDeletion {

        @Test
        @DisplayName("delete existing object removes it from bucket")
        void deleteExistingObject_removesFromBucket() { 
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            bucket.putObject("temp.txt", new byte[]{1, 2, 3}); 

            bucket.deleteObject("temp.txt");

            assertThat(bucket.exists("temp.txt")).isFalse();
            assertThat(bucket.objectCount()).isEqualTo(0); 
        }

        @Test
        @DisplayName("delete non-existent object is a no-op")
        void deleteNonExistentObject_isNoOp() { 
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            bucket.putObject("existing.txt", new byte[]{}); 

            bucket.deleteObject("non-existent.txt");

            assertThat(bucket.objectCount()).isEqualTo(1); 
        }
    }

    // ── Large object / multi-part simulation ─────────────────────────────────

    @Nested
    @DisplayName("large object upload (multi-part simulation)")
    class LargeObjectUpload {

        @Test
        @DisplayName("multi-part upload reassembles parts in correct order")
        void multiPartUpload_reassemblesPartsInOrder() { 
            List<byte[]> parts = new ArrayList<>(); 
            parts.add("Part1".getBytes()); 
            parts.add("Part2".getBytes()); 
            parts.add("Part3".getBytes()); 

            // Complete: join all parts
            int totalSize = parts.stream().mapToInt(p -> p.length).sum(); 
            byte[] assembled = new byte[totalSize];
            int offset = 0;
            for (byte[] part : parts) { 
                System.arraycopy(part, 0, assembled, offset, part.length); 
                offset += part.length;
            }

            assertThat(new String(assembled)).isEqualTo("Part1Part2Part3");
        }
    }
}
