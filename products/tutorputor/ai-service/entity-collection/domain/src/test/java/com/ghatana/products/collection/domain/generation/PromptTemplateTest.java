package com.ghatana.products.collection.domain.generation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PromptTemplate.
 *
 * Tests validate:
 * - Variable substitution in user and system prompts
 * - Variable reference detection
 * - Template completeness checking
 * - Missing variable identification
 * - Edge cases (null, empty, special chars)
 * - Builder pattern construction
 *
 * @see PromptTemplate
 */
@DisplayName("PromptTemplate Tests")
class PromptTemplateTest {

    private PromptTemplate.Builder builder;

    @BeforeEach
    void setUp() {
        // GIVEN: Builder with basic setup
        builder = PromptTemplate.builder()
                .userPrompt("Default prompt");
    }

    /**
     * Verifies basic template creation with user prompt only.
     *
     * GIVEN: User prompt set
     * WHEN: build() called
     * THEN: Template created with user prompt and no system prompt
     */
    @Test
    @DisplayName("Should create template with user prompt only")
    void shouldCreateTemplateWithUserPromptOnly() {
        // WHEN: Build template
        PromptTemplate template = builder.build();

        // THEN: User prompt set, system prompt null
        assertThat(template.getUserPrompt())
                .as("User prompt should match builder input")
                .isEqualTo("Default prompt");
        assertThat(template.getSystemPrompt())
                .as("System prompt should be null when not set")
                .isNull();
    }

    /**
     * Verifies template creation with both system and user prompts.
     *
     * GIVEN: Both system and user prompts
     * WHEN: build() called
     * THEN: Both prompts stored in template
     */
    @Test
    @DisplayName("Should create template with system and user prompts")
    void shouldCreateTemplateWithSystemAndUserPrompts() {
        // GIVEN: Both prompts
        String systemPrompt = "You are a helpful assistant.";
        String userPrompt = "Answer the following question.";

        // WHEN: Build
        PromptTemplate template = builder
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build();

        // THEN: Both set
        assertThat(template.getSystemPrompt()).isEqualTo(systemPrompt);
        assertThat(template.getUserPrompt()).isEqualTo(userPrompt);
    }

    /**
     * Verifies simple variable substitution.
     *
     * GIVEN: Template with {{text}} placeholder and variable set
     * WHEN: resolveUserPrompt() called
     * THEN: Variable replaced in resolved prompt
     */
    @Test
    @DisplayName("Should substitute single variable in prompt")
    void shouldSubstituteSingleVariableInPrompt() {
        // GIVEN: Template with variable placeholder
        PromptTemplate template = builder
                .userPrompt("Summarize: {{text}}")
                .variable("text", "Lorem ipsum dolor sit amet.")
                .build();

        // WHEN: Resolve
        String resolved = template.resolveUserPrompt();

        // THEN: Variable replaced
        assertThat(resolved)
                .as("Variable {{text}} should be replaced with value")
                .isEqualTo("Summarize: Lorem ipsum dolor sit amet.");
    }

    /**
     * Verifies multiple variable substitution.
     *
     * GIVEN: Template with multiple placeholders
     * WHEN: resolveUserPrompt() called
     * THEN: All variables replaced in order
     */
    @Test
    @DisplayName("Should substitute multiple variables in prompt")
    void shouldSubstituteMultipleVariablesInPrompt() {
        // GIVEN: Multiple variables
        PromptTemplate template = builder
                .userPrompt("Generate {{type}} about {{topic}} for {{audience}}")
                .variable("type", "article")
                .variable("topic", "AI safety")
                .variable("audience", "beginners")
                .build();

        // WHEN: Resolve
        String resolved = template.resolveUserPrompt();

        // THEN: All replaced
        assertThat(resolved)
                .as("All variables should be substituted")
                .isEqualTo("Generate article about AI safety for beginners");
    }

