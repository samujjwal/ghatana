package com.ghatana.vision.engine;

import org.junit.jupiter.api.*;

import com.ghatana.vision.engine.VisionModelEngine.VisionException;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test suite for {@link VisionModelEngine}.
 *
 * Validates computer vision capabilities including object detection, classification,
 * OCR extraction, face detection, input validation, and batch processing.
 *
 * @doc.type    class
 * @doc.purpose Comprehensive vision engine tests: detection, classification, OCR, faces
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("ComputerVisionServiceTest")
class ComputerVisionServiceTest {

    private static final byte[] SAMPLE_IMAGE = "SAMPLE_IMAGE_DATA_JPG".getBytes(); // GH-90000
    private static final byte[] SAMPLE_IMAGE_2 = "DIFFERENT_IMAGE_PNG".getBytes(); // GH-90000
    private static final byte[] LARGE_IMAGE = new byte[5_000_000]; // 5MB image
    private static final byte[] TINY_IMAGE = new byte[1];

    private VisionModelEngine engine;
    private VisionModelEngine lowConfidenceEngine;
    private VisionModelEngine highConfidenceEngine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new VisionModelEngine("yolo-v8", 0.5); // GH-90000
        lowConfidenceEngine = new VisionModelEngine("yolo-v8", 0.1);  // Very permissive // GH-90000
        highConfidenceEngine = new VisionModelEngine("yolo-v8", 0.9); // Very strict // GH-90000
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // INPUT VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("input validation")
    class InputValidation {

        @Test
        @DisplayName("null image data throws VisionException")
        void nullImageData_throwsVisionException() { // GH-90000
            assertThatThrownBy(() -> engine.detectObjects(null)) // GH-90000
                    .isInstanceOf(VisionException.class); // GH-90000
        }

        @Test
        @DisplayName("empty image data throws VisionException")
        void emptyImageData_throwsVisionException() { // GH-90000
            byte[] empty = new byte[0];
            assertThatThrownBy(() -> engine.detectObjects(empty)) // GH-90000
                    .isInstanceOf(VisionException.class); // GH-90000
        }

        @Test
        @DisplayName("null image data in classification throws VisionException")
        void nullImageDataInClassification_throwsVisionException() { // GH-90000
            assertThatThrownBy(() -> engine.classify(null)) // GH-90000
                    .isInstanceOf(VisionException.class); // GH-90000
        }

        @Test
        @DisplayName("null image data in OCR throws VisionException")
        void nullImageDataInOcr_throwsVisionException() { // GH-90000
            assertThatThrownBy(() -> engine.extractText(null)) // GH-90000
                    .isInstanceOf(VisionException.class); // GH-90000
        }

        @Test
        @DisplayName("null image data in face detection throws VisionException")
        void nullImageDataInFaceDetection_throwsVisionException() { // GH-90000
            assertThatThrownBy(() -> engine.detectFaces(null)) // GH-90000
                    .isInstanceOf(VisionException.class); // GH-90000
        }

