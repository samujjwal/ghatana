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

package com.ghatana.yappc.core.framework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FrameworkTemplateRegistry.

 * @doc.type class
 * @doc.purpose Handles framework template registry test operations
 * @doc.layer core
 * @doc.pattern Test
*/
class FrameworkTemplateRegistryTest {

    @TempDir
    Path tempDir;

    private FrameworkTemplateRegistry registry;
    private Path templatesDir;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        templatesDir = tempDir.resolve("templates/frameworks [GH-90000]");
        Files.createDirectories(templatesDir); // GH-90000

        // Create test template structure
        createTestTemplates(); // GH-90000

        registry = new FrameworkTemplateRegistry(List.of(templatesDir)); // GH-90000
    }

    @Test
    @DisplayName("Should register template successfully [GH-90000]")
    void testRegisterTemplate() { // GH-90000
        FrameworkTemplate template = createTestTemplate( // GH-90000
            "test:component:button",
            "test",
            "component",
            "button"
        );

        registry.registerTemplate(template); // GH-90000

        Optional<FrameworkTemplate> found = registry.getTemplate("test:component:button [GH-90000]");
        assertTrue(found.isPresent()); // GH-90000
        assertEquals("button", found.get().name()); // GH-90000
    }

    @Test
    @DisplayName("Should find templates by framework [GH-90000]")
    void testFindTemplatesByFramework() { // GH-90000
        // Clear registry and create fresh one without auto-discovery
        registry = new FrameworkTemplateRegistry(List.of()); // GH-90000

        FrameworkTemplate template1 = createTestTemplate("react:component:button", "react", "component", "button"); // GH-90000
        FrameworkTemplate template2 = createTestTemplate("react:component:input", "react", "component", "input"); // GH-90000
        FrameworkTemplate template3 = createTestTemplate("vue:component:button", "vue", "component", "button"); // GH-90000

        registry.registerTemplate(template1); // GH-90000
        registry.registerTemplate(template2); // GH-90000
        registry.registerTemplate(template3); // GH-90000

        List<FrameworkTemplate> reactTemplates = registry.findTemplates("react", "*"); // GH-90000

        assertEquals(2, reactTemplates.size()); // GH-90000
        assertTrue(reactTemplates.stream().allMatch(t -> "react".equals(t.framework()))); // GH-90000
    }

    @Test
    @DisplayName("Should find templates by framework and version [GH-90000]")
    void testFindTemplatesByFrameworkAndVersion() { // GH-90000
        // Clear registry and create fresh one without auto-discovery
        registry = new FrameworkTemplateRegistry(List.of()); // GH-90000

        FrameworkTemplate template1 = createTestTemplate("react:component:button", "react", "18.0", "component", "button"); // GH-90000
        FrameworkTemplate template2 = createTestTemplate("react:component:input", "react", "17.0", "component", "input"); // GH-90000

        registry.registerTemplate(template1); // GH-90000
        registry.registerTemplate(template2); // GH-90000

        List<FrameworkTemplate> react18 = registry.findTemplates("react", "18.0"); // GH-90000

        assertEquals(1, react18.size()); // GH-90000
        assertEquals("button", react18.get(0).name()); // GH-90000
    }

    @Test
    @DisplayName("Should find templates by category [GH-90000]")
    void testFindTemplatesByCategory() { // GH-90000
        FrameworkTemplate template1 = createTestTemplate("react:component:button", "react", "component", "button"); // GH-90000
        FrameworkTemplate template2 = createTestTemplate("react:page:dashboard", "react", "page", "dashboard"); // GH-90000
        FrameworkTemplate template3 = createTestTemplate("react:component:input", "react", "component", "input"); // GH-90000

        registry.registerTemplate(template1); // GH-90000
        registry.registerTemplate(template2); // GH-90000
        registry.registerTemplate(template3); // GH-90000

        List<FrameworkTemplate> components = registry.findByCategory("react", "component"); // GH-90000

        assertEquals(2, components.size()); // GH-90000
        assertTrue(components.stream().allMatch(t -> "component".equals(t.category()))); // GH-90000
    }

    @Test
    @DisplayName("Should return empty list for unknown framework [GH-90000]")
    void testFindTemplatesUnknownFramework() { // GH-90000
        List<FrameworkTemplate> templates = registry.findTemplates("unknown", "*"); // GH-90000
        assertTrue(templates.isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should get all registered frameworks [GH-90000]")
    void testGetFrameworks() { // GH-90000
        // Clear registry and create fresh one without auto-discovery
        registry = new FrameworkTemplateRegistry(List.of()); // GH-90000

        registry.registerTemplate(createTestTemplate("react:component:button", "react", "component", "button")); // GH-90000
        registry.registerTemplate(createTestTemplate("vue:component:button", "vue", "component", "button")); // GH-90000
        registry.registerTemplate(createTestTemplate("angular:component:button", "angular", "component", "button")); // GH-90000

        var frameworks = registry.getFrameworks(); // GH-90000

        assertEquals(3, frameworks.size()); // GH-90000
        assertTrue(frameworks.contains("react [GH-90000]"));
        assertTrue(frameworks.contains("vue [GH-90000]"));
        assertTrue(frameworks.contains("angular [GH-90000]"));
    }

    @Test
    @DisplayName("Should validate template successfully [GH-90000]")
    void testValidateTemplateSuccess() throws Exception { // GH-90000
        Path templateFile = templatesDir.resolve("test.hbs [GH-90000]");
        Files.writeString(templateFile, "{{componentName}}"); // GH-90000

        FrameworkTemplate template = new FrameworkTemplate( // GH-90000
            "test:component:button",
            "test",
            "*",
            "component",
            "button",
            "Test button",
            templateFile,
            Map.of(), // GH-90000
            List.of(), // GH-90000
            new TemplateMetadata("Test", "Author", "1.0", List.of(), Map.of(), List.of(), List.of()) // GH-90000
        );

        var result = registry.validateTemplate(template); // GH-90000

        assertTrue(result.valid()); // GH-90000
        assertTrue(result.errors().isEmpty()); // GH-90000
    }

    @Test
    @DisplayName("Should fail validation for missing ID [GH-90000]")
    void testValidateTemplateMissingId() throws Exception { // GH-90000
        Path templateFile = templatesDir.resolve("test.hbs [GH-90000]");
        Files.writeString(templateFile, "{{componentName}}"); // GH-90000

        FrameworkTemplate template = new FrameworkTemplate( // GH-90000
            null,
            "test",
            "*",
            "component",
            "button",
            "Test button",
            templateFile,
            Map.of(), // GH-90000
            List.of(), // GH-90000
            null
        );

        var result = registry.validateTemplate(template); // GH-90000

        assertFalse(result.valid()); // GH-90000
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("ID is required [GH-90000]")));
    }

    @Test
    @DisplayName("Should fail validation for missing framework [GH-90000]")
    void testValidateTemplateMissingFramework() throws Exception { // GH-90000
        Path templateFile = templatesDir.resolve("test.hbs [GH-90000]");
        Files.writeString(templateFile, "{{componentName}}"); // GH-90000

        FrameworkTemplate template = new FrameworkTemplate( // GH-90000
            "test:component:button",
            null,
            "*",
            "component",
            "button",
            "Test button",
            templateFile,
            Map.of(), // GH-90000
            List.of(), // GH-90000
            null
        );

        var result = registry.validateTemplate(template); // GH-90000

        assertFalse(result.valid()); // GH-90000
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Framework is required [GH-90000]")));
    }

    @Test
    @DisplayName("Should fail validation for non-existent template file [GH-90000]")
    void testValidateTemplateNonExistentFile() { // GH-90000
        FrameworkTemplate template = new FrameworkTemplate( // GH-90000
            "test:component:button",
            "test",
            "*",
            "component",
            "button",
            "Test button",
            Path.of("/non/existent/file.hbs [GH-90000]"),
            Map.of(), // GH-90000
            List.of(), // GH-90000
            null
        );

        var result = registry.validateTemplate(template); // GH-90000

        assertFalse(result.valid()); // GH-90000
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("does not exist [GH-90000]")));
    }

    @Test
    @DisplayName("Should discover templates from directory [GH-90000]")
    void testDiscoverTemplates() throws Exception { // GH-90000
        // Templates were created in setUp, registry should have discovered them
        var frameworks = registry.getFrameworks(); // GH-90000

        assertFalse(frameworks.isEmpty()); // GH-90000
        assertTrue(frameworks.contains("react [GH-90000]"));
    }

    @Test
    @DisplayName("Should handle template with metadata [GH-90000]")
    void testTemplateWithMetadata() throws Exception { // GH-90000
        Path templateFile = templatesDir.resolve("react/components/test.hbs [GH-90000]");
        Files.createDirectories(templateFile.getParent()); // GH-90000
        Files.writeString(templateFile, "{{componentName}}"); // GH-90000

        TemplateMetadata metadata = new TemplateMetadata( // GH-90000
            "Test component",
            "Test Author",
            "1.0.0",
            List.of("component", "react"), // GH-90000
            Map.of("componentName", new TemplateVariable("componentName", "string", "Component name", true, null)), // GH-90000
            List.of("react [GH-90000]"),
            List.of() // GH-90000
        );

        FrameworkTemplate template = new FrameworkTemplate( // GH-90000
            "react:components:test",
            "react",
            "*",
            "components",
            "test",
            "Test component",
            templateFile,
            metadata.variables(), // GH-90000
            metadata.dependencies(), // GH-90000
            metadata
        );

        registry.registerTemplate(template); // GH-90000

        Optional<FrameworkTemplate> found = registry.getTemplate("react:components:test [GH-90000]");
        assertTrue(found.isPresent()); // GH-90000
        assertNotNull(found.get().metadata()); // GH-90000
        assertEquals("Test Author", found.get().metadata().author()); // GH-90000
    }

    @Test
    @DisplayName("Should match template by framework and version [GH-90000]")
    void testTemplateMatches() throws Exception { // GH-90000
        Path templateFile = templatesDir.resolve("test.hbs [GH-90000]");
        Files.writeString(templateFile, "{{componentName}}"); // GH-90000

        FrameworkTemplate template = new FrameworkTemplate( // GH-90000
            "react:component:button",
            "react",
            "18.0",
            "component",
            "button",
            "Test button",
            templateFile,
            Map.of(), // GH-90000
            List.of(), // GH-90000
            null
        );

        assertTrue(template.matches("react", "18.0")); // GH-90000
        assertTrue(template.matches("react", "*")); // GH-90000
        assertFalse(template.matches("react", "17.0")); // GH-90000
        assertFalse(template.matches("vue", "18.0")); // GH-90000
    }

    @Test
    @DisplayName("Should get template content [GH-90000]")
    void testGetTemplateContent() throws Exception { // GH-90000
        String content = "Hello {{name}}!";
        Path templateFile = templatesDir.resolve("test.hbs [GH-90000]");
        Files.writeString(templateFile, content); // GH-90000

        FrameworkTemplate template = new FrameworkTemplate( // GH-90000
            "test:greeting:hello",
            "test",
            "*",
            "greeting",
            "hello",
            "Hello template",
            templateFile,
            Map.of(), // GH-90000
            List.of(), // GH-90000
            null
        );

        assertEquals(content, template.getContent()); // GH-90000
    }

    // Helper methods

    private void createTestTemplates() throws Exception { // GH-90000
        // Create React component template
        Path reactComponents = templatesDir.resolve("react/components [GH-90000]");
        Files.createDirectories(reactComponents); // GH-90000
        Files.writeString(reactComponents.resolve("button.hbs [GH-90000]"), "export function {{componentName}}() {}");

        // Create Spring Boot controller template
        Path springControllers = templatesDir.resolve("spring-boot/controllers [GH-90000]");
        Files.createDirectories(springControllers); // GH-90000
        Files.writeString(springControllers.resolve("rest-controller.hbs [GH-90000]"), "@RestController public class {{controllerName}} {}");
    }

    private FrameworkTemplate createTestTemplate(String id, String framework, String category, String name) { // GH-90000
        return createTestTemplate(id, framework, "*", category, name); // GH-90000
    }

    private FrameworkTemplate createTestTemplate(String id, String framework, String version, String category, String name) { // GH-90000
        try {
            Path templateFile = templatesDir.resolve(framework).resolve(category).resolve(name + ".hbs"); // GH-90000
            Files.createDirectories(templateFile.getParent()); // GH-90000
            Files.writeString(templateFile, "{{content}}"); // GH-90000

            return new FrameworkTemplate( // GH-90000
                id,
                framework,
                version,
                category,
                name,
                "Test template",
                templateFile,
                Map.of(), // GH-90000
                List.of(), // GH-90000
                null
            );
        } catch (Exception e) { // GH-90000
            throw new RuntimeException(e); // GH-90000
        }
    }
}
