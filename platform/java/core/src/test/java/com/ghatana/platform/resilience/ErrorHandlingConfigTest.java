/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class) // GH-90000
@DisplayName("ErrorHandlingConfig – Pipeline Error Strategy")
class ErrorHandlingConfigTest {

    @Test
    @Order(1) // GH-90000
    @DisplayName("1. failFast() factory creates correct config")
    void failFastFactory() { // GH-90000
        ErrorHandlingConfig config = ErrorHandlingConfig.failFast(); // GH-90000
        assertThat(config.getStrategy()).isEqualTo(ErrorHandlingConfig.ErrorStrategy.FAIL_FAST); // GH-90000
        assertThat(config.isContinueOnError()).isFalse(); // GH-90000
        assertThat(config.hasDlq()).isFalse(); // GH-90000
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("2. retry() factory creates correct config with defaults")
    void retryFactory() { // GH-90000
        ErrorHandlingConfig config = ErrorHandlingConfig.retry(5); // GH-90000
        assertThat(config.getStrategy()).isEqualTo(ErrorHandlingConfig.ErrorStrategy.RETRY); // GH-90000
        assertThat(config.getMaxRetries()).isEqualTo(5); // GH-90000
        assertThat(config.getRetryDelay()).isEqualTo(Duration.ofSeconds(1)); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("3. retry() with custom delay")
    void retryWithDelay() { // GH-90000
        ErrorHandlingConfig config = ErrorHandlingConfig.retry(3, Duration.ofMillis(500)); // GH-90000
        assertThat(config.getMaxRetries()).isEqualTo(3); // GH-90000
        assertThat(config.getRetryDelay()).isEqualTo(Duration.ofMillis(500)); // GH-90000
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("4. deadLetter() factory creates correct config")
    void deadLetterFactory() { // GH-90000
        ErrorHandlingConfig config = ErrorHandlingConfig.deadLetter("fraud-dlq");
        assertThat(config.getStrategy()).isEqualTo(ErrorHandlingConfig.ErrorStrategy.DEAD_LETTER); // GH-90000
        assertThat(config.getDeadLetterQueueId()).isEqualTo("fraud-dlq");
        assertThat(config.hasDlq()).isTrue(); // GH-90000
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("5. continueOnError() factory creates correct config")
    void continueOnErrorFactory() { // GH-90000
        ErrorHandlingConfig config = ErrorHandlingConfig.continueOnError(); // GH-90000
        assertThat(config.getStrategy()).isEqualTo(ErrorHandlingConfig.ErrorStrategy.CONTINUE); // GH-90000
        assertThat(config.isContinueOnError()).isTrue(); // GH-90000
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("6. Builder with custom timeout")
    void builderWithTimeout() { // GH-90000
        ErrorHandlingConfig config = ErrorHandlingConfig.builder() // GH-90000
                .strategy(ErrorHandlingConfig.ErrorStrategy.RETRY) // GH-90000
                .maxRetries(2) // GH-90000
                .timeout(Duration.ofSeconds(30)) // GH-90000
                .build(); // GH-90000

        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(30)); // GH-90000
        assertThat(config.getMaxRetries()).isEqualTo(2); // GH-90000
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("7. hasDlq() returns false for null/empty DLQ ID")
    void hasDlqEmpty() { // GH-90000
        assertThat(ErrorHandlingConfig.failFast().hasDlq()).isFalse(); // GH-90000
        assertThat(ErrorHandlingConfig.retry(1).hasDlq()).isFalse(); // GH-90000

        ErrorHandlingConfig withEmptyDlq = ErrorHandlingConfig.builder() // GH-90000
                .deadLetterQueueId("")
                .build(); // GH-90000
        assertThat(withEmptyDlq.hasDlq()).isFalse(); // GH-90000
    }

    @Test
    @Order(8) // GH-90000
    @DisplayName("8. toString() includes key fields")
    void toStringFormat() { // GH-90000
        ErrorHandlingConfig config = ErrorHandlingConfig.deadLetter("my-dlq");
        String str = config.toString(); // GH-90000
        assertThat(str) // GH-90000
                .contains("DEAD_LETTER")
                .contains("my-dlq");
    }
}
