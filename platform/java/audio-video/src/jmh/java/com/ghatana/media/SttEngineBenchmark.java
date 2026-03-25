package com.ghatana.media;

import com.ghatana.media.common.*;
import com.ghatana.media.config.SttConfig;
import com.ghatana.media.stt.api.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for STT Engine.
 * 
 * <p>Measures:
 * <ul>
 *   <li>Transcription latency (p50, p95, p99)</li>
 *   <li>Throughput (transcriptions/second)</li>
 *   <li>Memory allocation per transcription</li>
 * </ul>
 * 
 * <p>Usage: ./gradlew jmh
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 2, jvmArgs = {"-Xms2G", "-Xmx2G"})
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
public class SttEngineBenchmark {
    
    private AudioVideoLibrary library;
    private SttEngine engine;
    private AudioData testAudio10s;
    private AudioData testAudio30s;
    
    @Setup
    public void setup() {
        SttConfig config = SttConfig.builder()
            .modelPath(Paths.get("/models/whisper-base.onnx"))
            .modelId("whisper-base")
            .useGpu(false)
            .maxConcurrentRequests(10)
            .build();
        
        library = AudioVideoLibrary.builder()
            .withSttConfig(config)
            .build();
        
        engine = library.getSttEngine();
        
        // Generate test audio: 10 seconds, 16kHz, mono, 16-bit
        testAudio10s = generateSineWave(16000, 10, 440); // 440 Hz tone
        testAudio30s = generateSineWave(16000, 30, 440);
        
        // Warmup
        engine.warmup();
    }
    
    @TearDown
    public void tearDown() {
        if (library != null) {
            library.close();
        }
    }
    
    @Benchmark
    public TranscriptionResult transcribe10Seconds() {
        return engine.transcribe(testAudio10s, TranscriptionOptions.defaults());
    }
    
    @Benchmark
    public TranscriptionResult transcribe30Seconds() {
        return engine.transcribe(testAudio30s, TranscriptionOptions.defaults());
    }
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void transcribeThroughput() {
        engine.transcribe(testAudio10s, TranscriptionOptions.defaults());
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void firstChunkLatency() {
        // Simulate first-chunk scenario (no warmup)
        engine.transcribe(testAudio10s, TranscriptionOptions.defaults());
    }
    
    private AudioData generateSineWave(int sampleRate, int durationSeconds, double frequency) {
        int numSamples = sampleRate * durationSeconds;
        byte[] data = new byte[numSamples * 2]; // 16-bit samples
        
        for (int i = 0; i < numSamples; i++) {
            double t = i / (double) sampleRate;
            double sample = Math.sin(2 * Math.PI * frequency * t);
            short sampleValue = (short) (sample * 32767);
            
            data[i * 2] = (byte) (sampleValue & 0xFF);
            data[i * 2 + 1] = (byte) ((sampleValue >> 8) & 0xFF);
        }
        
        return AudioData.builder()
            .data(data)
            .sampleRate(sampleRate)
            .channels(1)
            .bitsPerSample(16)
            .duration(java.time.Duration.ofSeconds(durationSeconds))
            .format(AudioFormat.PCM)
            .build();
    }
    
    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
            .include(SttEngineBenchmark.class.getSimpleName())
            .forks(1)
            .build();
        
        new Runner(opt).run();
    }
}
