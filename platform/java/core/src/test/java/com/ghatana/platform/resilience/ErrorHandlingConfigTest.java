/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.resilience;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
@DisplayName("ErrorHandlingConfig – Pipeline Error Strategy")
class ErrorHandlingConfigTest {

    @Test
    @Order(1)
    @DisplayName("1. failFast() factory creates correct config")
    void failFastFactory() {
        ErrorHandlingConfig config = ErrorHandlingConfig.failFast();
        assertThat(config.getStrategy()).isEqualTo(ErrorHandlingConfig.ErrorStrategy.FAIL_FAST);
        assertThat(config.isContinueOnError()).isFalse();
        assertThat(config.hasDlq()).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("2. retry() factory creates correct config with defaults")
    void retryFactory() {
        ErrorHandlingConfig config = ErrorHandlingConfig.retry(5);
        assertThat(config.getStrategy()).isEqualTo(ErrorHandlingConfig.ErrorStrategy.RETRY);
        assertThat(config.getMaxRetries()).isEqualTo(5);
        assertThat(config.getRetryDelay()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    @Order(3)
    @DisplayName("3. retry() with custom delay")
    void retryWithDelay() {
        ErrorHandlingConfig config = ErrorHandlingConfig.retry(3, Duration.ofMillis(500));
        assertThat(config.getMaxRetries()).isEqualTo(3);
        assertThat(config.getRetryDelay()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @Order(4)
    @DisplayName("4. deadLetter() factory creates correct config")
    void deadLetterFactory() {
        ErrorHandlingConfig config = ErrorHandlingConfig.deadLetter("fraud-dlq");
        assertThat(config.getStrategy()).isEqualTo(ErrorHandlingConfig.ErrorStrategy.DEAD_LETTER);
        assertThat(config.getDeadLetterQueueId()).isEqualTo("fraud-dlq");
        assertThat(config.hasDlq()).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("5. continueOnError() factory creates correct config")
    void continueOnErrorFactory() {
        ErrorHandlingConfig config = ErrorHandlingConfig.continueOnError();
        assertThat(config.getStrategy()).isEqualTo(ErrorHandlingConfig.ErrorStrategy.CONTINUE);
        assertThat(config.isContinueOnError()).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("6. Builder with custom timeout")
    void builderWithTimeout() {
        ErrorHandlingConfig config = ErrorHandlingConfig.builder()
                .strategy(ErrorHandlingConfig.ErrorStrategy.RETRY)
                .maxRetries(2)
                .timeout(Duration.ofSeconds(30))
                .build();

        assertThat(config.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.getMaxRetries()).isEqualTo(2);
    }

    @Test
    @Order(7)
    @DisplayName("7. hasDlq() returns false for null/empty DLQ ID")
    void hasDlqEmpty() {
        assertThat(ErrorHandlingConfig.failFast().hasDlq()).isFalse();
        assertThat(ErrorHandlingConfig.retry(1).hasDlq()).isFalse();

        ErrorHandlingConfig withEmptyDlq = ErrorHandlingConfig.builder()
                .deadLetterQueueId("")
                .build();
        assertThat(withEmptyDlq.hasDlq()).isFalse();
    }

    @Test
    @Order(8)
    @DisplayName("8. toString() includes key fields")
    void toStringFormat() {
        ErrorHandlingConfig config = ErrorHandlingConfig.deadLetter("my-dlq");
        String str = config.toString();
        assertThat(str)
                .contains("DEAD_LETTER")
                .contains("my-dlq");
    }
}
