package com.ghatana.platform.testing.data;

import com.ghatana.platform.testing.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@UnitTest
@DisplayName("Test Data Generator [GH-90000]")
class TestDataGeneratorTest {

    @Test
    @DisplayName("should generate random string [GH-90000]")
    void shouldGenerateRandomString() { // GH-90000
        // When
        String result = TestDataGenerator.randomString(); // GH-90000

        // Then
        assertThat(result) // GH-90000
            .isNotBlank() // GH-90000
            .hasSize(36); // UUID length // GH-90000
    }

    @RepeatedTest(5) // GH-90000
    @DisplayName("should generate random email [GH-90000]")
    void shouldGenerateRandomEmail() { // GH-90000
        // When
        String email = TestDataGenerator.randomEmail(); // GH-90000

        // Then
        assertThat(email) // GH-90000
            .isNotBlank() // GH-90000
            .contains("@ [GH-90000]")
            .contains(". [GH-90000]");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100}) // GH-90000
    @DisplayName("should generate random int within range [GH-90000]")
    void shouldGenerateRandomInt(int max) { // GH-90000
        // When
        int result = TestDataGenerator.randomInt(0, max); // GH-90000

        // Then
        assertThat(result) // GH-90000
            .isGreaterThanOrEqualTo(0) // GH-90000
            .isLessThanOrEqualTo(max); // GH-90000
    }

    @Test
    @DisplayName("should generate random date [GH-90000]")
    void shouldGenerateRandomDate() { // GH-90000
        // When
        LocalDate date = TestDataGenerator.randomDate(); // GH-90000
        LocalDate now = LocalDate.now(); // GH-90000
        LocalDate oneYearAgo = now.minusYears(1); // GH-90000

        // Then
        assertThat(date) // GH-90000
            .isAfterOrEqualTo(oneYearAgo) // GH-90000
            .isBeforeOrEqualTo(now); // GH-90000
    }

    @Test
    @DisplayName("should generate random name [GH-90000]")
    void shouldGenerateRandomName() { // GH-90000
        // When
        String name = TestDataGenerator.randomName(); // GH-90000

        // Then
        assertThat(name) // GH-90000
            .isNotBlank() // GH-90000
            .contains("  [GH-90000]"); // At least first and last name
    }

    @Test
    @DisplayName("should generate random sentence [GH-90000]")
    void shouldGenerateRandomSentence() { // GH-90000
        // When
        String sentence = TestDataGenerator.randomSentence(); // GH-90000

        // Then
        assertThat(sentence) // GH-90000
            .isNotBlank() // GH-90000
            .endsWith(". [GH-90000]");
    }
}
