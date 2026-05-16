package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.SourceLocator;
import com.ghatana.yappc.storage.source.RepositorySnapshotRepository;
import com.ghatana.yappc.storage.source.SourceImportJobRepository;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Unit tests for SourceImportServiceImpl — job lifecycle, scope isolation, and cancel/retry guards
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SourceImportServiceImpl Tests")
class SourceImportServiceImplTest {

    @Mock
    private SourceProviderRegistry providerRegistry;

    @Mock
    private SourceImportJobRepository jobRepository;

    @Mock
    private RepositorySnapshotRepository snapshotRepository;

    private SourceImportServiceImpl service;

    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    @BeforeEach
    void setUp() {
        service = new SourceImportServiceImpl(providerRegistry, jobRepository, snapshotRepository, DIRECT_EXECUTOR);
    }

    @Test
    @DisplayName("getJob delegates to repository with correct tenantId scope")
    void getJob_delegatesToRepositoryWithTenantScope() {
        String jobId = "job-abc";
        String tenantId = "tenant-xyz";

        when(jobRepository.findById(jobId, tenantId)).thenReturn(Promise.of(Optional.empty()));

        Promise<Optional<SourceImportService.SourceImportJob>> result = service.getJob(jobId, tenantId);

        verify(jobRepository).findById(eq(jobId), eq(tenantId));
        assertThat(result.getResult()).isEmpty();
    }

    @Test
    @DisplayName("listJobs delegates to repository with full tenant/workspace/project scope")
    void listJobs_delegatesToRepositoryWithFullScope() {
        String tenantId = "tenant-xyz";
        String workspaceId = "ws-1";
        String projectId = "proj-1";

        when(jobRepository.findByScope(tenantId, workspaceId, projectId)).thenReturn(Promise.of(List.of()));

        service.listJobs(tenantId, workspaceId, projectId);

        verify(jobRepository).findByScope(eq(tenantId), eq(workspaceId), eq(projectId));
    }

    @Test
    @DisplayName("cancelJob returns false when job does not exist")
    void cancelJob_returnsFalseWhenJobNotFound() {
        String jobId = "missing-job";
        String tenantId = "tenant-xyz";

        when(jobRepository.findById(jobId, tenantId)).thenReturn(Promise.of(Optional.empty()));

        Promise<Boolean> result = service.cancelJob(jobId, tenantId, "user-request");

        assertThat(result.getResult()).isFalse();
    }

    @Test
    @DisplayName("cancelJob returns false when job is already COMPLETED")
    void cancelJob_returnsFalseWhenAlreadyCompleted() {
        String jobId = "done-job";
        String tenantId = "tenant-xyz";

        SourceImportService.SourceImportJob completedJob = makeJob(jobId, tenantId, SourceImportService.JobStatus.COMPLETED);
        when(jobRepository.findById(jobId, tenantId)).thenReturn(Promise.of(Optional.of(completedJob)));

        Promise<Boolean> result = service.cancelJob(jobId, tenantId, "user-request");

        assertThat(result.getResult()).isFalse();
    }

    @Test
    @DisplayName("cancelJob cancels an IN_PROGRESS job and persists CANCELLED status")
    void cancelJob_cancelsInProgressJob() {
        String jobId = "active-job";
        String tenantId = "tenant-xyz";

        SourceImportService.SourceImportJob activeJob = makeJob(jobId, tenantId, SourceImportService.JobStatus.IN_PROGRESS);
        when(jobRepository.findById(jobId, tenantId)).thenReturn(Promise.of(Optional.of(activeJob)));
        when(jobRepository.save(any())).thenReturn(Promise.of(null));

        Promise<Boolean> result = service.cancelJob(jobId, tenantId, "user-request");

        assertThat(result.getResult()).isTrue();
        verify(jobRepository).save(any(SourceImportService.SourceImportJob.class));
    }

    @Test
    @DisplayName("retryJob throws when job does not exist")
    void retryJob_throwsWhenJobNotFound() {
        String jobId = "ghost-job";
        String tenantId = "tenant-xyz";

        when(jobRepository.findById(jobId, tenantId)).thenReturn(Promise.of(Optional.empty()));

        Promise<SourceImportService.SourceImportJob> result = service.retryJob(jobId, tenantId);

        assertThat(result.isException()).isTrue();
        assertThat(result.getException()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("retryJob throws when job is still IN_PROGRESS")
    void retryJob_throwsWhenJobNotFailed() {
        String jobId = "active-job";
        String tenantId = "tenant-xyz";

        SourceImportService.SourceImportJob inProgressJob = makeJob(jobId, tenantId, SourceImportService.JobStatus.IN_PROGRESS);
        when(jobRepository.findById(jobId, tenantId)).thenReturn(Promise.of(Optional.of(inProgressJob)));

        Promise<SourceImportService.SourceImportJob> result = service.retryJob(jobId, tenantId);

        assertThat(result.isException()).isTrue();
        assertThat(result.getException()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getJobSnapshot returns empty when job has no snapshot")
    void getJobSnapshot_returnsEmptyWhenNoSnapshot() {
        String jobId = "no-snap-job";
        String tenantId = "tenant-xyz";

        SourceImportService.SourceImportJob jobWithNoSnapshot = makeJob(jobId, tenantId, SourceImportService.JobStatus.COMPLETED);
        when(jobRepository.findById(jobId, tenantId)).thenReturn(Promise.of(Optional.of(jobWithNoSnapshot)));

        Promise<Optional<com.ghatana.yappc.domain.source.RepositorySnapshot>> result =
                service.getJobSnapshot(jobId, tenantId);

        assertThat(result.getResult()).isEmpty();
    }

    @Test
    @DisplayName("getJobSnapshot returns empty when job is not found")
    void getJobSnapshot_returnsEmptyWhenJobMissing() {
        String jobId = "missing";
        String tenantId = "tenant-xyz";

        when(jobRepository.findById(jobId, tenantId)).thenReturn(Promise.of(Optional.empty()));

        Promise<Optional<com.ghatana.yappc.domain.source.RepositorySnapshot>> result =
                service.getJobSnapshot(jobId, tenantId);

        assertThat(result.getResult()).isEmpty();
    }

    private SourceImportService.SourceImportJob makeJob(String jobId, String tenantId, SourceImportService.JobStatus status) {
        SourceLocator locator = SourceLocator.builder()
                .provider("github")
                .repoId("org/repo")
                .tenantId(tenantId)
                .workspaceId("ws-1")
                .projectId("proj-1")
                .build();

        return new SourceImportService.SourceImportJob(
                jobId,
                tenantId,
                "ws-1",
                "proj-1",
                locator,
                status,
                0,
                "test-step",
                null,
                null,
                java.time.Instant.now(),
                java.time.Instant.now(),
                null
        );
    }
}
