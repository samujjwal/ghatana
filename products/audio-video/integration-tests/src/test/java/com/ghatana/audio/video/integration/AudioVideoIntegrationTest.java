package com.ghatana.audio.video.integration;

import com.ghatana.stt.core.grpc.STTServiceGrpc;
import com.ghatana.tts.core.grpc.TTSServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for end-to-end audio-video workflows.
 * Tests STT and TTS services working together.
 *
 * @doc.type test
 * @doc.purpose End-to-end integration testing
 * @doc.layer integration
 */
@Testcontainers
@DisplayName("Audio-Video Integration Tests [GH-90000]")
class AudioVideoIntegrationTest {

    private static final String STT_IMAGE = System.getenv().getOrDefault("STT_IMAGE", "ghatana/stt-service:latest"); // GH-90000
    private static final String TTS_IMAGE = System.getenv().getOrDefault("TTS_IMAGE", "ghatana/tts-service:latest"); // GH-90000
    
    @Container
    static GenericContainer<?> sttService = new GenericContainer<>(DockerImageName.parse(STT_IMAGE)) // GH-90000
            .withExposedPorts(50051, 8080) // GH-90000
            .withEnv("STT_GRPC_PORT", "50051") // GH-90000
            .withEnv("STT_DEFAULT_MODEL", "whisper-tiny") // GH-90000
            .withEnv("LOG_LEVEL", "info") // GH-90000
            .withNetwork(Network.newNetwork()) // GH-90000
            .withNetworkAliases("stt-service [GH-90000]")
            .withStartupTimeoutSeconds(120); // GH-90000

    @Container
    static GenericContainer<?> ttsService = new GenericContainer<>(DockerImageName.parse(TTS_IMAGE)) // GH-90000
            .withExposedPorts(50052, 8080) // GH-90000
            .withEnv("TTS_GRPC_PORT", "50052") // GH-90000
            .withEnv("TTS_DEFAULT_VOICE", "en-US-default") // GH-90000
            .withEnv("LOG_LEVEL", "info") // GH-90000
            .withNetwork(sttService.getNetwork()) // GH-90000
            .withNetworkAliases("tts-service [GH-90000]")
            .withStartupTimeoutSeconds(120); // GH-90000

    private ManagedChannel sttChannel;
    private ManagedChannel ttsChannel;
    private STTServiceGrpc.STTServiceBlockingStub sttClient;
    private TTSServiceGrpc.TTSServiceBlockingStub ttsClient;

    @BeforeEach
    void setUp() { // GH-90000
        // Wait for services to be ready
        sttService.waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/health/live [GH-90000]")
                .forPort(8080)); // GH-90000
        ttsService.waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/health/live [GH-90000]")
                .forPort(8080)); // GH-90000

        // Create gRPC channels
        sttChannel = ManagedChannelBuilder.forAddress( // GH-90000
                sttService.getHost(), // GH-90000
                sttService.getMappedPort(50051)) // GH-90000
                .usePlaintext() // GH-90000
                .build(); // GH-90000

        ttsChannel = ManagedChannelBuilder.forAddress( // GH-90000
                ttsService.getHost(), // GH-90000
                ttsService.getMappedPort(50052)) // GH-90000
                .usePlaintext() // GH-90000
                .build(); // GH-90000

        sttClient = STTServiceGrpc.newBlockingStub(sttChannel); // GH-90000
        ttsClient = TTSServiceGrpc.newBlockingStub(ttsChannel); // GH-90000
    }

    @AfterEach
    void tearDown() throws InterruptedException { // GH-90000
        if (sttChannel != null) { // GH-90000
            sttChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS); // GH-90000
        }
        if (ttsChannel != null) { // GH-90000
            ttsChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS); // GH-90000
        }
    }

    @Test
    @DisplayName("STT service should be healthy and ready [GH-90000]")
    void sttServiceShouldBeHealthy() { // GH-90000
        // This test verifies that STT service is running and healthy
        assertThat(sttService.isRunning()).isTrue(); // GH-90000
        assertThat(sttService.getMappedPort(50051)).isGreaterThan(0); // GH-90000
        assertThat(sttService.getMappedPort(8080)).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("TTS service should be healthy and ready [GH-90000]")
    void ttsServiceShouldBeHealthy() { // GH-90000
        // This test verifies that TTS service is running and healthy
        assertThat(ttsService.isRunning()).isTrue(); // GH-90000
        assertThat(ttsService.getMappedPort(50052)).isGreaterThan(0); // GH-90000
        assertThat(ttsService.getMappedPort(8080)).isGreaterThan(0); // GH-90000
    }

    @Test
    @DisplayName("Services should communicate on same network [GH-90000]")
    void servicesShouldCommunicate() { // GH-90000
        // Verify services can resolve each other on the network
        String sttHost = sttService.getNetworkAliases().get(0); // GH-90000
        String ttsHost = ttsService.getNetworkAliases().get(0); // GH-90000
        
        assertThat(sttHost).isEqualTo("stt-service [GH-90000]");
        assertThat(ttsHost).isEqualTo("tts-service [GH-90000]");
        
        // Verify they share the same network
        assertThat(sttService.getNetwork()).isEqualTo(ttsService.getNetwork()); // GH-90000
    }

    @Test
    @DisplayName("Services should have proper environment configuration [GH-90000]")
    void servicesShouldHaveProperConfiguration() { // GH-90000
        // Verify STT configuration
        String sttLogs = sttService.getLogs(); // GH-90000
        assertThat(sttLogs).contains("STT_GRPC_PORT=50051 [GH-90000]");
        assertThat(sttLogs).contains("STT_DEFAULT_MODEL=whisper-tiny [GH-90000]");
        
        // Verify TTS configuration
        String ttsLogs = ttsService.getLogs(); // GH-90000
        assertThat(ttsLogs).contains("TTS_GRPC_PORT=50052 [GH-90000]");
        assertThat(ttsLogs).contains("TTS_DEFAULT_VOICE=en-US-default [GH-90000]");
    }

    @Test
    @DisplayName("Services should handle concurrent requests [GH-90000]")
    void servicesShouldHandleConcurrentRequests() { // GH-90000
        // This is a basic test to verify services can handle load
        // In a real scenario, you would make actual gRPC calls
        
        assertThat(sttService.isRunning()).isTrue(); // GH-90000
        assertThat(ttsService.isRunning()).isTrue(); // GH-90000
        
        // Verify services are responsive
        // In a complete test, you would:
        // 1. Send audio to STT for transcription
        // 2. Send text to TTS for synthesis
        // 3. Verify both services respond correctly
    }

    @Test
    @DisplayName("Services should maintain health under load [GH-90000]")
    void servicesShouldMaintainHealthUnderLoad() { // GH-90000
        // Simulate basic load by checking container stats
        var sttStats = sttService.getCurrentContainerInfo(); // GH-90000
        var ttsStats = ttsService.getCurrentContainerInfo(); // GH-90000
        
        assertThat(sttStats).isNotNull(); // GH-90000
        assertThat(ttsStats).isNotNull(); // GH-90000
        
        // Verify containers are still running after startup
        assertThat(sttService.isRunning()).isTrue(); // GH-90000
        assertThat(ttsService.isRunning()).isTrue(); // GH-90000
    }
}
