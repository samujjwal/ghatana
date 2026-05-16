package com.ghatana.yappc.services.source;

import com.ghatana.yappc.domain.source.SourceLocator;
import com.ghatana.yappc.domain.source.RepositorySnapshot;
import com.ghatana.yappc.storage.source.SourceImportJobRepository;
import com.ghatana.yappc.storage.source.RepositorySnapshotRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @doc.type class
 * @doc.purpose Implementation of durable source import job lifecycle service.
 *              Orchestrates source providers, job persistence, and snapshot creation.
 * @doc.layer service
 * @doc.pattern Service
 */
public class SourceImportServiceImpl implements SourceImportService {

    private static final Logger log = LoggerFactory.getLogger(SourceImportServiceImpl.class);

    private final SourceProviderRegistry providerRegistry;
    private final SourceImportJobRepository jobRepository;
    private final RepositorySnapshotRepository snapshotRepository;
    private final Executor executor;

    public SourceImportServiceImpl(
            SourceProviderRegistry providerRegistry,
            SourceImportJobRepository jobRepository,
            RepositorySnapshotRepository snapshotRepository,
            Executor executor) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry must not be null");
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository must not be null");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<SourceImportJob> startImport(SourceLocator locator) {
        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        SourceImportJob job = new SourceImportJob(
                jobId,
                locator.tenantId(),
                locator.workspaceId(),
                locator.projectId(),
                locator,
                JobStatus.PENDING,
                0,
                "created",
                null,
                null,
                now,
                now,
                null
        );

        log.info("Starting source import job {} for provider={} repoId={} tenant={} workspace={} project={}",
                jobId, locator.provider(), locator.repoId(), locator.tenantId(), locator.workspaceId(), locator.projectId());

