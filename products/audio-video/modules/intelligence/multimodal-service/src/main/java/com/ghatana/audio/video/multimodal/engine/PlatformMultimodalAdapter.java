package com.ghatana.audio.video.multimodal.engine;

import com.ghatana.audio.video.vision.video.VideoFrameExtractor;
import com.ghatana.media.AudioVideoLibrary;
import com.ghatana.media.common.AudioData;
import com.ghatana.media.common.BoundingBox;
import com.ghatana.media.common.ColorSpace;
import com.ghatana.media.common.ImageData;
import com.ghatana.media.common.ImageFormat;
import com.ghatana.media.config.SttConfig;
import com.ghatana.media.config.TtsConfig;
import com.ghatana.media.config.VisionConfig;
import com.ghatana.media.stt.api.TranscriptionOptions;
import com.ghatana.media.stt.api.TranscriptionResult;
import com.ghatana.media.stt.api.WordTiming;
import com.ghatana.media.vision.api.DetectedObject;
import com.ghatana.media.vision.api.DetectionOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * @doc.type class
 * @doc.purpose Thin product adapter that delegates multimodal media processing to the platform library
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class PlatformMultimodalAdapter implements MultimodalMediaGateway {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformMultimodalAdapter.class);

    private final AudioVideoRuntimeSettings settings;
    private final AudioVideoLibrary library;
    private final VideoFrameExtractor frameExtractor;

    public PlatformMultimodalAdapter() {
        this(AudioVideoRuntimeSettings.load());
    }

    PlatformMultimodalAdapter(AudioVideoRuntimeSettings settings) {
        this.settings = settings;
        this.library = AudioVideoLibrary.builder()
                .withSttConfig(SttConfig.builder()
                        .modelId(settings.sttModelId())
                        .enableTimestamps(true)
                        .build())
                .withTtsConfig(TtsConfig.builder()
                        .defaultVoiceId(settings.ttsVoiceId())
                        .build())
                .withVisionConfig(VisionConfig.builder()
                        .modelId(settings.visionModelId())
                        .defaultConfidenceThreshold(0.5)
                        .build())
                .withMetrics(settings.metricsEnabled())
                .build();
        this.frameExtractor = new VideoFrameExtractor();
    }

    @Override
    public AudioResult transcribe(byte[] audioData) {
        try {
            TranscriptionResult result = library.getSttEngine().transcribe(
                    new AudioData(audioData, settings.sttSampleRate(), settings.sttChannels(), settings.sttBitsPerSample()),
                    TranscriptionOptions.builder()
                            .language(Locale.forLanguageTag(settings.languageTag()))
                            .enableTimestamps(true)
                            .timeout(Duration.ofSeconds(30))
                            .build());

            List<AudioResult.TimedSegment> timedSegments = result.words() == null
                    ? List.of()
                    : result.words().stream()
                            .map(PlatformMultimodalAdapter::toTimedSegment)
                            .toList();

            return AudioResult.builder()
                    .transcription(result.text())
                    .confidence(result.confidence())
                    .timedSegments(timedSegments)
                    .build();
        } catch (Exception e) {
            throw new MultimodalException("Platform STT processing failed", e);
        }
    }

    @Override
    public VisualResult analyseImage(byte[] imageData) {
        try {
            ImageData image = toImageData(imageData);
            var detection = library.getVisionEngine().detect(image, DetectionOptions.defaults());
            return VisualResult.builder()
                    .sceneDescription(library.getVisionEngine().caption(image))
                    .detections(detection.objects().stream().map(PlatformMultimodalAdapter::toDetection).toList())
                    .confidence(detection.objects().stream().map(DetectedObject::confidence).max(Comparator.naturalOrder()).orElse(0.0))
                    .build();
        } catch (Exception e) {
            throw new MultimodalException("Platform vision processing failed", e);
        }
    }

    @Override
    public VisualResult analyseVideo(byte[] videoData, int sampleFps, int maxFrames) {
        int resolvedSampleFps = sampleFps > 0 ? sampleFps : settings.defaultVideoSampleFps();
        int resolvedMaxFrames = maxFrames > 0 ? maxFrames : settings.defaultVideoMaxFrames();
        Path tempDir = null;
        Path tempVideo = null;
        try {
            tempDir = Files.createTempDirectory("multimodal-video-frames-");
            tempVideo = Files.createTempFile("multimodal-video-", ".bin");
            Files.write(tempVideo, videoData);

            VideoFrameExtractor.ExtractionConfig config = VideoFrameExtractor.ExtractionConfig.builder()
                    .fps(resolvedSampleFps)
                    .maxFrames(resolvedMaxFrames)
                    .format("jpg")
                    .build();
            List<VideoFrameExtractor.ExtractedFrame> frames = frameExtractor.extractFrames(tempVideo, tempDir, config);
            List<FrameResult> frameResults = new ArrayList<>();
            List<DetectionResult> mergedDetections = new ArrayList<>();
            List<String> captions = new ArrayList<>();

            for (VideoFrameExtractor.ExtractedFrame frame : frames) {
                byte[] frameBytes = Files.readAllBytes(frame.getPath());
                ImageData image = toImageData(frameBytes);
                var detection = library.getVisionEngine().detect(image, DetectionOptions.defaults());
                List<DetectionResult> detections = detection.objects().stream().map(PlatformMultimodalAdapter::toDetection).toList();
                frameResults.add(new FrameResult(frame.getFrameNumber(), frame.getTimestampMs(), detections));
                mergedDetections.addAll(detections);
                captions.add(library.getVisionEngine().caption(image));
            }

            return VisualResult.builder()
                    .sceneDescription(captions.isEmpty() ? "No frames extracted" : captions.getFirst())
                    .detections(mergedDetections)
                    .frameResults(frameResults)
                    .confidence(mergedDetections.stream().map(DetectionResult::getConfidence).max(Comparator.naturalOrder()).orElse(0.0))
                    .build();
        } catch (Exception e) {
            throw new MultimodalException("Platform video analysis failed", e);
        } finally {
            deleteRecursively(tempDir);
            deleteRecursively(tempVideo);
        }
    }

    @Override
    public String backendName() {
        return "platform-audio-video";
    }

    @Override
    public boolean metricsEnabled() {
        return settings.metricsEnabled();
    }

    @Override
    public void close() {
        library.close();
    }

    private ImageData toImageData(byte[] imageData) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
        int width = bufferedImage != null ? bufferedImage.getWidth() : settings.defaultImageWidth();
        int height = bufferedImage != null ? bufferedImage.getHeight() : settings.defaultImageHeight();
        return new ImageData(imageData, width, height, detectFormat(imageData), ColorSpace.RGB);
    }

    private ImageFormat detectFormat(byte[] imageData) {
        if (imageData.length > 2 && (imageData[0] & 0xFF) == 0xFF && (imageData[1] & 0xFF) == 0xD8) {
            return ImageFormat.JPEG;
        }
        if (imageData.length > 8 && imageData[0] == (byte) 0x89 && imageData[1] == 0x50) {
            return ImageFormat.PNG;
        }
        return ImageFormat.PNG;
    }

    private static DetectionResult toDetection(DetectedObject detectedObject) {
        BoundingBox bbox = detectedObject.bbox();
        return new DetectionResult(
                detectedObject.className(),
                detectedObject.confidence(),
                bbox.x(),
                bbox.y(),
                bbox.width(),
                bbox.height());
    }

    private static AudioResult.TimedSegment toTimedSegment(WordTiming wordTiming) {
        return new AudioResult.TimedSegment(
                Math.round(wordTiming.startSec() * 1000),
                Math.round(wordTiming.endSec() * 1000),
                wordTiming.word());
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    stream.forEach(this::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.debug("Failed to delete temporary path {}", path, e);
        }
    }
}
