/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.yappc.services.intent;

/**
 * Input for an intent capture request.
 *
 * @doc.type class
 * @doc.purpose Value object carrying raw user intent text for capture operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IntentInput(String description) {
}
