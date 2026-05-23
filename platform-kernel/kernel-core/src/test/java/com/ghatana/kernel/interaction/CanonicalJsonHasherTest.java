package com.ghatana.kernel.interaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Canonical JSON hasher")
class CanonicalJsonHasherTest {

    @Test
    @DisplayName("uses a stable opaque type marker for empty bean payloads")
    void usesStableOpaqueTypeMarkerForEmptyBeanPayloads() {
        String firstHash = CanonicalJsonHasher.hash(new Object());
        String secondHash = CanonicalJsonHasher.hash(new Object());

        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(CanonicalJsonHasher.toCanonicalJson(new Object()))
                .isEqualTo("{\"opaqueType\":\"java.lang.Object\"}");
    }

    @Test
    @DisplayName("produces same hash for equivalent payloads with different field ordering")
    void producesSameHashForEquivalentPayloadsWithDifferentFieldOrdering() {
        Map<String, Object> payload1 = Map.of(
                "name", "test",
                "value", 42,
                "flag", true
        );
        Map<String, Object> payload2 = Map.of(
                "flag", true,
                "name", "test",
                "value", 42
        );

        String hash1 = CanonicalJsonHasher.hash(payload1);
        String hash2 = CanonicalJsonHasher.hash(payload2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("produces different hashes for semantically different payloads")
    void producesDifferentHashesForSemanticallyDifferentPayloads() {
        Map<String, Object> payload1 = Map.of("value", 42);
        Map<String, Object> payload2 = Map.of("value", 43);

        String hash1 = CanonicalJsonHasher.hash(payload1);
        String hash2 = CanonicalJsonHasher.hash(payload2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("handles nested payloads correctly")
    void handlesNestedPayloadsCorrectly() {
        Map<String, Object> payload1 = Map.of(
                "outer", Map.of("inner", 42)
        );
        Map<String, Object> payload2 = Map.of(
                "outer", Map.of("inner", 42)
        );

        String hash1 = CanonicalJsonHasher.hash(payload1);
        String hash2 = CanonicalJsonHasher.hash(payload2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("handles null values explicitly")
    void handlesNullValuesExplicitly() {
        Map<String, Object> payload1 = Map.of("value", (Object) null);
        Map<String, Object> payload2 = Map.of("value", (Object) null);

        String hash1 = CanonicalJsonHasher.hash(payload1);
        String hash2 = CanonicalJsonHasher.hash(payload2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("handles list payloads correctly")
    void handlesListPayloadsCorrectly() {
        List<String> payload1 = List.of("a", "b", "c");
        List<String> payload2 = List.of("a", "b", "c");

        String hash1 = CanonicalJsonHasher.hash(payload1);
        String hash2 = CanonicalJsonHasher.hash(payload2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("handles record payloads correctly")
    void handlesRecordPayloadsCorrectly() {
        TestRecord record1 = new TestRecord("test", 42);
        TestRecord record2 = new TestRecord("test", 42);

        String hash1 = CanonicalJsonHasher.hash(record1);
        String hash2 = CanonicalJsonHasher.hash(record2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("produces different hashes for different record values")
    void producesDifferentHashesForDifferentRecordValues() {
        TestRecord record1 = new TestRecord("test", 42);
        TestRecord record2 = new TestRecord("test", 43);

        String hash1 = CanonicalJsonHasher.hash(record1);
        String hash2 = CanonicalJsonHasher.hash(record2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    private record TestRecord(String name, int value) {
    }
}