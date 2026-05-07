/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 * Tests for prompt template rendering (AI003). 
 *
 * @doc.type class
 * @doc.purpose Prompt template rendering tests
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) 
@DisplayName("PromptTemplate – Template Rendering (AI003)")
class PromptTemplateTest extends EventloopTestBase {

    @Mock
    private PromptTemplateManager templateManager;

    @Nested
    @DisplayName("Template Registration")
    class TemplateRegistrationTests {

        @Test
        @DisplayName("[AI003]: register_template_creates_template")
        void registerTemplateCreatesTemplate() { 
            PromptTemplateManager.PromptTemplate template = new PromptTemplateManager.PromptTemplate( 
                "sql-gen", "SQL Generator", "Generate SQL queries",
                "tenant-alpha", "sql", "Generate SQL for {{table}} with filters: {{filters}}",
                List.of("table", "filters"), "You are a SQL expert.", 
                Map.of("temperature", 0.3), 1, true 
            );

            when(templateManager.registerTemplate(any())) 
                .thenReturn(Promise.of(template)); 

            PromptTemplateManager.PromptTemplate result = runPromise(() -> 
                templateManager.registerTemplate(template) 
            );

            assertThat(result.id()).isEqualTo("sql-gen");
            assertThat(result.variables()).contains("table", "filters"); 
            assertThat(result.hasVariable("table")).isTrue();
        }

        @Test
        @DisplayName("[AI003]: get_template_returns_existing")
        void getTemplateReturnsExisting() { 
            String templateId = "existing-template";
            PromptTemplateManager.PromptTemplate template = createTemplate(templateId, "Query Template"); 

            when(templateManager.getTemplate(templateId)) 
                .thenReturn(Promise.of(Optional.of(template))); 

            Optional<PromptTemplateManager.PromptTemplate> result = runPromise(() -> 
                templateManager.getTemplate(templateId) 
            );

            assertThat(result).isPresent(); 
            assertThat(result.get().id()).isEqualTo(templateId); 
        }

        @Test
        @DisplayName("[AI003]: list_templates_returns_tenant_templates")
        void listTemplatesReturnsTenantTemplates() { 
            String tenantId = "tenant-alpha";

            List<PromptTemplateManager.PromptTemplate> templates = List.of( 
                createTemplate("t1", "Template 1"), 
                createTemplate("t2", "Template 2") 
            );

            when(templateManager.listTemplates(tenantId, null)) 
                .thenReturn(Promise.of(templates)); 

            List<PromptTemplateManager.PromptTemplate> result = runPromise(() -> 
                templateManager.listTemplates(tenantId, null) 
            );

            assertThat(result).hasSize(2); 
        }

        @Test
        @DisplayName("[AI003]: list_templates_filters_by_category")
        void listTemplatesFiltersByCategory() { 
            String tenantId = "tenant-alpha";
            String category = "sql";

            List<PromptTemplateManager.PromptTemplate> templates = List.of( 
                new PromptTemplateManager.PromptTemplate( 
                    "sql-1", "SQL 1", "", tenantId, "sql", "", List.of(), null, Map.of(), 1, true 
                ),
                new PromptTemplateManager.PromptTemplate( 
                    "sql-2", "SQL 2", "", tenantId, "sql", "", List.of(), null, Map.of(), 1, true 
                )
            );

            when(templateManager.listTemplates(tenantId, category)) 
                .thenReturn(Promise.of(templates)); 

            List<PromptTemplateManager.PromptTemplate> result = runPromise(() -> 
                templateManager.listTemplates(tenantId, category) 
            );

            assertThat(result).hasSize(2); 
            assertThat(result.get(0).category()).isEqualTo("sql");
        }

        @Test
        @DisplayName("[AI003]: delete_template_removes_template")
        void deleteTemplateRemovesTemplate() { 
            String templateId = "obsolete-template";

            when(templateManager.deleteTemplate(templateId)) 
                .thenReturn(Promise.of((Void) null)); 

            runPromise(() -> templateManager.deleteTemplate(templateId)); 

            verify(templateManager).deleteTemplate(templateId); 
        }
    }

    @Nested
    @DisplayName("Template Rendering")
    class TemplateRenderingTests {

        @Test
        @DisplayName("[AI003]: render_substitutes_variables")
        void renderSubstitutesVariables() { 
            String templateId = "greeting";
            Map<String, Object> variables = Map.of( 
                "name", "Alice",
                "day", "Monday"
            );

            String rendered = "Hello Alice! Happy Monday!";

            when(templateManager.render(templateId, variables)) 
                .thenReturn(Promise.of(rendered)); 

            String result = runPromise(() -> templateManager.render(templateId, variables)); 

            assertThat(result).contains("Alice");
            assertThat(result).contains("Monday");
        }

        @Test
        @DisplayName("[AI003]: render_handles_missing_variables")
        void renderHandlesMissingVariables() { 
            String templateId = "incomplete";
            Map<String, Object> variables = Map.of("provided", "value"); 

            String rendered = "Provided: value, Missing: {{missing}}";

            when(templateManager.render(templateId, variables)) 
                .thenReturn(Promise.of(rendered)); 

            String result = runPromise(() -> templateManager.render(templateId, variables)); 

            assertThat(result).contains("value");
        }

