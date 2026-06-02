package com.ghatana.datacloud.memory.media;

import com.ghatana.datacloud.operations.InMemoryOperationRecorder;
import com.ghatana.datacloud.operations.OperationStatus;
import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Service-level lifecycle coverage for media artifact processing
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MediaArtifactService")
class MediaArtifactServiceTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-a";

    private DataCloudMediaArtifactRepository repository;
    private InMemoryOperationRecorder operationRecorder;
    private MediaArtifactEventEmitter eventEmitter;
    private Principal principal;
    private HttpRequest request;

    @BeforeEach
    void setUp() {
        repository = new DataCloudMediaArtifactRepository();
        operationRecorder = new InMemoryOperationRecorder();
        eventEmitter = mock(MediaArtifactEventEmitter.class);

        when(eventEmitter.emitCreated(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Promise.of(mock(Offset.class)));
        when(eventEmitter.emitTranscriptionRequested(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(mock(Offset.class)));
        when(eventEmitter.emitProcessingRequested(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(mock(Offset.class)));
        when(eventEmitter.emitProcessingFailed(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(mock(Offset.class)));

        principal = new Principal("editor-user", List.of("EDITOR"), TENANT_ID);
        request = mock(HttpRequest.class);
        when(request.getHeader(HttpHeaders.of("X-Request-ID"))).thenReturn("req-123");
    }

    @Test
    @DisplayName("createArtifact requires consent for audio")
    void createArtifactRequiresConsentForAudio() {
        MediaArtifactService service = new MediaArtifactService(repository, eventEmitter, operationRecorder);

        assertThatThrownBy(() -> service.createArtifact(
                TENANT_ID,
                "agent-1",
                "audio/wav",
                "s3://bucket/audio.wav",
                100L,
                "chk",
                1000L,
                "tool",
                "corr",
                null,
                null,
                null,
                Map.of(),
                principal,
                request
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consentStatus is required");
    }

    @Test
    @DisplayName("createArtifact requires consent for video")
    void createArtifactRequiresConsentForVideo() {
        MediaArtifactService service = new MediaArtifactService(repository, eventEmitter, operationRecorder);

        assertThatThrownBy(() -> service.createArtifact(
                TENANT_ID,
                "agent-1",
                "video/mp4",
                "s3://bucket/video.mp4",
                100L,
                "chk",
                1000L,
                "tool",
                "corr",
                null,
                null,
                null,
                Map.of(),
                principal,
                request
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consentStatus is required");
    }

    @Test
    @DisplayName("createArtifact allows non-media without explicit consent")
    void createArtifactAllowsNonMediaWithoutConsent() {
        MediaArtifactService service = new MediaArtifactService(repository, eventEmitter, operationRecorder);

        MediaArtifactRecord saved = runPromise(() -> service.createArtifact(
                TENANT_ID,
                "agent-1",
                "image/png",
                "s3://bucket/image.png",
                100L,
                "chk",
                0L,
                "tool",
                "corr",
                null,
                null,
                null,
                Map.of(),
                principal,
                request
        ));

        assertThat(saved.processingState()).isEqualTo(MediaArtifactRecord.LIFECYCLE_REGISTERED);
        verify(eventEmitter, atLeastOnce()).emitCreated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("triggerTranscription blocks when consent denied and records BLOCKED operation")
    void triggerTranscriptionBlocksWhenConsentDenied() {
        MediaArtifactService service = new MediaArtifactService(repository, eventEmitter, operationRecorder);
        MediaArtifactRecord artifact = MediaArtifactRecord.create(
                TENANT_ID,
                "agent-1",
                "audio/wav",
                "s3://bucket/audio.wav",
                100L,
                "chk",
                1000L,
                "tool",
                "corr",
                Map.of("consentStatus", MediaArtifactRecord.CONSENT_DENIED),
                "seed-user"
        );
        runPromise(() -> repository.save(artifact));

        Optional<MediaProcessingJob> result = runPromise(() -> service.triggerTranscription(
                artifact.artifactId(),
                TENANT_ID,
                "en-US",
                principal,
                request
        ));

        assertThat(result).isEmpty();
        assertThat(operationRecorder.allRecordsSnapshot())
                .anyMatch(record -> record.status() == OperationStatus.BLOCKED);
    }

    @Test
    @DisplayName("triggerTranscription blocks when consent pending")
    void triggerTranscriptionBlocksWhenConsentPending() {
        MediaArtifactService service = new MediaArtifactService(repository, eventEmitter, operationRecorder);
        MediaArtifactRecord artifact = MediaArtifactRecord.create(
                TENANT_ID,
                "agent-1",
                "audio/wav",
                "s3://bucket/audio.wav",
                100L,
                "chk",
                1000L,
                "tool",
                "corr",
                Map.of("consentStatus", MediaArtifactRecord.CONSENT_PENDING),
                "seed-user"
        );
        runPromise(() -> repository.save(artifact));

        Optional<MediaProcessingJob> result = runPromise(() -> service.triggerTranscription(
                artifact.artifactId(),
                TENANT_ID,
                "en-US",
                principal,
                request
        ));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("triggerTranscription blocks when retention invalid")
    void triggerTranscriptionBlocksWhenRetentionInvalid() {
        MediaArtifactService service = new MediaArtifactService(repository, eventEmitter, operationRecorder);
        MediaArtifactRecord artifact = MediaArtifactRecord.create(
                TENANT_ID,
                "agent-1",
                "audio/wav",
                "s3://bucket/audio.wav",
                100L,
                "chk",
                1000L,
                "tool",
                "corr",
                Map.of(
                        "consentStatus", MediaArtifactRecord.CONSENT_GRANTED,
                        "retentionUntil", Instant.now().minusSeconds(60).toString()
                ),
                "seed-user"
        );
        runPromise(() -> repository.save(artifact));

        Optional<MediaProcessingJob> result = runPromise(() -> service.triggerTranscription(
                artifact.artifactId(),
                TENANT_ID,
                "en-US",
                principal,
                request
        ));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("triggerTranscription creates durable job when consent granted")
    void triggerTranscriptionCreatesDurableJobWhenConsentGranted() {
        MediaArtifactService service = new MediaArtifactService(repository, eventEmitter, operationRecorder);
        MediaArtifactRecord artifact = MediaArtifactRecord.create(
                TENANT_ID,
                "agent-1",
                "audio/wav",
                "s3://bucket/audio.wav",
                100L,
                "chk",
                1000L,
                "tool",
                "corr",
                Map.of("consentStatus", MediaArtifactRecord.CONSENT_GRANTED),
                "seed-user"
        );
        runPromise(() -> repository.save(artifact));

        Optional<MediaProcessingJob> result = runPromise(() -> service.triggerTranscription(
                artifact.artifactId(),
                TENANT_ID,
                "en-US",
                principal,
                request
        ));

        assertThat(result).isPresent();
        verify(eventEmitter, atLeastOnce()).emitTranscriptionRequested(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("missing processor in production profile fails closed and emits failed event")
    void missingProcessorInProductionFailsClosed() {
        MediaArtifactService service = new MediaArtifactService(
                repository,
                eventEmitter,
                operationRecorder,
                null,
                "production"
        );

        MediaArtifactRecord artifact = MediaArtifactRecord.create(
                TENANT_ID,
                "agent-1",
                "audio/wav",
                "s3://bucket/audio.wav",
                100L,
                "chk",
                1000L,
                "tool",
                "corr",
                Map.of("consentStatus", MediaArtifactRecord.CONSENT_GRANTED),
                "seed-user"
        );
        runPromise(() -> repository.save(artifact));

        Optional<MediaProcessingJob> result = runPromise(() -> service.triggerTranscription(
                artifact.artifactId(),
                TENANT_ID,
                "en-US",
                principal,
                request
        ));

        assertThat(result).isEmpty();
        MediaArtifactRecord updated = runPromise(() -> repository.findById(artifact.artifactId(), TENANT_ID)).orElseThrow();
        assertThat(updated.processingState()).isEqualTo(MediaArtifactRecord.LIFECYCLE_FAILED);
        assertThat(updated.lastError()).contains("processor unavailable");
        verify(eventEmitter, atLeastOnce()).emitProcessingFailed(anyString(), anyString(), anyString(), anyString(), anyString());
    }
}
