package com.ghatana.audio.video.vision.yolo;

import com.ghatana.audio.video.vision.model.*;
import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.scijava.nativelib.NativeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * YOLOv8 integration for cost-optimized object detection.
 * Uses GPL-3.0 licensed YOLOv8 with OpenCV for high-performance detection.
 * 
 * @doc.type component
 * @doc.purpose Object detection using YOLOv8
 * @doc.layer vision-core
 * @doc.pattern adapter
 */
public class YoloV8Adapter {
    
    private static final Logger LOG = LoggerFactory.getLogger(YoloV8Adapter.class);
    
    static {
        try {
            // Load OpenCV native library
            NativeLoader.loadLibrary(Core.NATIVE_LIBRARY_NAME);
            LOG.info("OpenCV native library loaded successfully");
        } catch (Exception e) {
            LOG.error("Failed to load OpenCV native library", e);
            throw new RuntimeException("OpenCV initialization failed", e);
        }
    }
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Path modelPath;
    private final Map<String, Integer> classMapping;
    private final double confidenceThreshold;
    private final double nmsThreshold;

    /** OpenCV DNN network loaded from the ONNX model file. Null until {@link #initialize} is called. */
    private Net net;
    
    // YOLOv8 model parameters
    private static final int INPUT_SIZE = 640;
    private static final int MAX_DETECTIONS = 100;
    
    // COCO dataset class names (80 classes)
    private static final List<String> COCO_CLASSES = Arrays.asList(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard",
        "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase",
        "scissors", "teddy bear", "hair drier", "toothbrush"
    );
    
    public YoloV8Adapter(Path modelPath, double confidenceThreshold, double nmsThreshold) {
        this.modelPath = modelPath;
        this.confidenceThreshold = confidenceThreshold;
        this.nmsThreshold = nmsThreshold;
        this.classMapping = createClassMapping();
    }
    
    /**
     * Initialize YOLOv8 model.
     * 
     * @param modelName Model name (e.g., "yolov8n", "yolov8s")
     * @throws RuntimeException if initialization fails
     */
    public void initialize(String modelName) {
        if (initialized.get()) {
            LOG.warn("YOLOv8 already initialized");
            return;
        }
        
        try {
            // Load YOLOv8 model
            Path modelFile = modelPath.resolve(modelName + ".pt");
            if (!Files.exists(modelFile)) {
                throw new RuntimeException("YOLOv8 model not found: " + modelFile);
            }
            
            // Initialize OpenCV DNN module with YOLOv8
            initializeYoloModel(modelFile.toString());
            
            initialized.set(true);
            LOG.info("YOLOv8 initialized successfully with model: {}", modelName);
            
        } catch (Exception e) {
            LOG.error("YOLOv8 initialization failed", e);
            throw new RuntimeException("YOLOv8 initialization failed", e);
        }
    }
    
    /**
     * Detect objects in an image.
     * 
     * @param imageData Image data as byte array
     * @param options Detection options
     * @return List of detected objects
     */
    public List<DetectedObject> detectObjects(byte[] imageData, DetectionOptions options) {
        ensureInitialized();
        
        try {
            // Convert byte array to OpenCV Mat
            Mat image = bytesToMat(imageData);
            
            // Preprocess image
            Mat processedImage = preprocessImage(image);
            
            // Run YOLOv8 inference
            List<YoloDetection> rawDetections = runInference(processedImage);
            
            // Post-process detections
            List<DetectedObject> detectedObjects = postProcessDetections(rawDetections, image.size());
            
            // Apply filtering based on options
            if (options.getTargetClasses() != null && !options.getTargetClasses().isEmpty()) {
                detectedObjects = filterByClasses(detectedObjects, options.getTargetClasses());
            }
            
            // Sort by confidence
            detectedObjects.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
            
            // Limit results
            if (options.getMaxDetections() > 0) {
                detectedObjects = detectedObjects.subList(0, Math.min(detectedObjects.size(), options.getMaxDetections()));
            }
            
            return detectedObjects;
            
        } catch (Exception e) {
            LOG.error("Object detection failed", e);
            throw new RuntimeException("Object detection failed", e);
        }
    }
    
    /**
     * Detect objects in a video frame.
     * 
     * @param frame Video frame
     * @param options Detection options
     * @return List of detected objects
     */
    public List<DetectedObject> detectObjectsInFrame(Mat frame, DetectionOptions options) {
        ensureInitialized();
        
        try {
            // Preprocess frame
            Mat processedFrame = preprocessImage(frame);
            
            // Run YOLOv8 inference
            List<YoloDetection> rawDetections = runInference(processedFrame);
            
            // Post-process detections
            List<DetectedObject> detectedObjects = postProcessDetections(rawDetections, frame.size());
            
            // Apply filtering
            if (options.getTargetClasses() != null && !options.getTargetClasses().isEmpty()) {
                detectedObjects = filterByClasses(detectedObjects, options.getTargetClasses());
            }
            
            // Sort and limit
            detectedObjects.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
            if (options.getMaxDetections() > 0) {
                detectedObjects = detectedObjects.subList(0, Math.min(detectedObjects.size(), options.getMaxDetections()));
            }
            
            return detectedObjects;
            
        } catch (Exception e) {
            LOG.error("Frame object detection failed", e);
            throw new RuntimeException("Frame object detection failed", e);
        }
    }
    