        return jobRepository.save(job)
                .then(v -> executeImport(jobId, locator))
                .whenException(e -> log.error("Failed to start import job {}", jobId, e));
    }

    @Override
    public Promise<Optional<SourceImportJob>> getJob(String jobId, String tenantId) {
        return jobRepository.findById(jobId, tenantId);
    }

    @Override
    public Promise<List<SourceImportJob>> listJobs(String tenantId, String workspaceId, String projectId) {
        return jobRepository.findByScope(tenantId, workspaceId, projectId);
    }

    @Override
    public Promise<Boolean> cancelJob(String jobId, String tenantId, String reason) {
        log.info("Cancelling import job {} for tenant={} reason={}", jobId, tenantId, reason);

        return jobRepository.findById(jobId, tenantId)
                .then(optJob -> {
                    if (optJob.isEmpty()) {
                        return Promise.of(false);
                    }
                    SourceImportJob job = optJob.get();
                    if (job.status() == JobStatus.COMPLETED || job.status() == JobStatus.CANCELLED) {
                        return Promise.of(false);
                    }
                    SourceImportJob cancelledJob = new SourceImportJob(
                            job.jobId(),
                            job.tenantId(),
                            job.workspaceId(),
                            job.projectId(),
                            job.locator(),
                            JobStatus.CANCELLED,
                            job.progressPercent(),
                            "cancelled",
                            reason,
                            job.snapshotId(),
                            job.createdAt(),
                            Instant.now(),
                            Instant.now()
                    );
                    return jobRepository.save(cancelledJob).map(v -> true);
                });
    }

    @Override
    public Promise<SourceImportJob> retryJob(String jobId, String tenantId) {
        log.info("Retrying import job {} for tenant={}", jobId, tenantId);

        return jobRepository.findById(jobId, tenantId)
                .then(optJob -> {
                    if (optJob.isEmpty()) {
                        return Promise.<SourceImportJob>ofException(
                                new IllegalArgumentException("Job not found: " + jobId));
                    }
                    SourceImportJob job = optJob.get();
                    if (job.status() != JobStatus.FAILED && job.status() != JobStatus.CANCELLED) {
                        return Promise.<SourceImportJob>ofException(
                                new IllegalStateException("Can only retry failed or cancelled jobs"));
                    }
                    return startImport(job.locator());
                });
    }

    @Override
    public Promise<Optional<RepositorySnapshot>> getJobSnapshot(String jobId, String tenantId) {
        return jobRepository.findById(jobId, tenantId)
                .then(optJob -> {
                    if (optJob.isEmpty()) {
                        return Promise.of(Optional.<RepositorySnapshot>empty());
                    }
                    SourceImportJob job = optJob.get();
                    if (job.snapshotId() == null) {
                        return Promise.of(Optional.<RepositorySnapshot>empty());
                    }
                    return snapshotRepository.findById(job.snapshotId(), tenantId)
                            .map(Optional::ofNullable);
                });
    }

    @Override
    public Promise<CompilePipelineResult> runCompilePipeline(SourceLocator locator) {
        String jobId = UUID.randomUUID().toString();
        log.info("Running compile pipeline job {} for repo={}", jobId, locator.repoId());

        return startImport(locator)
                .then(job -> {
                    // Wait for import completion and create pipeline result
                    // In production, this would orchestrate the full pipeline:
                    // 1. Import source
                    // 2. Run TS extractor worker
                    // 3. Build artifact graph
                    // 4. Synthesize semantic model
                    return waitForCompletion(job.jobId(), locator.tenantId())
                            .then(finalJob -> {
                                if (finalJob.status() != JobStatus.COMPLETED) {
                                    return Promise.<CompilePipelineResult>ofException(
                                            new RuntimeException("Import failed: " + finalJob.errorMessage()));
                                }
                                return getJobSnapshot(finalJob.jobId(), locator.tenantId())
                                        .map(optSnapshot -> new CompilePipelineResult(
                                                finalJob.jobId(),
                                                finalJob.snapshotId(),
                                                optSnapshot.orElse(null),
                                                finalJob.status().name(),
                                                java.util.Map.of(),
                                                java.util.Map.of(),
                                                Instant.now()
                                        ));
                            });
                });
    }

    private Promise<SourceImportJob> executeImport(String jobId, SourceLocator locator) {
        return Promise.ofBlocking(executor, () -> {
            // Update job to in-progress
            SourceImportJob inProgressJob = updateJobStatus(jobId, JobStatus.IN_PROGRESS, 10, "resolving_source");
            jobRepository.saveBlocking(inProgressJob);

            // Resolve the source provider
            SourceProvider provider = providerRegistry.resolve(locator.provider())
                    .orElseThrow(() -> new IllegalStateException("No provider found for: " + locator.provider()));

            // Build scope context
            SourceProvider.ScopeContext scope = new SourceProvider.ScopeContext(
                    locator.tenantId(),
                    locator.workspaceId(),
                    locator.projectId(),
                    null // principalId would come from security context
            );

            // Update progress
            SourceImportJob resolvingJob = updateJobStatus(jobId, JobStatus.IN_PROGRESS, 30, "materializing_snapshot");
            jobRepository.saveBlocking(resolvingJob);

            // Resolve the snapshot (blocking call wrapped in Promise.ofBlocking)
            RepositorySnapshot snapshot = provider.resolve(locator, scope).getResult();

            // Save the snapshot
            snapshotRepository.saveBlocking(snapshot);

            // Mark job as completed
            SourceImportJob completedJob = new SourceImportJob(
                    jobId,
                    locator.tenantId(),
                    locator.workspaceId(),
                    locator.projectId(),
                    locator,
                    JobStatus.COMPLETED,
                    100,
                    "completed",
                    null,
                    snapshot.snapshotId(),
                    resolvingJob.createdAt(),
                    Instant.now(),
                    Instant.now()
            );
            jobRepository.saveBlocking(completedJob);

            return completedJob;
        }).whenException(e -> {
            log.error("Import job {} failed", jobId, e);
            SourceImportJob failedJob = new SourceImportJob(
                    jobId,
                    locator.tenantId(),
                    locator.workspaceId(),
                    locator.projectId(),
                    locator,
                    JobStatus.FAILED,
                    0,
                    "failed",
                    e.getMessage(),
                    null,
                    Instant.now(),
                    Instant.now(),
                    null
            );
            jobRepository.saveBlocking(failedJob);
        });
    }

    private SourceImportJob updateJobStatus(String jobId, JobStatus status, int progress, String step) {
        // This is a helper that would normally fetch and update - simplified here
        return new SourceImportJob(
                jobId, null, null, null, null, status, progress, step, null, null,
                Instant.now(), Instant.now(), null
        );
    }

    private Promise<SourceImportJob> waitForCompletion(String jobId, String tenantId) {
        // Poll for job completion
        return jobRepository.findById(jobId, tenantId)
                .then(optJob -> {
                    if (optJob.isEmpty()) {
                        return Promise.<SourceImportJob>ofException(
                                new IllegalStateException("Job not found during wait: " + jobId));
                    }
                    SourceImportJob job = optJob.get();
                    if (job.status() == JobStatus.COMPLETED ||
                            job.status() == JobStatus.FAILED ||
                            job.status() == JobStatus.CANCELLED) {
                        return Promise.of(job);
                    }
                    // Poll again after delay (in production, use proper async scheduling)
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return waitForCompletion(jobId, tenantId);
                });
    }

    private RepositorySnapshot convertToDomainSnapshot(
            com.ghatana.yappc.services.source.RepositorySnapshot serviceSnapshot,
            SourceLocator locator) {

        String snapshotId = UUID.randomUUID().toString();

        List<RepositorySnapshot.SnapshotFile> domainFiles = serviceSnapshot.files().stream()
                .map(f -> new RepositorySnapshot.SnapshotFile(
                        f.relativePath(),
                        f.absolutePath(),
                        f.sizeBytes(),
                        f.lastModifiedAt(),
                        null // content checksum not provided by service snapshot
                ))
                .toList();

        List<RepositorySnapshot.SnapshotDiagnostic> diagnostics = serviceSnapshot.diagnostics().stream()
                .map(d -> new RepositorySnapshot.SnapshotDiagnostic(
                        RepositorySnapshot.DiagnosticLevel.WARNING,
                        "provider",
                        d.toString(),
                        null,
                        Instant.now()
                ))
                .toList();

        return RepositorySnapshot.builder()
                .snapshotId(snapshotId)
                .provider(serviceSnapshot.provider())
                .repoId(serviceSnapshot.repoId())
                .commitSha(serviceSnapshot.commitSha())
                .contentHash(serviceSnapshot.contentChecksum())
                .materializedRoot(serviceSnapshot.localRootPath())
                .files(domainFiles)
                .checksum(serviceSnapshot.contentChecksum())
                .diagnostics(diagnostics)
                .createdAt(serviceSnapshot.snapshotAt())
                .tenantId(locator.tenantId())
                .workspaceId(locator.workspaceId())
                .projectId(locator.projectId())
                .build();
    }
}
