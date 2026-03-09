package com.ghatana.audio.video.vision.detection;

import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YOLOv8 object detection with ONNX Runtime integration.
 * 
 * <p>Production-ready implementation using ONNX Runtime for inference.
 * Supports YOLOv8 models exported to ONNX format.
 * 
 * @doc.type class
 * @doc.purpose YOLOv8 object detection with ONNX Runtime
 * @doc.layer ml-inference
 */
public class OnnxYoloV8Detector implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(OnnxYoloV8Detector.class);

    private static final int INPUT_WIDTH = 640;
    private static final int INPUT_HEIGHT = 640;
    private static final int NUM_CHANNELS = 3;

    static {
        // Load OpenCV native library
        try {
            nu.pattern.OpenCV.loadLocally();
            LOG.info("OpenCV loaded successfully");
        } catch (Exception e) {
            LOG.error("Failed to load OpenCV", e);
        }
    }

    private final OrtEnvironment env;
    private final OrtSession session;
    private final List<String> classNames;
    private final float confidenceThreshold;
    private final float nmsThreshold;

    /**
     * Create detector with ONNX model.
     * 
     * @param modelPath Path to ONNX model file
     * @param classNames List of class names
     * @param confidenceThreshold Confidence threshold for detections
     * @param nmsThreshold NMS IoU threshold
     * @throws OrtException If model loading fails
     */
    public OnnxYoloV8Detector(Path modelPath, List<String> classNames, 
                              float confidenceThreshold, float nmsThreshold) throws OrtException {
        this.env = OrtEnvironment.getEnvironment();
        this.classNames = new ArrayList<>(classNames);
        this.confidenceThreshold = confidenceThreshold;
        this.nmsThreshold = nmsThreshold;

        // Create session options
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        
        // Enable CPU optimizations
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
        options.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors());
        
        // Load model
        this.session = env.createSession(modelPath.toString(), options);
        
        LOG.info("Loaded YOLOv8 ONNX model: {}, classes: {}", 
            modelPath.getFileName(), classNames.size());
    }

    /**
     * Detect objects in an image file.
     * 
     * @param imagePath Path to image file
     * @param config Detection configuration
     * @return List of detected objects
     * @throws IOException If detection fails
     */
    public List<YoloV8Detector.Detection> detect(Path imagePath, 
                                                  YoloV8Detector.DetectionConfig config) 
            throws IOException {
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
     * Detect objects in an OpenCV Mat.
     * 
     * @param image OpenCV Mat containing the image
     * @param config Detection configuration
     * @return List of detected objects
     */
    public List<YoloV8Detector.Detection> detect(Mat image, 
                                                  YoloV8Detector.DetectionConfig config) {
        int originalWidth = image.width();
        int originalHeight = image.height();

        // Preprocess image
        Mat preprocessed = preprocessImage(image);
        
        try {
            // Convert to ONNX tensor and run inference
            float[][][] output = runInference(preprocessed);
            
            // Post-process results
            return postProcess(output, originalWidth, originalHeight, config);
            
        } catch (OrtException e) {
            LOG.error("ONNX inference failed", e);
            return new ArrayList<>();
        } finally {
            preprocessed.release();
        }
    }

    /**
     * Detect objects from raw image bytes.
     * 
     * @param imageBytes Raw image bytes
     * @param config Detection configuration
     * @return List of detected objects
     * @throws IOException If detection fails
     */
    public List<YoloV8Detector.Detection> detectFromBytes(byte[] imageBytes, 
                                                           YoloV8Detector.DetectionConfig config) 
            throws IOException {
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

    private float[][][] runInference(Mat preprocessed) throws OrtException {
        // Convert Mat to ONNX tensor format: [batch, channels, height, width]
        float[] inputData = new float[1 * NUM_CHANNELS * INPUT_HEIGHT * INPUT_WIDTH];
        
        // OpenCV Mat is in HWC format, need to convert to CHW
        int idx = 0;
        for (int c = 0; c < NUM_CHANNELS; c++) {
            for (int h = 0; h < INPUT_HEIGHT; h++) {
                for (int w = 0; w < INPUT_WIDTH; w++) {
                    double[] pixel = preprocessed.get(h, w);
                    inputData[idx++] = (float) pixel[c];
                }
            }
        }

        // Create ONNX tensor
        long[] shape = {1, NUM_CHANNELS, INPUT_HEIGHT, INPUT_WIDTH};
        OnnxTensor inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(inputData), shape);

        // Run inference
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(session.getInputNames().iterator().next(), inputTensor);
        
        OrtSession.Result result = session.run(inputs);
        
        // Extract output tensor
        OnnxTensor outputTensor = (OnnxTensor) result.get(0);
        float[][][] output = (float[][][]) outputTensor.getValue();
        
        // Cleanup
        inputTensor.close();
        result.close();
        
        return output;
    }

    private List<YoloV8Detector.Detection> postProcess(float[][][] output, 
                                                        int originalWidth, 
                                                        int originalHeight, 
                                                        YoloV8Detector.DetectionConfig config) {
        List<YoloV8Detector.Detection> allDetections = new ArrayList<>();
        
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
            
            YoloV8Detector.BoundingBox bbox = new YoloV8Detector.BoundingBox(x, y, width, height);
            allDetections.add(new YoloV8Detector.Detection(className, maxScore, bbox, maxClassId));
        }
        
        // Apply Non-Maximum Suppression
        List<YoloV8Detector.Detection> nmsDetections = applyNMS(allDetections, config.getNmsThreshold());
        
        // Limit to max detections
        if (nmsDetections.size() > config.getMaxDetections()) {
            nmsDetections = nmsDetections.subList(0, config.getMaxDetections());
        }
        
        LOG.info("Detected {} objects after NMS", nmsDetections.size());
        return nmsDetections;
    }

    private List<YoloV8Detector.Detection> applyNMS(List<YoloV8Detector.Detection> detections, 
                                                     float nmsThreshold) {
        // Sort by confidence (descending)
        detections.sort((a, b) -> Float.compare(b.getConfidence(), a.getConfidence()));
        
        List<YoloV8Detector.Detection> result = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];
        
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            
            YoloV8Detector.Detection current = detections.get(i);
            result.add(current);
            
            // Suppress overlapping detections
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                
                YoloV8Detector.Detection other = detections.get(j);
                
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

    @Override
    public void close() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (OrtException e) {
            LOG.error("Failed to close ONNX session", e);
        }
    }

    /**
     * Get model input info.
     */
    public String getModelInfo() throws OrtException {
        StringBuilder info = new StringBuilder();
        info.append("Model Inputs:\n");
        for (NodeInfo input : session.getInputInfo().values()) {
            info.append("  ").append(input.getName()).append(": ").append(input.getInfo()).append("\n");
        }
        info.append("Model Outputs:\n");
        for (NodeInfo output : session.getOutputInfo().values()) {
            info.append("  ").append(output.getName()).append(": ").append(output.getInfo()).append("\n");
        }
        return info.toString();
    }
}
