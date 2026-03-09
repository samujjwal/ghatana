package com.ghatana.yappc.api.scaffold;

import com.ghatana.yappc.api.scaffold.dto.*;
import com.ghatana.yappc.api.scaffold.GenerationContext;
import com.ghatana.yappc.api.scaffold.GenerationResult;
import com.ghatana.yappc.api.scaffold.ScaffoldEngine;
import com.ghatana.yappc.api.scaffold.Template;
import com.ghatana.yappc.api.scaffold.TemplateRegistry;
import io.activej.inject.annotation.Inject;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Scaffolding Service - Wraps core scaffold engine from libs:scaffold.
 * 
 * <p>Responsibilities:
 * <ul>
 *   <li>Template discovery and metadata</li>
 *   <li>Project generation from templates</li>
 *   <li>Async job execution and tracking</li>
 *   <li>Feature pack management</li>
 *   <li>Conflict detection and resolution</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Business logic for project scaffolding
 * @doc.layer product
 * @doc.pattern Service
 */
public class ScaffoldService {
    
    private static final Logger log = LoggerFactory.getLogger(ScaffoldService.class);
    
    private final ScaffoldEngine scaffoldEngine;
    private final TemplateRegistry templateRegistry;
    private final Executor blockingExecutor;
    
    private final ConcurrentHashMap<String, JobStatus> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, byte[]> projectArchives = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConflictReport> conflicts = new ConcurrentHashMap<>();
    
    @Inject
    public ScaffoldService(ScaffoldEngine scaffoldEngine, TemplateRegistry templateRegistry) {
        this.scaffoldEngine = scaffoldEngine;
        this.templateRegistry = templateRegistry;
        this.blockingExecutor = Executors.newFixedThreadPool(4);
        log.info("ScaffoldService initialized with scaffold engine");
    }
    
    /**
     * Lists all available project templates.
     * 
     * @return promise of template list
     */
    public Promise<List<TemplateInfo>> listTemplates(String category) {
        log.debug("Listing templates, category={}", category);
        
        return Promise.ofBlocking(blockingExecutor, () -> {
            List<Template> templates = templateRegistry.getAllTemplates();
            return templates.stream()
                    .filter(t -> category == null || category.equals(t.getCategory()))
                    .map(this::toTemplateInfo)
                    .collect(Collectors.toList());
        });
    }

    public Promise<List<TemplateInfo>> listTemplates() {
        return listTemplates(null);
    }
    
