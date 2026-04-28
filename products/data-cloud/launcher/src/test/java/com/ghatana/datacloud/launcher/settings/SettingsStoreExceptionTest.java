/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Tests for SettingsStoreException
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SettingsStoreException")
class SettingsStoreExceptionTest {

    @Test
    @DisplayName("constructor with message and cause")
    void constructor_setsMessageAndCause() {
        Throwable cause = new RuntimeException("inner error");
        SettingsStoreException ex = new SettingsStoreException("failed to save", cause);
        
        assertThat(ex.getMessage()).isEqualTo("failed to save");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    @DisplayName("constructor with null cause")
    void constructor_withNullCause() {
        SettingsStoreException ex = new SettingsStoreException("failed to save", null);
        
        assertThat(ex.getMessage()).isEqualTo("failed to save");
        assertThat(ex.getCause()).isNull();
    }
}
