package com.ghatana.platform.testing.assertions;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JsonAssertionsTest {

    private static final String SIMPLE_JSON = "{\"name\":\"test\",\"value\":42}";
    private static final String NESTED_JSON = "{\"user\":{\"id\":1,\"name\":\"test\"},\"roles\":[\"admin\",\"user\"]}";
    private static final String ARRAY_JSON = "[{\"id\":1,\"name\":\"one\"},{\"id\":2,\"name\":\"two\"}]";

    @Test
    void assertJsonEquals_shouldPassForEqualJson() {
        String expected = "{\"name\":\"test\"}";
        String actual = "{\"name\": \"test\"}"; // Different formatting, same content
        JsonAssertions.assertJsonEquals(expected, actual);
    }

    @Test
    void assertJsonEquals_shouldFailForDifferentJson() {
        String expected = "{\"name\":\"test1\"}";
        String actual = "{\"name\":\"test2\"}";
        assertThrows(AssertionError.class, 
            () -> JsonAssertions.assertJsonEquals(expected, actual));
    }

    @Test
    void assertJsonContains_shouldPassWhenContainsAllFields() {
        String expected = "{\"name\":\"test\"}";
        String actual = "{\"name\":\"test\",\"value\":42}";
        JsonAssertions.assertJsonContains(expected, actual);
    }

    @Test
    void assertJsonPath_shouldReturnCorrectValue() {
        JsonAssertions.assertJsonPath(SIMPLE_JSON, "$.name", "test");
        JsonAssertions.assertJsonPath(SIMPLE_JSON, "$.value", 42);
    }

    @Test
    void assertJsonPath_shouldHandleNestedStructures() {
        JsonAssertions.assertJsonPath(NESTED_JSON, "$.user.id", 1);
        JsonAssertions.assertJsonPath(NESTED_JSON, "$.user.name", "test");
    }

    @Test
    void assertJsonArraySize_shouldCheckArraySize() {
        JsonAssertions.assertJsonArraySize(NESTED_JSON, "$.roles", 2);
        JsonAssertions.assertJsonArraySize(ARRAY_JSON, "$", 2);
    }

    @Test
    void assertHasJsonPath_shouldPassWhenPathExists() {
        JsonAssertions.assertHasJsonPath(SIMPLE_JSON, "$.name");
        JsonAssertions.assertHasJsonPath(NESTED_JSON, "$.user.id");
        JsonAssertions.assertHasJsonPath(NESTED_JSON, "$.roles[0]");
    }

    @Test
    void assertDoesNotHaveJsonPath_shouldPassWhenPathDoesNotExist() {
        JsonAssertions.assertDoesNotHaveJsonPath(SIMPLE_JSON, "$.nonexistent");
        JsonAssertions.assertDoesNotHaveJsonPath(NESTED_JSON, "$.user.nonexistent");
    }

    @Test
    void assertJsonPath_shouldFailForIncorrectValue() {
        assertThrows(AssertionError.class, 
            () -> JsonAssertions.assertJsonPath(SIMPLE_JSON, "$.name", "wrong"));
    }

    @Test
    void assertJsonArraySize_shouldFailForWrongSize() {
        assertThrows(AssertionError.class, 
            () -> JsonAssertions.assertJsonArraySize(NESTED_JSON, "$.roles", 3));
    }

    @Test
    void assertHasJsonPath_shouldFailForMissingPath() {
        assertThrows(AssertionError.class, 
            () -> JsonAssertions.assertHasJsonPath(SIMPLE_JSON, "$.nonexistent"));
    }

    @Test
    void assertDoesNotHaveJsonPath_shouldFailForExistingPath() {
        assertThrows(AssertionError.class, 
            () -> JsonAssertions.assertDoesNotHaveJsonPath(SIMPLE_JSON, "$.name"));
    }

    // New explicit test covering root-level arrays and nested arrays, including empty arrays and a negative case
    @Test
    void assertJsonArraySize_explicitRootAndNestedCases() {
        // Root-level empty array
        String emptyRoot = "[]";
        JsonAssertions.assertJsonArraySize(emptyRoot, "$", 0);

        // Nested empty array
        String nestedEmpty = "{\"items\": []}";
        JsonAssertions.assertJsonArraySize(nestedEmpty, "$.items", 0);

        // Root-level complex array
        String complexRoot = "[{\"a\":1},{\"b\":2},{\"c\":3}]";
        JsonAssertions.assertJsonArraySize(complexRoot, "$", 3);

        // Nested numeric array
        String nestedNumeric = "{\"data\": [1,2,3,4]}";
        JsonAssertions.assertJsonArraySize(nestedNumeric, "$.data", 4);

        // Negative case: path exists but is an object, not an array
        String notArray = "{\"obj\": {\"k\":\"v\"}}";
        assertThrows(AssertionError.class, () -> JsonAssertions.assertJsonArraySize(notArray, "$.obj", 1));
    }
}
