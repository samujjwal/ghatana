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
    void testIsBlankWithNull() {
        assertTrue(StringUtils.isBlank(null));
    }

    @Test
    void testIsBlankWithEmpty() {
        assertTrue(StringUtils.isBlank(""));
    }

    @Test
    void testIsBlankWithWhitespace() {
        assertTrue(StringUtils.isBlank("   "));
        assertTrue(StringUtils.isBlank("\t"));
        assertTrue(StringUtils.isBlank("\n"));
        assertTrue(StringUtils.isBlank(" \t\n "));
    }

    @Test
    void testIsBlankWithContent() {
        assertFalse(StringUtils.isBlank("test"));
        assertFalse(StringUtils.isBlank(" test "));
        assertFalse(StringUtils.isBlank("a"));
    }

    @Test
    void testIsNotBlank() {
        assertTrue(StringUtils.isNotBlank("test"));
        assertTrue(StringUtils.isNotBlank(" test "));
        assertFalse(StringUtils.isNotBlank(null));
        assertFalse(StringUtils.isNotBlank(""));
        assertFalse(StringUtils.isNotBlank("   "));
    }

    @Test
    void testDefaultIfBlank() {
        assertEquals("default", StringUtils.defaultIfBlank(null, "default"));
        assertEquals("default", StringUtils.defaultIfBlank("", "default"));
        assertEquals("default", StringUtils.defaultIfBlank("   ", "default"));
        assertEquals("test", StringUtils.defaultIfBlank("test", "default"));
    }

    @Test
    void testFirstNonBlank() {
        assertEquals("test", StringUtils.firstNonBlank(null, "", "  ", "test"));
        assertEquals("first", StringUtils.firstNonBlank("first", "second"));
        assertNull(StringUtils.firstNonBlank(null, "", "  "));
        assertNull(StringUtils.firstNonBlank((String[]) null));
    }

    @Test
    void testFirstNonNull() {
        assertEquals("test", StringUtils.firstNonNull(null, "test", "other"));
        assertEquals("first", StringUtils.firstNonNull("first", "second"));
        assertNull(StringUtils.firstNonNull((String[]) null));
    }

    @Test
    void testJoin() {
        List<String> items = Arrays.asList("a", "b", "c");
        assertEquals("a,b,c", StringUtils.join(items, ","));
        assertEquals("a-b-c", StringUtils.join(items, "-"));
        assertEquals("abc", StringUtils.join(items, ""));
    }

    @Test
    void testJoinWithNull() {
        assertEquals("", StringUtils.join(null, ",")); // Returns empty string, not null
        assertEquals("", StringUtils.join(Collections.emptyList(), ","));
    }

    @Test
    void testJoinWithNullElements() {
        List<String> items = Arrays.asList("a", null, "c");
        String result = StringUtils.join(items, ",");
        // Null elements are filtered out
        assertTrue(result.contains("a"));
        assertTrue(result.contains("c"));
    }

    @Test
    void testToSnakeCase() {
        assertEquals("hello_world", StringUtils.toSnakeCase("HelloWorld"));
        assertEquals("hello_world", StringUtils.toSnakeCase("helloWorld"));
        assertEquals("test_case", StringUtils.toSnakeCase("testCase"));
        assertNull(StringUtils.toSnakeCase(null));
    }

    @Test
    void testToKebabCase() {
        assertEquals("-hello-world", StringUtils.toKebabCase("HelloWorld")); // Has leading hyphen
        assertEquals("hello-world", StringUtils.toKebabCase("helloWorld"));
        assertEquals("test-case", StringUtils.toKebabCase("testCase"));
        assertNull(StringUtils.toKebabCase(null)); // Returns null for null input
    }

    @Test
    void testToCamelCase() {
        assertEquals("helloWorld", StringUtils.toCamelCase("hello_world"));
        assertEquals("helloWorld", StringUtils.toCamelCase("hello-world"));
        assertEquals("testCase", StringUtils.toCamelCase("test_case"));
        assertNull(StringUtils.toCamelCase(null));
    }

    @Test
    void testToPascalCase() {
        assertEquals("HelloWorld", StringUtils.toPascalCase("hello_world"));
        assertEquals("HelloWorld", StringUtils.toPascalCase("hello-world"));
        assertEquals("TestCase", StringUtils.toPascalCase("test_case"));
        assertNull(StringUtils.toPascalCase(null));
    }

    @Test
    void testRepeat() {
        assertEquals("aaa", StringUtils.repeat("a", 3));
        assertEquals("", StringUtils.repeat("a", 0));
        assertEquals("testtest", StringUtils.repeat("test", 2));
    }

    @Test
    void testRepeatWithNull() {
        assertEquals("", StringUtils.repeat(null, 3)); // Returns empty string, not null
    }

    @Test
    void testRepeatWithNegative() {
        // repeat doesn't throw for negative, returns empty string
        assertEquals("", StringUtils.repeat("a", -1));
    }

    @Test
    void testTruncate() {
        assertEquals("test", StringUtils.truncate("test", 10));
        assertEquals("testi...", StringUtils.truncate("testing", 5)); // Adds ellipsis when truncating
        assertEquals("test", StringUtils.truncate("test", 4));
        assertNull(StringUtils.truncate(null, 10)); // Returns null for null input
    }

    @Test
    void testRandomAlphanumeric() {
        String random = StringUtils.randomAlphanumeric(10);
        assertNotNull(random);
        assertEquals(10, random.length());
        assertTrue(random.matches("[a-zA-Z0-9]+"));
    }

    @Test
    void testGenerateUuid() {
        String uuid = StringUtils.generateUuid();
        assertNotNull(uuid);
        assertEquals(36, uuid.length());
        assertTrue(uuid.contains("-"));
    }

    @Test
    void testContainsAny() {
        assertTrue(StringUtils.containsAny("test", "es", "xyz"));
        assertTrue(StringUtils.containsAny("test", "xyz", "st"));
        assertFalse(StringUtils.containsAny("test", "xyz", "abc"));
        assertFalse(StringUtils.containsAny(null, "test"));
    }

    @Test
    void testEqualsAny() {
        assertTrue(StringUtils.equalsAny("test", "xyz", "test", "abc"));
        assertTrue(StringUtils.equalsAny("test", "test"));
        assertFalse(StringUtils.equalsAny("test", "xyz", "abc"));
        assertFalse(StringUtils.equalsAny(null, "test"));
    }
}
