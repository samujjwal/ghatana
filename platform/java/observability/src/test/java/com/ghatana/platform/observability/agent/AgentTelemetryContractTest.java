/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
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
 * no raw sensitive content (prompts, payloads) is defined as an attribute key. 
 */
@DisplayName("AgentTelemetryContract")
class AgentTelemetryContractTest {

    @Test
    @DisplayName("VERSION is non-null and non-empty")
    void versionIsNonEmpty() { 
        assertThat(AgentTelemetryContract.VERSION).isNotBlank(); 
    }

    @Test
    @DisplayName("SPAN_PREFIX is non-null and non-empty")
    void spanPrefixIsNonEmpty() { 
        assertThat(AgentTelemetryContract.SPAN_PREFIX).isNotBlank(); 
    }

    @Test
    @DisplayName("all span name constants start with SPAN_PREFIX")
    void allSpanNamesStartWithPrefix() throws Exception { 
        String prefix = AgentTelemetryContract.SPAN_PREFIX;
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { 
            if (field.getName().startsWith("SPAN_") && !field.getName().equals("SPAN_PREFIX")) {
                String value = (String) field.get(null); 
                assertThat(value) 
                        .as("Span name constant %s", field.getName()) 
                        .startsWith(prefix); 
            }
        }
    }

    @Test
    @DisplayName("all attribute key constants start with SPAN_PREFIX")
    void allAttributeKeysStartWithPrefix() throws Exception { 
        String prefix = AgentTelemetryContract.SPAN_PREFIX;
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { 
            if (field.getName().startsWith("ATTR_")) {
                String value = (String) field.get(null); 
                assertThat(value) 
                        .as("Attribute key constant %s", field.getName()) 
                        .startsWith(prefix); 
            }
        }
    }

    @Test
    @DisplayName("all PUBLIC_STATIC_FINAL String constants are non-blank")
    void allConstantsAreNonBlank() throws Exception { 
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { 
            if (field.getType() == String.class) { 
                String value = (String) field.get(null); 
                assertThat(value) 
                        .as("Constant %s must not be blank", field.getName()) 
                        .isNotBlank(); 
            }
        }
    }

    @Test
    @DisplayName("no duplicate constant values exist")
    void noDuplicateValues() throws Exception { 
        Map<String, String> values = new HashMap<>(); 
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { 
            if (field.getType() == String.class && !field.getName().equals("VERSION")) {
                String value = (String) field.get(null); 
                assertThat(values) 
                        .as("Value '%s' from field %s is duplicated by field %s", 
                                value, field.getName(), values.get(value)) 
                        .doesNotContainKey(value); 
                values.put(value, field.getName()); 
            }
        }
    }

    @Test
    @DisplayName("sensitive raw content fields are NOT defined in the contract")
    void noRawPayloadAttributesAreDefined() throws Exception { 
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { 
            if (field.getType() == String.class) { 
                String value = ((String) field.get(null)).toLowerCase(); 
                assertThat(value) 
                        .as("Field %s must not define a raw payload key", field.getName()) 
                        .doesNotContain("prompt")
                        .doesNotContain("payload")
                        .doesNotContain("embedding")
                        .doesNotContain("completion")
                        .doesNotContain("fragment");
            }
        }
    }

    @Test
    @DisplayName("11 span name constants are defined")
    void elevenSpanPhasesAreDefined() { 
        long count = 0;
        for (Field field : AgentTelemetryContract.class.getDeclaredFields()) { 
            if (field.getName().startsWith("SPAN_") && !field.getName().equals("SPAN_PREFIX")) {
                count++;
            }
        }
        assertThat(count).isEqualTo(11); 
    }
}
