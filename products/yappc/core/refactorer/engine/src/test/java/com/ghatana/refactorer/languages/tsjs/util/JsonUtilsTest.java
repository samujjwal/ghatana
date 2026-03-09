/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages.tsjs.util;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles json utils test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class JsonUtilsTest {

    static class TestData {
        private String name;
        private int value;

        // Default constructor for Jackson
        public TestData() {}

        public TestData(String name, int value) {
            this.name = name;
            this.value = value;
        }

        // Getters and setters for Jackson
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestData testData = (TestData) obj;
            return value == testData.value
                    && (name == null ? testData.name == null : name.equals(testData.name));
        }

        @Override
        public int hashCode() {
            int result = name != null ? name.hashCode() : 0;
            result = 31 * result + value;
            return result;
        }
    }

    @Test
    void testFromJson() throws JsonProcessingException {
        // Arrange
        String json = "{\"name\":\"test\",\"value\":42}";

        // Act
        TestData result = JsonUtils.fromJson(json, TestData.class);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("test");
        assertThat(result.getValue()).isEqualTo(42);
    }

    @Test
    void testToJson() throws JsonProcessingException {
        // Arrange
        TestData testData = new TestData("test", 42);
        String expectedJson = "{\"name\":\"test\",\"value\":42}";

        // Act
        String json = JsonUtils.toJson(testData);

        // Assert
        assertThat(json).isEqualTo(expectedJson);
    }

    @Test
    void testFromJsonWithUnknownProperties() throws JsonProcessingException {
        // Arrange - JSON with extra properties that aren't in the target class
        String json = "{\"name\":\"test\",\"value\":42,\"extra\":\"should be ignored\"}";

        // Act - Should not throw because FAIL_ON_UNKNOWN_PROPERTIES is false
        TestData result = JsonUtils.fromJson(json, TestData.class);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("test");
        assertThat(result.getValue()).isEqualTo(42);
    }

    @Test
    void testFromJsonWithInvalidJson() {
        // Arrange - Invalid JSON
        String invalidJson = "{invalid-json}";

        // Act & Assert
        assertThatThrownBy(() -> JsonUtils.fromJson(invalidJson, TestData.class))
                .isInstanceOf(JsonProcessingException.class);
    }
}
