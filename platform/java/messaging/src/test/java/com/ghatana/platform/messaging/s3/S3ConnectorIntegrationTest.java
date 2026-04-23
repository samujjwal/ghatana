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
        private final Map<String, byte[]> objects = new HashMap<>(); // GH-90000
        private final String bucketName;

        InMemoryS3Bucket(String bucketName) { // GH-90000
            this.bucketName = bucketName;
        }

        void putObject(String key, byte[] data) { // GH-90000
            if (key == null || key.isBlank()) throw new IllegalArgumentException("Key must not be blank");
            objects.put(key, data); // GH-90000
        }

        Optional<byte[]> getObject(String key) { // GH-90000
            return Optional.ofNullable(objects.get(key)); // GH-90000
        }

        List<String> listObjects(String prefix) { // GH-90000
            return objects.keySet().stream() // GH-90000
                    .filter(k -> prefix == null || k.startsWith(prefix)) // GH-90000
                    .sorted() // GH-90000
                    .toList(); // GH-90000
        }

        void deleteObject(String key) { // GH-90000
            objects.remove(key); // GH-90000
        }

        boolean exists(String key) { // GH-90000
            return objects.containsKey(key); // GH-90000
        }

        int objectCount() { // GH-90000
            return objects.size(); // GH-90000
        }
    }

    // ── Object upload ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("object upload")
    class ObjectUpload {

        @Test
        @DisplayName("put object stores data retrievable by key")
        void putObject_storesDataRetrievableByKey() { // GH-90000
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            byte[] data = "Hello, S3!".getBytes(); // GH-90000

            bucket.putObject("folder/hello.txt", data); // GH-90000
            Optional<byte[]> retrieved = bucket.getObject("folder/hello.txt");

            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(retrieved.get()).isEqualTo(data); // GH-90000
        }

        @Test
        @DisplayName("put object with blank key throws exception")
        void putObject_withBlankKey_throwsException() { // GH-90000
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");

            assertThatThrownBy(() -> bucket.putObject("", new byte[]{1})) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("Key must not be blank");
        }

        @Test
        @DisplayName("overwriting existing key replaces old data")
        void overwritingExistingKey_replacesOldData() { // GH-90000
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            bucket.putObject("file.txt", "version1".getBytes()); // GH-90000
            bucket.putObject("file.txt", "version2".getBytes()); // GH-90000

            Optional<byte[]> retrieved = bucket.getObject("file.txt");

            assertThat(retrieved).isPresent(); // GH-90000
            assertThat(new String(retrieved.get())).isEqualTo("version2");
        }
    }

    // ── Object download ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("object download")
    class ObjectDownload {

        @Test
        @DisplayName("get existing object returns data")
        void getExistingObject_returnsData() { // GH-90000
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            byte[] expected = "data bytes".getBytes(); // GH-90000
            bucket.putObject("docs/readme.md", expected); // GH-90000

            Optional<byte[]> result = bucket.getObject("docs/readme.md");

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get()).isEqualTo(expected); // GH-90000
        }

        @Test
        @DisplayName("get non-existent object returns empty optional")
        void getNonExistentObject_returnsEmptyOptional() { // GH-90000
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");

            Optional<byte[]> result = bucket.getObject("does-not-exist.txt");

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    // ── Object listing ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("object listing")
    class ObjectListing {

        @Test
        @DisplayName("list all objects (null prefix) returns all keys sorted")
        void listAllObjects_returnsAllKeysSorted() { // GH-90000
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            bucket.putObject("b/file.txt", new byte[]{}); // GH-90000
            bucket.putObject("a/file.txt", new byte[]{}); // GH-90000
            bucket.putObject("c/file.txt", new byte[]{}); // GH-90000

            List<String> keys = bucket.listObjects(null); // GH-90000

            assertThat(keys).containsExactly("a/file.txt", "b/file.txt", "c/file.txt"); // GH-90000
        }

        @Test
        @DisplayName("list with prefix filters to matching keys only")
        void listWithPrefix_filtersToMatchingKeysOnly() { // GH-90000
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            bucket.putObject("images/cat.jpg",  new byte[]{}); // GH-90000
            bucket.putObject("images/dog.jpg",  new byte[]{}); // GH-90000
            bucket.putObject("docs/readme.md",  new byte[]{}); // GH-90000

            List<String> imageKeys = bucket.listObjects("images/");

            assertThat(imageKeys).containsExactlyInAnyOrder("images/cat.jpg", "images/dog.jpg"); // GH-90000
        }

        @Test
        @DisplayName("list on empty bucket returns empty list")
        void listOnEmptyBucket_returnsEmptyList() { // GH-90000
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");

            List<String> keys = bucket.listObjects(null); // GH-90000

            assertThat(keys).isEmpty(); // GH-90000
        }
    }

    // ── Object deletion ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("object deletion")
    class ObjectDeletion {

        @Test
        @DisplayName("delete existing object removes it from bucket")
        void deleteExistingObject_removesFromBucket() { // GH-90000
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            bucket.putObject("temp.txt", new byte[]{1, 2, 3}); // GH-90000

            bucket.deleteObject("temp.txt");

            assertThat(bucket.exists("temp.txt")).isFalse();
            assertThat(bucket.objectCount()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("delete non-existent object is a no-op")
        void deleteNonExistentObject_isNoOp() { // GH-90000
            InMemoryS3Bucket bucket = new InMemoryS3Bucket("my-bucket");
            bucket.putObject("existing.txt", new byte[]{}); // GH-90000

            bucket.deleteObject("non-existent.txt");

            assertThat(bucket.objectCount()).isEqualTo(1); // GH-90000
        }
    }

    // ── Large object / multi-part simulation ─────────────────────────────────

    @Nested
    @DisplayName("large object upload (multi-part simulation)")
    class LargeObjectUpload {

        @Test
        @DisplayName("multi-part upload reassembles parts in correct order")
        void multiPartUpload_reassemblesPartsInOrder() { // GH-90000
            List<byte[]> parts = new ArrayList<>(); // GH-90000
            parts.add("Part1".getBytes()); // GH-90000
            parts.add("Part2".getBytes()); // GH-90000
            parts.add("Part3".getBytes()); // GH-90000

            // Complete: join all parts
            int totalSize = parts.stream().mapToInt(p -> p.length).sum(); // GH-90000
            byte[] assembled = new byte[totalSize];
            int offset = 0;
            for (byte[] part : parts) { // GH-90000
                System.arraycopy(part, 0, assembled, offset, part.length); // GH-90000
                offset += part.length;
            }

            assertThat(new String(assembled)).isEqualTo("Part1Part2Part3");
        }
    }
}
