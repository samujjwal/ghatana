/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.settings;

/**
 * Runtime exception for {@link SettingsStore} failures.
 *
 * @doc.type class
 * @doc.purpose Signal unrecoverable storage errors in settings persistence layer
 * @doc.layer product
 * @doc.pattern Exception
 */
public class SettingsStoreException extends RuntimeException {

    public SettingsStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