    /**
     * Check if adapter is initialized.
     * 
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized.get();
    }
    
    /**
     * Get supported class names.
     * 
     * @return list of supported class names
     */
    public List<String> getSupportedClasses() {
        return new ArrayList<>(COCO_CLASSES);
    }
    
    // Private methods
    
    private void ensureInitialized() {
        if (!initialized.get()) {
            throw new IllegalStateException("YOLOv8 not initialized");
        }
    }
    
    private Map<String, Integer> createClassMapping() {
        Map<String, Integer> mapping = new HashMap<>();
        for (int i = 0; i < COCO_CLASSES.size(); i++) {
            mapping.put(COCO_CLASSES.get(i), i);
        }
        return mapping;
    }
    
    private void initializeYoloModel(String modelPath) {
        LOG.info("Loading YOLOv8 ONNX model from: {}", modelPath);
        net = Dnn.readNetFromOnnx(modelPath);
        if (net.empty()) {
            throw new RuntimeException("OpenCV DNN failed to load ONNX model: " + modelPath);
        }
        // Prefer GPU backend when available; fall back to CPU transparently.
        net.setPreferableBackend(Dnn.DNN_BACKEND_DEFAULT);
        net.setPreferableTarget(Dnn.DNN_TARGET_CPU);
        LOG.info("YOLOv8 ONNX model loaded — output layers: {}", getOutputLayerNames());
    }
    
