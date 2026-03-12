package com.ghatana.core.template;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link YamlTemplateEngine}.
 *
 * <p>Covers: variable substitution, missing-variable fail-fast, whitespace tolerance,
 * inheritance resolution up to max depth, cycle detection, and depth-exceeded guard.
 *
 * @doc.type class
 * @doc.purpose Tests for YamlTemplateEngine — rendering and inheritance
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("YamlTemplateEngine")
class YamlTemplateEngineTest {

    private static final YamlTemplateEngine ENGINE = new YamlTemplateEngine();

    // =========================================================================
    //  render() — variable substitution
    // =========================================================================

    @Nested
    @DisplayName("render()")
    class RenderTests {

        @Test
        @DisplayName("replaces a single {{ var }} placeholder")
        void singlePlaceholderIsReplaced() {
            String raw = "model: {{ model }}";
            TemplateContext ctx = TemplateContext.builder().put("model", "gpt-4o").build();

            String result = ENGINE.render(raw, ctx);

            assertThat(result).isEqualTo("model: gpt-4o");
        }

        @Test
        @DisplayName("replaces multiple placeholders in one pass")
        void multiplePlaceholdersReplaced() {
            String raw = "id: {{ id }}\ntenantId: {{ tenantId }}\nmodel: {{ model }}";
            TemplateContext ctx = TemplateContext.builder()
                    .put("id", "agent-001")
                    .put("tenantId", "acme")
                    .put("model", "claude-3-5-sonnet")
                    .build();

            String result = ENGINE.render(raw, ctx);

            assertThat(result)
                    .contains("id: agent-001")
                    .contains("tenantId: acme")
                    .contains("model: claude-3-5-sonnet");
        }

        @Test
        @DisplayName("tolerates extra whitespace inside {{ var }}")
        void whitespaceInsidePlaceholderIsTolerated() {
            String raw = "timeout: {{  timeout  }}";
            TemplateContext ctx = TemplateContext.builder().put("timeout", "5000").build();

            String result = ENGINE.render(raw, ctx);

            assertThat(result).isEqualTo("timeout: 5000");
        }

        @Test
        @DisplayName("returns input unchanged when there are no placeholders")
        void noPlaceholderReturnedUnchanged() {
            String raw = "key: value";
            String result = ENGINE.render(raw, TemplateContext.empty());

            assertThat(result).isEqualTo("key: value");
        }

        @Test
        @DisplayName("throws ISE when a placeholder has no binding")
        void undefinedVariableThrowsIllegalStateException() {
            String raw = "model: {{ missingVar }}";
            TemplateContext ctx = TemplateContext.empty();

            assertThatThrownBy(() -> ENGINE.render(raw, ctx))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("missingVar");
        }

        @Test
        @DisplayName("replacement value containing special Matcher chars is escaped")
        void replacementWithSpecialCharsIsEscaped() {
            // Dollar sign and backslash are special in Matcher.appendReplacement
            String raw = "pattern: {{ pattern }}";
            TemplateContext ctx = TemplateContext.builder()
                    .put("pattern", "$1 \\n special")
                    .build();

            // Must not throw and must produce exact literal
            String result = ENGINE.render(raw, ctx);
            assertThat(result).isEqualTo("pattern: $1 \\n special");
        }

        @ParameterizedTest
        @DisplayName("replaces placeholder of various identifier styles")
        @ValueSource(strings = {"myVar", "my_var", "MY_VAR", "var123"})
        void variousIdentifierStylesAreReplaced(String varName) {
            String raw = "x: {{ " + varName + " }}";
            TemplateContext ctx = TemplateContext.builder().put(varName, "hello").build();

            String result = ENGINE.render(raw, ctx);
            assertThat(result).isEqualTo("x: hello");
        }
    }

    // =========================================================================
    //  renderWithInheritance() — file inheritance
    // =========================================================================

    @Nested
    @DisplayName("renderWithInheritance()")
    class InheritanceTests {

        @TempDir
        Path dir;

