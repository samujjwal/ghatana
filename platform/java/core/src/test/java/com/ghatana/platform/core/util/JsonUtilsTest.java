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
    void testToJson() throws Exception {
        Map<String, Object> data = Map.of("name", "test", "value", 42);
        String json = JsonUtils.toJson(data);
        
        assertNotNull(json);
        assertTrue(json.contains("name"));
        assertTrue(json.contains("test"));
        assertTrue(json.contains("42"));
    }

    @Test
    void testToJsonWithNull() throws Exception {
        assertEquals("null", JsonUtils.toJson(null)); // Returns JSON "null", not Java null
    }

    @Test
    void testToPrettyJson() throws Exception {
        Map<String, Object> data = Map.of("name", "test", "value", 42);
        String json = JsonUtils.toPrettyJson(data);
        
        assertNotNull(json);
        assertTrue(json.contains("\n")); // Pretty print has newlines
        assertTrue(json.contains("name"));
    }

    @Test
    void testFromJsonWithTypeReference() throws Exception {
        String json = "{\"name\":\"test\",\"value\":42}";
        Map<String, Object> data = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
        
        assertNotNull(data);
        assertEquals("test", data.get("name"));
        assertEquals(42, ((Number) data.get("value")).intValue());
    }

    @Test
    void testFromJsonWithNullString() {
        String nullString = null;
        TestData data = JsonUtils.fromJsonSafe(nullString, TestData.class);
        assertNull(data);
    }

    @Test
    void testFromJsonWithBlank() {
        assertNull(JsonUtils.fromJsonSafe("", TestData.class));
        assertNull(JsonUtils.fromJsonSafe("   ", TestData.class));
    }

    @Test
    void testFromJsonClass() throws Exception {
        String json = "{\"name\":\"test\",\"value\":42}";
        TestData data = JsonUtils.fromJson(json, TestData.class);
        
        assertNotNull(data);
        assertEquals("test", data.name);
        assertEquals(42, data.value);
    }

    @Test
    void testFromJsonSafe() {
        String json = "{\"name\":\"test\",\"value\":42}";
        TestData data = JsonUtils.fromJsonSafe(json, TestData.class);
        
        assertNotNull(data);
        assertEquals("test", data.name);
        assertEquals(42, data.value);
    }

    @Test
    void testFromJsonSafeWithInvalidJson() {
        String invalidJson = "{invalid json}";
        TestData data = JsonUtils.fromJsonSafe(invalidJson, TestData.class);
        
        assertNull(data); // Should return null instead of throwing
    }

    @Test
    void testFromJsonSafeWithNull() {
        assertNull(JsonUtils.fromJsonSafe(null, TestData.class));
    }

    @Test
    void testToJsonSafe() {
        Map<String, Object> data = Map.of("name", "test", "value", 42);
        String json = JsonUtils.toJsonSafe(data);
        
        assertNotNull(json);
        assertTrue(json.contains("name"));
    }

    @Test
    void testToJsonSafeWithNull() {
        assertEquals("null", JsonUtils.toJsonSafe(null)); // Returns JSON "null", not Java null
    }

    @Test
    void testRoundTrip() throws Exception {
        TestData original = new TestData("test", 42);
        String json = JsonUtils.toJson(original);
        TestData restored = JsonUtils.fromJson(json, TestData.class);
        
        assertNotNull(restored);
        assertEquals(original.name, restored.name);
        assertEquals(original.value, restored.value);
    }

    @Test
    void testListSerialization() throws Exception {
        List<String> list = List.of("a", "b", "c");
        String json = JsonUtils.toJson(list);
        List<String> restored = JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
        
        assertNotNull(restored);
        assertEquals(3, restored.size());
        assertEquals("a", restored.get(0));
        assertEquals("b", restored.get(1));
        assertEquals("c", restored.get(2));
    }

    @Test
    void testNestedObjects() throws Exception {
        Map<String, Object> nested = Map.of(
            "outer", Map.of(
                "inner", Map.of(
                    "value", 42
                )
            )
        );
        
        String json = JsonUtils.toJson(nested);
        Map<String, Object> restored = JsonUtils.fromJson(json, new TypeReference<Map<String, Object>>() {});
        
        assertNotNull(restored);
        assertTrue(restored.containsKey("outer"));
    }

    @Test
    void testDateTimeSerialization() throws Exception {
        OffsetDateTime now = OffsetDateTime.of(2025, 1, 5, 14, 30, 0, 0, ZoneOffset.UTC);
        DateTimeWrapper wrapper = new DateTimeWrapper(now);
        
        String json = JsonUtils.toJson(wrapper);
        assertNotNull(json);
        assertTrue(json.contains("2025-01-05T14:30:00Z"));
        
        DateTimeWrapper restored = JsonUtils.fromJson(json, DateTimeWrapper.class);
        assertNotNull(restored);
        assertEquals(now, restored.timestamp);
    }

    @Test
    void testEmptyCollections() throws Exception {
        List<String> emptyList = List.of();
        String json = JsonUtils.toJson(emptyList);
        
        assertEquals("[]", json);
        
        List<String> restored = JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
        assertNotNull(restored);
        assertTrue(restored.isEmpty());
    }

    @Test
    void testNullFieldsExcluded() throws Exception {
        NullableData data = new NullableData("test", null);
        String json = JsonUtils.toJson(data);
        
        assertNotNull(json);
        assertTrue(json.contains("name"));
        assertFalse(json.contains("value")); // Null fields should be excluded
    }

    @Test
    void testToMap() {
        TestData data = new TestData("test", 42);
        Map<String, Object> map = JsonUtils.toMap(data);
        
        assertNotNull(map);
        assertEquals("test", map.get("name"));
        assertEquals(42, ((Number) map.get("value")).intValue());
    }

    @Test
    void testDeepCopy() {
        TestData original = new TestData("test", 42);
        TestData copy = JsonUtils.deepCopy(original, TestData.class);
        
        assertNotNull(copy);
        assertNotSame(original, copy);
        assertEquals(original.name, copy.name);
        assertEquals(original.value, copy.value);
    }

    // Test helper classes
    public static class TestData {
        public String name;
        public int value;
        
        public TestData() {}
        
        public TestData(String name, int value) {
            this.name = name;
            this.value = value;
        }
    }

    public static class DateTimeWrapper {
        public OffsetDateTime timestamp;
        
        public DateTimeWrapper() {}
        
        public DateTimeWrapper(OffsetDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }

    public static class NullableData {
        public String name;
        public Integer value;
        
        public NullableData() {}
        
        public NullableData(String name, Integer value) {
            this.name = name;
            this.value = value;
        }
    }
}
