package com.ghatana.media;

import com.ghatana.media.common.*;
import com.ghatana.media.config.TtsConfig;
import com.ghatana.media.tts.api.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for TTS Engine.
 * 
 * <p>Measures Real-Time Factor (RTF) - synthesis time / audio duration.
 * RTF < 1.0 means real-time capable.
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
public class TtsEngineBenchmark {
    
    private AudioVideoLibrary library;
    private TtsEngine engine;
    private String shortText;
    private String mediumText;
    private String longText;
    
    @Setup
    public void setup() {
        TtsConfig config = TtsConfig.builder()
            .voiceModelPath(Paths.get("/models/piper-en.onnx"))
            .defaultVoiceId("piper-en")
            .sampleRate(22050)
            .maxConcurrentRequests(10)
            .build();
        
        library = AudioVideoLibrary.builder()
            .withTtsConfig(config)
            .build();
        
        engine = library.getTtsEngine();
        
        // Test texts of different lengths
        shortText = "Hello world."; // ~1 second
        mediumText = "The quick brown fox jumps over the lazy dog. This is a test of text to speech synthesis."; // ~5 seconds
        longText = "Text-to-speech synthesis is the artificial production of human speech. " +
                   "A computer system used for this purpose is called a speech computer or speech synthesizer, " +
                   "and can be implemented in software or hardware products."; // ~10 seconds
        
        engine.warmup();
    }
    
    @TearDown
    public void tearDown() {
        if (library != null) {
            library.close();
        }
    }
    
    @Benchmark
    public AudioData synthesizeShort() {
        return engine.synthesize(shortText, SynthesisOptions.defaults());
    }
    
    @Benchmark
    public AudioData synthesizeMedium() {
        return engine.synthesize(mediumText, SynthesisOptions.defaults());
    }
    
    @Benchmark
    public AudioData synthesizeLong() {
        return engine.synthesize(longText, SynthesisOptions.defaults());
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void synthesizeThroughput() {
        engine.synthesize(shortText, SynthesisOptions.defaults());
    }
    
    /**
     * Calculate RTF for different text lengths.
     * RTF = synthesis_time_ms / audio_duration_ms
     */
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(TtsEngineBenchmark.class.getSimpleName())
            .forks(1)
            .build();
        
        new Runner(opt).run();
    }
}
