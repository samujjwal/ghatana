/*
 * Copyright (c) 2026 Ghatana
 */
package com.ghatana.yappc.services.intent;

import java.util.Map;

/**
 * Structured specification derived from a captured intent.
 *
 * @doc.type class
 * @doc.purpose Value object representing a structured intent specification
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record IntentSpec(String description, String intentType, Map<String, String> metadata) {
}