        @Test
        @DisplayName("file without extends is rendered directly")
        void noExtendsFile() throws IOException {
            Path file = createYaml(dir, "agent.yaml", """
                    id: agent-001
                    name: {{ name }}
                    type: DETERMINISTIC
                    """);

            TemplateContext ctx = TemplateContext.builder().put("name", "MyAgent").build();
            String result = ENGINE.renderWithInheritance(file, ctx);

            assertThat(result).contains("id: agent-001").contains("name: MyAgent");
        }

        @Test
        @DisplayName("child overrides parent field and parent fields are merged")
        void childOverridesParentField() throws IOException {
            createYaml(dir, "base.yaml", """
                    id: base-id
                    name: BaseName
                    timeout: 30
                    """);
            Path child = createYaml(dir, "child.yaml", """
                    extends: base.yaml
                    name: ChildName
                    """);

            String result = ENGINE.renderWithInheritance(child, TemplateContext.empty());

            // child wins on 'name', parent provides 'timeout'
            assertThat(result).contains("name: ChildName").contains("timeout: '30'");
            // 'extends' key must be stripped
            assertThat(result).doesNotContain("extends:");
        }

        @Test
        @DisplayName("three-level inheritance chain is resolved")
        void threeLevelChain() throws IOException {
            createYaml(dir, "grand.yaml", """
                    level: grand
                    fromGrand: yes
                    """);
            createYaml(dir, "parent.yaml", """
                    extends: grand.yaml
                    level: parent
                    fromParent: yes
                    """);
            Path child = createYaml(dir, "child.yaml", """
                    extends: parent.yaml
                    level: child
                    """);

            String result = ENGINE.renderWithInheritance(child, TemplateContext.empty());

            assertThat(result)
                    .contains("level: child")
                    .contains("fromGrand: 'yes'")
                    .contains("fromParent: 'yes'")
                    .doesNotContain("extends:");
        }

        @Test
        @DisplayName("renders {{ }} variables after inheritance merge")
        void variablesSubstitutedAfterInheritanceMerge() throws IOException {
            createYaml(dir, "base.yaml", """
                    model: base-model
                    tenantId: {{ tenantId }}
                    """);
            Path child = createYaml(dir, "child.yaml", """
                    extends: base.yaml
                    name: {{ name }}
                    """);

            TemplateContext ctx = TemplateContext.builder()
                    .put("tenantId", "acme")
                    .put("name", "ChildAgent")
                    .build();
            String result = ENGINE.renderWithInheritance(child, ctx);

            assertThat(result)
                    .contains("tenantId: acme")
                    .contains("name: ChildAgent");
        }

        @Test
        @DisplayName("throws ISE when extends chain exceeds MAX_EXTENDS_DEPTH")
        void extendsChainExceedingMaxDepthThrows() throws IOException {
            // Create a chain of 4 which is > MAX_EXTENDS_DEPTH (3)
            createYaml(dir, "level0.yaml", "root: true\n");
            createYaml(dir, "level1.yaml", "extends: level0.yaml\nl: 1\n");
            createYaml(dir, "level2.yaml", "extends: level1.yaml\nl: 2\n");
            createYaml(dir, "level3.yaml", "extends: level2.yaml\nl: 3\n");
            Path level4 = createYaml(dir, "level4.yaml", "extends: level3.yaml\nl: 4\n");

            assertThatThrownBy(() -> ENGINE.renderWithInheritance(level4, TemplateContext.empty()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(String.valueOf(YamlTemplateEngine.MAX_EXTENDS_DEPTH));
        }

        @Test
        @DisplayName("throws ISE on circular extends chain")
        void circularExtendsThrows() throws IOException {
            // a.yaml -> b.yaml -> a.yaml
            Path a = dir.resolve("a.yaml");
            Path b = dir.resolve("b.yaml");
            Files.writeString(a, "extends: b.yaml\nfoo: 1\n");
            Files.writeString(b, "extends: a.yaml\nbar: 2\n");

            assertThatThrownBy(() -> ENGINE.renderWithInheritance(a, TemplateContext.empty()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContainingAnyOf("Circular", "circular");
        }
    }

    // =========================================================================
    //  Helpers
    // =========================================================================

    private static Path createYaml(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
