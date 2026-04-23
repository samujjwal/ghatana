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
@DisplayName("EmotionControlService — AV-008.2")
class EmotionControlServiceTest {

    private final EmotionControlService service = EmotionControlService.INSTANCE;

    @ParameterizedTest(name = "prosodyFor({0}) returns non-null") // GH-90000
    @EnumSource(EmotionControlService.EmotionType.class) // GH-90000
    void prosodyForAllEmotionsNonNull(EmotionControlService.EmotionType emotion) { // GH-90000
        assertThat(service.prosodyFor(emotion)).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("NEUTRAL produces identity prosody (rate=1.0, pitch=0.0)")
    void neutralIsIdentity() { // GH-90000
        EmotionControlService.ProsodyParameters p = service.prosodyFor(EmotionControlService.EmotionType.NEUTRAL); // GH-90000
        assertThat(p.speakingRateMul()).isEqualTo(1.0f); // GH-90000
        assertThat(p.pitchShiftSt()).isEqualTo(0.0f); // GH-90000
        assertThat(p.hasSsml()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("HAPPY produces faster speech and higher pitch")
    void happyProsody() { // GH-90000
        EmotionControlService.ProsodyParameters p = service.prosodyFor(EmotionControlService.EmotionType.HAPPY); // GH-90000
        assertThat(p.speakingRateMul()).isGreaterThan(1.0f); // GH-90000
        assertThat(p.pitchShiftSt()).isGreaterThan(0.0f); // GH-90000
        assertThat(p.hasSsml()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("SAD produces slower speech and lower pitch")
    void sadProsody() { // GH-90000
        EmotionControlService.ProsodyParameters p = service.prosodyFor(EmotionControlService.EmotionType.SAD); // GH-90000
        assertThat(p.speakingRateMul()).isLessThan(1.0f); // GH-90000
        assertThat(p.pitchShiftSt()).isLessThan(0.0f); // GH-90000
        assertThat(p.hasSsml()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("EXCITED produces the fastest speech rate")
    void excitedFastest() { // GH-90000
        float excitedRate = service.prosodyFor(EmotionControlService.EmotionType.EXCITED).speakingRateMul(); // GH-90000
        float happyRate   = service.prosodyFor(EmotionControlService.EmotionType.HAPPY).speakingRateMul(); // GH-90000
        assertThat(excitedRate).isGreaterThan(happyRate); // GH-90000
    }

    @Test
    @DisplayName("applyEmotionSsml wraps text with SSML tags for HAPPY")
    void applyEmotionSsmlWrapsText() { // GH-90000
        String result = service.applyEmotionSsml("Hello world", EmotionControlService.EmotionType.HAPPY); // GH-90000
        assertThat(result).contains("Hello world");
        assertThat(result).contains("<prosody");
        assertThat(result).contains("</prosody>");
    }

    @Test
    @DisplayName("applyEmotionSsml returns plain text for NEUTRAL")
    void applyEmotionSsmlNeutralNoTags() { // GH-90000
        String result = service.applyEmotionSsml("Hello", EmotionControlService.EmotionType.NEUTRAL); // GH-90000
        assertThat(result).isEqualTo("Hello");
    }

    @Test
    @DisplayName("applyEmotionSsml throws for blank text")
    void applyEmotionSsmlThrowsBlankText() { // GH-90000
        assertThatThrownBy(() -> service.applyEmotionSsml("  ", EmotionControlService.EmotionType.HAPPY)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("prosodyFor throws NullPointerException for null emotion")
    void prosodyForNullThrows() { // GH-90000
        assertThatThrownBy(() -> service.prosodyFor(null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("parseEmotion handles case-insensitive input")
    void parseEmotionCaseInsensitive() { // GH-90000
        assertThat(EmotionControlService.parseEmotion("happy")).isEqualTo(EmotionControlService.EmotionType.HAPPY);
        assertThat(EmotionControlService.parseEmotion("EXCITED")).isEqualTo(EmotionControlService.EmotionType.EXCITED);
        assertThat(EmotionControlService.parseEmotion("Sad")).isEqualTo(EmotionControlService.EmotionType.SAD);
    }

    @Test
    @DisplayName("parseEmotion throws for unknown emotion")
    void parseEmotionThrowsForUnknown() { // GH-90000
        assertThatThrownBy(() -> EmotionControlService.parseEmotion("ANGRY"))
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("Unknown emotion");
    }

    @Test
    @DisplayName("EmotionType.isValid returns true for valid emotions")
    void isValidReturnsTrue() { // GH-90000
        assertThat(EmotionControlService.EmotionType.isValid("happy")).isTrue();
        assertThat(EmotionControlService.EmotionType.isValid("NEUTRAL")).isTrue();
    }

    @Test
    @DisplayName("EmotionType.isValid returns false for invalid emotions")
    void isValidReturnsFalse() { // GH-90000
        assertThat(EmotionControlService.EmotionType.isValid("angry")).isFalse();
        assertThat(EmotionControlService.EmotionType.isValid(null)).isFalse(); // GH-90000
    }
}
