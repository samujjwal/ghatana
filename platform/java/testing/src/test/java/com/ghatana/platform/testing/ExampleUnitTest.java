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
    void basicAssertions() {
        // Given
        String actual = "test";
        
        // When & Then
        assertThat(actual)
            .isNotBlank()
            .hasSize(4)
            .isEqualToIgnoringCase("TEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"test1", "test2", "test3"})
    @DisplayName("should pass with different test data")
    void parameterizedTest(String input) {
        assertThat(input).startsWith("test");
    }

    @Test
    @DisplayName("should demonstrate mocking")
    void mockExample() {
        // Given
        var mockService = mock(Runnable.class);
        
        // When
        mockService.run();
        
        // Then
        verify(mockService, times(1)).run();
    }

    @Test
    @DisplayName("should demonstrate exception handling")
    void exceptionHandling() {
        assertThatThrownBy(() -> {
            throw new IllegalArgumentException("Test exception");
        })
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Test exception");
    }
}
