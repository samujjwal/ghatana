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
    void setUp() throws Exception {
        templatesDir = tempDir.resolve("templates/frameworks");
        Files.createDirectories(templatesDir);
        
        // Create test template structure
        createTestTemplates();
        
        registry = new FrameworkTemplateRegistry(List.of(templatesDir));
    }

    @Test
    @DisplayName("Should register template successfully")
    void testRegisterTemplate() {
        FrameworkTemplate template = createTestTemplate(
            "test:component:button",
            "test",
            "component",
            "button"
        );
        
        registry.registerTemplate(template);
        
        Optional<FrameworkTemplate> found = registry.getTemplate("test:component:button");
        assertTrue(found.isPresent());
        assertEquals("button", found.get().name());
    }

    @Test
    @DisplayName("Should find templates by framework")
    void testFindTemplatesByFramework() {
        // Clear registry and create fresh one without auto-discovery
        registry = new FrameworkTemplateRegistry(List.of());
        
        FrameworkTemplate template1 = createTestTemplate("react:component:button", "react", "component", "button");
        FrameworkTemplate template2 = createTestTemplate("react:component:input", "react", "component", "input");
        FrameworkTemplate template3 = createTestTemplate("vue:component:button", "vue", "component", "button");
        
        registry.registerTemplate(template1);
        registry.registerTemplate(template2);
        registry.registerTemplate(template3);
        
        List<FrameworkTemplate> reactTemplates = registry.findTemplates("react", "*");
        
        assertEquals(2, reactTemplates.size());
        assertTrue(reactTemplates.stream().allMatch(t -> "react".equals(t.framework())));
    }

    @Test
    @DisplayName("Should find templates by framework and version")
    void testFindTemplatesByFrameworkAndVersion() {
        // Clear registry and create fresh one without auto-discovery
        registry = new FrameworkTemplateRegistry(List.of());
        
        FrameworkTemplate template1 = createTestTemplate("react:component:button", "react", "18.0", "component", "button");
        FrameworkTemplate template2 = createTestTemplate("react:component:input", "react", "17.0", "component", "input");
        
        registry.registerTemplate(template1);
        registry.registerTemplate(template2);
        
        List<FrameworkTemplate> react18 = registry.findTemplates("react", "18.0");
        
        assertEquals(1, react18.size());
        assertEquals("button", react18.get(0).name());
    }

    @Test
    @DisplayName("Should find templates by category")
    void testFindTemplatesByCategory() {
        FrameworkTemplate template1 = createTestTemplate("react:component:button", "react", "component", "button");
        FrameworkTemplate template2 = createTestTemplate("react:page:dashboard", "react", "page", "dashboard");
        FrameworkTemplate template3 = createTestTemplate("react:component:input", "react", "component", "input");
        
        registry.registerTemplate(template1);
        registry.registerTemplate(template2);
        registry.registerTemplate(template3);
        
        List<FrameworkTemplate> components = registry.findByCategory("react", "component");
        
        assertEquals(2, components.size());
        assertTrue(components.stream().allMatch(t -> "component".equals(t.category())));
    }

    @Test
    @DisplayName("Should return empty list for unknown framework")
    void testFindTemplatesUnknownFramework() {
        List<FrameworkTemplate> templates = registry.findTemplates("unknown", "*");
        assertTrue(templates.isEmpty());
    }

    @Test
    @DisplayName("Should get all registered frameworks")
    void testGetFrameworks() {
        // Clear registry and create fresh one without auto-discovery
        registry = new FrameworkTemplateRegistry(List.of());
        
        registry.registerTemplate(createTestTemplate("react:component:button", "react", "component", "button"));
        registry.registerTemplate(createTestTemplate("vue:component:button", "vue", "component", "button"));
        registry.registerTemplate(createTestTemplate("angular:component:button", "angular", "component", "button"));
        
        var frameworks = registry.getFrameworks();
        
        assertEquals(3, frameworks.size());
        assertTrue(frameworks.contains("react"));
        assertTrue(frameworks.contains("vue"));
        assertTrue(frameworks.contains("angular"));
    }

    @Test
    @DisplayName("Should validate template successfully")
    void testValidateTemplateSuccess() throws Exception {
        Path templateFile = templatesDir.resolve("test.hbs");
        Files.writeString(templateFile, "{{componentName}}");
        
        FrameworkTemplate template = new FrameworkTemplate(
            "test:component:button",
            "test",
            "*",
            "component",
            "button",
            "Test button",
            templateFile,
            Map.of(),
            List.of(),
            new TemplateMetadata("Test", "Author", "1.0", List.of(), Map.of(), List.of(), List.of())
        );
        
        var result = registry.validateTemplate(template);
        
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should fail validation for missing ID")
    void testValidateTemplateMissingId() throws Exception {
        Path templateFile = templatesDir.resolve("test.hbs");
        Files.writeString(templateFile, "{{componentName}}");
        
        FrameworkTemplate template = new FrameworkTemplate(
            null,
            "test",
            "*",
            "component",
            "button",
            "Test button",
            templateFile,
            Map.of(),
            List.of(),
            null
        );
        
        var result = registry.validateTemplate(template);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("ID is required")));
    }

    @Test
    @DisplayName("Should fail validation for missing framework")
    void testValidateTemplateMissingFramework() throws Exception {
        Path templateFile = templatesDir.resolve("test.hbs");
        Files.writeString(templateFile, "{{componentName}}");
        
        FrameworkTemplate template = new FrameworkTemplate(
            "test:component:button",
            null,
            "*",
            "component",
            "button",
            "Test button",
            templateFile,
            Map.of(),
            List.of(),
            null
        );
        
        var result = registry.validateTemplate(template);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("Framework is required")));
    }

    @Test
    @DisplayName("Should fail validation for non-existent template file")
    void testValidateTemplateNonExistentFile() {
        FrameworkTemplate template = new FrameworkTemplate(
            "test:component:button",
            "test",
            "*",
            "component",
            "button",
            "Test button",
            Path.of("/non/existent/file.hbs"),
            Map.of(),
            List.of(),
            null
        );
        
        var result = registry.validateTemplate(template);
        
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("does not exist")));
    }

    @Test
    @DisplayName("Should discover templates from directory")
    void testDiscoverTemplates() throws Exception {
        // Templates were created in setUp, registry should have discovered them
        var frameworks = registry.getFrameworks();
        
        assertFalse(frameworks.isEmpty());
        assertTrue(frameworks.contains("react"));
    }

    @Test
    @DisplayName("Should handle template with metadata")
    void testTemplateWithMetadata() throws Exception {
        Path templateFile = templatesDir.resolve("react/components/test.hbs");
        Files.createDirectories(templateFile.getParent());
        Files.writeString(templateFile, "{{componentName}}");
        
        TemplateMetadata metadata = new TemplateMetadata(
            "Test component",
            "Test Author",
            "1.0.0",
            List.of("component", "react"),
            Map.of("componentName", new TemplateVariable("componentName", "string", "Component name", true, null)),
            List.of("react"),
            List.of()
        );
        
        FrameworkTemplate template = new FrameworkTemplate(
            "react:components:test",
            "react",
            "*",
            "components",
            "test",
            "Test component",
            templateFile,
            metadata.variables(),
            metadata.dependencies(),
            metadata
        );
        
        registry.registerTemplate(template);
        
        Optional<FrameworkTemplate> found = registry.getTemplate("react:components:test");
        assertTrue(found.isPresent());
        assertNotNull(found.get().metadata());
        assertEquals("Test Author", found.get().metadata().author());
    }

    @Test
    @DisplayName("Should match template by framework and version")
    void testTemplateMatches() throws Exception {
        Path templateFile = templatesDir.resolve("test.hbs");
        Files.writeString(templateFile, "{{componentName}}");
        
        FrameworkTemplate template = new FrameworkTemplate(
            "react:component:button",
            "react",
            "18.0",
            "component",
            "button",
            "Test button",
            templateFile,
            Map.of(),
            List.of(),
            null
        );
        
        assertTrue(template.matches("react", "18.0"));
        assertTrue(template.matches("react", "*"));
        assertFalse(template.matches("react", "17.0"));
        assertFalse(template.matches("vue", "18.0"));
    }

    @Test
    @DisplayName("Should get template content")
    void testGetTemplateContent() throws Exception {
        String content = "Hello {{name}}!";
        Path templateFile = templatesDir.resolve("test.hbs");
        Files.writeString(templateFile, content);
        
        FrameworkTemplate template = new FrameworkTemplate(
            "test:greeting:hello",
            "test",
            "*",
            "greeting",
            "hello",
            "Hello template",
            templateFile,
            Map.of(),
            List.of(),
            null
        );
        
        assertEquals(content, template.getContent());
    }

    // Helper methods

    private void createTestTemplates() throws Exception {
        // Create React component template
        Path reactComponents = templatesDir.resolve("react/components");
        Files.createDirectories(reactComponents);
        Files.writeString(reactComponents.resolve("button.hbs"), "export function {{componentName}}() {}");
        
        // Create Spring Boot controller template
        Path springControllers = templatesDir.resolve("spring-boot/controllers");
        Files.createDirectories(springControllers);
        Files.writeString(springControllers.resolve("rest-controller.hbs"), "@RestController public class {{controllerName}} {}");
    }

    private FrameworkTemplate createTestTemplate(String id, String framework, String category, String name) {
        return createTestTemplate(id, framework, "*", category, name);
    }

    private FrameworkTemplate createTestTemplate(String id, String framework, String version, String category, String name) {
        try {
            Path templateFile = templatesDir.resolve(framework).resolve(category).resolve(name + ".hbs");
            Files.createDirectories(templateFile.getParent());
            Files.writeString(templateFile, "{{content}}");
            
            return new FrameworkTemplate(
                id,
                framework,
                version,
                category,
                name,
                "Test template",
                templateFile,
                Map.of(),
                List.of(),
                null
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
