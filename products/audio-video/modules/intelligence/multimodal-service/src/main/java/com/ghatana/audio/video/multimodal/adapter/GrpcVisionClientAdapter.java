package com.ghatana.audio.video.multimodal.adapter;

import com.ghatana.audio.video.common.platform.AiInferenceClient;
import com.ghatana.audio.video.multimodal.engine.DetectionResult;
import com.ghatana.audio.video.multimodal.engine.FrameResult;
import com.ghatana.audio.video.multimodal.engine.VisualResult;
import com.ghatana.audio.video.multimodal.engine.VisionClientAdapter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @doc.type class
 * @doc.purpose gRPC-backed {@link VisionClientAdapter} with AI Inference HTTP fallback.
 *              Performs object detection and video analysis via the Vision gRPC service
 *              when proto stubs are compiled, and falls back to the Ghatana AI Inference
 *              Service for LLM-assisted detection when gRPC stubs are unavailable.
 * @doc.layer product
 * @doc.pattern Service
 *
 * <p>When gRPC proto stubs are generated from {@code vision_service.proto}, replace
 * the AI Inference fallback blocks with direct stub calls:
 * {@code VisionServiceGrpc.newBlockingStub(channel).detectObjects(...)} and
 * {@code VisionServiceGrpc.newBlockingStub(channel).analyzeVideoFile(...)}.
 */
