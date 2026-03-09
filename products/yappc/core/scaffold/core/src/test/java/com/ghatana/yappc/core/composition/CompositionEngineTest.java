/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.composition;

import com.ghatana.yappc.core.pack.PackEngine;
import com.ghatana.yappc.core.template.HandlebarsTemplateEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CompositionEngine condition evaluation.
 
 * @doc.type class
 * @doc.purpose Handles composition engine test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class CompositionEngineTest {

    private CompositionEngine engine;
    private Method evaluateConditionMethod;

    @BeforeEach
    void setUp() throws Exception {
        // Use mock PackEngine and real HandlebarsTemplateEngine
        PackEngine mockPackEngine = mock(PackEngine.class);
        HandlebarsTemplateEngine templateEngine = new HandlebarsTemplateEngine();
        
        engine = new CompositionEngine(mockPackEngine, templateEngine);
        
        // Access private method for testing
        evaluateConditionMethod = CompositionEngine.class.getDeclaredMethod(
            "evaluateCondition", String.class, Map.class);
        evaluateConditionMethod.setAccessible(true);
    }

    @Test
    @DisplayName("Empty condition should evaluate to true")
    void testEmptyCondition() throws Exception {
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "", Map.of()));
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, null, Map.of()));
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "   ", Map.of()));
    }

    @Test
    @DisplayName("Simple variable check - boolean true")
    void testSimpleVariableTrue() throws Exception {
        Map<String, Object> vars = Map.of("enabled", true);
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "enabled", vars));
    }

    @Test
    @DisplayName("Simple variable check - boolean false")
    void testSimpleVariableFalse() throws Exception {
        Map<String, Object> vars = Map.of("enabled", false);
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "enabled", vars));
    }

    @Test
    @DisplayName("Simple variable check - string non-empty")
    void testSimpleVariableStringNonEmpty() throws Exception {
        Map<String, Object> vars = Map.of("name", "test");
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "name", vars));
    }

    @Test
    @DisplayName("Simple variable check - string empty")
    void testSimpleVariableStringEmpty() throws Exception {
        Map<String, Object> vars = Map.of("name", "");
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "name", vars));
    }

    @Test
    @DisplayName("Simple variable check - string 'false'")
    void testSimpleVariableStringFalse() throws Exception {
        Map<String, Object> vars = Map.of("flag", "false");
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "flag", vars));
    }

    @Test
    @DisplayName("Simple variable check - missing variable")
    void testSimpleVariableMissing() throws Exception {
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "missing", Map.of()));
    }

    @Test
    @DisplayName("Negation - negate true")
    void testNegationTrue() throws Exception {
        Map<String, Object> vars = Map.of("enabled", true);
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "!enabled", vars));
    }

    @Test
    @DisplayName("Negation - negate false")
    void testNegationFalse() throws Exception {
        Map<String, Object> vars = Map.of("disabled", false);
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "!disabled", vars));
    }

    @Test
    @DisplayName("Equality check - strings equal")
    void testEqualityStringsEqual() throws Exception {
        Map<String, Object> vars = Map.of("env", "prod");
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "env == 'prod'", vars));
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "env == \"prod\"", vars));
    }

    @Test
    @DisplayName("Equality check - strings not equal")
    void testEqualityStringsNotEqual() throws Exception {
        Map<String, Object> vars = Map.of("env", "dev");
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "env == 'prod'", vars));
    }

    @Test
    @DisplayName("Inequality check - strings not equal")
    void testInequalityStringsNotEqual() throws Exception {
        Map<String, Object> vars = Map.of("env", "dev");
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "env != 'prod'", vars));
    }

    @Test
    @DisplayName("Inequality check - strings equal")
    void testInequalityStringsEqual() throws Exception {
        Map<String, Object> vars = Map.of("env", "prod");
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "env != 'prod'", vars));
    }

    @Test
    @DisplayName("Equality with whitespace")
    void testEqualityWithWhitespace() throws Exception {
        Map<String, Object> vars = Map.of("env", "prod");
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "  env  ==  'prod'  ", vars));
    }

    @Test
    @DisplayName("Complex condition - number as string")
    void testNumberAsString() throws Exception {
        Map<String, Object> vars = Map.of("count", 5);
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "count == '5'", vars));
    }

    @Test
    @DisplayName("Null variable in equality check")
    void testNullVariableEquality() throws Exception {
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "missing == 'value'", Map.of()));
    }
}
