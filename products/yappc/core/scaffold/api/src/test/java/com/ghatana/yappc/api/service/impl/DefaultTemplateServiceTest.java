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

package com.ghatana.yappc.api.service.impl;

import com.ghatana.yappc.api.YappcConfig;
import com.ghatana.yappc.api.service.TemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DefaultTemplateService.
 */
@DisplayName("DefaultTemplateService Tests")
/**
 * @doc.type class
 * @doc.purpose Handles default template service test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DefaultTemplateServiceTest {

    @TempDir
    Path tempDir;

    private TemplateService templateService;

    @BeforeEach
    void setUp() { // GH-90000
        YappcConfig config = YappcConfig.builder() // GH-90000
                .packsPath(tempDir.resolve("packs"))
                .workspacePath(tempDir) // GH-90000
                .build(); // GH-90000
        templateService = new DefaultTemplateService(config); // GH-90000
    }

    @Nested
    @DisplayName("Variable Substitution")
    class VariableSubstitution {

        @Test
        @DisplayName("Should substitute simple variables")
        void shouldSubstituteSimpleVariables() { // GH-90000
            String template = "Hello, {{name}}!";
            Map<String, Object> variables = Map.of("name", "World"); // GH-90000

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).isEqualTo("Hello, World!");
        }

        @Test
        @DisplayName("Should substitute multiple variables")
        void shouldSubstituteMultipleVariables() { // GH-90000
            String template = "package {{packageName}};\n\npublic class {{className}} {}";
            Map<String, Object> variables = Map.of( // GH-90000
                    "packageName", "com.example",
                    "className", "MyService"
            );

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).contains("package com.example;");
            assertThat(result).contains("public class MyService {}");
        }

        @Test
        @DisplayName("Should handle missing variables as empty string")
        void shouldHandleMissingVariablesAsEmptyString() { // GH-90000
            String template = "Hello, {{name}}!";
            Map<String, Object> variables = Map.of(); // GH-90000

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).isEqualTo("Hello, !");
        }

        @Test
        @DisplayName("Should handle null template")
        void shouldHandleNullTemplate() { // GH-90000
            String result = templateService.render(null, Map.of()); // GH-90000

            assertThat(result).isNull(); // GH-90000
        }

        @Test
        @DisplayName("Should handle empty template")
        void shouldHandleEmptyTemplate() { // GH-90000
            String result = templateService.render("", Map.of()); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Template Helpers")
    class TemplateHelpers {

        @Test
        @DisplayName("Should apply lowercase helper")
        void shouldApplyLowercaseHelper() { // GH-90000
            String template = "{{lowercase name}}";
            Map<String, Object> variables = Map.of("name", "HELLO"); // GH-90000

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("Should apply uppercase helper")
        void shouldApplyUppercaseHelper() { // GH-90000
            String template = "{{uppercase name}}";
            Map<String, Object> variables = Map.of("name", "hello"); // GH-90000

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).isEqualTo("HELLO");
        }

        @Test
        @DisplayName("Should apply capitalize helper")
        void shouldApplyCapitalizeHelper() { // GH-90000
            String template = "{{capitalize name}}";
            Map<String, Object> variables = Map.of("name", "hello"); // GH-90000

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).isEqualTo("Hello");
        }

        @Test
        @DisplayName("Should apply camelCase helper")
        void shouldApplyCamelCaseHelper() { // GH-90000
            String template = "{{camelCase name}}";
            Map<String, Object> variables = Map.of("name", "my-service-name"); // GH-90000

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).isEqualTo("myServiceName");
        }

        @Test
        @DisplayName("Should apply pascalCase helper")
        void shouldApplyPascalCaseHelper() { // GH-90000
            String template = "{{pascalCase name}}";
            Map<String, Object> variables = Map.of("name", "my-service-name"); // GH-90000

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).isEqualTo("MyServiceName");
        }

        @Test
        @DisplayName("Should apply snakeCase helper")
        void shouldApplySnakeCaseHelper() { // GH-90000
            String template = "{{snakeCase name}}";
            Map<String, Object> variables = Map.of("name", "MyServiceName"); // GH-90000

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).isEqualTo("my_service_name");
        }

        @Test
        @DisplayName("Should apply kebabCase helper")
        void shouldApplyKebabCaseHelper() { // GH-90000
            String template = "{{kebabCase name}}";
            Map<String, Object> variables = Map.of("name", "MyServiceName"); // GH-90000

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).isEqualTo("my-service-name");
        }

        @Test
        @DisplayName("Should generate uuid")
        void shouldGenerateUuid() { // GH-90000
            String template = "id: {{uuid}}";

            String result = templateService.render(template, Map.of()); // GH-90000

            assertThat(result).startsWith("id: ");
            assertThat(result.substring(4)).matches("[a-f0-9-]{36}");
        }

        @Test
        @DisplayName("Should generate date")
        void shouldGenerateDate() { // GH-90000
            String template = "date: {{date}}";

            String result = templateService.render(template, Map.of()); // GH-90000

            assertThat(result).startsWith("date: ");
            assertThat(result.substring(6)).matches("\\d{4}-\\d{2}-\\d{2}");
        }
    }

    @Nested
    @DisplayName("Custom Helpers")
    class CustomHelpers {

        @Test
        @DisplayName("Should register and use custom helper")
        void shouldRegisterAndUseCustomHelper() { // GH-90000
            templateService.registerHelper("reverse", input -> // GH-90000
                    new StringBuilder(input).reverse().toString()); // GH-90000

            String template = "{{reverse name}}";
            Map<String, Object> variables = Map.of("name", "hello"); // GH-90000

            String result = templateService.render(template, variables); // GH-90000

            assertThat(result).isEqualTo("olleh");
        }
    }

    @Nested
    @DisplayName("Available Helpers")
    class AvailableHelpers {

        @Test
        @DisplayName("Should list all built-in helpers")
        void shouldListAllBuiltInHelpers() { // GH-90000
            var helpers = templateService.getAvailableHelpers(); // GH-90000

            assertThat(helpers).containsExactlyInAnyOrder( // GH-90000
                    "lowercase", "uppercase", "capitalize",
                    "camelCase", "pascalCase", "snakeCase", "kebabCase",
                    "uuid", "now", "date"
            );
        }
    }

    @Nested
    @DisplayName("Syntax Validation")
    class SyntaxValidation {

        @Test
        @DisplayName("Should validate correct syntax")
        void shouldValidateCorrectSyntax() { // GH-90000
            String template = "Hello, {{name}}! Today is {{date}}.";

            boolean valid = templateService.validateSyntax(template); // GH-90000

            assertThat(valid).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Should validate helper syntax")
        void shouldValidateHelperSyntax() { // GH-90000
            String template = "Hello, {{uppercase name}}!";

            boolean valid = templateService.validateSyntax(template); // GH-90000

            assertThat(valid).isTrue(); // GH-90000
        }
    }
}