    private Mat bytesToMat(byte[] imageData) {
        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
            return bufferedImageToMat(bufferedImage);
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert image data to Mat", e);
        }
    }
    
    private Mat bufferedImageToMat(BufferedImage bufferedImage) {
        // Convert BufferedImage to OpenCV Mat
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        int channels = bufferedImage.getColorModel().hasAlpha() ? 4 : 3;
        
        Mat mat = new Mat(height, width, CvType.CV_8UC(channels));
        
        // Convert pixel data
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = bufferedImage.getRGB(x, y);
                
                if (channels == 3) {
                    // BGR format for OpenCV
                    mat.put(y, x, 
                        (rgb >> 16) & 0xFF,  // Red -> Blue
                        (rgb >> 8) & 0xFF,   // Green -> Green
                        rgb & 0xFF           // Blue -> Red
                    );
                } else {
                    // BGRA format
                    mat.put(y, x,
                        (rgb >> 16) & 0xFF,  // Red -> Blue
                        (rgb >> 8) & 0xFF,   // Green -> Green
                        rgb & 0xFF,          // Blue -> Red
                        (rgb >> 24) & 0xFF   // Alpha
                    );
                }
            }
        }
        
        return mat;
    }
    
    private Mat preprocessImage(Mat image) {
        // Resize to input size
        Mat resized = new Mat();
        Imgproc.resize(image, resized, new Size(INPUT_SIZE, INPUT_SIZE));
        
        // Normalize pixel values
        Mat normalized = new Mat();
        resized.convertTo(normalized, CvType.CV_32F, 1.0/255.0);
        
        // Convert to NCHW format if needed
        Mat processed = Dnn.blobFromImage(normalized, 1.0, new Size(INPUT_SIZE, INPUT_SIZE), 
                             new Scalar(0, 0, 0), true, false);
        
        return processed;
    }
    
    /**
     * Run YOLOv8 ONNX inference using OpenCV DNN.
     *
     * <p>{@code processedImage} must be a 4-D blob produced by {@link Dnn#blobFromImage}
     * (shape: {@code [1, 3, 640, 640]}) so it can be passed directly to {@link Net#setInput}.
     *
     * @param processedImage preprocessed blob ready for the ONNX model
     * @return raw detection candidates (before NMS and confidence filtering)
     */
    private List<YoloDetection> runInference(Mat processedImage) {
        if (net == null || net.empty()) {
            LOG.warn("YOLOv8 net not initialised — returning empty detections");
            return Collections.emptyList();
        }

        // Feed the preprocessed blob directly into the network.
        net.setInput(processedImage);

        // Forward pass through all output layers.
        List<Mat> outputs = new ArrayList<>();
        net.forward(outputs, getOutputLayerNames());

        List<YoloDetection> detections = parseYoloOutput(outputs);
        LOG.debug("YOLOv8 raw predictions before NMS: {}", detections.size());
        return detections;
    }

    /**
     * Resolve output layer names from the loaded DNN network.
     * Typically returns {@code ["output0"]} for YOLOv8 ONNX exports.
     */
    private List<String> getOutputLayerNames() {
        MatOfInt outLayerIds = net.getUnconnectedOutLayers();
        List<String> allNames = net.getLayerNames();
        List<String> outputNames = new ArrayList<>();
        for (int id : outLayerIds.toArray()) {
            outputNames.add(allNames.get(id - 1)); // layer IDs are 1-based
        }
        return outputNames;
    }

    /**
     * Parse YOLOv8 ONNX output tensors into {@link YoloDetection} candidates.
     *
     * <p>YOLOv8 ONNX export produces a single output of shape {@code [1, 84, 8400]} where:
     * <ul>
     *   <li>Rows 0–3: centre-x, centre-y, width, height (in model input pixels)</li>
     *   <li>Rows 4–83: class probability scores for the 80 COCO classes</li>
     *   <li>8400 columns: anchor-free prediction candidates</li>
     * </ul>
     */
    private List<YoloDetection> parseYoloOutput(List<Mat> outputs) {
        if (outputs.isEmpty()) return Collections.emptyList();

        // Reshape from [1, 84, 8400] to [84, 8400] for indexed access.
        Mat raw = outputs.get(0);
        Mat result = raw.reshape(1, raw.size(1));

        int numBoxes   = result.cols();          // 8400
        int numClasses = result.rows() - 4;     // 80

        List<YoloDetection> candidates = new ArrayList<>();
        for (int i = 0; i < numBoxes; i++) {
            // Find highest-scoring class.
            double maxScore  = 0.0;
            int    bestClass = 0;
            for (int c = 0; c < numClasses; c++) {
                double score = result.get(4 + c, i)[0];
                if (score > maxScore) {
                    maxScore  = score;
                    bestClass = c;
                }
            }
            if (maxScore < confidenceThreshold) continue;

            // YOLOv8 bounding-box format: cx, cy, w, h (in 640×640 model coords).
            double cx = result.get(0, i)[0];
            double cy = result.get(1, i)[0];
            double bw = result.get(2, i)[0];
            double bh = result.get(3, i)[0];

            // Convert to top-left origin.
            double x = cx - bw / 2.0;
            double y = cy - bh / 2.0;

            candidates.add(new YoloDetection(bestClass, maxScore, x, y, bw, bh));
        }
        return candidates;
    }
    
    private List<DetectedObject> postProcessDetections(List<YoloDetection> rawDetections, Size originalSize) {
        List<DetectedObject> detectedObjects = new ArrayList<>();
        
        // Scale bounding boxes back to original image size
        double scaleX = originalSize.width / INPUT_SIZE;
        double scaleY = originalSize.height / INPUT_SIZE;
        
        for (YoloDetection detection : rawDetections) {
            if (detection.confidence >= confidenceThreshold) {
                // Scale coordinates
                double x = detection.x * scaleX;
                double y = detection.y * scaleY;
                double width = detection.width * scaleX;
                double height = detection.height * scaleY;
                
                // Create bounding box
                BoundingBox bbox = BoundingBox.builder()
                    .x(x)
                    .y(y)
                    .width(width)
                    .height(height)
                    .build();
                
                // Create object attributes
                ObjectAttributes attributes = ObjectAttributes.builder()
                    .size(calculateObjectSize(width, height))
                    .build();
                
                // Create detected object
                DetectedObject obj = DetectedObject.builder()
                    .className(COCO_CLASSES.get(detection.classId))
                    .confidence(detection.confidence)
                    .boundingBox(bbox)
                    .attributes(attributes)
                    .build();
                
                detectedObjects.add(obj);
            }
        }
        
        // Apply Non-Maximum Suppression
        return applyNonMaximumSuppression(detectedObjects);
    }
    
    private List<DetectedObject> applyNonMaximumSuppression(List<DetectedObject> detections) {
        List<DetectedObject> filtered = new ArrayList<>();
        
        for (DetectedObject detection : detections) {
            boolean keep = true;
            
            for (DetectedObject kept : filtered) {
                double iou = detection.getBoundingBox().calculateIoU(kept.getBoundingBox());
                if (iou > nmsThreshold && detection.getClassName().equals(kept.getClassName())) {
                    keep = false;
                    break;
                }
            }
            
            if (keep) {
                filtered.add(detection);
            }
        }
        
        return filtered;
    }
    
    private List<DetectedObject> filterByClasses(List<DetectedObject> detections, Set<String> targetClasses) {
        List<DetectedObject> filtered = new ArrayList<>();
        for (DetectedObject detection : detections) {
            if (targetClasses.contains(detection.getClassName())) {
                filtered.add(detection);
            }
        }
        return filtered;
    }
    
    private String calculateObjectSize(double width, double height) {
        double area = width * height;
        if (area < 10000) return "small";
        if (area < 50000) return "medium";
        return "large";
    }
    
    // Internal detection representation
    private static class YoloDetection {
        final int classId;
        final double confidence;
        final double x;
        final double y;
        final double width;
        final double height;
        
        YoloDetection(int classId, double confidence, double x, double y, double width, double height) {
            this.classId = classId;
            this.confidence = confidence;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }
}
