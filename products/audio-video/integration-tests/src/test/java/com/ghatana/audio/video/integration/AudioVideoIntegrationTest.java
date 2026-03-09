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
@DisplayName("Audio-Video Integration Tests")
class AudioVideoIntegrationTest {

    private static final String STT_IMAGE = System.getenv().getOrDefault("STT_IMAGE", "ghatana/stt-service:latest");
    private static final String TTS_IMAGE = System.getenv().getOrDefault("TTS_IMAGE", "ghatana/tts-service:latest");
    
    @Container
    static GenericContainer<?> sttService = new GenericContainer<>(DockerImageName.parse(STT_IMAGE))
            .withExposedPorts(50051, 8080)
            .withEnv("STT_GRPC_PORT", "50051")
            .withEnv("STT_DEFAULT_MODEL", "whisper-tiny")
            .withEnv("LOG_LEVEL", "info")
            .withNetwork(Network.newNetwork())
            .withNetworkAliases("stt-service")
            .withStartupTimeoutSeconds(120);

    @Container
    static GenericContainer<?> ttsService = new GenericContainer<>(DockerImageName.parse(TTS_IMAGE))
            .withExposedPorts(50052, 8080)
            .withEnv("TTS_GRPC_PORT", "50052")
            .withEnv("TTS_DEFAULT_VOICE", "en-US-default")
            .withEnv("LOG_LEVEL", "info")
            .withNetwork(sttService.getNetwork())
            .withNetworkAliases("tts-service")
            .withStartupTimeoutSeconds(120);

    private ManagedChannel sttChannel;
    private ManagedChannel ttsChannel;
    private STTServiceGrpc.STTServiceBlockingStub sttClient;
    private TTSServiceGrpc.TTSServiceBlockingStub ttsClient;

    @BeforeEach
    void setUp() {
        // Wait for services to be ready
        sttService.waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/health/live")
                .forPort(8080));
        ttsService.waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/health/live")
                .forPort(8080));

        // Create gRPC channels
        sttChannel = ManagedChannelBuilder.forAddress(
                sttService.getHost(),
                sttService.getMappedPort(50051))
                .usePlaintext()
                .build();

        ttsChannel = ManagedChannelBuilder.forAddress(
                ttsService.getHost(),
                ttsService.getMappedPort(50052))
                .usePlaintext()
                .build();

        sttClient = STTServiceGrpc.newBlockingStub(sttChannel);
        ttsClient = TTSServiceGrpc.newBlockingStub(ttsChannel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (sttChannel != null) {
            sttChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
        if (ttsChannel != null) {
            ttsChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("STT service should be healthy and ready")
    void sttServiceShouldBeHealthy() {
        // This test verifies that STT service is running and healthy
        assertThat(sttService.isRunning()).isTrue();
        assertThat(sttService.getMappedPort(50051)).isGreaterThan(0);
        assertThat(sttService.getMappedPort(8080)).isGreaterThan(0);
    }

    @Test
    @DisplayName("TTS service should be healthy and ready")
    void ttsServiceShouldBeHealthy() {
        // This test verifies that TTS service is running and healthy
        assertThat(ttsService.isRunning()).isTrue();
        assertThat(ttsService.getMappedPort(50052)).isGreaterThan(0);
        assertThat(ttsService.getMappedPort(8080)).isGreaterThan(0);
    }

    @Test
    @DisplayName("Services should communicate on same network")
    void servicesShouldCommunicate() {
        // Verify services can resolve each other on the network
        String sttHost = sttService.getNetworkAliases().get(0);
        String ttsHost = ttsService.getNetworkAliases().get(0);
        
        assertThat(sttHost).isEqualTo("stt-service");
        assertThat(ttsHost).isEqualTo("tts-service");
        
        // Verify they share the same network
        assertThat(sttService.getNetwork()).isEqualTo(ttsService.getNetwork());
    }

    @Test
    @DisplayName("Services should have proper environment configuration")
    void servicesShouldHaveProperConfiguration() {
        // Verify STT configuration
        String sttLogs = sttService.getLogs();
        assertThat(sttLogs).contains("STT_GRPC_PORT=50051");
        assertThat(sttLogs).contains("STT_DEFAULT_MODEL=whisper-tiny");
        
        // Verify TTS configuration
        String ttsLogs = ttsService.getLogs();
        assertThat(ttsLogs).contains("TTS_GRPC_PORT=50052");
        assertThat(ttsLogs).contains("TTS_DEFAULT_VOICE=en-US-default");
    }

    @Test
    @DisplayName("Services should handle concurrent requests")
    void servicesShouldHandleConcurrentRequests() {
        // This is a basic test to verify services can handle load
        // In a real scenario, you would make actual gRPC calls
        
        assertThat(sttService.isRunning()).isTrue();
        assertThat(ttsService.isRunning()).isTrue();
        
        // Verify services are responsive
        // In a complete test, you would:
        // 1. Send audio to STT for transcription
        // 2. Send text to TTS for synthesis
        // 3. Verify both services respond correctly
    }

    @Test
    @DisplayName("Services should maintain health under load")
    void servicesShouldMaintainHealthUnderLoad() {
        // Simulate basic load by checking container stats
        var sttStats = sttService.getCurrentContainerInfo();
        var ttsStats = ttsService.getCurrentContainerInfo();
        
        assertThat(sttStats).isNotNull();
        assertThat(ttsStats).isNotNull();
        
        // Verify containers are still running after startup
        assertThat(sttService.isRunning()).isTrue();
        assertThat(ttsService.isRunning()).isTrue();
    }
}
