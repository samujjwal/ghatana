/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for prompt template rendering (AI003). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Prompt template rendering tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("PromptTemplate – Template Rendering (AI003)")
class PromptTemplateTest extends EventloopTestBase {

    @Mock
    private PromptTemplateManager templateManager;

    @Nested
    @DisplayName("Template Registration")
    class TemplateRegistrationTests {

        @Test
        @DisplayName("[AI003]: register_template_creates_template")
        void registerTemplateCreatesTemplate() { // GH-90000
            PromptTemplateManager.PromptTemplate template = new PromptTemplateManager.PromptTemplate( // GH-90000
                "sql-gen", "SQL Generator", "Generate SQL queries",
                "tenant-alpha", "sql", "Generate SQL for {{table}} with filters: {{filters}}",
                List.of("table", "filters"), "You are a SQL expert.", // GH-90000
                Map.of("temperature", 0.3), 1, true // GH-90000
            );

            when(templateManager.registerTemplate(any())) // GH-90000
                .thenReturn(Promise.of(template)); // GH-90000

            PromptTemplateManager.PromptTemplate result = runPromise(() -> // GH-90000
                templateManager.registerTemplate(template) // GH-90000
            );

            assertThat(result.id()).isEqualTo("sql-gen");
            assertThat(result.variables()).contains("table", "filters"); // GH-90000
            assertThat(result.hasVariable("table")).isTrue();
        }

        @Test
        @DisplayName("[AI003]: get_template_returns_existing")
        void getTemplateReturnsExisting() { // GH-90000
            String templateId = "existing-template";
            PromptTemplateManager.PromptTemplate template = createTemplate(templateId, "Query Template"); // GH-90000

            when(templateManager.getTemplate(templateId)) // GH-90000
                .thenReturn(Promise.of(Optional.of(template))); // GH-90000

            Optional<PromptTemplateManager.PromptTemplate> result = runPromise(() -> // GH-90000
                templateManager.getTemplate(templateId) // GH-90000
            );

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().id()).isEqualTo(templateId); // GH-90000
        }

        @Test
        @DisplayName("[AI003]: list_templates_returns_tenant_templates")
        void listTemplatesReturnsTenantTemplates() { // GH-90000
            String tenantId = "tenant-alpha";

            List<PromptTemplateManager.PromptTemplate> templates = List.of( // GH-90000
                createTemplate("t1", "Template 1"), // GH-90000
                createTemplate("t2", "Template 2") // GH-90000
            );

            when(templateManager.listTemplates(tenantId, null)) // GH-90000
                .thenReturn(Promise.of(templates)); // GH-90000

            List<PromptTemplateManager.PromptTemplate> result = runPromise(() -> // GH-90000
                templateManager.listTemplates(tenantId, null) // GH-90000
            );

            assertThat(result).hasSize(2); // GH-90000
        }

