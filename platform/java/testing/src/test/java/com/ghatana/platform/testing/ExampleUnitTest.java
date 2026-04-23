package com.ghatana.platform.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@UnitTest
@DisplayName("Example Unit Test")
class ExampleUnitTest {

    @Test
    @DisplayName("should demonstrate basic assertions")
    void basicAssertions() { // GH-90000
        // Given
        String actual = "test";

        // When & Then
        assertThat(actual) // GH-90000
            .isNotBlank() // GH-90000
            .hasSize(4) // GH-90000
            .isEqualToIgnoringCase("TEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"test1", "test2", "test3"}) // GH-90000
    @DisplayName("should pass with different test data")
    void parameterizedTest(String input) { // GH-90000
        assertThat(input).startsWith("test");
    }

    @Test
    @DisplayName("should demonstrate mocking")
    void mockExample() { // GH-90000
        // Given
        var mockService = mock(Runnable.class); // GH-90000

        // When
        mockService.run(); // GH-90000

        // Then
        verify(mockService, times(1)).run(); // GH-90000
    }

    @Test
    @DisplayName("should demonstrate exception handling")
    void exceptionHandling() { // GH-90000
        assertThatThrownBy(() -> { // GH-90000
            throw new IllegalArgumentException("Test exception");
        })
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessage("Test exception");
    }
}
