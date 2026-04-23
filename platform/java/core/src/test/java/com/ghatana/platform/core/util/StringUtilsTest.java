package com.ghatana.platform.core.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StringUtils.
 */
class StringUtilsTest {

    @Test
    void testIsBlankWithNull() { // GH-90000
        assertTrue(StringUtils.isBlank(null)); // GH-90000
    }

    @Test
    void testIsBlankWithEmpty() { // GH-90000
        assertTrue(StringUtils.isBlank(""));
    }

    @Test
    void testIsBlankWithWhitespace() { // GH-90000
        assertTrue(StringUtils.isBlank("   "));
        assertTrue(StringUtils.isBlank("\t"));
        assertTrue(StringUtils.isBlank("\n"));
        assertTrue(StringUtils.isBlank(" \t\n "));
    }

    @Test
    void testIsBlankWithContent() { // GH-90000
        assertFalse(StringUtils.isBlank("test"));
        assertFalse(StringUtils.isBlank(" test "));
        assertFalse(StringUtils.isBlank("a"));
    }

    @Test
    void testIsNotBlank() { // GH-90000
        assertTrue(StringUtils.isNotBlank("test"));
        assertTrue(StringUtils.isNotBlank(" test "));
        assertFalse(StringUtils.isNotBlank(null)); // GH-90000
        assertFalse(StringUtils.isNotBlank(""));
        assertFalse(StringUtils.isNotBlank("   "));
    }

    @Test
    void testDefaultIfBlank() { // GH-90000
        assertEquals("default", StringUtils.defaultIfBlank(null, "default")); // GH-90000
        assertEquals("default", StringUtils.defaultIfBlank("", "default")); // GH-90000
        assertEquals("default", StringUtils.defaultIfBlank("   ", "default")); // GH-90000
        assertEquals("test", StringUtils.defaultIfBlank("test", "default")); // GH-90000
    }

    @Test
    void testFirstNonBlank() { // GH-90000
        assertEquals("test", StringUtils.firstNonBlank(null, "", "  ", "test")); // GH-90000
        assertEquals("first", StringUtils.firstNonBlank("first", "second")); // GH-90000
        assertNull(StringUtils.firstNonBlank(null, "", "  ")); // GH-90000
        assertNull(StringUtils.firstNonBlank((String[]) null)); // GH-90000
    }

    @Test
    void testFirstNonNull() { // GH-90000
        assertEquals("test", StringUtils.firstNonNull(null, "test", "other")); // GH-90000
        assertEquals("first", StringUtils.firstNonNull("first", "second")); // GH-90000
        assertNull(StringUtils.firstNonNull((String[]) null)); // GH-90000
    }

    @Test
    void testJoin() { // GH-90000
        List<String> items = Arrays.asList("a", "b", "c"); // GH-90000
        assertEquals("a,b,c", StringUtils.join(items, ",")); // GH-90000
        assertEquals("a-b-c", StringUtils.join(items, "-")); // GH-90000
        assertEquals("abc", StringUtils.join(items, "")); // GH-90000
    }

    @Test
    void testJoinWithNull() { // GH-90000
        assertEquals("", StringUtils.join(null, ",")); // Returns empty string, not null // GH-90000
        assertEquals("", StringUtils.join(Collections.emptyList(), ",")); // GH-90000
    }

    @Test
    void testJoinWithNullElements() { // GH-90000
        List<String> items = Arrays.asList("a", null, "c"); // GH-90000
        String result = StringUtils.join(items, ","); // GH-90000
        // Null elements are filtered out
        assertTrue(result.contains("a"));
        assertTrue(result.contains("c"));
    }

    @Test
    void testToSnakeCase() { // GH-90000
        assertEquals("hello_world", StringUtils.toSnakeCase("HelloWorld"));
        assertEquals("hello_world", StringUtils.toSnakeCase("helloWorld"));
        assertEquals("test_case", StringUtils.toSnakeCase("testCase"));
        assertNull(StringUtils.toSnakeCase(null)); // GH-90000
    }

