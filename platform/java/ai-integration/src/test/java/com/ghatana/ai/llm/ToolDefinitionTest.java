package com.ghatana.ai.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ToolDefinition}.
 *
 * Covers builder, parameter management, OpenAI format conversion,
 * null validation, and immutability.
 */
@DisplayName("ToolDefinition [GH-90000]")
class ToolDefinitionTest {

    @Nested
    @DisplayName("builder [GH-90000]")
    class BuilderTests {

        @Test
        @DisplayName("builds with name and description [GH-90000]")
        void buildsWithNameAndDescription() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("search [GH-90000]")
                    .description("Search the codebase [GH-90000]")
                    .build(); // GH-90000
            assertThat(tool.getName()).isEqualTo("search [GH-90000]");
            assertThat(tool.getDescription()).isEqualTo("Search the codebase [GH-90000]");
            assertThat(tool.getParameters()).isEmpty(); // GH-90000
            assertThat(tool.getRequiredParameters()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("addParameter adds required parameter [GH-90000]")
        void addRequiredParameter() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("search [GH-90000]")
                    .description("Search [GH-90000]")
                    .addParameter("query", "string", "The search query", true) // GH-90000
                    .build(); // GH-90000
            assertThat(tool.getParameters()).containsKey("query [GH-90000]");
            assertThat(tool.getRequiredParameters()).containsExactly("query [GH-90000]");
        }

        @Test
        @DisplayName("addParameter adds optional parameter [GH-90000]")
        void addOptionalParameter() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("search [GH-90000]")
                    .description("Search [GH-90000]")
                    .addParameter("limit", "integer", "Max results", false) // GH-90000
                    .build(); // GH-90000
            assertThat(tool.getParameters()).containsKey("limit [GH-90000]");
            assertThat(tool.getRequiredParameters()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("multiple parameters tracked correctly [GH-90000]")
        void multipleParameters() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("search [GH-90000]")
                    .description("Search [GH-90000]")
                    .addParameter("query", "string", "The query", true) // GH-90000
                    .addParameter("limit", "integer", "Max results", false) // GH-90000
                    .addParameter("format", "string", "Output format", true) // GH-90000
                    .build(); // GH-90000
            assertThat(tool.getParameters()).hasSize(3); // GH-90000
            assertThat(tool.getRequiredParameters()).containsExactlyInAnyOrder("query", "format"); // GH-90000
        }
    }

    @Nested
    @DisplayName("null validation [GH-90000]")
    class NullValidation {

        @Test
        @DisplayName("null name throws NullPointerException [GH-90000]")
        void nullName() { // GH-90000
            assertThatThrownBy(() -> ToolDefinition.builder() // GH-90000
                    .description("desc [GH-90000]")
                    .build()) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("name [GH-90000]");
        }

        @Test
        @DisplayName("null description throws NullPointerException [GH-90000]")
        void nullDescription() { // GH-90000
            assertThatThrownBy(() -> ToolDefinition.builder() // GH-90000
                    .name("tool [GH-90000]")
                    .build()) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("description [GH-90000]");
        }
    }

    @Nested
    @DisplayName("ParameterDefinition record [GH-90000]")
    class ParameterDefinitionTests {

        @Test
        @DisplayName("creates with type and description [GH-90000]")
        void createsParameterDef() { // GH-90000
            ToolDefinition.ParameterDefinition param =
                    new ToolDefinition.ParameterDefinition("string", "A text query"); // GH-90000
            assertThat(param.type()).isEqualTo("string [GH-90000]");
            assertThat(param.description()).isEqualTo("A text query [GH-90000]");
        }

        @Test
        @DisplayName("null type throws NullPointerException [GH-90000]")
        void nullType() { // GH-90000
            assertThatThrownBy(() -> new ToolDefinition.ParameterDefinition(null, "desc")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null description throws NullPointerException [GH-90000]")
        void nullDescription() { // GH-90000
            assertThatThrownBy(() -> new ToolDefinition.ParameterDefinition("string", null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("toOpenAIFormat() [GH-90000]")
    @SuppressWarnings("unchecked [GH-90000]")
    class OpenAIFormat {

        @Test
        @DisplayName("produces correct OpenAI function calling structure [GH-90000]")
        void correctStructure() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("search_code [GH-90000]")
                    .description("Search the codebase [GH-90000]")
                    .addParameter("query", "string", "The search query", true) // GH-90000
                    .addParameter("limit", "integer", "Max results", false) // GH-90000
                    .build(); // GH-90000

            Map<String, Object> format = tool.toOpenAIFormat(); // GH-90000

            assertThat(format).containsEntry("type", "function"); // GH-90000
            Map<String, Object> function = (Map<String, Object>) format.get("function [GH-90000]");
            assertThat(function).containsEntry("name", "search_code"); // GH-90000
            assertThat(function).containsEntry("description", "Search the codebase"); // GH-90000

            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters [GH-90000]");
            assertThat(parameters).containsEntry("type", "object"); // GH-90000
            assertThat((List<String>) parameters.get("required [GH-90000]")).containsExactly("query [GH-90000]");

            Map<String, Object> properties = (Map<String, Object>) parameters.get("properties [GH-90000]");
            assertThat(properties).containsKey("query [GH-90000]");
            assertThat(properties).containsKey("limit [GH-90000]");

            Map<String, Object> queryProp = (Map<String, Object>) properties.get("query [GH-90000]");
            assertThat(queryProp).containsEntry("type", "string"); // GH-90000
            assertThat(queryProp).containsEntry("description", "The search query"); // GH-90000
        }

        @Test
        @DisplayName("empty parameters produce empty properties [GH-90000]")
        void emptyParameters() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("ping [GH-90000]")
                    .description("Ping the server [GH-90000]")
                    .build(); // GH-90000

            Map<String, Object> format = tool.toOpenAIFormat(); // GH-90000
            Map<String, Object> function = (Map<String, Object>) format.get("function [GH-90000]");
            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters [GH-90000]");
            Map<String, Object> properties = (Map<String, Object>) parameters.get("properties [GH-90000]");
            assertThat(properties).isEmpty(); // GH-90000
            assertThat((List<String>) parameters.get("required [GH-90000]")).isEmpty();
        }
    }

    @Nested
    @DisplayName("immutability [GH-90000]")
    class Immutability {

        @Test
        @DisplayName("parameters map is immutable [GH-90000]")
        void parametersImmutable() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("tool [GH-90000]")
                    .description("desc [GH-90000]")
                    .addParameter("p", "string", "param", true) // GH-90000
                    .build(); // GH-90000
            assertThatThrownBy(() -> tool.getParameters().put("new", // GH-90000
                    new ToolDefinition.ParameterDefinition("string", "new"))) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("requiredParameters list is immutable [GH-90000]")
        void requiredParamsImmutable() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("tool [GH-90000]")
                    .description("desc [GH-90000]")
                    .addParameter("p", "string", "param", true) // GH-90000
                    .build(); // GH-90000
            assertThatThrownBy(() -> tool.getRequiredParameters().add("new [GH-90000]"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }
}