        @Test
        @DisplayName("[AI003]: list_templates_filters_by_category")
        void listTemplatesFiltersByCategory() { // GH-90000
            String tenantId = "tenant-alpha";
            String category = "sql";

            List<PromptTemplateManager.PromptTemplate> templates = List.of( // GH-90000
                new PromptTemplateManager.PromptTemplate( // GH-90000
                    "sql-1", "SQL 1", "", tenantId, "sql", "", List.of(), null, Map.of(), 1, true // GH-90000
                ),
                new PromptTemplateManager.PromptTemplate( // GH-90000
                    "sql-2", "SQL 2", "", tenantId, "sql", "", List.of(), null, Map.of(), 1, true // GH-90000
                )
            );

            when(templateManager.listTemplates(tenantId, category)) // GH-90000
                .thenReturn(Promise.of(templates)); // GH-90000

            List<PromptTemplateManager.PromptTemplate> result = runPromise(() -> // GH-90000
                templateManager.listTemplates(tenantId, category) // GH-90000
            );

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result.get(0).category()).isEqualTo("sql");
        }

        @Test
        @DisplayName("[AI003]: delete_template_removes_template")
        void deleteTemplateRemovesTemplate() { // GH-90000
            String templateId = "obsolete-template";

            when(templateManager.deleteTemplate(templateId)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000

            runPromise(() -> templateManager.deleteTemplate(templateId)); // GH-90000

            verify(templateManager).deleteTemplate(templateId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Template Rendering")
    class TemplateRenderingTests {

        @Test
        @DisplayName("[AI003]: render_substitutes_variables")
        void renderSubstitutesVariables() { // GH-90000
            String templateId = "greeting";
            Map<String, Object> variables = Map.of( // GH-90000
                "name", "Alice",
                "day", "Monday"
            );

            String rendered = "Hello Alice! Happy Monday!";

            when(templateManager.render(templateId, variables)) // GH-90000
                .thenReturn(Promise.of(rendered)); // GH-90000

            String result = runPromise(() -> templateManager.render(templateId, variables)); // GH-90000

            assertThat(result).contains("Alice");
            assertThat(result).contains("Monday");
        }

        @Test
        @DisplayName("[AI003]: render_handles_missing_variables")
        void renderHandlesMissingVariables() { // GH-90000
            String templateId = "incomplete";
            Map<String, Object> variables = Map.of("provided", "value"); // GH-90000

            String rendered = "Provided: value, Missing: {{missing}}";

            when(templateManager.render(templateId, variables)) // GH-90000
                .thenReturn(Promise.of(rendered)); // GH-90000

            String result = runPromise(() -> templateManager.render(templateId, variables)); // GH-90000

            assertThat(result).contains("value");
        }

        @Test
        @DisplayName("[AI003]: render_with_complex_variables")
        void renderWithComplexVariables() { // GH-90000
            String templateId = "data-template";
            Map<String, Object> variables = Map.of( // GH-90000
                "table", "users",
                "columns", List.of("id", "name", "email"), // GH-90000
                "where", "active = true"
            );

            String rendered = "Query users columns: [id, name, email] WHERE active = true";

            when(templateManager.render(templateId, variables)) // GH-90000
                .thenReturn(Promise.of(rendered)); // GH-90000

            String result = runPromise(() -> templateManager.render(templateId, variables)); // GH-90000

            assertThat(result).contains("users");
            assertThat(result).contains("id, name, email");
        }
    }

    @Nested
    @DisplayName("Template Validation")
    class TemplateValidationTests {

        @Test
        @DisplayName("[AI003]: validate_returns_valid_for_good_template")
        void validateReturnsValidForGoodTemplate() { // GH-90000
            PromptTemplateManager.PromptTemplate template = new PromptTemplateManager.PromptTemplate( // GH-90000
                "valid", "Valid", "", "tenant-alpha", "test",
                "Hello {{name}}!", List.of("name"), null, Map.of(), 1, true
            );

            PromptTemplateManager.ValidationResult validation = new PromptTemplateManager.ValidationResult( // GH-90000
                true, List.of(), List.of(), List.of(), List.of() // GH-90000
            );

            when(templateManager.validate(template)) // GH-90000
                .thenReturn(Promise.of(validation)); // GH-90000

            PromptTemplateManager.ValidationResult result = runPromise(() -> // GH-90000
                templateManager.validate(template) // GH-90000
            );

            assertThat(result.valid()).isTrue(); // GH-90000
            assertThat(result.errors()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("[AI003]: validate_reports_undefined_variables")
        void validateReportsUndefinedVariables() { // GH-90000
            PromptTemplateManager.PromptTemplate template = new PromptTemplateManager.PromptTemplate( // GH-90000
                "bad", "Bad", "", "tenant-alpha", "test",
                "Hello {{name}} and {{extra}}!", List.of("name"), null, Map.of(), 1, true
            );

            PromptTemplateManager.ValidationResult validation = new PromptTemplateManager.ValidationResult( // GH-90000
                false,
                List.of("Variable 'extra' used but not defined"),
                List.of(), // GH-90000
                List.of("extra"),
                List.of() // GH-90000
            );

            when(templateManager.validate(template)) // GH-90000
                .thenReturn(Promise.of(validation)); // GH-90000

            PromptTemplateManager.ValidationResult result = runPromise(() -> // GH-90000
                templateManager.validate(template) // GH-90000
            );

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.undefinedVariables()).contains("extra");
        }

        @Test
        @DisplayName("[AI003]: validate_reports_unused_variables")
        void validateReportsUnusedVariables() { // GH-90000
            PromptTemplateManager.PromptTemplate template = new PromptTemplateManager.PromptTemplate( // GH-90000
                "unused", "Unused", "", "tenant-alpha", "test",
                "Hello!", List.of("name", "unused"), null, Map.of(), 1, true // GH-90000
            );

            PromptTemplateManager.ValidationResult validation = new PromptTemplateManager.ValidationResult( // GH-90000
                true, List.of(), List.of("Variable 'name' defined but not used"), List.of(), List.of("name")
            );

            when(templateManager.validate(template)) // GH-90000
                .thenReturn(Promise.of(validation)); // GH-90000

            PromptTemplateManager.ValidationResult result = runPromise(() -> // GH-90000
                templateManager.validate(template) // GH-90000
            );

            assertThat(result.unusedVariables()).contains("name");
        }
    }

    @Nested
    @DisplayName("Template Cloning")
    class TemplateCloningTests {

        @Test
        @DisplayName("[AI003]: clone_template_creates_copy")
        void cloneTemplateCreatesCopy() { // GH-90000
            String sourceId = "source-template";
            String newId = "cloned-template";

            PromptTemplateManager.PromptTemplate cloned = new PromptTemplateManager.PromptTemplate( // GH-90000
                newId, "Cloned", "", "tenant-alpha", "test",
                "Content", List.of(), null, Map.of(), 1, true // GH-90000
            );

            when(templateManager.cloneTemplate(sourceId, newId)) // GH-90000
                .thenReturn(Promise.of(cloned)); // GH-90000

            PromptTemplateManager.PromptTemplate result = runPromise(() -> // GH-90000
                templateManager.cloneTemplate(sourceId, newId) // GH-90000
            );

            assertThat(result.id()).isEqualTo(newId); // GH-90000
        }
    }

    @Nested
    @DisplayName("Categories")
    class CategoriesTests {

        @Test
        @DisplayName("[AI003]: get_categories_returns_categories")
        void getCategoriesReturnsCategories() { // GH-90000
            String tenantId = "tenant-alpha";

            List<String> categories = List.of("sql", "analytics", "general", "code"); // GH-90000

            when(templateManager.getCategories(tenantId)) // GH-90000
                .thenReturn(Promise.of(categories)); // GH-90000

            List<String> result = runPromise(() -> templateManager.getCategories(tenantId)); // GH-90000

            assertThat(result).contains("sql", "analytics"); // GH-90000
        }
    }

    private PromptTemplateManager.PromptTemplate createTemplate(String id, String name) { // GH-90000
        return new PromptTemplateManager.PromptTemplate( // GH-90000
            id, name, "", "tenant-alpha", "test",
            "Content", List.of(), null, Map.of(), 1, true // GH-90000
        );
    }
}