    /**
     * Verifies undefined variable placeholder remains.
     *
     * GIVEN: Template with undefined variable
     * WHEN: resolveUserPrompt() called
     * THEN: Placeholder remains unchanged
     */
    @Test
    @DisplayName("Should keep undefined variable placeholder unchanged")
    void shouldKeepUndefinedVariablePlaceholderUnchanged() {
        // GIVEN: Variable referenced but not provided
        PromptTemplate template = builder
                .userPrompt("Summarize: {{text}}, author: {{author}}")
                .variable("text", "Content here")
                .build();

        // WHEN: Resolve
        String resolved = template.resolveUserPrompt();

        // THEN: Undefined {{author}} remains, {{text}} replaced
        assertThat(resolved)
                .as("Undefined variable should remain as placeholder")
                .isEqualTo("Summarize: Content here, author: {{author}}");
    }

    /**
     * Verifies variable substitution in both system and user prompts.
     *
     * GIVEN: Variables in both prompts
     * WHEN: resolveSystemPrompt() and resolveUserPrompt() called
     * THEN: Both resolved with variables
     */
    @Test
    @DisplayName("Should resolve variables in both system and user prompts")
    void shouldResolveVariablesInBothSystemAndUserPrompts() {
        // GIVEN: Variables in both
        PromptTemplate template = builder
                .systemPrompt("You are {{role}} with {{expertise}} expertise.")
                .userPrompt("Answer about {{topic}}")
                .variable("role", "expert")
                .variable("expertise", "technical")
                .variable("topic", "databases")
                .build();

        // WHEN: Resolve both
        String systemResolved = template.resolveSystemPrompt();
        String userResolved = template.resolveUserPrompt();

        // THEN: Both resolved
        assertThat(systemResolved)
                .isEqualTo("You are expert with technical expertise.");
        assertThat(userResolved)
                .isEqualTo("Answer about databases");
    }

    /**
     * Verifies detection of referenced variables.
     *
     * GIVEN: Template with various variable references
     * WHEN: getReferencedVariables() called
     * THEN: All referenced variables identified (no duplicates)
     */
    @Test
    @DisplayName("Should identify all referenced variables")
    void shouldIdentifyAllReferencedVariables() {
        // GIVEN: Template with repeated and single references
        PromptTemplate template = builder
                .systemPrompt("You are {{role}}")
                .userPrompt("{{role}}: Summarize {{text}}")
                .build();

        // WHEN: Get referenced
        Set<String> referenced = template.getReferencedVariables();

        // THEN: Unique references identified (no duplicates)
        assertThat(referenced)
                .as("Should contain unique referenced variables")
                .containsExactlyInAnyOrder("role", "text");
    }

    /**
     * Verifies template completeness with all variables provided.
     *
     * GIVEN: All referenced variables provided
     * WHEN: isComplete() called
     * THEN: Returns true
     */
    @Test
    @DisplayName("Should report template complete when all variables provided")
    void shouldReportTemplateCompleteWhenAllVariablesProvided() {
        // GIVEN: All variables provided
        PromptTemplate template = builder
                .userPrompt("{{greeting}}, {{name}}!")
                .variable("greeting", "Hello")
                .variable("name", "Alice")
                .build();

        // WHEN: Check complete
        boolean complete = template.isComplete();

        // THEN: Complete
        assertThat(complete)
                .as("Template should be complete with all variables provided")
                .isTrue();
    }

    /**
     * Verifies template incompleteness with missing variables.
     *
     * GIVEN: Some variables missing
     * WHEN: isComplete() called
     * THEN: Returns false
     */
    @Test
    @DisplayName("Should report template incomplete when variables missing")
    void shouldReportTemplateIncompleteWhenVariablesMissing() {
        // GIVEN: Only one of two variables provided
        PromptTemplate template = builder
                .userPrompt("{{greeting}}, {{name}}!")
                .variable("greeting", "Hello")
                .build();

        // WHEN: Check complete
        boolean complete = template.isComplete();

        // THEN: Incomplete
        assertThat(complete)
                .as("Template should be incomplete with missing variables")
                .isFalse();
    }

