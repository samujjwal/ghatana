package com.ghatana.multimodal.quality;

import com.ghatana.multimodal.engine.CrossModalFusionEngine;
import com.ghatana.multimodal.engine.CrossModalFusionEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Cross-modal accuracy regression tests for {@link CrossModalFusionEngine}.
 *
 * <p>Validates fusion accuracy under benchmark conditions, including missing modality
 * degradation, noise tolerance, and temporal alignment properties.
 *
 * @doc.type    class
 * @doc.purpose Cross-modal accuracy: benchmark dataset, missing modality, noise robustness
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("CrossModalAccuracyTest [GH-90000]")
class CrossModalAccuracyTest {

    private static final int DIM = 128;
    private CrossModalFusionEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new CrossModalFusionEngine("crossmodal-accuracy-v1", DIM); // GH-90000
    }

    // ── Benchmark dataset fixtures ────────────────────────────────────────────

    @Test
    @DisplayName("high-quality benchmark fusion returns high confidence [GH-90000]")
    void highQualityBenchmarkHighConfidence() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.95); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.92); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, visual); // GH-90000
        // With both modalities > 0.9, fusion confidence should be > 0.5
        assertThat(result.fusionConfidence()).isGreaterThan(0.5); // GH-90000
    }

    @Test
    @DisplayName("low-quality audio reduces fusion confidence compared to high-quality [GH-90000]")
    void lowQualityAudioReducesConfidence() { // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.9); // GH-90000
        FusionResult highAudio = engine.fuseAudioVisual(makeAudio(DIM, 0.95), visual); // GH-90000
        FusionResult lowAudio = engine.fuseAudioVisual(makeAudio(DIM, 0.2), visual); // GH-90000
        assertThat(lowAudio.fusionConfidence()).isLessThanOrEqualTo(highAudio.fusionConfidence()); // GH-90000
    }

    @Test
    @DisplayName("balanced modality weights when both have equal confidence [GH-90000]")
    void balancedWeightsForEqualConfidence() { // GH-90000
        double conf = 0.8;
        AudioFeatures audio = makeAudio(DIM, conf); // GH-90000
        VisualFeatures visual = makeVisual(DIM, conf); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, visual); // GH-90000
        double audioW = result.modalityWeights().get("audio [GH-90000]");
        double visualW = result.modalityWeights().get("visual [GH-90000]");
        assertThat(audioW).isCloseTo(visualW, within(0.01)); // GH-90000
    }

    // ── Missing modality degradation ──────────────────────────────────────────

    @Test
    @DisplayName("missing audio produces lower confidence than full audio-visual fusion [GH-90000]")
    void missingAudioReducesConfidenceVsFullFusion() { // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.8); // GH-90000
        AudioFeatures fullAudio = makeAudio(DIM, 0.8); // GH-90000
        AudioFeatures emptyAudio = new AudioFeatures(new double[0], 0.0); // GH-90000
        FusionResult fullResult = engine.fuseAudioVisual(fullAudio, visual); // GH-90000
        FusionResult degradedResult = engine.fuseAudioVisual(emptyAudio, visual); // GH-90000
        assertThat(degradedResult.fusionConfidence()) // GH-90000
                .isLessThanOrEqualTo(fullResult.fusionConfidence()); // GH-90000
    }

    @Test
    @DisplayName("missing visual modality still produces valid fused embeddings [GH-90000]")
    void missingVisualProducesValidEmbeddings() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.85); // GH-90000
        VisualFeatures emptyVisual = new VisualFeatures(new double[0], new double[0], 0.0); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, emptyVisual); // GH-90000
        assertThat(result.fusedEmbeddings()).hasSize(DIM); // GH-90000
        assertThat(result.hadMissingModality()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("missing text modality in text-image fusion still produces valid result [GH-90000]")
    void missingTextProducesValidTextImageFusion() { // GH-90000
        TextFeatures emptyText = new TextFeatures(new double[0], "", 0.0); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.8); // GH-90000
        FusionResult result = engine.fuseTextImage(emptyText, visual); // GH-90000
        assertThat(result.fusedEmbeddings()).hasSize(DIM); // GH-90000
        assertThat(result.hadMissingModality()).isTrue(); // GH-90000
    }

    // ── Noise robustness ──────────────────────────────────────────────────────

    @Test
    @DisplayName("noisy audio embeddings produce a valid fusion result [GH-90000]")
    void noisyAudioEmbeddingsHandled() { // GH-90000
        AudioFeatures noisy = makeNoisyAudio(DIM); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.8); // GH-90000
        FusionResult result = engine.fuseAudioVisual(noisy, visual); // GH-90000
        assertThat(result.fusedEmbeddings()).hasSize(DIM); // GH-90000
        assertThat(result.fusionConfidence()).isBetween(0.0, 1.0); // GH-90000
    }

    @Test
    @DisplayName("noisy visual embeddings produce a valid fusion result [GH-90000]")
    void noisyVisualEmbeddingsHandled() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.8); // GH-90000
        VisualFeatures noisy = makeNoisyVisual(DIM); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, noisy); // GH-90000
        assertThat(result.fusedEmbeddings()).hasSize(DIM); // GH-90000
    }

    @Test
    @DisplayName("both modalities noisy still produces valid fusion [GH-90000]")
    void bothNoisyProducesValidFusion() { // GH-90000
        AudioFeatures noisyAudio = makeNoisyAudio(DIM); // GH-90000
        VisualFeatures noisyVisual = makeNoisyVisual(DIM); // GH-90000
        assertThatCode(() -> engine.fuseAudioVisual(noisyAudio, noisyVisual)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ── Temporal alignment accuracy ───────────────────────────────────────────

    @Test
    @DisplayName("audio-visual fusion is deterministic for identical inputs [GH-90000]")
    void fusionIsDeterministic() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.85); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.75); // GH-90000
        double[] fused1 = engine.fuseAudioVisual(audio, visual).fusedEmbeddings(); // GH-90000
        double[] fused2 = engine.fuseAudioVisual(audio, visual).fusedEmbeddings(); // GH-90000
        assertThat(fused1).containsExactly(fused2); // GH-90000
    }

    @Test
    @DisplayName("audio-visual fusion is commutative in terms of output dimension [GH-90000]")
    void fusionOutputDimensionStable() { // GH-90000
        for (int trial = 0; trial < 5; trial++) { // GH-90000
            AudioFeatures audio = makeAudio(DIM, 0.7 + trial * 0.05); // GH-90000
            VisualFeatures visual = makeVisual(DIM, 0.8 - trial * 0.05); // GH-90000
            FusionResult result = engine.fuseAudioVisual(audio, visual); // GH-90000
            assertThat(result.fusedEmbeddings()).hasSize(DIM); // GH-90000
        }
    }

    // ── Embedding dimension variants ──────────────────────────────────────────

    @ParameterizedTest(name = "dim={0}") // GH-90000
    @ValueSource(ints = {16, 64, 128, 256}) // GH-90000
    @DisplayName("accuracy tests scale correctly across embedding dimensions [GH-90000]")
    void dimensionScaling(int dim) { // GH-90000
        CrossModalFusionEngine e = new CrossModalFusionEngine("crossmodal-accuracy-v1", dim); // GH-90000
        FusionResult result = e.fuseAudioVisual(makeAudio(dim, 0.85), makeVisual(dim, 0.75)); // GH-90000
        assertThat(result.fusedEmbeddings()).hasSize(dim); // GH-90000
        assertThat(result.fusionConfidence()).isBetween(0.0, 1.0); // GH-90000
    }

    // ── Text-image accuracy ───────────────────────────────────────────────────

    @Test
    @DisplayName("high-confidence text-image fusion has confidence > 0.5 [GH-90000]")
    void highConfidenceTextImageFusion() { // GH-90000
        TextFeatures text = makeText(DIM, "caption text", 0.92); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.88); // GH-90000
        FusionResult result = engine.fuseTextImage(text, visual); // GH-90000
        assertThat(result.fusionConfidence()).isGreaterThan(0.5); // GH-90000
    }

    @Test
    @DisplayName("text-image fusion produces distinct output from audio-visual fusion [GH-90000]")
    void textImageDistinctFromAudioVisual() { // GH-90000
        TextFeatures text = makeText(DIM, "image caption", 0.9); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.8); // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.9); // GH-90000
        double[] textImageFused = engine.fuseTextImage(text, visual).fusedEmbeddings(); // GH-90000
        double[] audioVisualFused = engine.fuseAudioVisual(audio, visual).fusedEmbeddings(); // GH-90000
        // Different input types should produce different outputs
        assertThat(textImageFused).isNotEqualTo(audioVisualFused); // GH-90000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AudioFeatures makeAudio(int dim, double confidence) { // GH-90000
        double[] emb = new double[dim];
        for (int i = 0; i < dim; i++) emb[i] = (double) i / dim * confidence; // GH-90000
        return new AudioFeatures(emb, confidence); // GH-90000
    }

    private static VisualFeatures makeVisual(int dim, double confidence) { // GH-90000
        double[] emb = new double[dim];
        double[] boxes = new double[4];
        for (int i = 0; i < dim; i++) emb[i] = (1.0 - (double) i / dim) * confidence; // GH-90000
        return new VisualFeatures(emb, boxes, confidence); // GH-90000
    }

    private static TextFeatures makeText(int dim, String text, double confidence) { // GH-90000
        double[] emb = new double[dim];
        for (int i = 0; i < dim; i++) emb[i] = Math.cos((double) i / dim) * confidence; // GH-90000
        return new TextFeatures(emb, text, confidence); // GH-90000
    }

    private static AudioFeatures makeNoisyAudio(int dim) { // GH-90000
        double[] emb = new double[dim];
        for (int i = 0; i < dim; i++) emb[i] = (i % 7 == 0) ? Double.MIN_VALUE : (double) i / dim; // GH-90000
        return new AudioFeatures(emb, 0.4); // GH-90000
    }

    private static VisualFeatures makeNoisyVisual(int dim) { // GH-90000
        double[] emb = new double[dim];
        double[] boxes = new double[4];
        for (int i = 0; i < dim; i++) emb[i] = (i % 5 == 0) ? 0.0 : (double) (dim - i) / dim; // GH-90000
        return new VisualFeatures(emb, boxes, 0.3); // GH-90000
    }

    private static org.assertj.core.data.Offset<Double> within(double d) { // GH-90000
        return org.assertj.core.data.Offset.offset(d); // GH-90000
    }
}
