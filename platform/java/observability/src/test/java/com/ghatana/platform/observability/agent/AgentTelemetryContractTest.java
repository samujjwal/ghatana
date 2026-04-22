/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.platform.observability.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AgentTelemetryContract} constants class.
 *
 * <p>Validates that all constants are non-null, non-empty, unique, and that
 * no raw sensitive content (prompts, payloads) is defined as an attribute key. // GH-90000
 */
@DisplayName("AgentTelemetryContract [GH-90000]")
class AgentTelemetryContractTest {

    @Test
    @DisplayName("VERSION is non-null and non-empty [GH-90000]")
    void versionIsNonEmpty() { // GH-90000
        assertThat(AgentTelemetryContract.VERSION).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("SPAN_PREFIX is non-null and non-empty [GH-90000]")
    void spanPrefixIsNonEmpty() { // GH-90000
        assertThat(AgentTelemetryContract.SPAN_PREFIX).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("all span name constants start with SPAN_PREFIX [GH-90000]")
    void allSpanNamesStartWithPrefix() throws Exception { // GH-90000
        String prefix = AgentTelemetryContract.SPAN_PREFIX;
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { // GH-90000
            if (field.getName().startsWith("SPAN_ [GH-90000]") && !field.getName().equals("SPAN_PREFIX [GH-90000]")) {
                String value = (String) field.get(null); // GH-90000
                assertThat(value) // GH-90000
                        .as("Span name constant %s", field.getName()) // GH-90000
                        .startsWith(prefix); // GH-90000
            }
        }
    }

    @Test
    @DisplayName("all attribute key constants start with SPAN_PREFIX [GH-90000]")
    void allAttributeKeysStartWithPrefix() throws Exception { // GH-90000
        String prefix = AgentTelemetryContract.SPAN_PREFIX;
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { // GH-90000
            if (field.getName().startsWith("ATTR_ [GH-90000]")) {
                String value = (String) field.get(null); // GH-90000
                assertThat(value) // GH-90000
                        .as("Attribute key constant %s", field.getName()) // GH-90000
                        .startsWith(prefix); // GH-90000
            }
        }
    }

    @Test
    @DisplayName("all PUBLIC_STATIC_FINAL String constants are non-blank [GH-90000]")
    void allConstantsAreNonBlank() throws Exception { // GH-90000
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { // GH-90000
            if (field.getType() == String.class) { // GH-90000
                String value = (String) field.get(null); // GH-90000
                assertThat(value) // GH-90000
                        .as("Constant %s must not be blank", field.getName()) // GH-90000
                        .isNotBlank(); // GH-90000
            }
        }
    }

    @Test
    @DisplayName("no duplicate constant values exist [GH-90000]")
    void noDuplicateValues() throws Exception { // GH-90000
        Map<String, String> values = new HashMap<>(); // GH-90000
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { // GH-90000
            if (field.getType() == String.class && !field.getName().equals("VERSION [GH-90000]")) {
                String value = (String) field.get(null); // GH-90000
                assertThat(values) // GH-90000
                        .as("Value '%s' from field %s is duplicated by field %s", // GH-90000
                                value, field.getName(), values.get(value)) // GH-90000
                        .doesNotContainKey(value); // GH-90000
                values.put(value, field.getName()); // GH-90000
            }
        }
    }

    @Test
    @DisplayName("sensitive raw content fields are NOT defined in the contract [GH-90000]")
    void noRawPayloadAttributesAreDefined() throws Exception { // GH-90000
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { // GH-90000
            if (field.getType() == String.class) { // GH-90000
                String value = ((String) field.get(null)).toLowerCase(); // GH-90000
                assertThat(value) // GH-90000
                        .as("Field %s must not define a raw payload key", field.getName()) // GH-90000
                        .doesNotContain("prompt [GH-90000]")
                        .doesNotContain("payload [GH-90000]")
                        .doesNotContain("embedding [GH-90000]")
                        .doesNotContain("completion [GH-90000]")
                        .doesNotContain("fragment [GH-90000]");
            }
        }
    }

    @Test
    @DisplayName("11 span name constants are defined [GH-90000]")
    void elevenSpanPhasesAreDefined() { // GH-90000
        long count = 0;
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { // GH-90000
            if (field.getName().startsWith("SPAN_ [GH-90000]") && !field.getName().equals("SPAN_PREFIX [GH-90000]")) {
                count++;
            }
        }
        assertThat(count).isEqualTo(11); // GH-90000
    }
}
