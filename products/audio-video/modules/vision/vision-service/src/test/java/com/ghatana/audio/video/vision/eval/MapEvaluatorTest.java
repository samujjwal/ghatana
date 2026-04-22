package com.ghatana.audio.video.vision.eval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link MapEvaluator}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for mAP evaluation infrastructure
 * @doc.layer product
 * @doc.pattern TestCase
 */
@DisplayName("MapEvaluator [GH-90000]")
class MapEvaluatorTest {

    // -------------------------------------------------------------------------
    // IoU unit tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("IoU: identical boxes → 1.0 [GH-90000]")
    void iou_identicalBoxes_isOne() { // GH-90000
        double iou = MapEvaluator.iou(box(0, 0, 10, 10), box(0, 0, 10, 10)); // GH-90000
        assertThat(iou).isCloseTo(1.0, within(1e-9)); // GH-90000
    }

    @Test
    @DisplayName("IoU: non-overlapping boxes → 0.0 [GH-90000]")
    void iou_noOverlap_isZero() { // GH-90000
        double iou = MapEvaluator.iou(box(0, 0, 5, 5), box(10, 10, 5, 5)); // GH-90000
        assertThat(iou).isCloseTo(0.0, within(1e-9)); // GH-90000
    }

    @Test
    @DisplayName("IoU: 50%% overlap → ~0.33 [GH-90000]")
    void iou_halfOverlap_correctValue() { // GH-90000
        // a = [0,0,10,10], b = [5,0,10,10]: intersection=50, union=150
        double iou = MapEvaluator.iou(box(0, 0, 10, 10), box(5, 0, 10, 10)); // GH-90000
        assertThat(iou).isCloseTo(50.0 / 150.0, within(1e-6)); // GH-90000
    }

    // -------------------------------------------------------------------------
    // AP + mAP tests
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("mAP: perfect predictions → 1.0 [GH-90000]")
    void map_perfectPredictions_isOne() { // GH-90000
        var gts = List.of(gt("img1", "cat",  box(0, 0, 10, 10))); // GH-90000
        var preds = List.of(pred("img1", "cat", box(0, 0, 10, 10), 0.99)); // GH-90000

        var result = MapEvaluator.computeMap(gts, preds, 0.5); // GH-90000
        assertThat(result.mAp()).isCloseTo(1.0, within(1e-6)); // GH-90000
    }

    @Test
    @DisplayName("mAP: no predictions → 0.0 [GH-90000]")
    void map_noPredictions_isZero() { // GH-90000
        var gts = List.of(gt("img1", "dog", box(0, 0, 10, 10))); // GH-90000
        var result = MapEvaluator.computeMap(gts, List.of(), 0.5); // GH-90000
        assertThat(result.mAp()).isCloseTo(0.0, within(1e-6)); // GH-90000
    }

    @Test
    @DisplayName("mAP: wrong class → 0.0 [GH-90000]")
    void map_wrongClass_isZero() { // GH-90000
        var gts  = List.of(gt("img1", "cat", box(0, 0, 10, 10))); // GH-90000
        var preds = List.of(pred("img1", "dog", box(0, 0, 10, 10), 0.9)); // wrong class // GH-90000
        var result = MapEvaluator.computeMap(gts, preds, 0.5); // GH-90000
        assertThat(result.apFor("cat [GH-90000]")).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("mAP: low IoU prediction → 0.0 at iou=0.5 [GH-90000]")
    void map_lowIouPrediction_isZero() { // GH-90000
        var gts   = List.of(gt("img1", "car", box(0, 0, 100, 100))); // GH-90000
        var preds = List.of(pred("img1", "car", box(90, 90, 100, 100), 0.9)); // barely overlapping // GH-90000
        var result = MapEvaluator.computeMap(gts, preds, 0.5); // GH-90000
        assertThat(result.apFor("car [GH-90000]")).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("mAP: multi-class, partial match → between 0 and 1 [GH-90000]")
    void map_multiClass_partialMatch() { // GH-90000
        var gts = List.of( // GH-90000
            gt("img1", "cat", box(0, 0, 10, 10)), // GH-90000
            gt("img1", "dog", box(20, 20, 10, 10)) // GH-90000
        );
        var preds = List.of( // GH-90000
            pred("img1", "cat", box(0, 0, 10, 10), 0.9),   // correct // GH-90000
            pred("img1", "dog", box(100, 100, 10, 10), 0.8) // wrong location // GH-90000
        );
        var result = MapEvaluator.computeMap(gts, preds, 0.5); // GH-90000
        assertThat(result.mAp()).isGreaterThan(0.0).isLessThan(1.0); // GH-90000
        assertThat(result.apFor("cat [GH-90000]")).isCloseTo(1.0, within(1e-6));
        assertThat(result.apFor("dog [GH-90000]")).isCloseTo(0.0, within(1e-6));
    }

    @Test
    @DisplayName("apFor: unknown class → 0.0 [GH-90000]")
    void apFor_unknownClass_isZero() { // GH-90000
        var result = MapEvaluator.computeMap(List.of(), List.of(), 0.5); // GH-90000
        assertThat(result.apFor("unicorn [GH-90000]")).isCloseTo(0.0, within(1e-9));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static double[] box(double x, double y, double w, double h) { // GH-90000
        return new double[]{x, y, w, h};
    }

    private static MapEvaluator.GroundTruth gt(String img, String cls, double[] box) { // GH-90000
        return new MapEvaluator.GroundTruth(img, cls, box); // GH-90000
    }

    private static MapEvaluator.Prediction pred(String img, String cls, double[] box, double conf) { // GH-90000
        return new MapEvaluator.Prediction(img, cls, box, conf); // GH-90000
    }
}