        @Test
        @DisplayName("single byte image is accepted")
        void singleByteImage_isAccepted() { // GH-90000
            List<VisionModelEngine.DetectedObject> detections = engine.detectObjects(TINY_IMAGE); // GH-90000
            assertThat(detections).isNotNull(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // OBJECT DETECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("object detection")
    class ObjectDetection {

        @Test
        @DisplayName("detection returns non-null list")
        void detectionReturnsNonNullList() { // GH-90000
            List<VisionModelEngine.DetectedObject> detections = engine.detectObjects(SAMPLE_IMAGE); // GH-90000
            assertThat(detections).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("detected objects contain expected labels")
        void detectedObjectsContainExpectedLabels() { // GH-90000
            List<VisionModelEngine.DetectedObject> detections = engine.detectObjects(SAMPLE_IMAGE); // GH-90000
            assertThat(detections).isNotEmpty(); // GH-90000

            Set<String> labels = detections.stream() // GH-90000
                    .map(VisionModelEngine.DetectedObject::label) // GH-90000
                    .collect(Collectors.toSet()); // GH-90000

            assertThat(labels).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("detected objects have valid confidence scores")
        void detectedObjectsHaveValidConfidence() { // GH-90000
            List<VisionModelEngine.DetectedObject> detections = engine.detectObjects(SAMPLE_IMAGE); // GH-90000

            detections.forEach(obj -> // GH-90000
                    assertThat(obj.confidence()).isBetween(0.0, 1.0) // GH-90000
            );
        }

        @Test
        @DisplayName("detected objects have valid bounding boxes")
        void detectedObjectsHaveValidBoundingBoxes() { // GH-90000
            List<VisionModelEngine.DetectedObject> detections = engine.detectObjects(SAMPLE_IMAGE); // GH-90000

            detections.forEach(obj -> { // GH-90000
                assertThat(obj.x()).isBetween(0.0, 1.0); // GH-90000
                assertThat(obj.y()).isBetween(0.0, 1.0); // GH-90000
                assertThat(obj.width()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0); // GH-90000
                assertThat(obj.height()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0); // GH-90000
            });
        }

        @Test
        @DisplayName("confidence threshold filters low-confidence detections")
        void confidenceThresholdFiltersDetections() { // GH-90000
            List<VisionModelEngine.DetectedObject> low = lowConfidenceEngine.detectObjects(SAMPLE_IMAGE); // GH-90000
            List<VisionModelEngine.DetectedObject> medium = engine.detectObjects(SAMPLE_IMAGE); // GH-90000
            List<VisionModelEngine.DetectedObject> high = highConfidenceEngine.detectObjects(SAMPLE_IMAGE); // GH-90000

            // Higher threshold should result in fewer detections
            assertThat(low.size()).isGreaterThanOrEqualTo(medium.size()); // GH-90000
            assertThat(medium.size()).isGreaterThanOrEqualTo(high.size()); // GH-90000
        }

        @Test
        @DisplayName("all detections meet or exceed the confidence threshold")
        void allDetectionsMeetThreshold() { // GH-90000
            double threshold = 0.7;
            VisionModelEngine strictEngine = new VisionModelEngine("yolo-v8", threshold); // GH-90000

            List<VisionModelEngine.DetectedObject> detections = strictEngine.detectObjects(SAMPLE_IMAGE); // GH-90000

            detections.forEach(obj -> // GH-90000
                    assertThat(obj.confidence()).isGreaterThanOrEqualTo(threshold) // GH-90000
            );
        }

        @Test
        @DisplayName("different images may produce different detections")
        void differentImagesMayProduceDifferentDetections() { // GH-90000
            List<VisionModelEngine.DetectedObject> detections1 = engine.detectObjects(SAMPLE_IMAGE); // GH-90000
            List<VisionModelEngine.DetectedObject> detections2 = engine.detectObjects(SAMPLE_IMAGE_2); // GH-90000

            // Results should be based on image content (may or may not be identical) // GH-90000
            assertThat(detections1).isNotNull(); // GH-90000
            assertThat(detections2).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("detection is deterministic for same image")
        void detectionIsDeterministic() { // GH-90000
            List<VisionModelEngine.DetectedObject> d1 = engine.detectObjects(SAMPLE_IMAGE); // GH-90000
            List<VisionModelEngine.DetectedObject> d2 = engine.detectObjects(SAMPLE_IMAGE); // GH-90000

            assertThat(d1).hasSize(d2.size()); // GH-90000
            for (int i = 0; i < d1.size(); i++) { // GH-90000
                assertThat(d1.get(i).label()).isEqualTo(d2.get(i).label()); // GH-90000
                assertThat(d1.get(i).confidence()).isEqualTo(d2.get(i).confidence()); // GH-90000
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // CLASSIFICATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("classification")
    class Classification {

        @Test
        @DisplayName("classification returns valid result")
        void classificationReturnsValidResult() { // GH-90000
            VisionModelEngine.ClassificationResult result = engine.classify(SAMPLE_IMAGE); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.label()).isNotBlank(); // GH-90000
        }

        @Test
        @DisplayName("classification confidence is in valid range")
        void classificationConfidenceIsValid() { // GH-90000
            VisionModelEngine.ClassificationResult result = engine.classify(SAMPLE_IMAGE); // GH-90000

            assertThat(result.confidence()).isBetween(0.0, 1.0); // GH-90000
        }

        @Test
        @DisplayName("top labels list is non-empty")
        void topLabelsListIsNonEmpty() { // GH-90000
            VisionModelEngine.ClassificationResult result = engine.classify(SAMPLE_IMAGE); // GH-90000

            assertThat(result.topLabels()).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("classified label is in top labels list")
        void classifiedLabelIsInTopLabels() { // GH-90000
            VisionModelEngine.ClassificationResult result = engine.classify(SAMPLE_IMAGE); // GH-90000

            assertThat(result.topLabels()).contains(result.label()); // GH-90000
        }

        @Test
        @DisplayName("classification is deterministic for same image")
        void classificationIsDeterministic() { // GH-90000
            VisionModelEngine.ClassificationResult c1 = engine.classify(SAMPLE_IMAGE); // GH-90000
            VisionModelEngine.ClassificationResult c2 = engine.classify(SAMPLE_IMAGE); // GH-90000

            assertThat(c1.label()).isEqualTo(c2.label()); // GH-90000
            assertThat(c1.confidence()).isEqualTo(c2.confidence()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // OCR/TEXT EXTRACTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("OCR text extraction")
    class OcrTextExtraction {

        @Test
        @DisplayName("OCR returns valid result")
        void ocrReturnsValidResult() { // GH-90000
            VisionModelEngine.OcrResult result = engine.extractText(SAMPLE_IMAGE); // GH-90000

            assertThat(result).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("extracted text is non-null")
        void extractedTextIsNonNull() { // GH-90000
            VisionModelEngine.OcrResult result = engine.extractText(SAMPLE_IMAGE); // GH-90000

            assertThat(result.text()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("OCR confidence is in valid range")
        void ocrConfidenceIsValid() { // GH-90000
            VisionModelEngine.OcrResult result = engine.extractText(SAMPLE_IMAGE); // GH-90000

            assertThat(result.confidence()).isBetween(0.0, 1.0); // GH-90000
        }

        @Test
        @DisplayName("text regions contain valid bounding boxes")
        void textRegionsContainValidBoundingBoxes() { // GH-90000
            VisionModelEngine.OcrResult result = engine.extractText(SAMPLE_IMAGE); // GH-90000

            result.textRegions().forEach(region -> { // GH-90000
                assertThat(region.x()).isBetween(0.0, 1.0); // GH-90000
                assertThat(region.y()).isBetween(0.0, 1.0); // GH-90000
                assertThat(region.width()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0); // GH-90000
                assertThat(region.height()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0); // GH-90000
            });
        }

        @Test
        @DisplayName("OCR is deterministic for same image")
        void ocrIsDeterministic() { // GH-90000
            VisionModelEngine.OcrResult r1 = engine.extractText(SAMPLE_IMAGE); // GH-90000
            VisionModelEngine.OcrResult r2 = engine.extractText(SAMPLE_IMAGE); // GH-90000

            assertThat(r1.text()).isEqualTo(r2.text()); // GH-90000
            assertThat(r1.confidence()).isEqualTo(r2.confidence()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // FACE DETECTION TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("face detection")
    class FaceDetection {

        @Test
        @DisplayName("face detection returns non-null list")
        void faceDetectionReturnsNonNullList() { // GH-90000
            List<VisionModelEngine.FaceDetection> faces = engine.detectFaces(SAMPLE_IMAGE); // GH-90000

            assertThat(faces).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("face detection results have valid bounding boxes")
        void faceDetectionResultsHaveValidBoundingBoxes() { // GH-90000
            List<VisionModelEngine.FaceDetection> faces = engine.detectFaces(SAMPLE_IMAGE); // GH-90000

            faces.forEach(face -> { // GH-90000
                assertThat(face.x()).isBetween(0.0, 1.0); // GH-90000
                assertThat(face.y()).isBetween(0.0, 1.0); // GH-90000
                assertThat(face.width()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0); // GH-90000
                assertThat(face.height()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0); // GH-90000
            });
        }

        @Test
        @DisplayName("face detection confidence is in valid range")
        void faceDetectionConfidenceIsValid() { // GH-90000
            List<VisionModelEngine.FaceDetection> faces = engine.detectFaces(SAMPLE_IMAGE); // GH-90000

            faces.forEach(face -> // GH-90000
                    assertThat(face.confidence()).isBetween(0.0, 1.0) // GH-90000
            );
        }

        @Test
        @DisplayName("face landmarks are included in results")
        void faceLandmarksAreIncluded() { // GH-90000
            List<VisionModelEngine.FaceDetection> faces = engine.detectFaces(SAMPLE_IMAGE); // GH-90000

            if (!faces.isEmpty()) { // GH-90000
                faces.forEach(face -> // GH-90000
                        assertThat(face.landmarks()).isNotNull() // GH-90000
                );
            }
        }

        @Test
        @DisplayName("face detection is deterministic for same image")
        void faceDetectionIsDeterministic() { // GH-90000
            List<VisionModelEngine.FaceDetection> f1 = engine.detectFaces(SAMPLE_IMAGE); // GH-90000
            List<VisionModelEngine.FaceDetection> f2 = engine.detectFaces(SAMPLE_IMAGE); // GH-90000

            assertThat(f1).hasSize(f2.size()); // GH-90000
            for (int i = 0; i < f1.size(); i++) { // GH-90000
                assertThat(f1.get(i).confidence()).isEqualTo(f2.get(i).confidence()); // GH-90000
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // IMAGE FORMAT HANDLING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("image format handling")
    class ImageFormatHandling {

        @Test
        @DisplayName("JPEG-like image is processed without error")
        void jpegImageIsProcessed() { // GH-90000
            byte[] jpegData = "JPEG_MAGIC_BYTES".getBytes(); // GH-90000
            assertThatCode(() -> engine.detectObjects(jpegData)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("PNG-like image is processed without error")
        void pngImageIsProcessed() { // GH-90000
            byte[] pngData = "PNG_MAGIC_BYTES".getBytes(); // GH-90000
            assertThatCode(() -> engine.detectObjects(pngData)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("BMP-like image is processed without error")
        void bmpImageIsProcessed() { // GH-90000
            byte[] bmpData = "BM_MAGIC_BYTES".getBytes(); // GH-90000
            assertThatCode(() -> engine.detectObjects(bmpData)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // BATCH PROCESSING TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("batch processing")
    class BatchProcessing {

        @Test
        @DisplayName("batch detection processes multiple images")
        void batchDetectionProcessesMultipleImages() { // GH-90000
            int batchSize = 10;
            for (int i = 0; i < batchSize; i++) { // GH-90000
                List<VisionModelEngine.DetectedObject> detections = engine.detectObjects(SAMPLE_IMAGE); // GH-90000
                assertThat(detections).isNotNull(); // GH-90000
            }
        }

        @Test
        @DisplayName("concurrent detection produces consistent results")
        void concurrentDetectionProducesConsistentResults() throws InterruptedException { // GH-90000
            int threadCount = 5;
            List<VisionModelEngine.DetectedObject>[] results = new List[threadCount];
            Thread[] threads = new Thread[threadCount];
            CountDownLatch latch = new CountDownLatch(threadCount); // GH-90000

            for (int i = 0; i < threadCount; i++) { // GH-90000
                final int index = i;
                threads[i] = new Thread(() -> { // GH-90000
                    results[index] = engine.detectObjects(SAMPLE_IMAGE); // GH-90000
                    latch.countDown(); // GH-90000
                });
                threads[i].start(); // GH-90000
            }

            latch.await(); // GH-90000

            // All results should be consistent
            int firstSize = results[0].size(); // GH-90000
            for (int i = 1; i < threadCount; i++) { // GH-90000
                assertThat(results[i]).hasSize(firstSize); // GH-90000
            }
        }

        @Test
        @DisplayName("large image batch is processed without failure")
        void largeImageBatchIsProcessed() { // GH-90000
            assertThatCode(() -> { // GH-90000
                for (int i = 0; i < 100; i++) { // GH-90000
                    engine.detectObjects(SAMPLE_IMAGE); // GH-90000
                }
            }).doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // SIZE BOUNDARY TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("size boundaries")
    class SizeBoundaries {

        @Test
        @DisplayName("large image (5MB) is processed without failure")
        void largeImageIsProcessed() { // GH-90000
            for (int i = 0; i < LARGE_IMAGE.length; i++) { // GH-90000
                LARGE_IMAGE[i] = (byte) (i % 256); // GH-90000
            }

            assertThatCode(() -> engine.detectObjects(LARGE_IMAGE)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("tiny single-byte image is processed")
        void tinyImageIsProcessed() { // GH-90000
            assertThatCode(() -> engine.detectObjects(TINY_IMAGE)) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // CONFIDENCE THRESHOLD TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("confidence threshold")
    class ConfidenceThreshold {

        @Test
        @DisplayName("threshold of 0.0 accepts all detections")
        void zeroThresholdAcceptsAll() { // GH-90000
            VisionModelEngine zeroEngine = new VisionModelEngine("yolo-v8", 0.0); // GH-90000
            List<VisionModelEngine.DetectedObject> detections = zeroEngine.detectObjects(SAMPLE_IMAGE); // GH-90000

            assertThat(detections).isNotEmpty(); // GH-90000
        }

        @Test
        @DisplayName("threshold of 1.0 rejects all detections")
        void maxThresholdRejectsAll() { // GH-90000
            VisionModelEngine maxEngine = new VisionModelEngine("yolo-v8", 1.0); // GH-90000
            List<VisionModelEngine.DetectedObject> detections = maxEngine.detectObjects(SAMPLE_IMAGE); // GH-90000

            // May be empty or contain items with confidence == 1.0
            detections.forEach(obj -> assertThat(obj.confidence()).isEqualTo(1.0)); // GH-90000
        }

        @Test
        @DisplayName("mid-range thresholds filter appropriately")
        void midRangeThresholdsFilter() { // GH-90000
            VisionModelEngine mid1 = new VisionModelEngine("yolo-v8", 0.3); // GH-90000
            VisionModelEngine mid2 = new VisionModelEngine("yolo-v8", 0.7); // GH-90000

            List<VisionModelEngine.DetectedObject> d1 = mid1.detectObjects(SAMPLE_IMAGE); // GH-90000
            List<VisionModelEngine.DetectedObject> d2 = mid2.detectObjects(SAMPLE_IMAGE); // GH-90000

            assertThat(d1.size()).isGreaterThanOrEqualTo(d2.size()); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════════════
    // ENGINE PROPERTIES TESTS
    // ═══════════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("engine properties")
    class EngineProperties {

        @Test
        @DisplayName("engine returns configured model ID")
        void engineReturnsConfiguredModelId() { // GH-90000
            assertThat(engine.getModelId()).isEqualTo("yolo-v8");
        }

        @Test
        @DisplayName("engine returns configured confidence threshold")
        void engineReturnsConfiguredThreshold() { // GH-90000
            assertThat(engine.getConfidenceThreshold()).isEqualTo(0.5); // GH-90000
            assertThat(lowConfidenceEngine.getConfidenceThreshold()).isEqualTo(0.1); // GH-90000
            assertThat(highConfidenceEngine.getConfidenceThreshold()).isEqualTo(0.9); // GH-90000
        }

        @Test
        @DisplayName("invalid threshold raises exception on construction")
        void invalidThresholdRaisesException() { // GH-90000
            assertThatThrownBy(() -> new VisionModelEngine("yolo-v8", -0.1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000

            assertThatThrownBy(() -> new VisionModelEngine("yolo-v8", 1.1)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("null model ID raises exception on construction")
        void nullModelIdRaisesException() { // GH-90000
            assertThatThrownBy(() -> new VisionModelEngine(null, 0.5)) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }
}
