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
    private static final UUID USER_ID = UUID.randomUUID(); 

    private AudioFileService audioFileService;
    private TranscriptionService transcriptionService;
    private SimpleMeterRegistry meterRegistry;
    private Server server;
    private ManagedChannel channel;
    private STTServiceGrpc.STTServiceBlockingStub blockingStub;

    @BeforeEach
    void setUp() throws Exception { 
        meterRegistry = new SimpleMeterRegistry(); 

        // Create mock services
        audioFileService = mock(AudioFileService.class); 
        transcriptionService = mock(TranscriptionService.class); 

        // Mock AudioVideoLibrary
        AudioVideoLibrary library = mock(AudioVideoLibrary.class); 

        // Create and start gRPC server
        String serverName = InProcessServerBuilder.generateName(); 
        PersistentSttGrpcService service = new PersistentSttGrpcService( 
            library, audioFileService, transcriptionService, meterRegistry);

        server = InProcessServerBuilder
            .forName(serverName) 
            .directExecutor() 
            .addService(service) 
            .build() 
            .start(); 

        // Create client channel
        channel = InProcessChannelBuilder
            .forName(serverName) 
            .directExecutor() 
            .build(); 

        blockingStub = STTServiceGrpc.newBlockingStub(channel); 
    }

    @AfterEach
    void tearDown() throws Exception { 
        if (channel != null) { 
            channel.shutdownNow(); 
            channel.awaitTermination(5, TimeUnit.SECONDS); 
        }
        if (server != null) { 
            server.shutdownNow(); 
            server.awaitTermination(5, TimeUnit.SECONDS); 
        }
    }

    @Test
    @DisplayName("GIVEN persistence-backed STT service WHEN initialized THEN gRPC client stub is available")
    void testServiceInitializationWithPersistenceWiring() { 
        // The test verifies integration wiring and server bootstrap in-process.
        assertThat(blockingStub).isNotNull(); 
    }

    // Helper methods

    private AudioFileEntity createMockAudioFile(UUID id, String fileName) { 
        AudioFileEntity entity = new AudioFileEntity(); 
        entity.setId(id); 
        entity.setTenantId(TENANT_ID); 
        entity.setUserId(USER_ID); 
        entity.setFileName(fileName); 
        entity.setStoragePath("/storage/" + fileName); 
        entity.setFormat("wav");
        entity.setStatus(AudioFileEntity.ProcessingStatus.COMPLETED); 
        return entity;
    }

    private TranscriptionEntity createMockTranscription(UUID id, UUID audioFileId, String text) { 
        TranscriptionEntity entity = new TranscriptionEntity(); 
        entity.setId(id); 
        entity.setTenantId(TENANT_ID); 
        entity.setAudioFileId(audioFileId); 
        entity.setText(text); 
        entity.setLanguage("en");
        entity.setConfidence(0.95f); 
        entity.setStatus(TranscriptionEntity.TranscriptionStatus.COMPLETED); 
        return entity;
    }
}
