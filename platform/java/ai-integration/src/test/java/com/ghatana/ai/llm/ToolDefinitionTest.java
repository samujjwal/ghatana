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
@DisplayName("ToolDefinition")
class ToolDefinitionTest {

    @Nested
    @DisplayName("builder")
    class BuilderTests {

        @Test
        @DisplayName("builds with name and description")
        void buildsWithNameAndDescription() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("search")
                    .description("Search the codebase")
                    .build(); // GH-90000
            assertThat(tool.getName()).isEqualTo("search");
            assertThat(tool.getDescription()).isEqualTo("Search the codebase");
            assertThat(tool.getParameters()).isEmpty(); // GH-90000
            assertThat(tool.getRequiredParameters()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("addParameter adds required parameter")
        void addRequiredParameter() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("search")
                    .description("Search")
                    .addParameter("query", "string", "The search query", true) // GH-90000
                    .build(); // GH-90000
            assertThat(tool.getParameters()).containsKey("query");
            assertThat(tool.getRequiredParameters()).containsExactly("query");
        }

        @Test
        @DisplayName("addParameter adds optional parameter")
        void addOptionalParameter() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("search")
                    .description("Search")
                    .addParameter("limit", "integer", "Max results", false) // GH-90000
                    .build(); // GH-90000
            assertThat(tool.getParameters()).containsKey("limit");
            assertThat(tool.getRequiredParameters()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("multiple parameters tracked correctly")
        void multipleParameters() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("search")
                    .description("Search")
                    .addParameter("query", "string", "The query", true) // GH-90000
                    .addParameter("limit", "integer", "Max results", false) // GH-90000
                    .addParameter("format", "string", "Output format", true) // GH-90000
                    .build(); // GH-90000
            assertThat(tool.getParameters()).hasSize(3); // GH-90000
            assertThat(tool.getRequiredParameters()).containsExactlyInAnyOrder("query", "format"); // GH-90000
        }
    }

    @Nested
    @DisplayName("null validation")
    class NullValidation {

        @Test
        @DisplayName("null name throws NullPointerException")
        void nullName() { // GH-90000
            assertThatThrownBy(() -> ToolDefinition.builder() // GH-90000
                    .description("desc")
                    .build()) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("null description throws NullPointerException")
        void nullDescription() { // GH-90000
            assertThatThrownBy(() -> ToolDefinition.builder() // GH-90000
                    .name("tool")
                    .build()) // GH-90000
                    .isInstanceOf(NullPointerException.class) // GH-90000
                    .hasMessageContaining("description");
        }
    }

    @Nested
    @DisplayName("ParameterDefinition record")
    class ParameterDefinitionTests {

        @Test
        @DisplayName("creates with type and description")
        void createsParameterDef() { // GH-90000
            ToolDefinition.ParameterDefinition param =
                    new ToolDefinition.ParameterDefinition("string", "A text query"); // GH-90000
            assertThat(param.type()).isEqualTo("string");
            assertThat(param.description()).isEqualTo("A text query");
        }

        @Test
        @DisplayName("null type throws NullPointerException")
        void nullType() { // GH-90000
            assertThatThrownBy(() -> new ToolDefinition.ParameterDefinition(null, "desc")) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("null description throws NullPointerException")
        void nullDescription() { // GH-90000
            assertThatThrownBy(() -> new ToolDefinition.ParameterDefinition("string", null)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    @Nested
    @DisplayName("toOpenAIFormat()")
    @SuppressWarnings("unchecked")
    class OpenAIFormat {

        @Test
        @DisplayName("produces correct OpenAI function calling structure")
        void correctStructure() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("search_code")
                    .description("Search the codebase")
                    .addParameter("query", "string", "The search query", true) // GH-90000
                    .addParameter("limit", "integer", "Max results", false) // GH-90000
                    .build(); // GH-90000

            Map<String, Object> format = tool.toOpenAIFormat(); // GH-90000

            assertThat(format).containsEntry("type", "function"); // GH-90000
            Map<String, Object> function = (Map<String, Object>) format.get("function");
            assertThat(function).containsEntry("name", "search_code"); // GH-90000
            assertThat(function).containsEntry("description", "Search the codebase"); // GH-90000

            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
            assertThat(parameters).containsEntry("type", "object"); // GH-90000
            assertThat((List<String>) parameters.get("required")).containsExactly("query");

            Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
            assertThat(properties).containsKey("query");
            assertThat(properties).containsKey("limit");

            Map<String, Object> queryProp = (Map<String, Object>) properties.get("query");
            assertThat(queryProp).containsEntry("type", "string"); // GH-90000
            assertThat(queryProp).containsEntry("description", "The search query"); // GH-90000
        }

        @Test
        @DisplayName("empty parameters produce empty properties")
        void emptyParameters() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("ping")
                    .description("Ping the server")
                    .build(); // GH-90000

            Map<String, Object> format = tool.toOpenAIFormat(); // GH-90000
            Map<String, Object> function = (Map<String, Object>) format.get("function");
            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
            Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
            assertThat(properties).isEmpty(); // GH-90000
            assertThat((List<String>) parameters.get("required")).isEmpty();
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("parameters map is immutable")
        void parametersImmutable() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("tool")
                    .description("desc")
                    .addParameter("p", "string", "param", true) // GH-90000
                    .build(); // GH-90000
            assertThatThrownBy(() -> tool.getParameters().put("new", // GH-90000
                    new ToolDefinition.ParameterDefinition("string", "new"))) // GH-90000
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }

        @Test
        @DisplayName("requiredParameters list is immutable")
        void requiredParamsImmutable() { // GH-90000
            ToolDefinition tool = ToolDefinition.builder() // GH-90000
                    .name("tool")
                    .description("desc")
                    .addParameter("p", "string", "param", true) // GH-90000
                    .build(); // GH-90000
            assertThatThrownBy(() -> tool.getRequiredParameters().add("new"))
                    .isInstanceOf(UnsupportedOperationException.class); // GH-90000
        }
    }
}
