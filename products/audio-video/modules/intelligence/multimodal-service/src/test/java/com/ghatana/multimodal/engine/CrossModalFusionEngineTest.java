package com.ghatana.multimodal.engine;

import com.ghatana.multimodal.engine.CrossModalFusionEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link CrossModalFusionEngine}.
 *
 * @doc.type    class
 * @doc.purpose CrossModalFusionEngine: audio-visual fusion, text-image fusion, missing modality handling
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("CrossModalFusionEngineTest")
class CrossModalFusionEngineTest {

    private static final int DIM = 64;

    private CrossModalFusionEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new CrossModalFusionEngine("crossmodal-v1", DIM); // GH-90000
    }

    // ── Audio-visual fusion ───────────────────────────────────────────────────

    @Test
    @DisplayName("fuseAudioVisual returns non-null result for valid inputs")
    void audioVisualFusionReturnsResult() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.8); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.7); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, visual); // GH-90000
        assertThat(result).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("fused embeddings have the expected dimension")
    void fusedEmbeddingsDimension() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.9); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.8); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, visual); // GH-90000
        assertThat(result.fusedEmbeddings()).hasSize(DIM); // GH-90000
    }

    @Test
    @DisplayName("fusion confidence is within [0, 1]")
    void audioVisualFusionConfidenceNormalized() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.85); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.75); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, visual); // GH-90000
        assertThat(result.fusionConfidence()).isBetween(0.0, 1.0); // GH-90000
    }

    @Test
    @DisplayName("modality weights map contains audio and visual keys")
    void audioVisualWeightsPresent() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.8); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.6); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, visual); // GH-90000
        assertThat(result.modalityWeights()).containsKeys("audio", "visual"); // GH-90000
    }

    @Test
    @DisplayName("fusion result is marked not missing modality when both present")
    void audioVisualBothPresentNotMissing() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.8); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.7); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, visual); // GH-90000
        assertThat(result.hadMissingModality()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("missing audio modality is detected and flagged")
    void missingAudioFlagged() { // GH-90000
        AudioFeatures emptyAudio = new AudioFeatures(new double[0], 0.0); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.7); // GH-90000
        FusionResult result = engine.fuseAudioVisual(emptyAudio, visual); // GH-90000
        assertThat(result.hadMissingModality()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("missing visual modality is detected and flagged")
    void missingVisualFlagged() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.8); // GH-90000
        VisualFeatures emptyVisual = new VisualFeatures(new double[0], new double[0], 0.0); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, emptyVisual); // GH-90000
        assertThat(result.hadMissingModality()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("both modalities absent throws FusionException")
    void bothModalitiesAbsentThrows() { // GH-90000
        AudioFeatures emptyAudio = new AudioFeatures(new double[0], 0.0); // GH-90000
        VisualFeatures emptyVisual = new VisualFeatures(new double[0], new double[0], 0.0); // GH-90000
        assertThatThrownBy(() -> engine.fuseAudioVisual(emptyAudio, emptyVisual)) // GH-90000
                .isInstanceOf(CrossModalFusionEngine.FusionException.class); // GH-90000
    }

    @Test
    @DisplayName("null audio argument throws NullPointerException")
    void nullAudioThrows() { // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.7); // GH-90000
        assertThatThrownBy(() -> engine.fuseAudioVisual(null, visual)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @Test
    @DisplayName("null visual argument throws NullPointerException")
    void nullVisualThrows() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.8); // GH-90000
        assertThatThrownBy(() -> engine.fuseAudioVisual(audio, null)) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    // ── Text-image fusion ─────────────────────────────────────────────────────

    @Test
    @DisplayName("fuseTextImage returns non-null result for valid inputs")
    void textImageFusionReturnsResult() { // GH-90000
        TextFeatures text = makeText(DIM, "Hello world", 0.9); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.8); // GH-90000
        FusionResult result = engine.fuseTextImage(text, visual); // GH-90000
        assertThat(result).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("text-image fused embeddings have expected dimension")
    void textImageFusedEmbeddingsDimension() { // GH-90000
        TextFeatures text = makeText(DIM, "Caption text", 0.85); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.75); // GH-90000
        FusionResult result = engine.fuseTextImage(text, visual); // GH-90000
        assertThat(result.fusedEmbeddings()).hasSize(DIM); // GH-90000
    }

    @Test
    @DisplayName("text-image fusion modality weights contain text and visual keys")
    void textImageWeightsPresent() { // GH-90000
        TextFeatures text = makeText(DIM, "Photo of a cat", 0.9); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.8); // GH-90000
        FusionResult result = engine.fuseTextImage(text, visual); // GH-90000
        assertThat(result.modalityWeights()).containsKeys("text", "visual"); // GH-90000
    }

    @Test
    @DisplayName("missing text modality is flagged in text-image fusion")
    void missingTextFlagged() { // GH-90000
        TextFeatures emptyText = new TextFeatures(new double[0], "", 0.0); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.8); // GH-90000
        FusionResult result = engine.fuseTextImage(emptyText, visual); // GH-90000
        assertThat(result.hadMissingModality()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("both text and visual absent throws FusionException in text-image fusion")
    void textImageBothAbsentThrows() { // GH-90000
        TextFeatures emptyText = new TextFeatures(new double[0], "", 0.0); // GH-90000
        VisualFeatures emptyVisual = new VisualFeatures(new double[0], new double[0], 0.0); // GH-90000
        assertThatThrownBy(() -> engine.fuseTextImage(emptyText, emptyVisual)) // GH-90000
                .isInstanceOf(CrossModalFusionEngine.FusionException.class); // GH-90000
    }

    // ── Cross-modal attention (structural) ──────────────────────────────────── // GH-90000

    @Test
    @DisplayName("different audio inputs produce different fused embeddings")
    void differentAudioProducesDifferentEmbeddings() { // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.8); // GH-90000
        AudioFeatures audio1 = makeAudio(DIM, 0.9); // GH-90000
        AudioFeatures audio2 = makeAudioWithValues(DIM, 0.5, 42.0); // GH-90000
        double[] fused1 = engine.fuseAudioVisual(audio1, visual).fusedEmbeddings(); // GH-90000
        double[] fused2 = engine.fuseAudioVisual(audio2, visual).fusedEmbeddings(); // GH-90000
        // At least one element should differ
        boolean differs = false;
        for (int i = 0; i < DIM; i++) { // GH-90000
            if (fused1[i] != fused2[i]) { differs = true; break; } // GH-90000
        }
        assertThat(differs).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("processing time is recorded and non-negative")
    void processingTimeIsNonNegative() { // GH-90000
        AudioFeatures audio = makeAudio(DIM, 0.85); // GH-90000
        VisualFeatures visual = makeVisual(DIM, 0.75); // GH-90000
        FusionResult result = engine.fuseAudioVisual(audio, visual); // GH-90000
        assertThat(result.processingTime().isNegative()).isFalse(); // GH-90000
    }

    // ── Embedding dimension variants ──────────────────────────────────────────

    @ParameterizedTest(name = "dim={0}") // GH-90000
    @ValueSource(ints = {8, 32, 64, 128, 256, 512}) // GH-90000
    @DisplayName("various embedding dimensions produce correctly-sized fusion output")
    void variousDimensionsProduceCorrectOutput(int dim) { // GH-90000
        CrossModalFusionEngine e = new CrossModalFusionEngine("crossmodal-v1", dim); // GH-90000
        FusionResult result = e.fuseAudioVisual(makeAudio(dim, 0.8), makeVisual(dim, 0.7)); // GH-90000
        assertThat(result.fusedEmbeddings()).hasSize(dim); // GH-90000
    }

    // ── Engine metadata ───────────────────────────────────────────────────────

    @Test
    @DisplayName("engine reports correct model ID")
    void engineModelId() { // GH-90000
        assertThat(engine.getModelId()).isEqualTo("crossmodal-v1");
    }

    @Test
    @DisplayName("engine reports correct embedding dimension")
    void engineEmbeddingDim() { // GH-90000
        assertThat(engine.getEmbeddingDim()).isEqualTo(DIM); // GH-90000
    }

    @Test
    @DisplayName("non-positive embedding dimension throws IllegalArgumentException")
    void nonPositiveDimThrows() { // GH-90000
        assertThatThrownBy(() -> new CrossModalFusionEngine("m", 0)) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AudioFeatures makeAudio(int dim, double confidence) { // GH-90000
        double[] emb = new double[dim];
        for (int i = 0; i < dim; i++) emb[i] = (double) i / dim; // GH-90000
        return new AudioFeatures(emb, confidence); // GH-90000
    }

    private AudioFeatures makeAudioWithValues(int dim, double confidence, double fillValue) { // GH-90000
        double[] emb = new double[dim];
        java.util.Arrays.fill(emb, fillValue); // GH-90000
        return new AudioFeatures(emb, confidence); // GH-90000
    }

    private VisualFeatures makeVisual(int dim, double confidence) { // GH-90000
        double[] emb = new double[dim];
        double[] boxes = new double[4];
        for (int i = 0; i < dim; i++) emb[i] = 1.0 - (double) i / dim; // GH-90000
        return new VisualFeatures(emb, boxes, confidence); // GH-90000
    }

    private TextFeatures makeText(int dim, String text, double confidence) { // GH-90000
        double[] emb = new double[dim];
        for (int i = 0; i < dim; i++) emb[i] = Math.sin(i); // GH-90000
        return new TextFeatures(emb, text, confidence); // GH-90000
    }
}
