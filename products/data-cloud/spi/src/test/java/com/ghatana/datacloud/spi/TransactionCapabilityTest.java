package com.ghatana.datacloud.spi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TransactionCapability}.
 */
@DisplayName("TransactionCapability")
class TransactionCapabilityTest {

    @Test
    @DisplayName("IsolationLevel enum contains all expected levels")
    void isolationLevelEnum_containsAllExpectedLevels() {
        TransactionCapability.IsolationLevel[] levels = TransactionCapability.IsolationLevel.values();
        assertThat(levels).contains(
                TransactionCapability.IsolationLevel.READ_COMMITTED,
                TransactionCapability.IsolationLevel.READ_UNCOMMITTED,
                TransactionCapability.IsolationLevel.REPEATABLE_READ,
                TransactionCapability.IsolationLevel.SERIALIZABLE
        );
    }

    @Test
    @DisplayName("IsolationLevel enum values are correct")
    void isolationLevelEnum_valuesAreCorrect() {
        assertThat(TransactionCapability.IsolationLevel.READ_COMMITTED.name()).isEqualTo("READ_COMMITTED");
        assertThat(TransactionCapability.IsolationLevel.READ_UNCOMMITTED.name()).isEqualTo("READ_UNCOMMITTED");
        assertThat(TransactionCapability.IsolationLevel.REPEATABLE_READ.name()).isEqualTo("REPEATABLE_READ");
        assertThat(TransactionCapability.IsolationLevel.SERIALIZABLE.name()).isEqualTo("SERIALIZABLE");
    }

    @Test
    @DisplayName("TransactionOptions defaults returns default options")
    void transactionOptions_defaults_returnsDefaultOptions() {
        TransactionCapability.TransactionOptions options = TransactionCapability.TransactionOptions.defaults();

        assertThat(options.isolationLevel()).isEqualTo(TransactionCapability.IsolationLevel.READ_COMMITTED);
        assertThat(options.readOnly()).isFalse();
        assertThat(options.timeout()).isEqualTo(java.time.Duration.ofSeconds(30));
        assertThat(options.properties()).isNotNull();
    }

    @Test
    @DisplayName("TransactionOptions builder creates options")
    void transactionOptionsBuilder_createsOptions() {
        TransactionCapability.TransactionOptions options = TransactionCapability.TransactionOptions.builder()
                .isolationLevel(TransactionCapability.IsolationLevel.SERIALIZABLE)
                .readOnly()
                .timeout(java.time.Duration.ofMinutes(5))
                .build();

        assertThat(options.isolationLevel()).isEqualTo(TransactionCapability.IsolationLevel.SERIALIZABLE);
        assertThat(options.readOnly()).isTrue();
        assertThat(options.timeout()).isEqualTo(java.time.Duration.ofMinutes(5));
    }
}
