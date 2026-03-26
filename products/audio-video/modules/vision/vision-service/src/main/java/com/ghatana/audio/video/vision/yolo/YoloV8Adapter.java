package com.ghatana.audio.video.vision.yolo;

import com.ghatana.audio.video.vision.detection.VisionDetector;
import com.ghatana.audio.video.vision.model.*;
import org.opencv.core.*;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
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

/**
 * YOLOv8 integration for cost-optimized object detection.
 * Uses GPL-3.0 licensed YOLOv8 with OpenCV for high-performance detection.
 * 
 * @doc.type component
 * @doc.purpose Object detection using YOLOv8
 * @doc.layer vision-core
 * @doc.pattern adapter
 */
public class YoloV8Adapter implements VisionDetector {
    
    private static final Logger LOG = LoggerFactory.getLogger(YoloV8Adapter.class);
    
    static {
        try {
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
    private Net net;
    
    private static final int INPUT_SIZE = 640;
    private static final int MAX_DETECTIONS = 100;
    
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
    
    public void initialize(String modelName) {
        if (initialized.get()) {
            LOG.warn("YOLOv8 already initialized");
            return;
        }
        
        try {
            Path modelFile = modelPath.resolve(modelName + ".pt");
            if (!Files.exists(modelFile)) {
                throw new RuntimeException("YOLOv8 model not found: " + modelFile);
            }
            
            initializeYoloModel(modelFile.toString());
            initialized.set(true);
            LOG.info("YOLOv8 initialized successfully with model: {}", modelName);
            
        } catch (Exception e) {
            LOG.error("YOLOv8 initialization failed", e);
            throw new RuntimeException("YOLOv8 initialization failed", e);
        }
    }
    
    public List<DetectedObject> detectObjects(byte[] imageData, DetectionOptions options) {
        ensureInitialized();
        
        try {
            Mat image = bytesToMat(imageData);
            Mat processedImage = preprocessImage(image);
            List<YoloDetection> rawDetections = runInference(processedImage);
            List<DetectedObject> detectedObjects = postProcessDetections(rawDetections, image.size());
            
            if (options.getTargetClasses() != null && !options.getTargetClasses().isEmpty()) {
                detectedObjects = filterByClasses(detectedObjects, options.getTargetClasses());
            }
            
            detectedObjects.sort((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()));
            
            if (options.getMaxDetections() > 0) {
                detectedObjects = detectedObjects.subList(0, Math.min(detectedObjects.size(), options.getMaxDetections()));
            }
            
            return detectedObjects;
            
        } catch (Exception e) {
            LOG.error("Object detection failed", e);
            throw new RuntimeException("Object detection failed", e);
        }
    }
    
    public List<DetectedObject> detectObjectsInFrame(Mat frame, DetectionOptions options) {
        ensureInitialized();
        
        try {
            Mat processedFrame = preprocessImage(frame);
            List<YoloDetection> rawDetections = runInference(processedFrame);
            List<DetectedObject> detectedObjects = postProcessDetections(rawDetections, frame.size());
            
            if (options.getTargetClasses() != null && !options.getTargetClasses().isEmpty()) {
                detectedObjects = filterByClasses(detectedObjects, options.getTargetClasses());
            }
            
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
    
    public boolean isInitialized() {
        return initialized.get();
    }
    
    public List<String> getSupportedClasses() {
        return new ArrayList<>(COCO_CLASSES);
    }
    
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
        net = Dnn.readNetFromONNX(modelPath);
        if (net.empty()) {
            throw new RuntimeException("OpenCV DNN failed to load ONNX model: " + modelPath);
        }
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
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        int channels = bufferedImage.getColorModel().hasAlpha() ? 4 : 3;
        
        Mat mat = new Mat(height, width, CvType.CV_8UC(channels));
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = bufferedImage.getRGB(x, y);
                if (channels == 3) {
                    mat.put(y, x, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
                } else {
                    mat.put(y, x, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, (rgb >> 24) & 0xFF);
                }
            }
        }
        return mat;
    }
    
    private Mat preprocessImage(Mat image) {
        Mat resized = new Mat();
        Imgproc.resize(image, resized, new Size(INPUT_SIZE, INPUT_SIZE));
        Mat normalized = new Mat();
        resized.convertTo(normalized, CvType.CV_32F, 1.0/255.0);
        return Dnn.blobFromImage(normalized, 1.0, new Size(INPUT_SIZE, INPUT_SIZE), new Scalar(0, 0, 0), true, false);
    }
    
    private List<YoloDetection> runInference(Mat processedImage) {
        if (net == null || net.empty()) {
            LOG.warn("YOLOv8 net not initialised — returning empty detections");
            return Collections.emptyList();
        }

        net.setInput(processedImage);

        List<Mat> outputs = new ArrayList<>();
        net.forward(outputs, getOutputLayerNames());

        List<YoloDetection> detections = parseYoloOutput(outputs);
        LOG.debug("YOLOv8 raw predictions before NMS: {}", detections.size());
        return detections;
    }

    private List<String> getOutputLayerNames() {
        MatOfInt outLayerIds = net.getUnconnectedOutLayers();
        List<String> allNames = net.getLayerNames();
        List<String> outputNames = new ArrayList<>();
        for (int id : outLayerIds.toArray()) {
            outputNames.add(allNames.get(id - 1));
        }
        return outputNames;
    }

    private List<YoloDetection> parseYoloOutput(List<Mat> outputs) {
        if (outputs.isEmpty()) return Collections.emptyList();

        Mat raw = outputs.get(0);
        Mat result = raw.reshape(1, raw.size(1));

        int numBoxes = result.cols();
        int numClasses = result.rows() - 4;

        List<YoloDetection> candidates = new ArrayList<>();
        for (int i = 0; i < numBoxes; i++) {
            double maxScore = 0.0;
            int bestClass = 0;
            for (int c = 0; c < numClasses; c++) {
                double score = result.get(4 + c, i)[0];
                if (score > maxScore) {
                    maxScore = score;
                    bestClass = c;
                }
            }
            if (maxScore < confidenceThreshold) continue;

            double cx = result.get(0, i)[0];
            double cy = result.get(1, i)[0];
            double bw = result.get(2, i)[0];
            double bh = result.get(3, i)[0];
            double x = cx - bw / 2.0;
            double y = cy - bh / 2.0;

            candidates.add(new YoloDetection(bestClass, maxScore, x, y, bw, bh));
        }
        return candidates;
    }
    
    private List<DetectedObject> postProcessDetections(List<YoloDetection> rawDetections, Size originalSize) {
        List<DetectedObject> detectedObjects = new ArrayList<>();
        double scaleX = originalSize.width / INPUT_SIZE;
        double scaleY = originalSize.height / INPUT_SIZE;
        
        for (YoloDetection detection : rawDetections) {
            if (detection.confidence >= confidenceThreshold) {
                double x = detection.x * scaleX;
                double y = detection.y * scaleY;
                double width = detection.width * scaleX;
                double height = detection.height * scaleY;
                
                BoundingBox bbox = BoundingBox.builder().x(x).y(y).width(width).height(height).build();
                ObjectAttributes attributes = ObjectAttributes.builder().size(calculateObjectSize(width, height)).build();
                DetectedObject obj = DetectedObject.builder()
                    .className(COCO_CLASSES.get(detection.classId))
                    .confidence(detection.confidence)
                    .boundingBox(bbox)
                    .attributes(attributes)
                    .build();
                detectedObjects.add(obj);
            }
        }
        
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