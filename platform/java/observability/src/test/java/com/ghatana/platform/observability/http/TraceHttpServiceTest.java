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
@DisplayName("TraceHttpService Tests")
class TraceHttpServiceTest {

    private MockTraceStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MockTraceStorage();
    }

    @Test
    @DisplayName("Should create service with valid storage")
    void shouldCreateServiceWithValidStorage() {
        // When
        TraceHttpService service = new TraceHttpService(storage);

        // Then
        assertThat(service).isNotNull();
        assertThat(service.getServiceName()).isEqualTo("trace-http-service");
    }

    @Test
    @DisplayName("Should throw exception when storage is null")
    void shouldThrowExceptionWhenStorageIsNull() {
        // When/Then
        assertThatThrownBy(() -> new TraceHttpService(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TraceStorage cannot be null");
    }

    @Test
    @DisplayName("Should return correct service name")
    void shouldReturnCorrectServiceName() {
        // Given
        TraceHttpService service = new TraceHttpService(storage);

        // When
        String serviceName = service.getServiceName();

        // Then
        assertThat(serviceName).isEqualTo("trace-http-service");
    }
}
