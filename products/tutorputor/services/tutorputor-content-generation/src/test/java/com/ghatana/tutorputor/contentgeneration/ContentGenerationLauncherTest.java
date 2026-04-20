package com.ghatana.tutorputor.contentgeneration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type test
 * @doc.purpose Verify launcher configuration parsing and fail-fast validation
 * @doc.layer application
 * @doc.pattern UnitTest
 */
class ContentGenerationLauncherTest {

    @Test
    void shouldUseDefaultPortWhenEnvValueMissing() {
        assertThat(ContentGenerationLauncher.resolvePort(null, 50051, "GRPC_PORT"))
            .isEqualTo(50051);
    }

    @Test
    void shouldParseConfiguredPort() {
        assertThat(ContentGenerationLauncher.resolvePort("8081", 50051, "HEALTH_PORT"))
            .isEqualTo(8081);
    }

    @Test
    void shouldRejectInvalidPortValue() {
        assertThatThrownBy(() -> ContentGenerationLauncher.resolvePort("invalid", 50051, "GRPC_PORT"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GRPC_PORT");
    }

    @Test
    void shouldRejectOutOfRangePort() {
        assertThatThrownBy(() -> ContentGenerationLauncher.resolvePort("70000", 50051, "GRPC_PORT"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("GRPC_PORT");
    }
}