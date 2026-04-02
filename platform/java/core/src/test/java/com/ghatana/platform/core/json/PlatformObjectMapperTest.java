package com.ghatana.platform.core.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Unit tests for PlatformObjectMapper singleton and builder
 * @doc.layer core
 * @doc.pattern Test
 */
@DisplayName("PlatformObjectMapper — canonical configuration and builder")
class PlatformObjectMapperTest {

    // ── singleton ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("instance() returns non-null singleton")
    void instanceReturnsNonNull() {
        assertThat(PlatformObjectMapper.instance()).isNotNull();
    }

    @Test
    @DisplayName("instance() returns same instance on repeated calls (singleton)")
    void instanceIsSingleton() {
        ObjectMapper first = PlatformObjectMapper.instance();
        ObjectMapper second = PlatformObjectMapper.instance();
        assertThat(first).isSameAs(second);
    }

    // ── date/time serialization ──────────────────────────────────────────────

    @Test
    @DisplayName("serializes Instant as ISO-8601 string, not numeric timestamp")
    void instantSerializedAsIso8601() throws Exception {
        ObjectMapper mapper = PlatformObjectMapper.instance();
        record Wrapper(Instant ts) {}
        Wrapper w = new Wrapper(Instant.parse("2026-01-15T10:30:00Z"));
        String json = mapper.writeValueAsString(w);
        assertThat(json).contains("2026-01-15T10:30:00Z");
        assertThat(json).doesNotContain("1737");  // not a numeric epoch value
    }

    @Test
    @DisplayName("serializes LocalDate as ISO date string")
    void localDateSerializedAsString() throws Exception {
        ObjectMapper mapper = PlatformObjectMapper.instance();
        record Wrapper(LocalDate date) {}
        Wrapper w = new Wrapper(LocalDate.of(2026, 3, 15));
        String json = mapper.writeValueAsString(w);
        assertThat(json).contains("2026-03-15");
    }

    // ── unknown properties ───────────────────────────────────────────────────

    @Test
    @DisplayName("deserializes JSON with unknown properties without exception")
    void deserializesWithUnknownProperties() throws Exception {
        ObjectMapper mapper = PlatformObjectMapper.instance();
        record Simple(String name) {}
        String json = "{\"name\":\"test\",\"unknown\":\"field\"}";
        Simple result = mapper.readValue(json, Simple.class);
        assertThat(result.name()).isEqualTo("test");
    }

    // ── Optional support ─────────────────────────────────────────────────────

    @Test
    @DisplayName("serializes present Optional as the value")
    void serializesPresentOptional() throws Exception {
        ObjectMapper mapper = PlatformObjectMapper.instance();
        record Wrapper(Optional<String> value) {}
        Wrapper w = new Wrapper(Optional.of("hello"));
        String json = mapper.writeValueAsString(w);
        assertThat(json).contains("hello");
    }

    @Test
    @DisplayName("serializes empty Optional as null or absent")
    void serializesEmptyOptional() throws Exception {
        ObjectMapper mapper = PlatformObjectMapper.instance();
        record Wrapper(Optional<String> value) {}
        Wrapper w = new Wrapper(Optional.empty());
        String json = mapper.writeValueAsString(w);
        // Empty Optional serialized as null
        assertThat(json).containsAnyOf("null", "{}");
    }

    // ── copy ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("copy() returns independent ObjectMapper instance")
    void copyReturnsIndependentInstance() {
        ObjectMapper copy = PlatformObjectMapper.copy();
        assertThat(copy).isNotNull();
        assertThat(copy).isNotSameAs(PlatformObjectMapper.instance());
    }

    @Test
    @DisplayName("copy() can be configured independently without affecting singleton")
    void copyCanBeConfiguredIndependently() {
        ObjectMapper copy = PlatformObjectMapper.copy();
        copy.enable(SerializationFeature.INDENT_OUTPUT);

        ObjectMapper singleton = PlatformObjectMapper.instance();
        // Singleton should not have indent output enabled
        assertThat(singleton.isEnabled(SerializationFeature.INDENT_OUTPUT)).isFalse();
    }

    // ── builder ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder() produces a functional ObjectMapper")
    void builderProducesFunctionalMapper() throws Exception {
        ObjectMapper mapper = PlatformObjectMapper.builder().build();
        assertThat(mapper).isNotNull();
        String json = mapper.writeValueAsString(42);
        assertThat(json).isEqualTo("42");
    }

    @Test
    @DisplayName("builder() mapper serializes Instant as ISO-8601")
    void builderMapperSerializesInstantAsIso() throws Exception {
        ObjectMapper mapper = PlatformObjectMapper.builder().build();
        record Wrapper(Instant ts) {}
        Wrapper w = new Wrapper(Instant.parse("2026-04-01T00:00:00Z"));
        String json = mapper.writeValueAsString(w);
        assertThat(json).contains("2026-04-01");
    }

    // ── round-trip ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("round-trip serialize/deserialize POJO preserves values")
    void roundTripPojo() throws Exception {
        ObjectMapper mapper = PlatformObjectMapper.instance();
        record Person(String name, int age) {}
        Person original = new Person("Alice", 30);
        String json = mapper.writeValueAsString(original);
        Person deserialized = mapper.readValue(json, Person.class);
        assertThat(deserialized).isEqualTo(original);
    }
}
