/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.languages.tsjs.util;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ghatana.platform.core.util.JsonUtils;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles json utils test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class JsonUtilsTest {

    static class JsonTestData {
        private String name;
        private int value;

        // Default constructor for Jackson
        public JsonTestData() {} // GH-90000

        public JsonTestData(String name, int value) { // GH-90000
            this.name = name;
            this.value = value;
        }

        // Getters and setters for Jackson
        public String getName() { // GH-90000
            return name;
        }

        public void setName(String name) { // GH-90000
            this.name = name;
        }

        public int getValue() { // GH-90000
            return value;
        }

        public void setValue(int value) { // GH-90000
            this.value = value;
        }

        @Override
        public boolean equals(Object obj) { // GH-90000
            if (this == obj) return true; // GH-90000
            if (obj == null || getClass() != obj.getClass()) return false; // GH-90000
            JsonTestData testData = (JsonTestData) obj; // GH-90000
            return value == testData.value
                    && (name == null ? testData.name == null : name.equals(testData.name)); // GH-90000
        }

        @Override
        public int hashCode() { // GH-90000
            int result = name != null ? name.hashCode() : 0; // GH-90000
            result = 31 * result + value;
            return result;
        }
    }

    @Test
    void testFromJson() throws JsonProcessingException { // GH-90000
        // Arrange
        String json = "{\"name\":\"test\",\"value\":42}";

        // Act
        JsonTestData result = JsonUtils.fromJson(json, JsonTestData.class); // GH-90000

        // Assert
        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getName()).isEqualTo("test");
        assertThat(result.getValue()).isEqualTo(42); // GH-90000
    }

    @Test
    void testToJson() throws JsonProcessingException { // GH-90000
        // Arrange
        JsonTestData testData = new JsonTestData("test", 42); // GH-90000
        String expectedJson = "{\"name\":\"test\",\"value\":42}";

        // Act
        String json = JsonUtils.toJson(testData); // GH-90000

        // Assert
        assertThat(json).isEqualTo(expectedJson); // GH-90000
    }

    @Test
    void testFromJsonWithUnknownProperties() throws JsonProcessingException { // GH-90000
        // Arrange - JSON with extra properties that aren't in the target class
        String json = "{\"name\":\"test\",\"value\":42,\"extra\":\"should be ignored\"}";

        // Act - Should not throw because FAIL_ON_UNKNOWN_PROPERTIES is false
        JsonTestData result = JsonUtils.fromJson(json, JsonTestData.class); // GH-90000

        // Assert
        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getName()).isEqualTo("test");
        assertThat(result.getValue()).isEqualTo(42); // GH-90000
    }

    @Test
    void testFromJsonWithInvalidJson() { // GH-90000
        // Arrange - Invalid JSON
        String invalidJson = "{invalid-json}";

        // Act & Assert
        assertThatThrownBy(() -> JsonUtils.fromJson(invalidJson, JsonTestData.class)) // GH-90000
                .isInstanceOf(JsonProcessingException.class); // GH-90000
    }
}
