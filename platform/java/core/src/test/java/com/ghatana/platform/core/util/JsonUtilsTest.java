package com.ghatana.platform.core.util;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JsonUtils.
 */
class JsonUtilsTest {

    @Test
    void testToJson() throws Exception { // GH-90000
        Map<String, Object> data = Map.of("name", "test", "value", 42); // GH-90000
        String json = JsonUtils.toJson(data); // GH-90000

        assertNotNull(json); // GH-90000
        assertTrue(json.contains("name"));
        assertTrue(json.contains("test"));
        assertTrue(json.contains("42"));
    }

    @Test
    void testToJsonWithNull() throws Exception { // GH-90000
        assertEquals("null", JsonUtils.toJson(null)); // Returns JSON "null", not Java null // GH-90000
    }

    @Test
    void testToPrettyJson() throws Exception { // GH-90000
        Map<String, Object> data = Map.of("name", "test", "value", 42); // GH-90000
        String json = JsonUtils.toPrettyJson(data); // GH-90000

        assertNotNull(json); // GH-90000
        assertTrue(json.contains("\n")); // Pretty print has newlines
        assertTrue(json.contains("name"));
    }

    @Test
    void testFromJsonWithTypeReference() throws Exception { // GH-90000
        String json = "{\"name\":\"test\",\"value\":42}";
        Map<String, Object> data = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {}); // GH-90000

