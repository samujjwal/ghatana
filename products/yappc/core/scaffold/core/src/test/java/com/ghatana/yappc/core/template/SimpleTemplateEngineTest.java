/*
 * Copyright (c) 2024 Ghatana, Inc. // GH-90000
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
package com.ghatana.yappc.core.template;

import static org.junit.jupiter.api.Assertions.*;

import com.ghatana.yappc.core.error.TemplateException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for SimpleTemplateEngine. Week 2, Day 6 deliverable - template engine
 * testing.

 * @doc.type class
 * @doc.purpose Handles simple template engine test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class SimpleTemplateEngineTest {

    private SimpleTemplateEngine templateEngine;

    @BeforeEach
    void setUp() { // GH-90000
        templateEngine = new SimpleTemplateEngine(); // GH-90000
    }

    @Test
    void testSimpleVariableSubstitution() throws TemplateException { // GH-90000
        String template = "Hello {{name}}!";
        Map<String, Object> context = Map.of("name", "World"); // GH-90000

        String result = templateEngine.render(template, context); // GH-90000

        assertEquals("Hello World!", result); // GH-90000
    }

    @Test
    void testPackagePathHelper() throws TemplateException { // GH-90000
        String template = "src/{{packagePath com.example.test}}";
        Map<String, Object> context = Map.of("com.example.test", "com.example.test"); // GH-90000

        String result = templateEngine.render(template, context); // GH-90000

        assertTrue(result.contains("com/example/test"));
    }

    @Test
    void testSafeImportHelper() throws TemplateException { // GH-90000
        String template = "{{safeImport java.util.List}}";
        Map<String, Object> context = Map.of("java.util.List", "java.util.List"); // GH-90000

        String result = templateEngine.render(template, context); // GH-90000

        assertTrue(result.contains("import java.util.List;"));
    }

    @Test
    void testEmptyVariableHandling() throws TemplateException { // GH-90000
        String template = "Hello {{missing}}!";
        Map<String, Object> context = Map.of("name", "World"); // GH-90000

        String result = templateEngine.render(template, context); // GH-90000

        assertEquals("Hello !", result); // GH-90000
    }

    @Test
    void testMergerCreation() { // GH-90000
        TemplateMerger merger = templateEngine.createMerger(); // GH-90000

        assertNotNull(merger); // GH-90000
        assertInstanceOf(DefaultTemplateMerger.class, merger); // GH-90000
    }
}
