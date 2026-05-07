/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for DataCloudTransportStartupException
 * @doc.layer product
 * @doc.pattern Test
 */
class DataCloudTransportStartupExceptionTest {

    @Test
    void shouldCreateExceptionWithMessageAndCause() {
        Throwable cause = new RuntimeException("inner cause");
        DataCloudTransportStartupException exception = new DataCloudTransportStartupException("test message", cause);

        assertThat(exception.getMessage()).isEqualTo("test message");
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    void shouldCreateExceptionWithNullCause() {
        DataCloudTransportStartupException exception = new DataCloudTransportStartupException("test message", null);

        assertThat(exception.getMessage()).isEqualTo("test message");
        assertThat(exception.getCause()).isNull();
    }
}