    @Test
    void testToKebabCase() { // GH-90000
        assertEquals("-hello-world", StringUtils.toKebabCase("HelloWorld")); // Has leading hyphen
        assertEquals("hello-world", StringUtils.toKebabCase("helloWorld"));
        assertEquals("test-case", StringUtils.toKebabCase("testCase"));
        assertNull(StringUtils.toKebabCase(null)); // Returns null for null input // GH-90000
    }

    @Test
    void testToCamelCase() { // GH-90000
        assertEquals("helloWorld", StringUtils.toCamelCase("hello_world"));
        assertEquals("helloWorld", StringUtils.toCamelCase("hello-world"));
        assertEquals("testCase", StringUtils.toCamelCase("test_case"));
        assertNull(StringUtils.toCamelCase(null)); // GH-90000
    }

    @Test
    void testToPascalCase() { // GH-90000
        assertEquals("HelloWorld", StringUtils.toPascalCase("hello_world"));
        assertEquals("HelloWorld", StringUtils.toPascalCase("hello-world"));
        assertEquals("TestCase", StringUtils.toPascalCase("test_case"));
        assertNull(StringUtils.toPascalCase(null)); // GH-90000
    }

    @Test
    void testRepeat() { // GH-90000
        assertEquals("aaa", StringUtils.repeat("a", 3)); // GH-90000
        assertEquals("", StringUtils.repeat("a", 0)); // GH-90000
        assertEquals("testtest", StringUtils.repeat("test", 2)); // GH-90000
    }

    @Test
    void testRepeatWithNull() { // GH-90000
        assertEquals("", StringUtils.repeat(null, 3)); // Returns empty string, not null // GH-90000
    }

    @Test
    void testRepeatWithNegative() { // GH-90000
        // repeat doesn't throw for negative, returns empty string
        assertEquals("", StringUtils.repeat("a", -1)); // GH-90000
    }

    @Test
    void testTruncate() { // GH-90000
        assertEquals("test", StringUtils.truncate("test", 10)); // GH-90000
        assertEquals("testi...", StringUtils.truncate("testing", 5)); // Adds ellipsis when truncating // GH-90000
        assertEquals("test", StringUtils.truncate("test", 4)); // GH-90000
        assertNull(StringUtils.truncate(null, 10)); // Returns null for null input // GH-90000
    }

    @Test
    void testRandomAlphanumeric() { // GH-90000
        String random = StringUtils.randomAlphanumeric(10); // GH-90000
        assertNotNull(random); // GH-90000
        assertEquals(10, random.length()); // GH-90000
        assertTrue(random.matches("[a-zA-Z0-9]+"));
    }

    @Test
    void testGenerateUuid() { // GH-90000
        String uuid = StringUtils.generateUuid(); // GH-90000
        assertNotNull(uuid); // GH-90000
        assertEquals(36, uuid.length()); // GH-90000
        assertTrue(uuid.contains("-"));
    }

    @Test
    void testContainsAny() { // GH-90000
        assertTrue(StringUtils.containsAny("test", "es", "xyz")); // GH-90000
        assertTrue(StringUtils.containsAny("test", "xyz", "st")); // GH-90000
        assertFalse(StringUtils.containsAny("test", "xyz", "abc")); // GH-90000
        assertFalse(StringUtils.containsAny(null, "test")); // GH-90000
    }

    @Test
    void testEqualsAny() { // GH-90000
        assertTrue(StringUtils.equalsAny("test", "xyz", "test", "abc")); // GH-90000
        assertTrue(StringUtils.equalsAny("test", "test")); // GH-90000
        assertFalse(StringUtils.equalsAny("test", "xyz", "abc")); // GH-90000
        assertFalse(StringUtils.equalsAny(null, "test")); // GH-90000
    }
}