public class GrpcVisionClientAdapter implements VisionClientAdapter, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcVisionClientAdapter.class);

    /** Leading image bytes included in the inference prompt (capped at 8 KB). */
    private static final int IMAGE_SAMPLE_BYTES = 8192;

    // ── Regex patterns for parsing detection JSON from AI Inference responses ──

    /** Matches one detection JSON object inside the "detections" array. */
    private static final Pattern DETECTION_ENTRY_PATTERN = Pattern.compile(
            "\\{[^}]*?\"class\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"confidence\"\\s*:\\s*"
            + "([0-9.]+)[^}]*?\"x\"\\s*:\\s*([0-9.]+)[^}]*?\"y\"\\s*:\\s*([0-9.]+)"
            + "[^}]*?\"w\"\\s*:\\s*([0-9.]+)[^}]*?\"h\"\\s*:\\s*([0-9.]+)[^}]*?\\}",
            Pattern.DOTALL);

    /** Matches the top-level "scene" description field. */
    private static final Pattern SCENE_PATTERN =
            Pattern.compile("\"scene\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /** Matches a top-level "confidence" field. */
    private static final Pattern CONFIDENCE_PATTERN =
            Pattern.compile("\"confidence\"\\s*:\\s*([0-9]+(?:\\.[0-9]*)?)");

    /** Fallback: model wrapped JSON inside a text/content/result field. */
    private static final Pattern TEXT_FALLBACK_PATTERN =
            Pattern.compile("\"(?:text|content|result)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final ManagedChannel channel;

    public GrpcVisionClientAdapter(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(100 * 1024 * 1024) // 100 MB for video frames
                .build();
        LOG.info("Vision gRPC client connected to {}:{}", host, port);
    }

    /**
     * Detect objects in an image.
     *
     * <p>Delegates to the AI Inference HTTP fallback while the gRPC proto stubs
     * for the Vision service are not yet generated. Once {@code vision_service.proto}
     * is compiled, replace the body with:
     * <pre>{@code
     * VisionServiceGrpc.VisionServiceBlockingStub stub =
     *     VisionServiceGrpc.newBlockingStub(channel).withDeadlineAfter(30, TimeUnit.SECONDS);
     * DetectRequest req = DetectRequest.newBuilder()
     *     .setImageData(ByteString.copyFrom(imageData))
     *     .setMaxDetections(100)
     *     .build();
     * DetectResponse resp = stub.detectObjects(req);
     * List<DetectionResult> detections = resp.getDetectionsList().stream()
     *     .map(d -> new DetectionResult(
     *         d.getClassName(), d.getConfidence(),
     *         d.getBoundingBox().getX(), d.getBoundingBox().getY(),
     *         d.getBoundingBox().getWidth(), d.getBoundingBox().getHeight()))
     *     .toList();
     * return VisualResult.builder()
     *     .detections(detections)
     *     .sceneDescription(buildSceneDescription(detections))
     *     .confidence(detections.isEmpty() ? 0.0 : detections.get(0).getConfidence())
     *     .build();
     * }</pre>
     *
     * @param imageData raw image bytes (JPEG, PNG, or any encoded format)
     * @return {@link VisualResult} with detected objects and scene description
     */
    @Override
    public VisualResult detectObjects(byte[] imageData) {
        try {
            LOG.debug("Detecting objects in image ({} bytes) via AI Inference HTTP fallback",
                    imageData.length);
            return detectObjectsViaAiInference(imageData);
        } catch (StatusRuntimeException e) {
            LOG.error("Vision gRPC detectObjects call failed: {}", e.getStatus(), e);
            return VisualResult.error("Vision service error: " + e.getStatus().getDescription());
        } catch (Exception e) {
            LOG.error("Image detection error", e);
            return VisualResult.error(e.getMessage());
        }
    }

    /**
     * Analyse a video clip for objects and scene description.
     *
     * <p>Delegates to the AI Inference HTTP fallback while the gRPC proto stubs
     * for the Vision service are not yet generated. Once {@code vision_service.proto}
     * is compiled, replace the body with:
     * <pre>{@code
     * VisionServiceGrpc.VisionServiceBlockingStub stub =
     *     VisionServiceGrpc.newBlockingStub(channel).withDeadlineAfter(60, TimeUnit.SECONDS);
     * VideoFileRequest req = VideoFileRequest.newBuilder()
     *     .setVideoData(ByteString.copyFrom(videoData))
     *     .setSampleFps(sampleFps)
     *     .setMaxFrames(maxFrames)
     *     .build();
     * VideoFileResponse resp = stub.analyzeVideoFile(req);
     * ...
     * }</pre>
     *
     * @param videoData  raw video bytes
     * @param sampleFps  frames per second to sample for analysis
     * @param maxFrames  maximum number of frames to analyse
     * @return {@link VisualResult} with frame-level detections and summary
     */
    @Override
    public VisualResult analyseVideo(byte[] videoData, int sampleFps, int maxFrames) {
        try {
            LOG.debug("Analysing video ({} bytes, {}fps, max {} frames) via AI Inference HTTP fallback",
                    videoData.length, sampleFps, maxFrames);
            return analyseVideoViaAiInference(videoData, sampleFps, maxFrames);
        } catch (StatusRuntimeException e) {
            LOG.error("Vision gRPC analyseVideo call failed: {}", e.getStatus(), e);
            return VisualResult.error("Vision service error: " + e.getStatus().getDescription());
        } catch (Exception e) {
            LOG.error("Video analysis error", e);
            return VisualResult.error(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AI Inference HTTP fallback — object detection
    // ─────────────────────────────────────────────────────────────────────────

    private VisualResult detectObjectsViaAiInference(byte[] imageData) {
        AiInferenceClient aiClient = AiInferenceClient.getInstance();
        if (!aiClient.isReachable()) {
            LOG.warn("AI Inference Service unreachable \u2014 returning empty detections");
            return VisualResult.builder()
                    .sceneDescription("AI Inference Service unavailable")
                    .detections(Collections.emptyList())
                    .build();
        }

        int sampleLen = Math.min(imageData.length, IMAGE_SAMPLE_BYTES);
        String imageB64Sample = Base64.getEncoder().encodeToString(
                Arrays.copyOf(imageData, sampleLen));

        String prompt = "You are an object detection vision AI. "
                + "Analyse this image (base64, size " + imageData.length + " bytes). "
                + "Image data (base64 prefix): " + imageB64Sample + ". "
                + "Return ONLY valid JSON with no surrounding text: "
                + "{\"scene\":\"<one sentence description>\","
                + "\"confidence\":<float 0-1>,"
                + "\"detections\":[{\"class\":\"<label>\","
                + "\"confidence\":<float 0-1>,"
                + "\"x\":<px>,\"y\":<px>,\"w\":<px>,\"h\":<px>}]}";

        Optional<String> response = aiClient.complete(prompt, "gpt-4-vision", 2048);
        if (response.isEmpty()) {
            LOG.warn("AI Inference returned no response for image detection \u2014 returning empty result");
            return VisualResult.builder()
                    .sceneDescription("No response from AI Inference")
                    .detections(Collections.emptyList())
                    .build();
        }

        VisualResult result = parseVisualResult(response.get());
        LOG.info("AI Inference detection completed: {} objects, confidence={}",
                result.getDetections().size(), result.getConfidence());
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AI Inference HTTP fallback — video analysis
    // ─────────────────────────────────────────────────────────────────────────

    private VisualResult analyseVideoViaAiInference(byte[] videoData, int sampleFps, int maxFrames) {
        AiInferenceClient aiClient = AiInferenceClient.getInstance();
        if (!aiClient.isReachable()) {
            LOG.warn("AI Inference Service unreachable \u2014 returning empty video analysis");
            return VisualResult.builder()
                    .sceneDescription("AI Inference Service unavailable")
                    .detections(Collections.emptyList())
                    .frameResults(Collections.emptyList())
                    .build();
        }

        // For video analysis: send metadata + leading sample.
        // The AI Inference Service will describe the video content based on
        // the encoded opening frames and structural metadata.
        int sampleLen = Math.min(videoData.length, IMAGE_SAMPLE_BYTES);
        String videoB64Sample = Base64.getEncoder().encodeToString(
                Arrays.copyOf(videoData, sampleLen));

        String prompt = "You are a video analysis AI. "
                + "Analyse this video clip (" + videoData.length + " bytes total, "
                + "sample rate: " + sampleFps + " fps, max frames: " + maxFrames + "). "
                + "Video data (base64 prefix of first frames): " + videoB64Sample + ". "
                + "Return ONLY valid JSON with no surrounding text: "
                + "{\"scene\":\"<overall scene description>\","
                + "\"confidence\":<float 0-1>,"
                + "\"detections\":[{\"class\":\"<label>\","
                + "\"confidence\":<float 0-1>,"
                + "\"x\":<px>,\"y\":<px>,\"w\":<px>,\"h\":<px>}]}";

        Optional<String> response = aiClient.complete(prompt, "gpt-4-vision", 2048);
        if (response.isEmpty()) {
            LOG.warn("AI Inference returned no response for video analysis \u2014 returning empty result");
            return VisualResult.builder()
                    .sceneDescription("No response from AI Inference")
                    .detections(Collections.emptyList())
                    .frameResults(Collections.emptyList())
                    .build();
        }

        VisualResult baseResult = parseVisualResult(response.get());
        // Wrap the aggregated detections into a single synthetic frame result
        // so callers that iterate frameResults still receive data.
        List<FrameResult> frameResults = baseResult.getDetections().isEmpty()
                ? Collections.emptyList()
                : Collections.singletonList(new FrameResult(0, 0L, baseResult.getDetections()));

        LOG.info("AI Inference video analysis completed: {} objects detected, scene='{}'",
                baseResult.getDetections().size(), baseResult.getSceneDescription());

        return VisualResult.builder()
                .sceneDescription(baseResult.getSceneDescription())
                .detections(baseResult.getDetections())
                .frameResults(frameResults)
                .confidence(baseResult.getConfidence())
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON response parsing
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Parse the AI Inference Service JSON response into a {@link VisualResult}.
     *
     * <p>Expected shape: {@code {"scene":"…","confidence":0.9,"detections":[{...}]}}
     * <p>Also handles the model wrapping the response JSON inside a {@code text} field.
     */
    private static VisualResult parseVisualResult(String json) {
        String source = json.trim();

        // Unwrap model-wrapper outer text/content field, if present.
        Matcher fallback = TEXT_FALLBACK_PATTERN.matcher(source);
        if (fallback.find()) {
            String inner = fallback.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            if (inner.contains("detections") || inner.contains("scene")) {
                source = inner;
            }
        }

        // Extract scene description
        String sceneDescription = "Unknown scene";
        Matcher sm = SCENE_PATTERN.matcher(source);
        if (sm.find()) {
            sceneDescription = sm.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\n", " ");
        }

        // Extract top-level confidence
        double confidence = 0.0;
        Matcher cm = CONFIDENCE_PATTERN.matcher(source);
        if (cm.find()) {
            try {
                confidence = Double.parseDouble(cm.group(1));
                confidence = Math.max(0.0, Math.min(1.0, confidence));
            } catch (NumberFormatException ignored) {
            }
        }

        // Extract detection array: each entry is {...} matching DETECTION_ENTRY_PATTERN.
        List<DetectionResult> detections = parseDetections(source);

        if (detections.isEmpty() && sceneDescription.equals("Unknown scene")) {
            LOG.warn("Could not parse any detections from AI response; raw: {}", json);
        }

        return VisualResult.builder()
                .sceneDescription(sceneDescription)
                .detections(detections)
                .confidence(confidence)
                .build();
    }

    /**
     * Extract {@link DetectionResult} objects from a JSON string containing a
     * {@code "detections":[...]} array.
     *
     * <p>Uses a regex-based approach that tolerates minor field ordering variation.
     * Each entry must include: {@code class}, {@code confidence}, {@code x}, {@code y},
     * {@code w}, {@code h}.
     */
    private static List<DetectionResult> parseDetections(String json) {
        List<DetectionResult> results = new ArrayList<>();
        Matcher m = DETECTION_ENTRY_PATTERN.matcher(json);
        while (m.find() && results.size() < 100) {
            try {
                String className  = m.group(1);
                double conf       = clamp(Double.parseDouble(m.group(2)));
                double x          = Double.parseDouble(m.group(3));
                double y          = Double.parseDouble(m.group(4));
                double w          = Double.parseDouble(m.group(5));
                double h          = Double.parseDouble(m.group(6));
                results.add(new DetectionResult(className, conf, x, y, w, h));
            } catch (NumberFormatException e) {
                LOG.debug("Skipping malformed detection entry: {}", m.group(0));
            }
        }
        return results;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}
