package com.ghatana.audio.video.vision.detection;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * YOLOv8 object detection using ONNX Runtime.
 * 
 * <p>Performs real-time object detection on images using YOLOv8 models.
 * Supports various YOLOv8 variants (nano, small, medium, large, xlarge).
 * 
 * @doc.type class
 * @doc.purpose YOLOv8 object detection engine
 * @doc.layer ml-inference
 */
public class YoloV8Detector {

    private static final Logger LOG = LoggerFactory.getLogger(YoloV8Detector.class);

    private static final int INPUT_WIDTH = 640;
    private static final int INPUT_HEIGHT = 640;
    private static final float CONFIDENCE_THRESHOLD = 0.5f;
    private static final float NMS_THRESHOLD = 0.4f;

    static {
        // Load OpenCV native library
        try {
            nu.pattern.OpenCV.loadLocally();
            LOG.info("OpenCV loaded successfully");
        } catch (Exception e) {
            LOG.error("Failed to load OpenCV", e);
        }
    }

    @SuppressWarnings("unused") // Will be used when ONNX Runtime is integrated
    private final Path modelPath;
    private final List<String> classNames;
    @SuppressWarnings("unused") // Will be used when ONNX Runtime is integrated
    private final float confidenceThreshold;
    @SuppressWarnings("unused") // Will be used when ONNX Runtime is integrated
    private final float nmsThreshold;

    /**
     * Detection result.
     */
    public static class Detection {
        private final String className;
        private final float confidence;
        private final BoundingBox bbox;
        private final int classId;

        public Detection(String className, float confidence, BoundingBox bbox, int classId) {
            this.className = className;
            this.confidence = confidence;
            this.bbox = bbox;
            this.classId = classId;
        }

        public String getClassName() { return className; }
        public float getConfidence() { return confidence; }
        public BoundingBox getBbox() { return bbox; }
        public int getClassId() { return classId; }

        @Override
        public String toString() {
            return String.format("Detection{class=%s, conf=%.2f, bbox=%s}", 
                className, confidence, bbox);
        }
    }

    /**
     * Bounding box coordinates.
     */
    public static class BoundingBox {
        private final float x;
        private final float y;
        private final float width;
        private final float height;

        public BoundingBox(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public float getX() { return x; }
        public float getY() { return y; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }

        public float getArea() {
            return width * height;
        }

        public float iou(BoundingBox other) {
            float x1 = Math.max(this.x, other.x);
            float y1 = Math.max(this.y, other.y);
            float x2 = Math.min(this.x + this.width, other.x + other.width);
            float y2 = Math.min(this.y + this.height, other.y + other.height);

            float intersectionArea = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
            float unionArea = this.getArea() + other.getArea() - intersectionArea;

            return unionArea > 0 ? intersectionArea / unionArea : 0;
        }

        @Override
        public String toString() {
            return String.format("BBox{x=%.1f, y=%.1f, w=%.1f, h=%.1f}", x, y, width, height);
        }
    }

    /**
     * Detection configuration.
     */
    public static class DetectionConfig {
        private final float confidenceThreshold;
        private final float nmsThreshold;
        private final int maxDetections;
        private final List<String> targetClasses;

        private DetectionConfig(Builder builder) {
            this.confidenceThreshold = builder.confidenceThreshold;
            this.nmsThreshold = builder.nmsThreshold;
            this.maxDetections = builder.maxDetections;
            this.targetClasses = builder.targetClasses;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private float confidenceThreshold = CONFIDENCE_THRESHOLD;
            private float nmsThreshold = NMS_THRESHOLD;
            private int maxDetections = 100;
            private List<String> targetClasses = new ArrayList<>();

            public Builder confidenceThreshold(float threshold) {
                this.confidenceThreshold = threshold;
                return this;
            }

            public Builder nmsThreshold(float threshold) {
                this.nmsThreshold = threshold;
                return this;
            }

            public Builder maxDetections(int max) {
                this.maxDetections = max;
                return this;
            }

            public Builder targetClasses(List<String> classes) {
                this.targetClasses = new ArrayList<>(classes);
                return this;
            }

            public DetectionConfig build() {
                return new DetectionConfig(this);
            }
        }

        public float getConfidenceThreshold() { return confidenceThreshold; }
        public float getNmsThreshold() { return nmsThreshold; }
        public int getMaxDetections() { return maxDetections; }
        public List<String> getTargetClasses() { return targetClasses; }
    }

    public YoloV8Detector(Path modelPath, List<String> classNames) {
        this(modelPath, classNames, CONFIDENCE_THRESHOLD, NMS_THRESHOLD);
    }

    public YoloV8Detector(Path modelPath, List<String> classNames, 
                          float confidenceThreshold, float nmsThreshold) {
        this.modelPath = modelPath;
        this.classNames = new ArrayList<>(classNames);
        this.confidenceThreshold = confidenceThreshold;
        this.nmsThreshold = nmsThreshold;
        
        LOG.info("Initialized YOLOv8 detector with model: {}, classes: {}", 
            modelPath.getFileName(), classNames.size());
    }

    /**
     * Detect objects in an image file.
     * 
     * @param imagePath Path to the image file
     * @param config Detection configuration
     * @return List of detected objects
     * @throws IOException If detection fails
     */
    public List<Detection> detect(Path imagePath, DetectionConfig config) throws IOException {
        Mat image = Imgcodecs.imread(imagePath.toString());
        if (image.empty()) {
            throw new IOException("Failed to load image: " + imagePath);
        }

        try {
            return detect(image, config);
        } finally {
            image.release();
        }
    }