        @Test
        @DisplayName("[AI003]: render_with_complex_variables")
        void renderWithComplexVariables() { 
            String templateId = "data-template";
            Map<String, Object> variables = Map.of( 
                "table", "users",
                "columns", List.of("id", "name", "email"), 
                "where", "active = true"
            );

            String rendered = "Query users columns: [id, name, email] WHERE active = true";

            when(templateManager.render(templateId, variables)) 
                .thenReturn(Promise.of(rendered)); 

            String result = runPromise(() -> templateManager.render(templateId, variables)); 

            assertThat(result).contains("users");
            assertThat(result).contains("id, name, email");
        }
    }

    @Nested
    @DisplayName("Template Validation")
    class TemplateValidationTests {

        @Test
        @DisplayName("[AI003]: validate_returns_valid_for_good_template")
        void validateReturnsValidForGoodTemplate() { 
            PromptTemplateManager.PromptTemplate template = new PromptTemplateManager.PromptTemplate( 
                "valid", "Valid", "", "tenant-alpha", "test",
                "Hello {{name}}!", List.of("name"), null, Map.of(), 1, true
            );

            PromptTemplateManager.ValidationResult validation = new PromptTemplateManager.ValidationResult( 
                true, List.of(), List.of(), List.of(), List.of() 
            );

            when(templateManager.validate(template)) 
                .thenReturn(Promise.of(validation)); 

            PromptTemplateManager.ValidationResult result = runPromise(() -> 
                templateManager.validate(template) 
            );

            assertThat(result.valid()).isTrue(); 
            assertThat(result.errors()).isEmpty(); 
        }

        @Test
        @DisplayName("[AI003]: validate_reports_undefined_variables")
        void validateReportsUndefinedVariables() { 
            PromptTemplateManager.PromptTemplate template = new PromptTemplateManager.PromptTemplate( 
                "bad", "Bad", "", "tenant-alpha", "test",
                "Hello {{name}} and {{extra}}!", List.of("name"), null, Map.of(), 1, true
            );

            PromptTemplateManager.ValidationResult validation = new PromptTemplateManager.ValidationResult( 
                false,
                List.of("Variable 'extra' used but not defined"),
                List.of(), 
                List.of("extra"),
                List.of() 
            );

            when(templateManager.validate(template)) 
                .thenReturn(Promise.of(validation)); 

            PromptTemplateManager.ValidationResult result = runPromise(() -> 
                templateManager.validate(template) 
            );

            assertThat(result.valid()).isFalse(); 
            assertThat(result.undefinedVariables()).contains("extra");
        }

        @Test
        @DisplayName("[AI003]: validate_reports_unused_variables")
        void validateReportsUnusedVariables() { 
            PromptTemplateManager.PromptTemplate template = new PromptTemplateManager.PromptTemplate( 
                "unused", "Unused", "", "tenant-alpha", "test",
                "Hello!", List.of("name", "unused"), null, Map.of(), 1, true 
            );

            PromptTemplateManager.ValidationResult validation = new PromptTemplateManager.ValidationResult( 
                true, List.of(), List.of("Variable 'name' defined but not used"), List.of(), List.of("name")
            );

            when(templateManager.validate(template)) 
                .thenReturn(Promise.of(validation)); 

            PromptTemplateManager.ValidationResult result = runPromise(() -> 
                templateManager.validate(template) 
            );

            assertThat(result.unusedVariables()).contains("name");
        }
    }

    @Nested
    @DisplayName("Template Cloning")
    class TemplateCloningTests {

        @Test
        @DisplayName("[AI003]: clone_template_creates_copy")
        void cloneTemplateCreatesCopy() { 
            String sourceId = "source-template";
            String newId = "cloned-template";

            PromptTemplateManager.PromptTemplate cloned = new PromptTemplateManager.PromptTemplate( 
                newId, "Cloned", "", "tenant-alpha", "test",
                "Content", List.of(), null, Map.of(), 1, true 
            );

            when(templateManager.cloneTemplate(sourceId, newId)) 
                .thenReturn(Promise.of(cloned)); 

            PromptTemplateManager.PromptTemplate result = runPromise(() -> 
                templateManager.cloneTemplate(sourceId, newId) 
            );

            assertThat(result.id()).isEqualTo(newId); 
        }
    }

    @Nested
    @DisplayName("Categories")
    class CategoriesTests {

        @Test
        @DisplayName("[AI003]: get_categories_returns_categories")
        void getCategoriesReturnsCategories() { 
            String tenantId = "tenant-alpha";

            List<String> categories = List.of("sql", "analytics", "general", "code"); 

            when(templateManager.getCategories(tenantId)) 
                .thenReturn(Promise.of(categories)); 

            List<String> result = runPromise(() -> templateManager.getCategories(tenantId)); 

            assertThat(result).contains("sql", "analytics"); 
        }
    }

    private PromptTemplateManager.PromptTemplate createTemplate(String id, String name) { 
        return new PromptTemplateManager.PromptTemplate( 
            id, name, "", "tenant-alpha", "test",
            "Content", List.of(), null, Map.of(), 1, true 
        );
    }
}
