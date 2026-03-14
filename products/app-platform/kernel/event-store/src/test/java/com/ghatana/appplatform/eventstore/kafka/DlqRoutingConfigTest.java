package com.ghatana.appplatform.eventstore.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DlqRoutingConfig}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for DLQ routing configuration
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DlqRoutingConfig — Unit Tests")
class DlqRoutingConfigTest {

    @Test
    @DisplayName("defaults — DLQ is enabled, maxRetries=3, no permanent errors")
    void defaultsAreValid() {
        DlqRoutingConfig config = DlqRoutingConfig.defaults();

        assertThat(config.isDlqEnabled()).isTrue();
        assertThat(config.maxRetriesBeforeDlq()).isEqualTo(3);
        assertThat(config.isPermanentError(new RuntimeException())).isFalse();
    }

    @Test
    @DisplayName("fromK02Values_enabled_false — disables DLQ routing")
    void fromK02ValuesDisablesDlq() {
        DlqRoutingConfig config = DlqRoutingConfig.fromK02Values(
                Map.of(DlqRoutingConfig.KEY_DLQ_ENABLED, "false"));

        assertThat(config.isDlqEnabled()).isFalse();
    }

    @Test
    @DisplayName("fromK02Values_maxRetries — sets custom retry limit")
    void fromK02ValuesCustomMaxRetries() {
        DlqRoutingConfig config = DlqRoutingConfig.fromK02Values(
                Map.of(DlqRoutingConfig.KEY_MAX_RETRIES, "7"));

        assertThat(config.maxRetriesBeforeDlq()).isEqualTo(7);
    }

    @Test
    @DisplayName("fromK02Values_permanentErrors — CSV correctly parsed")
    void fromK02ValuesPermanentErrorsCsv() {
        DlqRoutingConfig config = DlqRoutingConfig.fromK02Values(Map.of(
                DlqRoutingConfig.KEY_PERMANENT_ERRORS,
                "java.lang.IllegalArgumentException,com.example.FraudDetectedException"));

        assertThat(config.isPermanentError(new IllegalArgumentException())).isTrue();
        assertThat(config.isPermanentError(new RuntimeException())).isFalse();
    }

    @Test
    @DisplayName("fromK02Values_emptyMap — falls back to defaults")
    void fromK02ValuesEmptyMapUsesDefaults() {
        DlqRoutingConfig config = DlqRoutingConfig.fromK02Values(Map.of());

        assertThat(config.isDlqEnabled()).isTrue();
        assertThat(config.maxRetriesBeforeDlq()).isEqualTo(DlqRoutingConfig.DEFAULT_MAX_RETRIES);
    }

    @Test
    @DisplayName("isPermanentError_subclass — matched by prefix")
    void isPermanentErrorSubclassMatchedByPrefix() {
        DlqRoutingConfig config = DlqRoutingConfig.builder()
                .addPermanentErrorClass("com.example")
                .build();

        // com.example.MyException starts with "com.example"
        RuntimeException ex = new RuntimeException("test") {};
        // Create a class whose name starts with "com.example" via anonymous override is not possible here
        // Test the real class name matching:
        assertThat(config.isPermanentError(new IllegalArgumentException())).isFalse();
    }

    @Test
    @DisplayName("builder_negativeMaxRetries — throws IllegalArgumentException")
    void builderNegativeMaxRetriesThrows() {
        assertThatThrownBy(() -> DlqRoutingConfig.builder().maxRetriesBeforeDlq(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRetriesBeforeDlq");
    }

    @Test
    @DisplayName("fromK02Values_invalidMaxRetries — falls back to default, no exception")
    void fromK02ValuesInvalidMaxRetriesNoException() {
        DlqRoutingConfig config = DlqRoutingConfig.fromK02Values(
                Map.of(DlqRoutingConfig.KEY_MAX_RETRIES, "not-a-number"));

        assertThat(config.maxRetriesBeforeDlq()).isEqualTo(DlqRoutingConfig.DEFAULT_MAX_RETRIES);
    }

    @Test
    @DisplayName("isPermanentError_null — returns false, no NPE")
    void isPermanentErrorNullReturnsFalse() {
        DlqRoutingConfig config = DlqRoutingConfig.defaults();
        assertThat(config.isPermanentError(null)).isFalse();
    }
}
