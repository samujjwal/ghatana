package com.ghatana.agent.learning.reflex;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.agent.learning.SkillVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pattern Engine Reflex Tests")
class PatternEngineReflexTest extends EventloopTestBase {

    private PatternEngineReflex reflex;

    @BeforeEach
    void setUp() {
        reflex = new PatternEngineReflex();
    }

    @Test
    void shouldRegisterHighConfidencePolicyAndBypassLlm() {
        // GIVEN
        SkillVersion highConfidenceSkill = SkillVersion.builder()
                .versionId("v1")
                .skillId("skill-1")
                .version("1.0.0")
                .confidence(0.98)
                .status("ACTIVE")
                .build();

        reflex.registerPolicy("payment_event", highConfidenceSkill);

        // WHEN
        Boolean canBypass = runPromise(() -> reflex.canBypassLlm("payment_event"));
        SkillVersion result = runPromise(() -> reflex.executeReflex("payment_event"));

        // THEN
        assertThat(canBypass).isTrue();
        assertThat(result).isEqualTo(highConfidenceSkill);
    }

    @Test
    void shouldNotRegisterLowConfidencePolicy() {
        // GIVEN
        SkillVersion lowConfidenceSkill = SkillVersion.builder()
                .versionId("v2")
                .skillId("skill-2")
                .version("1.0.0")
                .confidence(0.80)
                .status("ACTIVE")
                .build();

        reflex.registerPolicy("refund_event", lowConfidenceSkill);

        // WHEN
        Boolean canBypass = runPromise(() -> reflex.canBypassLlm("refund_event"));
        Throwable error = null;
        try {
            runPromise(() -> reflex.executeReflex("refund_event"));
        } catch (Exception e) {
            error = e;
        }

        // THEN
        assertThat(canBypass).isFalse();
        assertThat(error).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No high-confidence reflex for refund_event");
    }

    @Test
    void shouldReturnFalseForUnknownEvent() {
        // WHEN
        Boolean canBypass = runPromise(() -> reflex.canBypassLlm("unknown_event"));

        // THEN
        assertThat(canBypass).isFalse();
    }
}