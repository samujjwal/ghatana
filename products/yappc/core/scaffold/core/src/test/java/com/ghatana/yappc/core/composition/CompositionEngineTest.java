/*
 * Copyright (c) 2025 Ghatana Platform Contributors // GH-90000
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); // GH-90000
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
    void setUp() throws Exception { // GH-90000
        // Use mock PackEngine and real HandlebarsTemplateEngine
        PackEngine mockPackEngine = mock(PackEngine.class); // GH-90000
        HandlebarsTemplateEngine templateEngine = new HandlebarsTemplateEngine(); // GH-90000

        engine = new CompositionEngine(mockPackEngine, templateEngine); // GH-90000

        // Access private method for testing
        evaluateConditionMethod = CompositionEngine.class.getDeclaredMethod( // GH-90000
            "evaluateCondition", String.class, Map.class);
        evaluateConditionMethod.setAccessible(true); // GH-90000
    }

    @Test
    @DisplayName("Empty condition should evaluate to true")
    void testEmptyCondition() throws Exception { // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "", Map.of())); // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, null, Map.of())); // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "   ", Map.of())); // GH-90000
    }

    @Test
    @DisplayName("Simple variable check - boolean true")
    void testSimpleVariableTrue() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("enabled", true); // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "enabled", vars)); // GH-90000
    }

    @Test
    @DisplayName("Simple variable check - boolean false")
    void testSimpleVariableFalse() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("enabled", false); // GH-90000
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "enabled", vars)); // GH-90000
    }

    @Test
    @DisplayName("Simple variable check - string non-empty")
    void testSimpleVariableStringNonEmpty() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("name", "test"); // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "name", vars)); // GH-90000
    }

    @Test
    @DisplayName("Simple variable check - string empty")
    void testSimpleVariableStringEmpty() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("name", ""); // GH-90000
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "name", vars)); // GH-90000
    }

    @Test
    @DisplayName("Simple variable check - string 'false'")
    void testSimpleVariableStringFalse() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("flag", "false"); // GH-90000
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "flag", vars)); // GH-90000
    }

    @Test
    @DisplayName("Simple variable check - missing variable")
    void testSimpleVariableMissing() throws Exception { // GH-90000
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "missing", Map.of())); // GH-90000
    }

    @Test
    @DisplayName("Negation - negate true")
    void testNegationTrue() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("enabled", true); // GH-90000
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "!enabled", vars)); // GH-90000
    }

    @Test
    @DisplayName("Negation - negate false")
    void testNegationFalse() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("disabled", false); // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "!disabled", vars)); // GH-90000
    }

    @Test
    @DisplayName("Equality check - strings equal")
    void testEqualityStringsEqual() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("env", "prod"); // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "env == 'prod'", vars)); // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "env == \"prod\"", vars)); // GH-90000
    }

    @Test
    @DisplayName("Equality check - strings not equal")
    void testEqualityStringsNotEqual() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("env", "dev"); // GH-90000
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "env == 'prod'", vars)); // GH-90000
    }

    @Test
    @DisplayName("Inequality check - strings not equal")
    void testInequalityStringsNotEqual() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("env", "dev"); // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "env != 'prod'", vars)); // GH-90000
    }

    @Test
    @DisplayName("Inequality check - strings equal")
    void testInequalityStringsEqual() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("env", "prod"); // GH-90000
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "env != 'prod'", vars)); // GH-90000
    }

    @Test
    @DisplayName("Equality with whitespace")
    void testEqualityWithWhitespace() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("env", "prod"); // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "  env  ==  'prod'  ", vars)); // GH-90000
    }

    @Test
    @DisplayName("Complex condition - number as string")
    void testNumberAsString() throws Exception { // GH-90000
        Map<String, Object> vars = Map.of("count", 5); // GH-90000
        assertTrue((Boolean) evaluateConditionMethod.invoke(engine, "count == '5'", vars)); // GH-90000
    }

    @Test
    @DisplayName("Null variable in equality check")
    void testNullVariableEquality() throws Exception { // GH-90000
        assertFalse((Boolean) evaluateConditionMethod.invoke(engine, "missing == 'value'", Map.of())); // GH-90000
    }
}