    /**
     * Verifies identification of missing variables.
     *
     * GIVEN: Template with some variables missing
     * WHEN: getMissingVariables() called
     * THEN: Returns set of missing variable names
     */
    @Test
    @DisplayName("Should identify missing variables")
    void shouldIdentifyMissingVariables() {
        // GIVEN: Some variables missing
        PromptTemplate template = builder
                .userPrompt("{{greeting}}, {{name}}! From {{location}}")
                .variable("greeting", "Hello")
                .build();

        // WHEN: Get missing
        Set<String> missing = template.getMissingVariables();

        // THEN: Correct variables identified
        assertThat(missing)
                .as("Should identify missing variables")
                .containsExactlyInAnyOrder("name", "location");
    }

    /**
     * Verifies empty missing variables when template complete.
     *
     * GIVEN: All variables provided
     * WHEN: getMissingVariables() called
     * THEN: Returns empty set
     */
    @Test
    @DisplayName("Should return empty missing variables when template complete")
    void shouldReturnEmptyMissingVariablesWhenTemplateComplete() {
        // GIVEN: Complete template
        PromptTemplate template = builder
                .userPrompt("{{greeting}}")
                .variable("greeting", "Hello")
                .build();

        // WHEN: Get missing
        Set<String> missing = template.getMissingVariables();

        // THEN: Empty
        assertThat(missing)
                .as("Should be empty when all variables provided")
                .isEmpty();
    }

    /**
     * Verifies null system prompt handling.
     *
     * GIVEN: System prompt not set (null)
     * WHEN: resolveSystemPrompt() called
     * THEN: Returns null
     */
    @Test
    @DisplayName("Should handle null system prompt")
    void shouldHandleNullSystemPrompt() {
        // GIVEN: No system prompt
        PromptTemplate template = builder.build();

        // WHEN: Resolve system prompt
        String resolved = template.resolveSystemPrompt();

        // THEN: Null returned
        assertThat(resolved)
                .as("Should return null when system prompt not set")
                .isNull();
    }

    /**
     * Verifies prompt length validation rejects oversized prompts.
     *
     * GIVEN: System or user prompt exceeds 10,000 chars
     * WHEN: build() called
     * THEN: IllegalArgumentException thrown
     */
    @Test
    @DisplayName("Should reject prompts exceeding 10000 characters")
    void shouldRejectPromptsExceeding10000Characters() {
        // GIVEN: Oversized user prompt
        String tooLong = "x".repeat(10001);

        // WHEN/THEN: Build fails
        assertThatThrownBy(() -> builder.userPrompt(tooLong).build())
                .as("Should reject user prompt > 10,000 chars")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10,000");
    }

