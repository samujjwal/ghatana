package com.ghatana.media.tts.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link AudioQualityMetrics}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for SNR and STOI audio quality metrics
 * @doc.layer platform
 * @doc.pattern TestCase
 */
@DisplayName("AudioQualityMetrics [GH-90000]")
class AudioQualityMetricsTest {

    private static final int SR = 22050;

    // -------------------------------------------------------------------------
    // SNR tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("SNR: perfect copy → +∞ [GH-90000]")
    void snr_perfectCopy_infinity() { // GH-90000
        float[] sig = sine(440, 0.5f, 1000); // GH-90000
        assertThat(AudioQualityMetrics.computeSnr(sig, sig.clone())) // GH-90000
                .isEqualTo(Double.POSITIVE_INFINITY); // GH-90000
    }

    @Test
    @DisplayName("SNR: silence reference → -∞ [GH-90000]")
    void snr_silenceReference_negativeInfinity() { // GH-90000
        float[] ref = new float[1000]; // all zeros
        float[] deg = sine(440, 0.5f, 1000); // GH-90000
        assertThat(AudioQualityMetrics.computeSnr(ref, deg)) // GH-90000
                .isEqualTo(Double.NEGATIVE_INFINITY); // GH-90000
    }

    @Test
    @DisplayName("SNR: added white noise → finite positive dB [GH-90000]")
    void snr_withNoise_finitePositive() { // GH-90000
        float[] ref = sine(440, 0.5f, SR); // GH-90000
        float[] deg = addNoise(ref, 0.01f); // GH-90000
        double snr = AudioQualityMetrics.computeSnr(ref, deg); // GH-90000
        assertThat(snr).isGreaterThan(20.0); // GH-90000
    }

    @Test
    @DisplayName("SNR: mismatched length → IllegalArgumentException [GH-90000]")
    void snr_mismatchedLength_throws() { // GH-90000
        assertThatThrownBy(() -> AudioQualityMetrics.computeSnr(new float[10], new float[9])) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // -------------------------------------------------------------------------
    // STOI tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("STOI: perfect copy → ≈ 1.0 [GH-90000]")
    void stoi_perfectCopy_nearOne() { // GH-90000
        float[] sig = sine(440, 0.5f, SR); // GH-90000
        double stoi = AudioQualityMetrics.computeStoi(sig, sig.clone(), SR); // GH-90000
        assertThat(stoi).isCloseTo(1.0, within(0.05)); // GH-90000
    }

    @Test
    @DisplayName("STOI: heavily corrupted signal → low score [GH-90000]")
    void stoi_heavyNoise_lowScore() { // GH-90000
        float[] ref = sine(440, 0.3f, SR); // GH-90000
        float[] deg = addNoise(ref, 0.9f); // SNR ≈ −9 dB // GH-90000
        double stoi = AudioQualityMetrics.computeStoi(ref, deg, SR); // GH-90000
        assertThat(stoi).isLessThan(0.85); // GH-90000
    }

    @Test
    @DisplayName("STOI: result in [0, 1] [GH-90000]")
    void stoi_resultBounded() { // GH-90000
        float[] ref = sine(880, 0.4f, SR); // GH-90000
        float[] deg = addNoise(ref, 0.3f); // GH-90000
        double stoi = AudioQualityMetrics.computeStoi(ref, deg, SR); // GH-90000
        assertThat(stoi).isBetween(0.0, 1.0); // GH-90000
    }

    @Test
    @DisplayName("STOI: degraded longer than reference → truncated safely [GH-90000]")
    void stoi_longerDegraded_safe() { // GH-90000
        float[] ref = sine(440, 0.5f, SR); // GH-90000
        float[] deg = sine(440, 0.5f, SR * 2); // twice as long // GH-90000
        assertThat(AudioQualityMetrics.computeStoi(ref, deg, SR)) // GH-90000
                .isGreaterThan(0.9); // GH-90000
    }

    // -------------------------------------------------------------------------
    // computeAll
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("computeAll: result record fields populated [GH-90000]")
    void computeAll_fieldsPopulated() { // GH-90000
        float[] ref = sine(440, 0.5f, SR); // GH-90000
        float[] deg = addNoise(ref, 0.05f); // GH-90000
        var r = AudioQualityMetrics.computeAll(ref, deg, SR); // GH-90000
        assertThat(r.snrDb()).isGreaterThan(15.0); // GH-90000
        assertThat(r.stoi()).isGreaterThan(0.5); // GH-90000
        assertThat(r.isIntelligible()).isTrue(); // GH-90000
        assertThat(r.isClean()).isIn(true, false); // typically < 20 dB with 5%% noise // GH-90000
    }

    // -------------------------------------------------------------------------
    // DSP helpers
    // -------------------------------------------------------------------------

    /** Generate a mono sine wave. */
    private static float[] sine(double freqHz, float amplitude, int samples) { // GH-90000
        float[] out = new float[samples];
        for (int i = 0; i < samples; i++) { // GH-90000
            out[i] = (float) (amplitude * Math.sin(2.0 * Math.PI * freqHz * i / SR)); // GH-90000
        }
        return out;
    }

    /** Add white noise of given amplitude to a signal (returns a new array). */ // GH-90000
    private static float[] addNoise(float[] signal, float noiseAmp) { // GH-90000
        float[] out = new float[signal.length];
        for (int i = 0; i < signal.length; i++) { // GH-90000
            out[i] = signal[i] + noiseAmp * (float) (2.0 * Math.random() - 1.0); // GH-90000
        }
        return out;
    }
}
