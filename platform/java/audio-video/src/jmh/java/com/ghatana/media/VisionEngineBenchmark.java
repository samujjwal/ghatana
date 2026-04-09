package com.ghatana.media;

import com.ghatana.media.common.*;
import com.ghatana.media.config.VisionConfig;
import com.ghatana.media.vision.api.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for Vision Engine.
 *
 * <p>Measures detection throughput (FPS) and latency for different image sizes.
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
public class VisionEngineBenchmark {

    private AudioVideoLibrary library;
    private VisionEngine engine;
    private ImageData imageSmall;   // 320x240
    private ImageData imageMedium;  // 640x480
    private ImageData imageLarge;   // 1280x720
    private ImageData imageHd;      // 1920x1080

    @Setup
    public void setup() {
        VisionConfig config = VisionConfig.builder()
            .modelPath(Paths.get("/models/yolov8n.onnx"))
            .modelId("yolov8n")
            .useGpu(false)
            .confidenceThreshold(0.5f)
            .maxDetections(100)
            .build();

        library = AudioVideoLibrary.builder()
            .withVisionConfig(config)
            .build();

        engine = library.getVisionEngine();

        // Generate test images of different sizes
        imageSmall = generateTestImage(320, 240);
        imageMedium = generateTestImage(640, 480);
        imageLarge = generateTestImage(1280, 720);
        imageHd = generateTestImage(1920, 1080);

        engine.warmup();
    }

    @TearDown
    public void tearDown() {
        if (library != null) {
            library.close();
        }
    }

    @Benchmark
    public DetectionResult detectSmall() {
        return engine.detect(imageSmall, DetectionOptions.defaults());
    }

    @Benchmark
    public DetectionResult detectMedium() {
        return engine.detect(imageMedium, DetectionOptions.defaults());
    }

    @Benchmark
    public DetectionResult detectLarge() {
        return engine.detect(imageLarge, DetectionOptions.defaults());
    }

    @Benchmark
    public DetectionResult detectHd() {
        return engine.detect(imageHd, DetectionOptions.defaults());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void detectThroughput() {
        engine.detect(imageMedium, DetectionOptions.defaults());
    }

    private ImageData generateTestImage(int width, int height) {
        int pixelCount = width * height;
        byte[] data = new byte[pixelCount * 3]; // RGB

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = (y * width + x) * 3;
                data[idx] = (byte) ((x * 255 / width) & 0xFF);
                data[idx + 1] = (byte) ((y * 255 / height) & 0xFF);
                data[idx + 2] = 128;
            }
        }

        return ImageData.builder()
            .data(data)
            .width(width)
            .height(height)
            .format(ImageFormat.RAW)
            .build();
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(VisionEngineBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