    /**
     * Detect objects in an image Mat.
     * 
     * @param image OpenCV Mat containing the image
     * @param config Detection configuration
     * @return List of detected objects
     */
    public List<Detection> detect(Mat image, DetectionConfig config) {
        // Preprocess image
        Mat preprocessed = preprocessImage(image);
        
        // Run inference (placeholder - would use ONNX Runtime in production)
        float[][][] output = runInference(preprocessed);
        
        // Post-process results
        List<Detection> detections = postProcess(output, image.width(), image.height(), config);
        
        preprocessed.release();
        
        LOG.info("Detected {} objects", detections.size());
        return detections;
    }

    /**
     * Detect objects in raw image bytes.
     * 
     * @param imageBytes Raw image bytes
     * @param config Detection configuration
     * @return List of detected objects
     * @throws IOException If detection fails
     */
    public List<Detection> detectFromBytes(byte[] imageBytes, DetectionConfig config) throws IOException {
        MatOfByte matOfByte = new MatOfByte(imageBytes);
        Mat image = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR);
        matOfByte.release();

        if (image.empty()) {
            throw new IOException("Failed to decode image from bytes");
        }

        try {
            return detect(image, config);
        } finally {
            image.release();
        }
    }

    private Mat preprocessImage(Mat image) {
        // Resize to model input size
        Mat resized = new Mat();
        Size size = new Size(INPUT_WIDTH, INPUT_HEIGHT);
        Imgproc.resize(image, resized, size);

        // Convert BGR to RGB
        Mat rgb = new Mat();
        Imgproc.cvtColor(resized, rgb, Imgproc.COLOR_BGR2RGB);
        resized.release();

        // Normalize to [0, 1]
        Mat normalized = new Mat();
        rgb.convertTo(normalized, CvType.CV_32F, 1.0 / 255.0);
        rgb.release();

        return normalized;
    }

    private float[][][] runInference(Mat preprocessed) {
        // Placeholder for ONNX Runtime inference
        // In production, this would:
        // 1. Convert Mat to ONNX tensor
        // 2. Run model.run() with ONNX Runtime
        // 3. Extract output tensor
        
        // For now, return mock output matching YOLOv8 format
        // YOLOv8 output: [batch, 84, 8400] where 84 = 4 bbox coords + 80 class scores
        int numDetections = 8400;
        float[][][] output = new float[1][84][numDetections];
        
        LOG.debug("Running inference (mock implementation)");
        return output;
    }

    private List<Detection> postProcess(float[][][] output, int originalWidth, int originalHeight, 
                                        DetectionConfig config) {
        List<Detection> allDetections = new ArrayList<>();
        
        int numDetections = output[0][0].length;
        
        // Extract detections
        for (int i = 0; i < numDetections; i++) {
            // Get bbox coordinates (center_x, center_y, width, height)
            float cx = output[0][0][i];
            float cy = output[0][1][i];
            float w = output[0][2][i];
            float h = output[0][3][i];
            
            // Get class scores
            float maxScore = 0;
            int maxClassId = 0;
            for (int c = 0; c < classNames.size() && c < 80; c++) {
                float score = output[0][4 + c][i];
                if (score > maxScore) {
                    maxScore = score;
                    maxClassId = c;
                }
            }
            
            // Filter by confidence threshold
            if (maxScore < config.getConfidenceThreshold()) {
                continue;
            }
            
            // Filter by target classes if specified
            String className = maxClassId < classNames.size() ? 
                classNames.get(maxClassId) : "unknown";
            
            if (!config.getTargetClasses().isEmpty() && 
                !config.getTargetClasses().contains(className)) {
                continue;
            }
            
            // Convert to corner coordinates and scale to original image size
            float scaleX = (float) originalWidth / INPUT_WIDTH;
            float scaleY = (float) originalHeight / INPUT_HEIGHT;
            
            float x = (cx - w / 2) * scaleX;
            float y = (cy - h / 2) * scaleY;
            float width = w * scaleX;
            float height = h * scaleY;
            
            BoundingBox bbox = new BoundingBox(x, y, width, height);
            allDetections.add(new Detection(className, maxScore, bbox, maxClassId));
        }
        
        // Apply Non-Maximum Suppression
        List<Detection> nmsDetections = applyNMS(allDetections, config.getNmsThreshold());
        
        // Limit to max detections
        if (nmsDetections.size() > config.getMaxDetections()) {
            nmsDetections = nmsDetections.subList(0, config.getMaxDetections());
        }
        
        return nmsDetections;
    }

    private List<Detection> applyNMS(List<Detection> detections, float nmsThreshold) {
        // Sort by confidence (descending)
        detections.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        
        List<Detection> result = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];
        
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            
            Detection current = detections.get(i);
            result.add(current);
            
            // Suppress overlapping detections
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                
                Detection other = detections.get(j);
                
                // Only suppress if same class
                if (current.getClassId() != other.getClassId()) continue;
                
                float iou = current.getBbox().iou(other.getBbox());
                if (iou > nmsThreshold) {
                    suppressed[j] = true;
                }
            }
        }
        
        return result;
    }

    /**
     * Get COCO class names (80 classes).
     */
    public static List<String> getCocoClassNames() {
        return Arrays.asList(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
            "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
            "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
            "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        );
    }
}
