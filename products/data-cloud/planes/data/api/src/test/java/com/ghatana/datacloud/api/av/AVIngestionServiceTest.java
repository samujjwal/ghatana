package com.ghatana.datacloud.api.av;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for AVIngestionService media artifact lifecycle.
 * 
 * Verifies:
 * - Register artifact creates entity/event
 * - Trigger transcription job visible in operations
 * - Completion writes transcript entity+lineage
 */
class AVIngestionServiceTest extends EventloopTestBase {

    @Test
    void registerArtifactCreatesEntityAndEvent() {
        // Mock implementation for testing
        AVIngestionService service = mock(AVIngestionService.class);
        
        AVAsset asset = new AVAsset(
            "asset-123",
            "tenant-456",
            AVAssetType.AUDIO,
            AVAssetFormat.MP3,
            "s3://bucket/audio.mp3",
            "s3://storage/audio.mp3",
            1024000L, // sizeBytes
            60000L, // durationMs
            null, // transcript
            null, // frameIndex
            new AVMetadata(
                "audio-recording",
                "meeting",
                Map.of("speaker", "user-1"),
                "en-US"
            ),
            new AVConsent(
                true,
                "2026-01-01",
                "meeting-recording-policy"
            ),
            new AVRetention(
                90, // retentionPeriod
                true, // deleteAfter
                365, // archiveAfter
                false, // redactionRequired
                null // redactionRules
            ),
            "uploaded"
        );

        when(service.registerAsset(asset))
            .thenReturn(Promise.of(asset));

        AVAsset registered = runPromise(() -> service.registerAsset(asset));

        assertThat(registered).isNotNull();
        assertThat(registered.getId()).isEqualTo("asset-123");
        assertThat(registered.getStatus()).isEqualTo("uploaded");
        assertThat(registered.getSizeBytes()).isEqualTo(1024000L);
    }

    @Test
    void triggerTranscriptionJobVisibleInOperations() {
        AVIngestionService service = mock(AVIngestionService.class);
        
        String assetId = "asset-123";
        String tenantId = "tenant-456";
        
        AVIngestionService.IngestionRequest request = new AVIngestionService.IngestionRequest(
            assetId,
            tenantId,
            AVAssetType.AUDIO,
            new AVIngestionService.ProcessingOptions(
                true, // enableTranscription
                false, // enableVisionAnalysis
                "en-US", // language
                null // customModel
            )
        );

        AVIngestionService.IngestionJob job = new AVIngestionService.IngestionJob(
            "job-789",
            assetId,
            tenantId,
            AVIngestionService.JobStatus.RUNNING,
            Instant.now(),
            null,
            null,
            Map.of("operationType", "transcription")
        );

        when(service.startIngestion(request))
            .thenReturn(Promise.of(job));

        AVIngestionService.IngestionJob startedJob = runPromise(() -> service.startIngestion(request));

        assertThat(startedJob).isNotNull();
        assertThat(startedJob.getJobId()).isEqualTo("job-789");
        assertThat(startedJob.getStatus()).isEqualTo(AVIngestionService.JobStatus.RUNNING);
        assertThat(startedJob.getMetadata()).containsKey("operationType");
        assertThat(startedJob.getMetadata().get("operationType")).isEqualTo("transcription");
    }

    @Test
    void completionWritesTranscriptEntityAndLineage() {
        AVIngestionService service = mock(AVIngestionService.class);
        
        String jobId = "job-789";
        
        AVTranscript transcript = new AVTranscript(
            "transcript-456",
            "en-US",
            0.95,
            "gpt-4-transcription",
            Instant.now(),
            new AVTranscript.Segment[]{
                new AVTranscript.Segment(
                    0,
                    5000,
                    "Hello, this is a test transcription.",
                    0.98,
                    new AVTranscript.Word[]{
                        new AVTranscript.Word("Hello", 0, 500, 0.99),
                        new AVTranscript.Word("this", 600, 900, 0.97),
                        new AVTranscript.Word("is", 1000, 1200, 0.98),
                        new AVTranscript.Word("a", 1300, 1400, 0.99),
                        new AVTranscript.Word("test", 1500, 1800, 0.98),
                        new AVTranscript.Word("transcription", 1900, 3000, 0.97)
                    }
                )
            }
        );

        AVIngestionService.ProcessingResults results = new AVIngestionService.ProcessingResults(
            transcript,
            null, // frameIndex
            null, // objectDetection
            null // sceneDetection
        );

        AVIngestionService.IngestionJob completedJob = new AVIngestionService.IngestionJob(
            jobId,
            "asset-123",
            "tenant-456",
            AVIngestionService.JobStatus.COMPLETED,
            Instant.now().minusSeconds(60),
            Instant.now(),
            results,
            Map.of(
                "lineage", Map.of(
                    "sourceAssetId", "asset-123",
                    "transcriptionJobId", jobId,
                    "transcriptId", "transcript-456"
                )
            )
        );

        when(service.getJobStatus(jobId, "tenant-456"))
            .thenReturn(Promise.of(Optional.of(completedJob)));

        Optional<AVIngestionService.IngestionJob> jobStatus = runPromise(() -> 
            service.getJobStatus(jobId, "tenant-456"));

        assertThat(jobStatus).isPresent();
        assertThat(jobStatus.get().getStatus()).isEqualTo(AVIngestionService.JobStatus.COMPLETED);
        assertThat(jobStatus.get().getResults()).isNotNull();
        assertThat(jobStatus.get().getResults().transcript()).isNotNull();
        assertThat(jobStatus.get().getResults().transcript().id()).isEqualTo("transcript-456");
        
        // Verify lineage metadata
        assertThat(jobStatus.get().getMetadata()).containsKey("lineage");
        Map<String, Object> lineage = (Map<String, Object>) jobStatus.get().getMetadata().get("lineage");
        assertThat(lineage).containsEntry("sourceAssetId", "asset-123");
        assertThat(lineage).containsEntry("transcriptionJobId", jobId);
        assertThat(lineage).containsEntry("transcriptId", "transcript-456");
    }

    @Test
    void failureReasonCapturedInJobStatus() {
        AVIngestionService service = mock(AVIngestionService.class);
        
        String jobId = "job-789";
        
        AVIngestionService.IngestionJob failedJob = new AVIngestionService.IngestionJob(
            jobId,
            "asset-123",
            "tenant-456",
            AVIngestionService.JobStatus.FAILED,
            Instant.now().minusSeconds(60),
            Instant.now(),
            null,
            Map.of(
                "failureReason", "Audio format not supported",
                "errorCode", "UNSUPPORTED_FORMAT",
                "retryCount", "3"
            )
        );

        when(service.getJobStatus(jobId, "tenant-456"))
            .thenReturn(Promise.of(Optional.of(failedJob)));

        Optional<AVIngestionService.IngestionJob> jobStatus = runPromise(() -> 
            service.getJobStatus(jobId, "tenant-456"));

        assertThat(jobStatus).isPresent();
        assertThat(jobStatus.get().getStatus()).isEqualTo(AVIngestionService.JobStatus.FAILED);
        assertThat(jobStatus.get().getMetadata()).containsKey("failureReason");
        assertThat(jobStatus.get().getMetadata().get("failureReason")).isEqualTo("Audio format not supported");
        assertThat(jobStatus.get().getMetadata().get("errorCode")).isEqualTo("UNSUPPORTED_FORMAT");
        assertThat(jobStatus.get().getMetadata().get("retryCount")).isEqualTo("3");
    }
}
