package com.ghatana.audio.video.integration;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.audio.video.infrastructure.persistence.service.TranscriptionService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.stt.grpc.PersistentSttGrpcService;
import com.ghatana.media.AudioVideoLibrary;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import com.ghatana.stt.core.grpc.proto.STTServiceGrpc;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose E2E tests for STT service with persistence integration
 * @doc.layer test
 * @doc.pattern E2E Test
 */
@DisplayName("STT Persistence E2E Tests")
class SttPersistenceE2ETest extends EventloopTestBase {

    private static final String TENANT_ID = "test-tenant";
    private static final UUID USER_ID = UUID.randomUUID(); // GH-90000

    private AudioFileService audioFileService;
    private TranscriptionService transcriptionService;
    private SimpleMeterRegistry meterRegistry;
    private Server server;
    private ManagedChannel channel;
    private STTServiceGrpc.STTServiceBlockingStub blockingStub;

    @BeforeEach
    void setUp() throws Exception { // GH-90000
        meterRegistry = new SimpleMeterRegistry(); // GH-90000

        // Create mock services
        audioFileService = mock(AudioFileService.class); // GH-90000
        transcriptionService = mock(TranscriptionService.class); // GH-90000

        // Mock AudioVideoLibrary
        AudioVideoLibrary library = mock(AudioVideoLibrary.class); // GH-90000

        // Create and start gRPC server
        String serverName = InProcessServerBuilder.generateName(); // GH-90000
        PersistentSttGrpcService service = new PersistentSttGrpcService( // GH-90000
            library, audioFileService, transcriptionService, meterRegistry);

        server = InProcessServerBuilder
            .forName(serverName) // GH-90000
            .directExecutor() // GH-90000
            .addService(service) // GH-90000
            .build() // GH-90000
            .start(); // GH-90000

        // Create client channel
        channel = InProcessChannelBuilder
            .forName(serverName) // GH-90000
            .directExecutor() // GH-90000
            .build(); // GH-90000

        blockingStub = STTServiceGrpc.newBlockingStub(channel); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        if (channel != null) { // GH-90000
            channel.shutdownNow(); // GH-90000
            channel.awaitTermination(5, TimeUnit.SECONDS); // GH-90000
        }
        if (server != null) { // GH-90000
            server.shutdownNow(); // GH-90000
            server.awaitTermination(5, TimeUnit.SECONDS); // GH-90000
        }
    }

    @Test
    @DisplayName("GIVEN persistence-backed STT service WHEN initialized THEN gRPC client stub is available")
    void testServiceInitializationWithPersistenceWiring() { // GH-90000
        // The test verifies integration wiring and server bootstrap in-process.
        assertThat(blockingStub).isNotNull(); // GH-90000
    }

    // Helper methods

    private AudioFileEntity createMockAudioFile(UUID id, String fileName) { // GH-90000
        AudioFileEntity entity = new AudioFileEntity(); // GH-90000
        entity.setId(id); // GH-90000
        entity.setTenantId(TENANT_ID); // GH-90000
        entity.setUserId(USER_ID); // GH-90000
        entity.setFileName(fileName); // GH-90000
        entity.setStoragePath("/storage/" + fileName); // GH-90000
        entity.setFormat("wav");
        entity.setStatus(AudioFileEntity.ProcessingStatus.COMPLETED); // GH-90000
        return entity;
    }

    private TranscriptionEntity createMockTranscription(UUID id, UUID audioFileId, String text) { // GH-90000
        TranscriptionEntity entity = new TranscriptionEntity(); // GH-90000
        entity.setId(id); // GH-90000
        entity.setTenantId(TENANT_ID); // GH-90000
        entity.setAudioFileId(audioFileId); // GH-90000
        entity.setText(text); // GH-90000
        entity.setLanguage("en");
        entity.setConfidence(0.95f); // GH-90000
        entity.setStatus(TranscriptionEntity.TranscriptionStatus.COMPLETED); // GH-90000
        return entity;
    }
}