    /**
     * Gets detailed information about a specific template.
     * 
     * @param templateId template identifier
     * @return promise of optional template info
     */
    public Promise<Optional<TemplateInfo>> getTemplate(String templateId) {
        log.debug("Getting template: {}", templateId);
        
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<Template> template = templateRegistry.getTemplate(templateId);
            return template.map(this::toTemplateInfo);
        });
    }
    
    /**
     * Gets template configuration schema.
     * 
     * @param templateId template identifier
     * @return promise of config schema map
     */
    public Promise<Map<String, Object>> getTemplateConfigSchema(String templateId) {
        log.debug("Getting template config schema: {}", templateId);
        
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<Template> template = templateRegistry.getTemplate(templateId);
            if (template.isEmpty()) {
                return Map.of();
            }
            return template.get().getConfigSchema();
        });
    }
    
    /**
     * Scaffolds a new project from template.
     * 
     * @param request scaffold configuration
     * @return promise of scaffold result with job ID
     */
    public Promise<ScaffoldResult> scaffoldProject(ScaffoldRequest request) {
        String jobId = UUID.randomUUID().toString();
        log.info("Starting scaffold job: jobId={}, template={}, project={}", 
                jobId, request.templateId(), request.projectName());
        
        // Initialize job status
        JobStatus status = JobStatus.running(jobId, 0, "Initializing scaffolding");
        jobs.put(jobId, status);

        Promise.ofBlocking(blockingExecutor, () -> {
            try {
                jobs.put(jobId, JobStatus.running(jobId, 10, "Loading template"));
                
                Optional<Template> template = templateRegistry.getTemplate(request.templateId());
                if (template.isEmpty()) {
                    throw new IllegalArgumentException("Template not found: " + request.templateId());
                }
                
                jobs.put(jobId, JobStatus.running(jobId, 25, "Preparing generation context"));
                
                GenerationContext context = GenerationContext.builder()
                        .template(template.get())
                        .projectName(request.projectName())
                        .outputPath(Path.of(request.outputPath()))
                        .configuration(request.configuration())
                        .build();
                
                jobs.put(jobId, JobStatus.running(jobId, 50, "Generating project files"));
                
                GenerationResult result = scaffoldEngine.generate(context);
                
                jobs.put(jobId, JobStatus.running(jobId, 75, "Creating archive"));
                
                byte[] archive = createZipArchive(Path.of(request.outputPath()));
                projectArchives.put(jobId, archive);
                
                jobs.put(jobId, JobStatus.completed(jobId));
                
                log.info("Scaffold job completed: jobId={}, files={}", jobId, result.filesGenerated().size());
                return null;
            } catch (Exception e) {
                log.error("Scaffold job failed: jobId={}", jobId, e);
                jobs.put(jobId, JobStatus.failed(jobId, e.getMessage()));
                throw e;
            }
        }).whenException(e -> log.error("Async scaffold execution failed: jobId={}", jobId, e);

        return Promise.of(ScaffoldResult.started(jobId));
    }

    /**
     * Gets current status for a job.
     *
     * @param jobId job identifier
     * @return promise of optional job status
     */
    public Promise<Optional<JobStatus>> getJobStatus(String jobId) {
        log.debug("Getting job status: {}", jobId);
        return Promise.of(Optional.ofNullable(jobs.get(jobId)));
    }
    
    /**
     * Downloads the generated project as ZIP archive.
     * 
     * @param jobId job identifier
     * @return promise of optional file data
     */
    public Promise<Optional<byte[]>> downloadProject(String jobId) {
        log.info("Downloading project: jobId={}", jobId);
        
        JobStatus status = jobs.get(jobId);
        if (status == null || !"COMPLETED".equals(status.status())) {
            return Promise.of(Optional.empty());
        }
        
        byte[] archive = projectArchives.get(jobId);
        return Promise.of(Optional.ofNullable(archive));
    }
    
    /**
     * Cancels an ongoing scaffolding job.
     * 
     * @param jobId job identifier
     * @return promise of true if cancelled
     */
    public Promise<Boolean> cancelJob(String jobId) {
        log.info("Cancelling job: {}", jobId);
        
        JobStatus status = jobs.get(jobId);
        if (status == null || "COMPLETED".equals(status.status()) || "FAILED".equals(status.status())) {
            return Promise.of(false);
        }
        
        // NOTE: Actually cancel the running job
        jobs.put(jobId, new JobStatus(jobId, "CANCELLED", status.progress(), 
                "Job cancelled by user", status.startedAt(), null, null));
        
        return Promise.of(true);
    }
    
    /**
     * Lists all available feature packs.
     * 
     * @return promise of feature pack list
     */
    public Promise<List<FeaturePackInfo>> listFeaturePacks() {
        log.debug("Listing feature packs");
        
        return Promise.ofBlocking(blockingExecutor, () -> {
            return templateRegistry.getAllFeaturePacks().stream()
                    .map(fp -> new FeaturePackInfo(
                            fp.id(),
                            fp.name(),
                            fp.description(),
                            fp.compatibleProjectTypes(),
                            fp.dependencies()))
                    .collect(Collectors.toList());
        });
    }
    
    /**
     * Adds a feature pack to an existing project.
     * 
     * @param projectId project identifier
     * @param request feature pack configuration
     * @return promise of scaffold result with job ID
     */
    public Promise<ScaffoldResult> addFeaturePack(String projectId, FeaturePackRequest request) {
        String jobId = UUID.randomUUID().toString();
        log.info("Adding feature pack: jobId={}, project={}, featurePack={}", 
                jobId, projectId, request.featurePackId());
        
        JobStatus status = JobStatus.running(jobId, 0, "Adding feature pack");
        jobs.put(jobId, status);
        
        Promise.ofBlocking(blockingExecutor, () -> {
            try {
                var featurePack = templateRegistry.getFeaturePack(request.featurePackId());
                if (featurePack.isEmpty()) {
                    throw new IllegalArgumentException("Feature pack not found: " + request.featurePackId());
                }
                
                GenerationContext context = GenerationContext.builder()
                        .featurePack(featurePack.get())
                        .projectPath(Path.of(projectId))
                        .configuration(request.configuration())
                        .build();
                
                scaffoldEngine.applyFeaturePack(context);
                jobs.put(jobId, JobStatus.completed(jobId));
                return null;
            } catch (Exception e) {
                log.error("Feature pack job failed: jobId={}", jobId, e);
                jobs.put(jobId, JobStatus.failed(jobId, e.getMessage()));
                throw e;
            }
        }).whenException(e -> log.error("Async feature pack execution failed", e);
        
        return Promise.of(ScaffoldResult.started(jobId));
    }
    
    /**
     * Gets conflicts detected during scaffolding.
     * 
     * @param jobId job identifier
     * @return promise of conflict report
     */
    public Promise<ConflictReport> getConflicts(String jobId) {
        log.debug("Getting conflicts for job: {}", jobId);
        
        ConflictReport report = conflicts.get(jobId);
        if (report == null) {
            report = new ConflictReport(jobId, List.of());
        }
        
        return Promise.of(report);
    }
    
    /**
     * Resolves conflicts with user decisions.
     * 
     * @param jobId job identifier
     * @param resolution conflict resolutions
     * @return promise of true if successful
     */
    public Promise<Boolean> resolveConflicts(String jobId, ConflictResolution resolution) {
        log.info("Resolving conflicts for job: {}", jobId);
        
        return Promise.ofBlocking(blockingExecutor, () -> {
            scaffoldEngine.resolveConflicts(jobId, resolution.resolutions());
            conflicts.remove(jobId);
            return true;
        });
    }
    
    // ============================================================================
    // Helper Methods
    // ============================================================================
    
    private TemplateInfo toTemplateInfo(Template template) {
        return new TemplateInfo(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getCategory(),
                template.getTags(),
                template.getVersion(),
                template.getConfigSchema());
    }
    
    private byte[] createZipArchive(Path sourcePath) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            Files.walk(sourcePath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            ZipEntry zipEntry = new ZipEntry(sourcePath.relativize(path).toString());
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to add file to archive", e);
                        }
                    });
        }
        return baos.toByteArray();
    }
}