        assertNotNull(data); // GH-90000
        assertEquals("test", data.get("name"));
        assertEquals(42, ((Number) data.get("value")).intValue());
    }

    @Test
    void testFromJsonWithNullString() { // GH-90000
        String nullString = null;
        TestData data = JsonUtils.fromJsonSafe(nullString, TestData.class); // GH-90000
        assertNull(data); // GH-90000
    }

    @Test
    void testFromJsonWithBlank() { // GH-90000
        assertNull(JsonUtils.fromJsonSafe("", TestData.class)); // GH-90000
        assertNull(JsonUtils.fromJsonSafe("   ", TestData.class)); // GH-90000
    }

    @Test
    void testFromJsonClass() throws Exception { // GH-90000
        String json = "{\"name\":\"test\",\"value\":42}";
        TestData data = JsonUtils.fromJson(json, TestData.class); // GH-90000

        assertNotNull(data); // GH-90000
        assertEquals("test", data.name); // GH-90000
        assertEquals(42, data.value); // GH-90000
    }

    @Test
    void testFromJsonSafe() { // GH-90000
        String json = "{\"name\":\"test\",\"value\":42}";
        TestData data = JsonUtils.fromJsonSafe(json, TestData.class); // GH-90000

        assertNotNull(data); // GH-90000
        assertEquals("test", data.name); // GH-90000
        assertEquals(42, data.value); // GH-90000
    }

    @Test
    void testFromJsonSafeWithInvalidJson() { // GH-90000
        String invalidJson = "{invalid json}";
        TestData data = JsonUtils.fromJsonSafe(invalidJson, TestData.class); // GH-90000

        assertNull(data); // Should return null instead of throwing // GH-90000
    }

    @Test
    void testFromJsonSafeWithNull() { // GH-90000
        assertNull(JsonUtils.fromJsonSafe(null, TestData.class)); // GH-90000
    }

    @Test
    void testToJsonSafe() { // GH-90000
        Map<String, Object> data = Map.of("name", "test", "value", 42); // GH-90000
        String json = JsonUtils.toJsonSafe(data); // GH-90000

        assertNotNull(json); // GH-90000
        assertTrue(json.contains("name"));
    }

    @Test
    void testToJsonSafeWithNull() { // GH-90000
        assertEquals("null", JsonUtils.toJsonSafe(null)); // Returns JSON "null", not Java null // GH-90000
    }

    @Test
    void testRoundTrip() throws Exception { // GH-90000
        TestData original = new TestData("test", 42); // GH-90000
        String json = JsonUtils.toJson(original); // GH-90000
        TestData restored = JsonUtils.fromJson(json, TestData.class); // GH-90000

        assertNotNull(restored); // GH-90000
        assertEquals(original.name, restored.name); // GH-90000
        assertEquals(original.value, restored.value); // GH-90000
    }

    @Test
    void testListSerialization() throws Exception { // GH-90000
        List<String> list = List.of("a", "b", "c"); // GH-90000
        String json = JsonUtils.toJson(list); // GH-90000
        List<String> restored = JsonUtils.fromJson(json, new TypeReference<List<String>>() {}); // GH-90000

        assertNotNull(restored); // GH-90000
        assertEquals(3, restored.size()); // GH-90000
        assertEquals("a", restored.get(0)); // GH-90000
        assertEquals("b", restored.get(1)); // GH-90000
        assertEquals("c", restored.get(2)); // GH-90000
    }

    @Test
    void testNestedObjects() throws Exception { // GH-90000
        Map<String, Object> nested = Map.of( // GH-90000
            "outer", Map.of( // GH-90000
                "inner", Map.of( // GH-90000
                    "value", 42
                )
            )
        );

        String json = JsonUtils.toJson(nested); // GH-90000
        Map<String, Object> restored = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {}); // GH-90000

        assertNotNull(restored); // GH-90000
        assertTrue(restored.containsKey("outer"));
    }

    @Test
    void testDateTimeSerialization() throws Exception { // GH-90000
        OffsetDateTime now = OffsetDateTime.of(2025, 1, 5, 14, 30, 0, 0, ZoneOffset.UTC); // GH-90000
        DateTimeWrapper wrapper = new DateTimeWrapper(now); // GH-90000

        String json = JsonUtils.toJson(wrapper); // GH-90000
        assertNotNull(json); // GH-90000
        assertTrue(json.contains("2025-01-05T14:30:00Z"));

        DateTimeWrapper restored = JsonUtils.fromJson(json, DateTimeWrapper.class); // GH-90000
        assertNotNull(restored); // GH-90000
        assertEquals(now, restored.timestamp); // GH-90000
    }

    @Test
    void testEmptyCollections() throws Exception { // GH-90000
        List<String> emptyList = List.of(); // GH-90000
        String json = JsonUtils.toJson(emptyList); // GH-90000

        assertEquals("[]", json); // GH-90000

        List<String> restored = JsonUtils.fromJson(json, new TypeReference<List<String>>() {}); // GH-90000
        assertNotNull(restored); // GH-90000
        assertTrue(restored.isEmpty()); // GH-90000
    }

    @Test
    void testNullFieldsExcluded() throws Exception { // GH-90000
        NullableData data = new NullableData("test", null); // GH-90000
        String json = JsonUtils.toJson(data); // GH-90000

        assertNotNull(json); // GH-90000
        assertTrue(json.contains("name"));
        assertFalse(json.contains("value")); // Null fields should be excluded
    }

    @Test
    void testToMap() { // GH-90000
        TestData data = new TestData("test", 42); // GH-90000
        Map<String, Object> map = JsonUtils.toMap(data); // GH-90000

        assertNotNull(map); // GH-90000
        assertEquals("test", map.get("name"));
        assertEquals(42, ((Number) map.get("value")).intValue());
    }

    @Test
    void testDeepCopy() { // GH-90000
        TestData original = new TestData("test", 42); // GH-90000
        TestData copy = JsonUtils.deepCopy(original, TestData.class); // GH-90000

        assertNotNull(copy); // GH-90000
        assertNotSame(original, copy); // GH-90000
        assertEquals(original.name, copy.name); // GH-90000
        assertEquals(original.value, copy.value); // GH-90000
    }

    // Test helper classes
    public static class TestData {
        public String name;
        public int value;

        public TestData() {} // GH-90000

        public TestData(String name, int value) { // GH-90000
            this.name = name;
            this.value = value;
        }
    }

    public static class DateTimeWrapper {
        public OffsetDateTime timestamp;

        public DateTimeWrapper() {} // GH-90000

        public DateTimeWrapper(OffsetDateTime timestamp) { // GH-90000
            this.timestamp = timestamp;
        }
    }

    public static class NullableData {
        public String name;
        public Integer value;

        public NullableData() {} // GH-90000

        public NullableData(String name, Integer value) { // GH-90000
            this.name = name;
            this.value = value;
        }
    }
}
