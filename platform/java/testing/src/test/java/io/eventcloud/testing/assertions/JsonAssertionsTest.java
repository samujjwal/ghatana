package com.ghatana.platform.testing.assertions;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JsonAssertionsTest {

    private static final String SIMPLE_JSON = "{\"name\":\"test\",\"value\":42}";
    private static final String NESTED_JSON = "{\"user\":{\"id\":1,\"name\":\"test\"},\"roles\":[\"admin\",\"user\"]}";
    private static final String ARRAY_JSON = "[{\"id\":1,\"name\":\"one\"},{\"id\":2,\"name\":\"two\"}]";

    @Test
    void assertJsonEquals_shouldPassForEqualJson() { // GH-90000
        String expected = "{\"name\":\"test\"}";
        String actual = "{\"name\": \"test\"}"; // Different formatting, same content
        JsonAssertions.assertJsonEquals(expected, actual); // GH-90000
    }

    @Test
    void assertJsonEquals_shouldFailForDifferentJson() { // GH-90000
        String expected = "{\"name\":\"test1\"}";
        String actual = "{\"name\":\"test2\"}";
        assertThrows(AssertionError.class, // GH-90000
            () -> JsonAssertions.assertJsonEquals(expected, actual)); // GH-90000
    }

    @Test
    void assertJsonContains_shouldPassWhenContainsAllFields() { // GH-90000
        String expected = "{\"name\":\"test\"}";
        String actual = "{\"name\":\"test\",\"value\":42}";
        JsonAssertions.assertJsonContains(expected, actual); // GH-90000
    }

    @Test
    void assertJsonPath_shouldReturnCorrectValue() { // GH-90000
        JsonAssertions.assertJsonPath(SIMPLE_JSON, "$.name", "test"); // GH-90000
        JsonAssertions.assertJsonPath(SIMPLE_JSON, "$.value", 42); // GH-90000
    }

    @Test
    void assertJsonPath_shouldHandleNestedStructures() { // GH-90000
        JsonAssertions.assertJsonPath(NESTED_JSON, "$.user.id", 1); // GH-90000
        JsonAssertions.assertJsonPath(NESTED_JSON, "$.user.name", "test"); // GH-90000
    }

    @Test
    void assertJsonArraySize_shouldCheckArraySize() { // GH-90000
        JsonAssertions.assertJsonArraySize(NESTED_JSON, "$.roles", 2); // GH-90000
        JsonAssertions.assertJsonArraySize(ARRAY_JSON, "$", 2); // GH-90000
    }

    @Test
    void assertHasJsonPath_shouldPassWhenPathExists() { // GH-90000
        JsonAssertions.assertHasJsonPath(SIMPLE_JSON, "$.name"); // GH-90000
        JsonAssertions.assertHasJsonPath(NESTED_JSON, "$.user.id"); // GH-90000
        JsonAssertions.assertHasJsonPath(NESTED_JSON, "$.roles[0]"); // GH-90000
    }

    @Test
    void assertDoesNotHaveJsonPath_shouldPassWhenPathDoesNotExist() { // GH-90000
        JsonAssertions.assertDoesNotHaveJsonPath(SIMPLE_JSON, "$.nonexistent"); // GH-90000
        JsonAssertions.assertDoesNotHaveJsonPath(NESTED_JSON, "$.user.nonexistent"); // GH-90000
    }

    @Test
    void assertJsonPath_shouldFailForIncorrectValue() { // GH-90000
        assertThrows(AssertionError.class, // GH-90000
            () -> JsonAssertions.assertJsonPath(SIMPLE_JSON, "$.name", "wrong")); // GH-90000
    }

    @Test
    void assertJsonArraySize_shouldFailForWrongSize() { // GH-90000
        assertThrows(AssertionError.class, // GH-90000
            () -> JsonAssertions.assertJsonArraySize(NESTED_JSON, "$.roles", 3)); // GH-90000
    }

    @Test
    void assertHasJsonPath_shouldFailForMissingPath() { // GH-90000
        assertThrows(AssertionError.class, // GH-90000
            () -> JsonAssertions.assertHasJsonPath(SIMPLE_JSON, "$.nonexistent")); // GH-90000
    }

    @Test
    void assertDoesNotHaveJsonPath_shouldFailForExistingPath() { // GH-90000
        assertThrows(AssertionError.class, // GH-90000
            () -> JsonAssertions.assertDoesNotHaveJsonPath(SIMPLE_JSON, "$.name")); // GH-90000
    }

    // New explicit test covering root-level arrays and nested arrays, including empty arrays and a negative case
    @Test
    void assertJsonArraySize_explicitRootAndNestedCases() { // GH-90000
        // Root-level empty array
        String emptyRoot = "[]";
        JsonAssertions.assertJsonArraySize(emptyRoot, "$", 0); // GH-90000

        // Nested empty array
        String nestedEmpty = "{\"items\": []}";
        JsonAssertions.assertJsonArraySize(nestedEmpty, "$.items", 0); // GH-90000

        // Root-level complex array
        String complexRoot = "[{\"a\":1},{\"b\":2},{\"c\":3}]";
        JsonAssertions.assertJsonArraySize(complexRoot, "$", 3); // GH-90000

        // Nested numeric array
        String nestedNumeric = "{\"data\": [1,2,3,4]}";
        JsonAssertions.assertJsonArraySize(nestedNumeric, "$.data", 4); // GH-90000

        // Negative case: path exists but is an object, not an array
        String notArray = "{\"obj\": {\"k\":\"v\"}}";
        assertThrows(AssertionError.class, () -> JsonAssertions.assertJsonArraySize(notArray, "$.obj", 1)); // GH-90000
    }
}
