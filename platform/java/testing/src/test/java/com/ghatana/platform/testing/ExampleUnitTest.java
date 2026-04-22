package com.ghatana.platform.testing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@UnitTest
@DisplayName("Example Unit Test [GH-90000]")
class ExampleUnitTest {

    @Test
    @DisplayName("should demonstrate basic assertions [GH-90000]")
    void basicAssertions() { // GH-90000
        // Given
        String actual = "test";

        // When & Then
        assertThat(actual) // GH-90000
            .isNotBlank() // GH-90000
            .hasSize(4) // GH-90000
            .isEqualToIgnoringCase("TEST [GH-90000]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"test1", "test2", "test3"}) // GH-90000
    @DisplayName("should pass with different test data [GH-90000]")
    void parameterizedTest(String input) { // GH-90000
        assertThat(input).startsWith("test [GH-90000]");
    }

    @Test
    @DisplayName("should demonstrate mocking [GH-90000]")
    void mockExample() { // GH-90000
        // Given
        var mockService = mock(Runnable.class); // GH-90000

        // When
        mockService.run(); // GH-90000

        // Then
        verify(mockService, times(1)).run(); // GH-90000
    }

    @Test
    @DisplayName("should demonstrate exception handling [GH-90000]")
    void exceptionHandling() { // GH-90000
        assertThatThrownBy(() -> { // GH-90000
            throw new IllegalArgumentException("Test exception [GH-90000]");
        })
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessage("Test exception [GH-90000]");
    }
}
