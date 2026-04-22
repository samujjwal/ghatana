package com.ghatana.tts.emotion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EmotionControlService} (AV-008.2). // GH-90000
 */
@DisplayName("EmotionControlService — AV-008.2 [GH-90000]")
class EmotionControlServiceTest {

    private final EmotionControlService service = EmotionControlService.INSTANCE;

    @ParameterizedTest(name = "prosodyFor({0}) returns non-null") // GH-90000
    @EnumSource(EmotionControlService.EmotionType.class) // GH-90000
    void prosodyForAllEmotionsNonNull(EmotionControlService.EmotionType emotion) { // GH-90000
        assertThat(service.prosodyFor(emotion)).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("NEUTRAL produces identity prosody (rate=1.0, pitch=0.0) [GH-90000]")
    void neutralIsIdentity() { // GH-90000
        EmotionControlService.ProsodyParameters p = service.prosodyFor(EmotionControlService.EmotionType.NEUTRAL); // GH-90000
        assertThat(p.speakingRateMul()).isEqualTo(1.0f); // GH-90000
        assertThat(p.pitchShiftSt()).isEqualTo(0.0f); // GH-90000
        assertThat(p.hasSsml()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("HAPPY produces faster speech and higher pitch [GH-90000]")
    void happyProsody() { // GH-90000
        EmotionControlService.ProsodyParameters p = service.prosodyFor(EmotionControlService.EmotionType.HAPPY); // GH-90000
        assertThat(p.speakingRateMul()).isGreaterThan(1.0f); // GH-90000
        assertThat(p.pitchShiftSt()).isGreaterThan(0.0f); // GH-90000
        assertThat(p.hasSsml()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("SAD produces slower speech and lower pitch [GH-90000]")
    void sadProsody() { // GH-90000
        EmotionControlService.ProsodyParameters p = service.prosodyFor(EmotionControlService.EmotionType.SAD); // GH-90000
        assertThat(p.speakingRateMul()).isLessThan(1.0f); // GH-90000
        assertThat(p.pitchShiftSt()).isLessThan(0.0f); // GH-90000
        assertThat(p.hasSsml()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("EXCITED produces the fastest speech rate [GH-90000]")
    void excitedFastest() { // GH-90000
        float excitedRate = service.prosodyFor(EmotionControlService.EmotionType.EXCITED).speakingRateMul(); // GH-90000
        float happyRate   = service.prosodyFor(EmotionControlService.EmotionType.HAPPY).speakingRateMul(); // GH-90000
        assertThat(excitedRate).isGreaterThan(happyRate); // GH-90000
    }

    @Test
    @DisplayName("applyEmotionSsml wraps text with SSML tags for HAPPY [GH-90000]")
    void applyEmotionSsmlWrapsText() { // GH-90000
        String result = service.applyEmotionSsml("Hello world", EmotionControlService.EmotionType.HAPPY); // GH-90000
        assertThat(result).contains("Hello world [GH-90000]");
        assertThat(result).contains("<prosody [GH-90000]");
        assertThat(result).contains("</prosody> [GH-90000]");
    }

    @Test
    @DisplayName("applyEmotionSsml returns plain text for NEUTRAL [GH-90000]")
    void applyEmotionSsmlNeutralNoTags() { // GH-90000
        String result = service.applyEmotionSsml("Hello", EmotionControlService.EmotionType.NEUTRAL); // GH-90000
        assertThat(result).isEqualTo("Hello [GH-90000]");
    }

    @Test
    @DisplayName("applyEmotionSsml throws for blank text [GH-90000]")
    void applyEmotionSsmlThrowsBlankText() { // GH-90000
        assertThatThrownBy(() -> service.applyEmotionSsml("  ", EmotionControlService.EmotionType.HAPPY)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("prosodyFor throws NullPointerException for null emotion [GH-90000]")
    void prosodyForNullThrows() { // GH-90000
        assertThatThrownBy(() -> service.prosodyFor(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("parseEmotion handles case-insensitive input [GH-90000]")
    void parseEmotionCaseInsensitive() { // GH-90000
        assertThat(EmotionControlService.parseEmotion("happy [GH-90000]")).isEqualTo(EmotionControlService.EmotionType.HAPPY);
        assertThat(EmotionControlService.parseEmotion("EXCITED [GH-90000]")).isEqualTo(EmotionControlService.EmotionType.EXCITED);
        assertThat(EmotionControlService.parseEmotion("Sad [GH-90000]")).isEqualTo(EmotionControlService.EmotionType.SAD);
    }

    @Test
    @DisplayName("parseEmotion throws for unknown emotion [GH-90000]")
    void parseEmotionThrowsForUnknown() { // GH-90000
        assertThatThrownBy(() -> EmotionControlService.parseEmotion("ANGRY [GH-90000]"))
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Unknown emotion [GH-90000]");
    }

    @Test
    @DisplayName("EmotionType.isValid returns true for valid emotions [GH-90000]")
    void isValidReturnsTrue() { // GH-90000
        assertThat(EmotionControlService.EmotionType.isValid("happy [GH-90000]")).isTrue();
        assertThat(EmotionControlService.EmotionType.isValid("NEUTRAL [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("EmotionType.isValid returns false for invalid emotions [GH-90000]")
    void isValidReturnsFalse() { // GH-90000
        assertThat(EmotionControlService.EmotionType.isValid("angry [GH-90000]")).isFalse();
        assertThat(EmotionControlService.EmotionType.isValid(null)).isFalse(); // GH-90000
    }
}
