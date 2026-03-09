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
        void buildsWithNameAndDescription() {
            ToolDefinition tool = ToolDefinition.builder()
                    .name("search")
                    .description("Search the codebase")
                    .build();
            assertThat(tool.getName()).isEqualTo("search");
            assertThat(tool.getDescription()).isEqualTo("Search the codebase");
            assertThat(tool.getParameters()).isEmpty();
            assertThat(tool.getRequiredParameters()).isEmpty();
        }

        @Test
        @DisplayName("addParameter adds required parameter")
        void addRequiredParameter() {
            ToolDefinition tool = ToolDefinition.builder()
                    .name("search")
                    .description("Search")
                    .addParameter("query", "string", "The search query", true)
                    .build();
            assertThat(tool.getParameters()).containsKey("query");
            assertThat(tool.getRequiredParameters()).containsExactly("query");
        }

        @Test
        @DisplayName("addParameter adds optional parameter")
        void addOptionalParameter() {
            ToolDefinition tool = ToolDefinition.builder()
                    .name("search")
                    .description("Search")
                    .addParameter("limit", "integer", "Max results", false)
                    .build();
            assertThat(tool.getParameters()).containsKey("limit");
            assertThat(tool.getRequiredParameters()).isEmpty();
        }

        @Test
        @DisplayName("multiple parameters tracked correctly")
        void multipleParameters() {
            ToolDefinition tool = ToolDefinition.builder()
                    .name("search")
                    .description("Search")
                    .addParameter("query", "string", "The query", true)
                    .addParameter("limit", "integer", "Max results", false)
                    .addParameter("format", "string", "Output format", true)
                    .build();
            assertThat(tool.getParameters()).hasSize(3);
            assertThat(tool.getRequiredParameters()).containsExactlyInAnyOrder("query", "format");
        }
    }

    @Nested
    @DisplayName("null validation")
    class NullValidation {

        @Test
        @DisplayName("null name throws NullPointerException")
        void nullName() {
            assertThatThrownBy(() -> ToolDefinition.builder()
                    .description("desc")
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("name");
        }

        @Test
        @DisplayName("null description throws NullPointerException")
        void nullDescription() {
            assertThatThrownBy(() -> ToolDefinition.builder()
                    .name("tool")
                    .build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("description");
        }
    }

    @Nested
    @DisplayName("ParameterDefinition record")
    class ParameterDefinitionTests {

        @Test
        @DisplayName("creates with type and description")
        void createsParameterDef() {
            ToolDefinition.ParameterDefinition param =
                    new ToolDefinition.ParameterDefinition("string", "A text query");
            assertThat(param.type()).isEqualTo("string");
            assertThat(param.description()).isEqualTo("A text query");
        }

        @Test
        @DisplayName("null type throws NullPointerException")
        void nullType() {
            assertThatThrownBy(() -> new ToolDefinition.ParameterDefinition(null, "desc"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null description throws NullPointerException")
        void nullDescription() {
            assertThatThrownBy(() -> new ToolDefinition.ParameterDefinition("string", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("toOpenAIFormat()")
    @SuppressWarnings("unchecked")
    class OpenAIFormat {

        @Test
        @DisplayName("produces correct OpenAI function calling structure")
        void correctStructure() {
            ToolDefinition tool = ToolDefinition.builder()
                    .name("search_code")
                    .description("Search the codebase")
                    .addParameter("query", "string", "The search query", true)
                    .addParameter("limit", "integer", "Max results", false)
                    .build();

            Map<String, Object> format = tool.toOpenAIFormat();

            assertThat(format).containsEntry("type", "function");
            Map<String, Object> function = (Map<String, Object>) format.get("function");
            assertThat(function).containsEntry("name", "search_code");
            assertThat(function).containsEntry("description", "Search the codebase");

            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
            assertThat(parameters).containsEntry("type", "object");
            assertThat((List<String>) parameters.get("required")).containsExactly("query");

            Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
            assertThat(properties).containsKey("query");
            assertThat(properties).containsKey("limit");

            Map<String, Object> queryProp = (Map<String, Object>) properties.get("query");
            assertThat(queryProp).containsEntry("type", "string");
            assertThat(queryProp).containsEntry("description", "The search query");
        }

        @Test
        @DisplayName("empty parameters produce empty properties")
        void emptyParameters() {
            ToolDefinition tool = ToolDefinition.builder()
                    .name("ping")
                    .description("Ping the server")
                    .build();

            Map<String, Object> format = tool.toOpenAIFormat();
            Map<String, Object> function = (Map<String, Object>) format.get("function");
            Map<String, Object> parameters = (Map<String, Object>) function.get("parameters");
            Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");
            assertThat(properties).isEmpty();
            assertThat((List<String>) parameters.get("required")).isEmpty();
        }
    }

    @Nested
    @DisplayName("immutability")
    class Immutability {

        @Test
        @DisplayName("parameters map is immutable")
        void parametersImmutable() {
            ToolDefinition tool = ToolDefinition.builder()
                    .name("tool")
                    .description("desc")
                    .addParameter("p", "string", "param", true)
                    .build();
            assertThatThrownBy(() -> tool.getParameters().put("new",
                    new ToolDefinition.ParameterDefinition("string", "new")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("requiredParameters list is immutable")
        void requiredParamsImmutable() {
            ToolDefinition tool = ToolDefinition.builder()
                    .name("tool")
                    .description("desc")
                    .addParameter("p", "string", "param", true)
                    .build();
            assertThatThrownBy(() -> tool.getRequiredParameters().add("new"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
