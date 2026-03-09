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
@DisplayName("Test Data Generator")
class TestDataGeneratorTest {

    @Test
    @DisplayName("should generate random string")
    void shouldGenerateRandomString() {
        // When
        String result = TestDataGenerator.randomString();
        
        // Then
        assertThat(result)
            .isNotBlank()
            .hasSize(36); // UUID length
    }

    @RepeatedTest(5)
    @DisplayName("should generate random email")
    void shouldGenerateRandomEmail() {
        // When
        String email = TestDataGenerator.randomEmail();
        
        // Then
        assertThat(email)
            .isNotBlank()
            .contains("@")
            .contains(".");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    @DisplayName("should generate random int within range")
    void shouldGenerateRandomInt(int max) {
        // When
        int result = TestDataGenerator.randomInt(0, max);
        
        // Then
        assertThat(result)
            .isGreaterThanOrEqualTo(0)
            .isLessThanOrEqualTo(max);
    }

    @Test
    @DisplayName("should generate random date")
    void shouldGenerateRandomDate() {
        // When
        LocalDate date = TestDataGenerator.randomDate();
        LocalDate now = LocalDate.now();
        LocalDate oneYearAgo = now.minusYears(1);
        
        // Then
        assertThat(date)
            .isAfterOrEqualTo(oneYearAgo)
            .isBeforeOrEqualTo(now);
    }

    @Test
    @DisplayName("should generate random name")
    void shouldGenerateRandomName() {
        // When
        String name = TestDataGenerator.randomName();
        
        // Then
        assertThat(name)
            .isNotBlank()
            .contains(" "); // At least first and last name
    }

    @Test
    @DisplayName("should generate random sentence")
    void shouldGenerateRandomSentence() {
        // When
        String sentence = TestDataGenerator.randomSentence();
        
        // Then
        assertThat(sentence)
            .isNotBlank()
            .endsWith(".");
    }
}
