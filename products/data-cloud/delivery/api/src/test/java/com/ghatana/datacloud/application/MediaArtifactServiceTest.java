package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.media.*;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests for MediaArtifactService.
 *
 * @doc.type class
 * @doc.purpose Media artifact service tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MediaArtifactService Tests")
class MediaArtifactServiceTest {

    @Test
    @DisplayName("Should register artifact with consent validation")
    void shouldRegisterArtifactWithConsentValidation() {
        var artifactRepository = mock(MediaArtifactRepository.class);
        var jobRepository = mock(MediaProcessingJobRepository.class);
        var transcriptRepository = mock(TranscriptRepository.class);
        var frameIndexRepository = mock(FrameIndexRepository.class);
        var consentRepository = mock(ConsentRepository.class);
        var retentionPolicyRepository = mock(RetentionPolicyRepository.class);
        
        var service = new MediaArtifactService(
                artifactRepository, jobRepository, transcriptRepository,
                frameIndexRepository, consentRepository, retentionPolicyRepository
        );
        
        var artifact = MediaArtifact.builder()
                .artifactId("artifact-123")
                .tenantId("tenant-456")
                .mediaType("video/mp4")
                .storageUri("s3://bucket/video.mp4")
                .sizeBytes(1024000L)
                .checksum("abc123")
                .durationMs(60000L)
                .classification(MediaArtifact.Classification.INTERNAL)
                .consentStatus(MediaArtifact.ConsentStatus.NONE)
                .metadata(Map.of())
                .build();
        
        when(artifactRepository.save(any())).thenReturn(artifact);
        
        var result = service.registerArtifact(
                "tenant-456", "agent-789", "video/mp4",
                "s3://bucket/video.mp4", 1024000L, "abc123", 60000L, Map.of()
        );
        
        assertThat(result).isNotNull();
        verify(artifactRepository).save(any());
    }

    @Test
    @DisplayName("Should validate privacy for artifact")
    void shouldValidatePrivacyForArtifact() {
        var artifactRepository = mock(MediaArtifactRepository.class);
        var jobRepository = mock(MediaProcessingJobRepository.class);
        var transcriptRepository = mock(TranscriptRepository.class);
        var frameIndexRepository = mock(FrameIndexRepository.class);
        var consentRepository = mock(ConsentRepository.class);
        var retentionPolicyRepository = mock(RetentionPolicyRepository.class);
        
        var service = new MediaArtifactService(
                artifactRepository, jobRepository, transcriptRepository,
                frameIndexRepository, consentRepository, retentionPolicyRepository
        );
        
        var artifact = MediaArtifact.builder()
                .artifactId("artifact-123")
                .tenantId("tenant-456")
                .mediaType("video/mp4")
                .classification(MediaArtifact.Classification.INTERNAL)
                .consentStatus(MediaArtifact.ConsentStatus.NONE)
                .build();
        
        when(artifactRepository.findByTenantIdAndArtifactId("tenant-456", "artifact-123"))
                .thenReturn(Optional.of(artifact));
        when(consentRepository.findByTenantIdAndMediaArtifactId(eq("tenant-456"), any()))
                .thenReturn(List.of());
        
        var result = service.validatePrivacy("artifact-123", "tenant-456");
        
        assertThat(result).isNotNull();
        verify(artifactRepository).findByTenantIdAndArtifactId("tenant-456", "artifact-123");
    }