    /**
     * Verifies null userPrompt rejected.
     *
     * GIVEN: userPrompt set to null
     * WHEN: userPrompt() called
     * THEN: NullPointerException thrown
     */
    @Test
    @DisplayName("Should require non-null user prompt")
    void shouldRequireNonNullUserPrompt() {
        // WHEN/THEN: Null user prompt rejected
        assertThatThrownBy(() -> builder.userPrompt(null).build())
                .as("Should reject null user prompt")
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies null variables map rejected.
     *
     * GIVEN: variables() called with null
     * WHEN: build() attempted
     * THEN: NullPointerException during variables() call
     */
    @Test
    @DisplayName("Should require non-null variables map")
    void shouldRequireNonNullVariablesMap() {
        // WHEN/THEN: Null variables rejected
        assertThatThrownBy(() -> builder.variables(null))
                .as("Should reject null variables map")
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies variable names must be alphanumeric.
     *
     * GIVEN: Template with valid variable name pattern
     * WHEN: resolved
     * THEN: Substitution works
     */
    @Test
    @DisplayName("Should support alphanumeric and underscore in variable names")
    void shouldSupportAlphanumericAndUnderscoreInVariableNames() {
        // GIVEN: Variables with allowed characters
        PromptTemplate template = builder
                .userPrompt("{{var_1}} and {{VAR_2}} and {{Var_3}}")
                .variable("var_1", "value1")
                .variable("VAR_2", "value2")
                .variable("Var_3", "value3")
                .build();

        // WHEN: Resolve
        String resolved = template.resolveUserPrompt();

        // THEN: All substituted
        assertThat(resolved)
                .isEqualTo("value1 and value2 and value3");
    }

    /**
     * Verifies variable substitution is idempotent.
     *
     * GIVEN: Resolved prompt called multiple times
     * WHEN: resolveUserPrompt() called again
     * THEN: Same result returned
     */
    @Test
    @DisplayName("Should be idempotent - multiple resolutions same result")
    void shouldBeIdempotentMultipleResolutionsSameResult() {
        // GIVEN: Template with variable
        PromptTemplate template = builder
                .userPrompt("Text: {{content}}")
                .variable("content", "Hello world")
                .build();

        // WHEN: Resolve multiple times
        String resolved1 = template.resolveUserPrompt();
        String resolved2 = template.resolveUserPrompt();
        String resolved3 = template.resolveUserPrompt();

        // THEN: Same result
        assertThat(resolved1)
                .as("First resolution")
                .isEqualTo(resolved2)
                .as("Should equal second resolution")
                .isEqualTo(resolved3)
                .as("Should equal third resolution");
    }

    /**
     * Verifies variables map immutable after construction.
     *
     * GIVEN: Template built with variables
     * WHEN: getVariables() returns map and caller modifies
     * THEN: Template variables unchanged
     */
    @Test
    @DisplayName("Should return immutable variables map")
    void shouldReturnImmutableVariablesMap() {
        // GIVEN: Template with variables
        PromptTemplate template = builder
                .variable("key1", "value1")
                .build();

        // WHEN: Get variables map and try to modify
        Map<String, String> vars = template.getVariables();
        
        // THEN: Modification fails
        assertThatThrownBy(() -> vars.put("key2", "value2"))
                .as("Variables map should be unmodifiable")
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Verifies template with empty system prompt.
     *
     * GIVEN: System prompt set to empty string
     * WHEN: build()
     * THEN: Template created successfully
     */
    @Test
    @DisplayName("Should accept empty string for system prompt")
    void shouldAcceptEmptyStringForSystemPrompt() {
        // GIVEN: Empty system prompt
        PromptTemplate template = builder
                .systemPrompt("")
                .build();

        // WHEN: Resolve system prompt
        String resolved = template.resolveSystemPrompt();

        // THEN: Empty string returned
        assertThat(resolved)
                .as("Empty system prompt should resolve to empty string")
                .isEmpty();
    }

    /**
     * Verifies template builder can be reused.
     *
     * GIVEN: Builder used to create first template
     * WHEN: Builder used again with different configuration
     * THEN: First template unchanged
     */
    @Test
    @DisplayName("Should allow builder reuse for multiple templates")
    void shouldAllowBuilderReuseForMultipleTemplates() {
        // GIVEN: Create first template
        PromptTemplate template1 = PromptTemplate.builder()
                .userPrompt("Prompt 1 {{var}}")
                .variable("var", "value1")
                .build();

        // WHEN: Create second template with different config
        PromptTemplate template2 = PromptTemplate.builder()
                .userPrompt("Prompt 2 {{var}}")
                .variable("var", "value2")
                .build();

        // THEN: Templates independent
        assertThat(template1.resolveUserPrompt())
                .isEqualTo("Prompt 1 value1");
        assertThat(template2.resolveUserPrompt())
                .isEqualTo("Prompt 2 value2");
    }
}
