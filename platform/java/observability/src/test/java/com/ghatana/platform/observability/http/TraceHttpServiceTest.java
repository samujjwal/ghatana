package com.ghatana.platform.observability.http;

import com.ghatana.platform.observability.trace.MockTraceStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TraceHttpService}.
 */
@DisplayName("TraceHttpService Tests [GH-90000]")
class TraceHttpServiceTest {

    private MockTraceStorage storage;

    @BeforeEach
    void setUp() { // GH-90000
        storage = new MockTraceStorage(); // GH-90000
    }

    @Test
    @DisplayName("Should create service with valid storage [GH-90000]")
    void shouldCreateServiceWithValidStorage() { // GH-90000
        // When
        TraceHttpService service = new TraceHttpService(storage); // GH-90000

        // Then
        assertThat(service).isNotNull(); // GH-90000
        assertThat(service.getServiceName()).isEqualTo("trace-http-service [GH-90000]");
    }

    @Test
    @DisplayName("Should throw exception when storage is null [GH-90000]")
    void shouldThrowExceptionWhenStorageIsNull() { // GH-90000
        // When/Then
        assertThatThrownBy(() -> new TraceHttpService(null)) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("TraceStorage cannot be null [GH-90000]");
    }

    @Test
    @DisplayName("Should return correct service name [GH-90000]")
    void shouldReturnCorrectServiceName() { // GH-90000
        // Given
        TraceHttpService service = new TraceHttpService(storage); // GH-90000

        // When
        String serviceName = service.getServiceName(); // GH-90000

        // Then
        assertThat(serviceName).isEqualTo("trace-http-service [GH-90000]");
    }
}
