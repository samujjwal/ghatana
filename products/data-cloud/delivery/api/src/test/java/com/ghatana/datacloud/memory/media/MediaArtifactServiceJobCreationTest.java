package com.ghatana.datacloud.memory.media;

import com.ghatana.datacloud.operations.InMemoryOperationRecorder;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Regression coverage for durable media job creation in the service layer
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MediaArtifactService durable jobs")
class MediaArtifactServiceJobCreationTest extends EventloopTestBase {

    private static final String TENANT_ID = "tenant-a";

    private DataCloudMediaArtifactRepository repository;
    private MediaArtifactService service;
    private HttpRequest request;
    private Principal principal;

    @BeforeEach
    void setUp() {
        repository = new DataCloudMediaArtifactRepository();
        MediaArtifactEventEmitter eventEmitter = mock(MediaArtifactEventEmitter.class);
        when(eventEmitter.emitTranscriptionRequested(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(mock(Offset.class)));
        when(eventEmitter.emitProcessingRequested(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Promise.of(mock(Offset.class)));

        service = new MediaArtifactService(
                repository,
                eventEmitter,
                new InMemoryOperationRecorder()
        );

        request = mock(HttpRequest.class);
        when(request.getHeader(HttpHeaders.of("X-Request-ID"))).thenReturn("req-123");

        principal = new Principal("editor-user", List.of("EDITOR"), TENANT_ID);
    }

    @Test
    @DisplayName("triggerTranscription returns and persists the created queued job")
    void triggerTranscriptionReturnsAndPersistsCreatedJob() {
        MediaArtifactRecord artifact = MediaArtifactRecord.create(
                TENANT_ID,
                "agent-1",
                "audio/wav",
                "s3://bucket/audio.wav",
                1024L,
                "abc123",
                30_000L,
                "tool-1",
                "corr-1",
                Map.of("consentStatus", MediaArtifactRecord.CONSENT_GRANTED),
                "seed-user"
        );
        runPromise(() -> repository.save(artifact));

        Optional<MediaProcessingJob> created = runPromise(() -> service.triggerTranscription(
                artifact.artifactId(),
                TENANT_ID,
                "en-US",
                principal,
                request
        ));

        assertThat(created).isPresent();
        MediaProcessingJob job = created.orElseThrow();
        assertThat(job.jobType()).isEqualTo(MediaProcessingJob.JobType.TRANSCRIPTION);
        assertThat(job.status()).isEqualTo(MediaProcessingJob.JobStatus.QUEUED);
        assertThat(job.parameters()).containsEntry("languageCode", "en-US");
        assertThat(job.requestId()).isEqualTo("req-123");
        assertThat(job.createdBy()).isEqualTo("editor-user");

        List<MediaProcessingJob> jobs = runPromise(() -> repository.findJobs(artifact.artifactId(), TENANT_ID));
        assertThat(jobs).singleElement().extracting(MediaProcessingJob::jobId).isEqualTo(job.jobId());

        MediaArtifactRecord updated = runPromise(() -> repository.findById(artifact.artifactId(), TENANT_ID))
                .orElseThrow();
        assertThat(updated.processingState()).isEqualTo(MediaArtifactRecord.LIFECYCLE_QUEUED);
        assertThat(updated.processingJobId()).isEqualTo(job.jobId());
    }

    @Test
    @DisplayName("triggerTranscription is blocked when artifact retention has expired")
    void triggerTranscriptionBlockedWhenRetentionExpired() {
        MediaArtifactRecord expiredArtifact = MediaArtifactRecord.create(
                TENANT_ID,
                "agent-1",
                "audio/wav",
                "s3://bucket/audio-expired.wav",
                2048L,
                "def456",
                20_000L,
                "tool-1",
                "corr-expired",
                Map.of(
                        "consentStatus", MediaArtifactRecord.CONSENT_GRANTED,
                        "retentionUntil", Instant.now().minusSeconds(60).toString()
                ),
                "seed-user"
        );
        runPromise(() -> repository.save(expiredArtifact));

        Optional<MediaProcessingJob> created = runPromise(() -> service.triggerTranscription(
                expiredArtifact.artifactId(),
                TENANT_ID,
                "en-US",
                principal,
                request
        ));

        assertThat(created).isEmpty();
        List<MediaProcessingJob> jobs = runPromise(() -> repository.findJobs(expiredArtifact.artifactId(), TENANT_ID));
        assertThat(jobs).isEmpty();
    }

    @Test
    @DisplayName("retryProcessing creates new job and clears last error")
    void retryProcessingCreatesNewJobAndClearsLastError() {
        MediaArtifactRecord artifact = MediaArtifactRecord.create(
                TENANT_ID,
                "agent-1",
                "audio/wav",
                "s3://bucket/audio-failed.wav",
                1024L,
                "ghi789",
                15_000L,
                "tool-1",
                "corr-retry",
                Map.of("consentStatus", MediaArtifactRecord.CONSENT_GRANTED),
                "seed-user"
        );
        runPromise(() -> repository.save(artifact));
        runPromise(() -> repository.updateProcessingState(artifact.artifactId(), TENANT_ID, MediaArtifactRecord.LIFECYCLE_FAILED));
        runPromise(() -> repository.updateLastError(artifact.artifactId(), TENANT_ID, "processor timed out", "seed-user"));

        Optional<MediaProcessingJob> retried = runPromise(() -> service.retryProcessing(
                artifact.artifactId(),
                TENANT_ID,
                principal,
                request
        ));

        assertThat(retried).isPresent();
        MediaArtifactRecord updated = runPromise(() -> repository.findById(artifact.artifactId(), TENANT_ID)).orElseThrow();
        assertThat(updated.processingState()).isEqualTo(MediaArtifactRecord.LIFECYCLE_QUEUED);
        assertThat(updated.lastError()).isNull();
        assertThat(updated.processingJobId()).isEqualTo(retried.orElseThrow().jobId());
    }

    @Test
    @DisplayName("getTranscript is blocked when artifact retention has expired")
    void getTranscriptBlockedWhenRetentionExpired() {
        MediaArtifactRecord artifact = MediaArtifactRecord.create(
                TENANT_ID,
                "agent-1",
                "audio/wav",
                "s3://bucket/audio-transcript.wav",
                1024L,
                "jkl012",
                10_000L,
                "tool-1",
                "corr-transcript",
                Map.of(
                        "consentStatus", MediaArtifactRecord.CONSENT_GRANTED,
                        "retentionUntil", Instant.now().minusSeconds(60).toString()
                ),
                "seed-user"
        );
        runPromise(() -> repository.save(artifact));

        Transcript transcript = Transcript.create(
                artifact.artifactId(),
                TENANT_ID,
                "job-1",
                "en-US",
                List.of(),
                "hello world",
                0.95,
                1000L,
                "seed-user"
        );
        runPromise(() -> repository.saveTranscript(artifact.artifactId(), TENANT_ID, transcript));

        Optional<Transcript> result = runPromise(() -> service.getTranscript(artifact.artifactId(), TENANT_ID));
        assertThat(result).isEmpty();
    }

        @Test
        @DisplayName("getFrameIndex is blocked when artifact retention has expired")
        void getFrameIndexBlockedWhenRetentionExpired() {
                MediaArtifactRecord artifact = MediaArtifactRecord.create(
                                TENANT_ID,
                                "agent-1",
                                "video/mp4",
                                "s3://bucket/video-index.mp4",
                                1024L,
                                "mno345",
                                12_000L,
                                "tool-1",
                                "corr-frame-index",
                                Map.of(
                                                "consentStatus", MediaArtifactRecord.CONSENT_GRANTED,
                                                "retentionUntil", Instant.now().minusSeconds(60).toString()
                                ),
                                "seed-user"
                );
                runPromise(() -> repository.save(artifact));

                FrameIndex frameIndex = FrameIndex.create(
                                artifact.artifactId(),
                                TENANT_ID,
                                "job-2",
                                FrameIndex.AnalysisType.OBJECT_DETECTION,
                                List.of(),
                                List.of(),
                                0.90,
                                10,
                                1000L,
                                "seed-user"
                );
                runPromise(() -> repository.saveFrameIndex(artifact.artifactId(), TENANT_ID, frameIndex));

                Optional<FrameIndex> result = runPromise(() -> service.getFrameIndex(artifact.artifactId(), TENANT_ID));
                assertThat(result).isEmpty();
        }
}