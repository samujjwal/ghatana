package com.ghatana.yappc.ai;

import com.ghatana.yappc.ai.requirements.ai.GeneratedRequirement;
import com.ghatana.yappc.ai.requirements.ai.RequirementType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AiSource} and integration of source labelling across surfaces.
 *
 * @doc.type class
 * @doc.purpose Verifies canonical AiSource enum values and source labelling on enrichment surfaces
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("AiSource")
class AiSourceTest {

    @Nested
    @DisplayName("enum values")
    class EnumValueTests {

        @Test
        @DisplayName("RULE value exists")
        void ruleValueExists() {
            assertThat(AiSource.RULE).isNotNull();
            assertThat(AiSource.RULE.name()).isEqualTo("RULE");
        }

        @Test
        @DisplayName("MODEL value exists")
        void modelValueExists() {
            assertThat(AiSource.MODEL).isNotNull();
            assertThat(AiSource.MODEL.name()).isEqualTo("MODEL");
        }

        @Test
        @DisplayName("exactly two values declared")
        void exactlyTwoValues() {
            assertThat(AiSource.values()).hasSize(2);
        }

        @Test
        @DisplayName("valueOf round-trips correctly")
        void valueOf_roundTrip() {
            assertThat(AiSource.valueOf("RULE")).isSameAs(AiSource.RULE);
            assertThat(AiSource.valueOf("MODEL")).isSameAs(AiSource.MODEL);
        }
    }

    @Nested
    @DisplayName("GeneratedRequirement source labelling")
    class GeneratedRequirementSourceTests {

        @Test
        @DisplayName("default source is 'model'")
        void defaultSource_isModel() {
            GeneratedRequirement req = GeneratedRequirement.builder()
                    .description("User can log in")
                    .type(RequirementType.FUNCTIONAL)
                    .build();
            assertThat(req.getSource()).isEqualTo("model");
        }

        @Test
        @DisplayName("explicit 'rule' source is preserved")
        void explicitRuleSource_isPreserved() {
            GeneratedRequirement req = GeneratedRequirement.builder()
                    .description("Mandatory field check")
                    .type(RequirementType.FUNCTIONAL)
                    .source("rule")
                    .build();
            assertThat(req.getSource()).isEqualTo("rule");
        }

        @Test
        @DisplayName("explicit 'model' source is preserved")
        void explicitModelSource_isPreserved() {
            GeneratedRequirement req = GeneratedRequirement.builder()
                    .description("Suggest feature refinements")
                    .type(RequirementType.NON_FUNCTIONAL)
                    .source("model")
                    .build();
            assertThat(req.getSource()).isEqualTo("model");
        }
    }
}