    @Test
    @DisplayName("Should create processing job")
    void shouldCreateProcessingJob() {
        var artifactRepository = mock(MediaArtifactRepository.class);
        var jobRepository = mock(MediaProcessingJobRepository.class);
        var transcriptRepository = mock(TranscriptRepository.class);
        var frameIndexRepository = mock(FrameIndexRepository.class);
        var consentRepository = mock(ConsentRepository.class);
        var retentionPolicyRepository = mock(RetentionPolicyRepository.class);
        
        var service = new MediaArtifactService(
                artifactRepository, jobRepository, transcriptRepository,
                frameIndexRepository, consentRepository, retentionPolicyRepository
        );
        
        var artifact = MediaArtifact.builder()
                .artifactId("artifact-123")
                .tenantId("tenant-456")
                .mediaType("video/mp4")
                .build();
        
        var job = MediaProcessingJob.builder()
                .jobId("job-789")
                .tenantId("tenant-456")
                .mediaArtifact(artifact)
                .jobType(MediaProcessingJob.JobType.TRANSCRIPTION)
                .status(MediaProcessingJob.JobStatus.PENDING)
                .build();
        
        when(artifactRepository.findByTenantIdAndArtifactId("tenant-456", "artifact-123"))
                .thenReturn(Optional.of(artifact));
        when(jobRepository.save(any())).thenReturn(job);
        
        var result = service.createProcessingJob(
                "artifact-123", "tenant-456",
                MediaProcessingJob.JobType.TRANSCRIPTION,
                Map.of("languageCode", "en-US"),
                "user-123"
        );
        
        assertThat(result).isNotNull();
        verify(jobRepository).save(any());
    }

    @Test
    @DisplayName("Should apply retention policy")
    void shouldApplyRetentionPolicy() {
        var artifactRepository = mock(MediaArtifactRepository.class);
        var jobRepository = mock(MediaProcessingJobRepository.class);
        var transcriptRepository = mock(TranscriptRepository.class);
        var frameIndexRepository = mock(FrameIndexRepository.class);
        var consentRepository = mock(ConsentRepository.class);
        var retentionPolicyRepository = mock(RetentionPolicyRepository.class);
        
        var service = new MediaArtifactService(
                artifactRepository, jobRepository, transcriptRepository,
                frameIndexRepository, consentRepository, retentionPolicyRepository
        );
        
        var artifact = MediaArtifact.builder()
                .artifactId("artifact-123")
                .tenantId("tenant-456")
                .retentionPolicy("standard-30-day")
                .build();
        
        var policy = RetentionPolicy.builder()
                .policyId("policy-789")
                .tenantId("tenant-456")
                .policyName("standard-30-day")
                .retentionDays(30)
                .build();
        
        when(retentionPolicyRepository.findByTenantIdAndPolicyName("tenant-456", "standard-30-day"))
                .thenReturn(Optional.of(policy));
        when(artifactRepository.findByTenantIdAndArtifactId("tenant-456", "artifact-123"))
                .thenReturn(Optional.of(artifact));
        when(artifactRepository.save(any())).thenReturn(artifact);
        
        var result = service.applyRetentionPolicy("artifact-123", "tenant-456", "standard-30-day");
        
        assertThat(result).isNotNull();
        verify(artifactRepository).save(any());
    }

    @Test
    @DisplayName("Should list artifacts with filtering")
    void shouldListArtifactsWithFiltering() {
        var artifactRepository = mock(MediaArtifactRepository.class);
        var jobRepository = mock(MediaProcessingJobRepository.class);
        var transcriptRepository = mock(TranscriptRepository.class);
        var frameIndexRepository = mock(FrameIndexRepository.class);
        var consentRepository = mock(ConsentRepository.class);
        var retentionPolicyRepository = mock(RetentionPolicyRepository.class);
        
        var service = new MediaArtifactService(
                artifactRepository, jobRepository, transcriptRepository,
                frameIndexRepository, consentRepository, retentionPolicyRepository
        );
        
        var artifacts = List.of(
                MediaArtifact.builder().artifactId("artifact-1").build(),
                MediaArtifact.builder().artifactId("artifact-2").build()
        );
        
        when(artifactRepository.findByTenantIdAndMediaType("tenant-456", "video/mp4"))
                .thenReturn(artifacts);
        
        var result = service.listArtifacts("tenant-456", "video/mp4", null, 50);
        
        assertThat(result).isNotNull();
        verify(artifactRepository).findByTenantIdAndMediaType("tenant-456", "video/mp4");
    }
}
