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
@DisplayName("PlatformObjectMapper — canonical configuration and builder [GH-90000]")
class PlatformObjectMapperTest {

    // ── singleton ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("instance() returns non-null singleton [GH-90000]")
    void instanceReturnsNonNull() { // GH-90000
        assertThat(PlatformObjectMapper.instance()).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("instance() returns same instance on repeated calls (singleton) [GH-90000]")
    void instanceIsSingleton() { // GH-90000
        ObjectMapper first = PlatformObjectMapper.instance(); // GH-90000
        ObjectMapper second = PlatformObjectMapper.instance(); // GH-90000
        assertThat(first).isSameAs(second); // GH-90000
    }

    // ── date/time serialization ──────────────────────────────────────────────

    @Test
    @DisplayName("serializes Instant as ISO-8601 string, not numeric timestamp [GH-90000]")
    void instantSerializedAsIso8601() throws Exception { // GH-90000
        ObjectMapper mapper = PlatformObjectMapper.instance(); // GH-90000
        record Wrapper(Instant ts) {} // GH-90000
        Wrapper w = new Wrapper(Instant.parse("2026-01-15T10:30:00Z [GH-90000]"));
        String json = mapper.writeValueAsString(w); // GH-90000
        assertThat(json).contains("2026-01-15T10:30:00Z [GH-90000]");
        assertThat(json).doesNotContain("1737 [GH-90000]");  // not a numeric epoch value
    }

    @Test
    @DisplayName("serializes LocalDate as ISO date string [GH-90000]")
    void localDateSerializedAsString() throws Exception { // GH-90000
        ObjectMapper mapper = PlatformObjectMapper.instance(); // GH-90000
        record Wrapper(LocalDate date) {} // GH-90000
        Wrapper w = new Wrapper(LocalDate.of(2026, 3, 15)); // GH-90000
        String json = mapper.writeValueAsString(w); // GH-90000
        assertThat(json).contains("2026-03-15 [GH-90000]");
    }

    // ── unknown properties ───────────────────────────────────────────────────

    @Test
    @DisplayName("deserializes JSON with unknown properties without exception [GH-90000]")
    void deserializesWithUnknownProperties() throws Exception { // GH-90000
        ObjectMapper mapper = PlatformObjectMapper.instance(); // GH-90000
        record Simple(String name) {} // GH-90000
        String json = "{\"name\":\"test\",\"unknown\":\"field\"}";
        Simple result = mapper.readValue(json, Simple.class); // GH-90000
        assertThat(result.name()).isEqualTo("test [GH-90000]");
    }

    // ── Optional support ─────────────────────────────────────────────────────

    @Test
    @DisplayName("serializes present Optional as the value [GH-90000]")
    void serializesPresentOptional() throws Exception { // GH-90000
        ObjectMapper mapper = PlatformObjectMapper.instance(); // GH-90000
        record Wrapper(Optional<String> value) {} // GH-90000
        Wrapper w = new Wrapper(Optional.of("hello [GH-90000]"));
        String json = mapper.writeValueAsString(w); // GH-90000
        assertThat(json).contains("hello [GH-90000]");
    }

    @Test
    @DisplayName("serializes empty Optional as null or absent [GH-90000]")
    void serializesEmptyOptional() throws Exception { // GH-90000
        ObjectMapper mapper = PlatformObjectMapper.instance(); // GH-90000
        record Wrapper(Optional<String> value) {} // GH-90000
        Wrapper w = new Wrapper(Optional.empty()); // GH-90000
        String json = mapper.writeValueAsString(w); // GH-90000
        // Empty Optional serialized as null
        assertThat(json).containsAnyOf("null", "{}"); // GH-90000
    }

    // ── copy ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("copy() returns independent ObjectMapper instance [GH-90000]")
    void copyReturnsIndependentInstance() { // GH-90000
        ObjectMapper copy = PlatformObjectMapper.copy(); // GH-90000
        assertThat(copy).isNotNull(); // GH-90000
        assertThat(copy).isNotSameAs(PlatformObjectMapper.instance()); // GH-90000
    }

    @Test
    @DisplayName("copy() can be configured independently without affecting singleton [GH-90000]")
    void copyCanBeConfiguredIndependently() { // GH-90000
        ObjectMapper copy = PlatformObjectMapper.copy(); // GH-90000
        copy.enable(SerializationFeature.INDENT_OUTPUT); // GH-90000

        ObjectMapper singleton = PlatformObjectMapper.instance(); // GH-90000
        // Singleton should not have indent output enabled
        assertThat(singleton.isEnabled(SerializationFeature.INDENT_OUTPUT)).isFalse(); // GH-90000
    }

    // ── builder ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder() produces a functional ObjectMapper [GH-90000]")
    void builderProducesFunctionalMapper() throws Exception { // GH-90000
        ObjectMapper mapper = PlatformObjectMapper.builder().build(); // GH-90000
        assertThat(mapper).isNotNull(); // GH-90000
        String json = mapper.writeValueAsString(42); // GH-90000
        assertThat(json).isEqualTo("42 [GH-90000]");
    }

    @Test
    @DisplayName("builder() mapper serializes Instant as ISO-8601 [GH-90000]")
    void builderMapperSerializesInstantAsIso() throws Exception { // GH-90000
        ObjectMapper mapper = PlatformObjectMapper.builder().build(); // GH-90000
        record Wrapper(Instant ts) {} // GH-90000
        Wrapper w = new Wrapper(Instant.parse("2026-04-01T00:00:00Z [GH-90000]"));
        String json = mapper.writeValueAsString(w); // GH-90000
        assertThat(json).contains("2026-04-01 [GH-90000]");
    }

    // ── round-trip ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("round-trip serialize/deserialize POJO preserves values [GH-90000]")
    void roundTripPojo() throws Exception { // GH-90000
        ObjectMapper mapper = PlatformObjectMapper.instance(); // GH-90000
        record Person(String name, int age) {} // GH-90000
        Person original = new Person("Alice", 30); // GH-90000
        String json = mapper.writeValueAsString(original); // GH-90000
        Person deserialized = mapper.readValue(json, Person.class); // GH-90000
        assertThat(deserialized).isEqualTo(original); // GH-90000
    }
}
