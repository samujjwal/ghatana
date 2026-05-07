package com.ghatana.digitalmarketing.bridge;

import com.ghatana.aiplatform.registry.ABTestingService;
import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryPort;
import com.ghatana.digitalmarketing.bridge.governance.AiExperimentConfig;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KERNEL-P2-3: AI model governance adapter for DMOS.
 *
 * <p>Bridges the platform {@link ModelRegistryPort} and {@link ABTestingService}
 * into the DMOS kernel bridge, providing:
 * <ul>
 *   <li><b>Versioned model registry</b> — register, promote, and deprecate AI models
 *       used by DMOS campaign AI features (ad-copy generation, audience scoring, etc.)
 *   <li><b>A/B evaluation framework</b> — define workspace-level experiments to split
 *       traffic between a baseline model and a candidate variant, enabling controlled
 *       model evaluation before full promotion to production.
 * </ul>
 *
 * <p>P0-011: Hardened with typed experiment configuration, approval gates, and audit events.
 * Model promotion now requires evaluation result, approval, and audit event recording.
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * DmosAiModelGovernanceAdapter governance = new DmosAiModelGovernanceAdapter(
 *     modelRegistryService, abTestingService);
 *
 * // Register a candidate model for DMOS ad-copy generation
 * governance.registerCandidateModel(tenantId, "dmos-adcopy", "v2.0.0");
 *
 * // Define an A/B experiment with typed configuration
 * governance.defineExperiment(tenantId, "adcopy-v2-trial",
 *     "dmos-adcopy:v1.0.0", "dmos-adcopy:v2.0.0", "20%");
 *
 * // Record metrics and approve before promotion
 * governance.recordExperimentMetrics(tenantId, "adcopy-v2-trial", metrics);
 * governance.approveExperiment(tenantId, "adcopy-v2-trial", "approver-id");
 *
 * // Promote with approval gate
 * governance.promoteToProduction(tenantId, "dmos-adcopy", "v2.0.0", "adcopy-v2-trial");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose AI model governance adapter for DMOS — versioned registry + A/B evaluation
 * @doc.layer product
 * @doc.pattern Adapter, Façade
 */
public final class DmosAiModelGovernanceAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(DmosAiModelGovernanceAdapter.class);

    private final ModelRegistryPort modelRegistry;
    private final ABTestingService abTesting;
    
    // P0-011: In-memory store for experiment configurations with approval state
    private final Map<String, AiExperimentConfig> experimentConfigs = new ConcurrentHashMap<>();
    
    // P0-011: Audit event recorder for model governance actions
    private final ModelGovernanceAuditRecorder auditRecorder;

    /**
     * @param modelRegistry platform model registry service
     * @param abTesting     platform A/B testing service
     */
    public DmosAiModelGovernanceAdapter(
            ModelRegistryPort modelRegistry,
            ABTestingService abTesting) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry required");
        this.abTesting = Objects.requireNonNull(abTesting, "abTesting required");
        this.auditRecorder = new ModelGovernanceAuditRecorder();
    }

    public DmosAiModelGovernanceAdapter(
            ModelRegistryPort modelRegistry,
            ABTestingService abTesting,
            ModelGovernanceAuditRecorder auditRecorder) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry required");
        this.abTesting = Objects.requireNonNull(abTesting, "abTesting required");
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder required");
    }

    // ─── Model Registry Operations ─────────────────────────────────────────────

    /**
     * Registers a new candidate model version in the platform model registry.
     * The model is registered in {@link DeploymentStatus#STAGING} status until
     * promoted via {@link #promoteToProduction(DmTenantId, String, String)}.
     *
     * @param tenantId tenant owning this model
     * @param modelName canonical DMOS model name (e.g. {@code "dmos-adcopy"})
     * @param version   semantic version string (e.g. {@code "v2.0.0"})
     * @return Promise of the registered {@link ModelMetadata}
     */
    public Promise<ModelMetadata> registerCandidateModel(
            DmTenantId tenantId, String modelName, String version) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(modelName, "modelName required");
        Objects.requireNonNull(version, "version required");

        return Promise.ofCallback(cb -> {
            try {
                ModelMetadata metadata = ModelMetadata.builder()
                        .id(java.util.UUID.randomUUID())
                        .tenantId(tenantId.getValue())
                        .name(modelName)
                        .version(version)
                        .deploymentStatus(DeploymentStatus.STAGED)
                        .createdAt(java.time.Instant.now())
                        .updatedAt(java.time.Instant.now())
                        .build();
                modelRegistry.register(metadata);
                LOG.info("[DMOS][AI-Gov] Candidate model registered: tenantId={} model={} version={}",
                        tenantId.getValue(), modelName, version);
                cb.set(metadata);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to register candidate model: model={} version={} error={}",
                        modelName, version, ex.getMessage(), ex);
                cb.setException(ex);
            }
        });
    }

    /**
     * P0-011: Promotes a staged model to {@link DeploymentStatus#PRODUCTION} with approval gate.
     * The model must already be registered via {@link #registerCandidateModel}.
     * 
     * <p>Requires experiment approval if experimentId is provided.
     *
     * @param tenantId    tenant owning the model
     * @param modelName   model name
     * @param version     version to promote
     * @param experimentId optional experiment ID that approved this promotion
     * @return Promise of the promoted {@link ModelMetadata}
     */
    public Promise<ModelMetadata> promoteToProduction(
            DmTenantId tenantId, String modelName, String version, String experimentId) {
        Objects.requireNonNull(tenantId, "tenantId required");

        return Promise.ofCallback(cb -> {
            try {
                // P0-011: Validate model refs
                String modelRef = modelName + ":" + version;
                AiExperimentConfig.validateModelRef(modelRef);
                
                // P0-011: Check approval gate if experiment provided
                if (experimentId != null) {
                    String configKey = tenantId.getValue() + ":" + experimentId;
                    AiExperimentConfig config = experimentConfigs.get(configKey);
                    if (config == null) {
                        cb.setException(new IllegalArgumentException(
                                "Experiment not found: " + experimentId));
                        return;
                    }
                    if (config.approvalState() != AiExperimentConfig.ApprovalState.APPROVED) {
                        cb.setException(new IllegalStateException(
                                "Experiment not approved: " + experimentId + 
                                " (state: " + config.approvalState() + ")"));
                        return;
                    }
                    // P0-011: Verify metrics exist for approved experiment
                    if (config.metrics().outcome().equals("IN_PROGRESS")) {
                        cb.setException(new IllegalStateException(
                                "Experiment metrics incomplete: " + experimentId));
                        return;
                    }
                }

                Optional<ModelMetadata> found =
                        modelRegistry.findByName(tenantId.getValue(), modelName, version);
                if (found.isEmpty()) {
                    cb.setException(new IllegalArgumentException(
                            "Model not found: " + modelName + "@" + version
                                    + " for tenant " + tenantId.getValue()));
                    return;
                }
                ModelMetadata model = found.get();
                modelRegistry.updateStatus(tenantId.getValue(), model.getId(), DeploymentStatus.PRODUCTION);

                // P0-011: Record audit event for promotion
                auditRecorder.recordPromotion(tenantId.getValue(), modelName, version, experimentId);

                // Re-fetch to get updated metadata
                ModelMetadata updated = modelRegistry.findByName(tenantId.getValue(), modelName, version)
                        .orElse(model);
                LOG.info("[DMOS][AI-Gov] Model promoted to PRODUCTION: tenantId={} model={} version={} experiment={}",
                        tenantId.getValue(), modelName, version, experimentId);
                cb.set(updated);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to promote model: model={} version={} error={}",
                        modelName, version, ex.getMessage(), ex);
                cb.setException(ex);
            }
        });
    }

    /**
     * Legacy promote method without experiment ID (for backward compatibility).
     */
    public Promise<ModelMetadata> promoteToProduction(
            DmTenantId tenantId, String modelName, String version) {
        return promoteToProduction(tenantId, modelName, version, null);
    }

    /**
     * Deprecates a model version, marking it {@link DeploymentStatus#DEPRECATED}.
     *
     * @return Promise that completes when the status is updated
     */
    public Promise<Void> deprecateModel(DmTenantId tenantId, String modelName, String version) {
        Objects.requireNonNull(tenantId, "tenantId required");

        return Promise.ofCallback(cb -> {
            try {
                Optional<ModelMetadata> found =
                        modelRegistry.findByName(tenantId.getValue(), modelName, version);
                if (found.isEmpty()) {
                    cb.setException(new IllegalArgumentException(
                            "Cannot deprecate unknown model: " + modelName + "@" + version));
                    return;
                }
                modelRegistry.updateStatus(tenantId.getValue(), found.get().getId(), DeploymentStatus.DEPRECATED);
                LOG.info("[DMOS][AI-Gov] Model deprecated: tenantId={} model={} version={}",
                        tenantId.getValue(), modelName, version);
                cb.set(null);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to deprecate model: error={}", ex.getMessage(), ex);
                cb.setException(ex);
            }
        });
    }

    /**
     * Lists all model versions for a given model name in descending order of registration.
     */
    public Promise<List<ModelMetadata>> listVersions(DmTenantId tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(modelName, "modelName required");

        return Promise.ofCallback(cb -> {
            try {
                List<ModelMetadata> versions = modelRegistry.listVersions(tenantId.getValue(), modelName);
                cb.set(versions);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to list versions: model={} error={}", modelName, ex.getMessage(), ex);
                cb.setException(ex);
            }
        });
    }

    /**
     * Lists all models at {@link DeploymentStatus#PRODUCTION} for the tenant.
     */
    public Promise<List<ModelMetadata>> listProductionModels(DmTenantId tenantId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        return Promise.ofCallback(cb -> {
            try {
                cb.set(modelRegistry.findByStatus(tenantId.getValue(), DeploymentStatus.PRODUCTION));
            } catch (Exception ex) {
                cb.setException(ex);
            }
        });
    }

    // ─── A/B Evaluation Operations ─────────────────────────────────────────────

    /**
     * P0-011: Defines a new A/B experiment with typed configuration and validated split percent.
     *
     * @param tenantId         tenant owning the experiment
     * @param experimentId     unique experiment identifier
     * @param baselineModelRef model reference, format {@code "name:version"}
     * @param variantModelRef  model reference for the variant
     * @param splitPercent     traffic split for variant, e.g. {@code "20%"}
     */
    public Promise<Void> defineExperiment(
            DmTenantId tenantId,
            String experimentId,
            String baselineModelRef,
            String variantModelRef,
            String splitPercent) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(experimentId, "experimentId required");
        Objects.requireNonNull(baselineModelRef, "baselineModelRef required");
        Objects.requireNonNull(variantModelRef, "variantModelRef required");
        Objects.requireNonNull(splitPercent, "splitPercent required");

        return Promise.ofCallback(cb -> {
            try {
                // P0-011: Validate model refs format
                AiExperimentConfig.validateModelRef(baselineModelRef);
                AiExperimentConfig.validateModelRef(variantModelRef);
                
                // P0-011: Parse and validate split percent
                AiExperimentConfig.SplitPercent validatedSplit = 
                        AiExperimentConfig.parseSplitPercent(splitPercent);
                
                // P0-011: Create typed experiment configuration
                AiExperimentConfig config = AiExperimentConfig.builder()
                        .experimentId(experimentId)
                        .baselineModelRef(baselineModelRef)
                        .variantModelRef(variantModelRef)
                        .splitPercent(validatedSplit)
                        .status(AiExperimentConfig.ExperimentStatus.DRAFT)
                        .approvalState(AiExperimentConfig.ApprovalState.PENDING)
                        .build();
                
                // Store typed configuration
                String configKey = tenantId.getValue() + ":" + experimentId;
                experimentConfigs.put(configKey, config);
                
                // Register with platform AB testing service
                ABTestingService.Experiment experiment = new ABTestingService.Experiment(
                        experimentId,
                        experimentId,
                        validatedSplit.toString(),
                        baselineModelRef,
                        variantModelRef
                );
                abTesting.registerExperiment(tenantId.getValue(), experiment);
                
                // P0-011: Record audit event
                auditRecorder.recordExperimentDefined(tenantId.getValue(), experimentId, 
                        baselineModelRef, variantModelRef, validatedSplit.toString());
                
                LOG.info("[DMOS][AI-Gov] A/B experiment defined: tenantId={} experimentId={} baseline={} variant={} split={}",
                        tenantId.getValue(), experimentId, baselineModelRef, variantModelRef, validatedSplit);
                cb.set(null);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to define experiment: id={} error={}", experimentId, ex.getMessage(), ex);
                cb.setException(ex);
            }
        });
    }

    /**
     * P0-011: Records metrics for an experiment.
     *
     * @param tenantId     tenant owning the experiment
     * @param experimentId experiment identifier
     * @param metrics      experiment metrics
     */
    public Promise<Void> recordExperimentMetrics(
            DmTenantId tenantId,
            String experimentId,
            AiExperimentConfig.ExperimentMetrics metrics) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(experimentId, "experimentId required");
        Objects.requireNonNull(metrics, "metrics required");

        return Promise.ofCallback(cb -> {
            try {
                String configKey = tenantId.getValue() + ":" + experimentId;
                AiExperimentConfig config = experimentConfigs.get(configKey);
                if (config == null) {
                    cb.setException(new IllegalArgumentException("Experiment not found: " + experimentId));
                    return;
                }
                
                // Update configuration with metrics
                AiExperimentConfig updated = config.toBuilder()
                        .metrics(metrics)
                        .updatedAt(java.time.Instant.now())
                        .build();
                experimentConfigs.put(configKey, updated);
                
                // P0-011: Record audit event
                auditRecorder.recordMetricsRecorded(tenantId.getValue(), experimentId, metrics.outcome());
                
                LOG.info("[DMOS][AI-Gov] Experiment metrics recorded: tenantId={} experimentId={} outcome={}",
                        tenantId.getValue(), experimentId, metrics.outcome());
                cb.set(null);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to record metrics: experimentId={} error={}", 
                        experimentId, ex.getMessage(), ex);
                cb.setException(ex);
            }
        });
    }

    /**
     * P0-011: Approves an experiment for model promotion.
     *
     * @param tenantId    tenant owning the experiment
     * @param experimentId experiment identifier
     * @param approverId  ID of the approver
     */
    public Promise<Void> approveExperiment(
            DmTenantId tenantId,
            String experimentId,
            String approverId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(experimentId, "experimentId required");
        Objects.requireNonNull(approverId, "approverId required");

        return Promise.ofCallback(cb -> {
            try {
                String configKey = tenantId.getValue() + ":" + experimentId;
                AiExperimentConfig config = experimentConfigs.get(configKey);
                if (config == null) {
                    cb.setException(new IllegalArgumentException("Experiment not found: " + experimentId));
                    return;
                }
                
                // Update approval state
                AiExperimentConfig updated = config.toBuilder()
                        .approvalState(AiExperimentConfig.ApprovalState.APPROVED)
                        .status(AiExperimentConfig.ExperimentStatus.COMPLETED)
                        .updatedAt(java.time.Instant.now())
                        .build();
                experimentConfigs.put(configKey, updated);
                
                // P0-011: Record audit event
                auditRecorder.recordApproval(tenantId.getValue(), experimentId, approverId);
                
                LOG.info("[DMOS][AI-Gov] Experiment approved: tenantId={} experimentId={} approver={}",
                        tenantId.getValue(), experimentId, approverId);
                cb.set(null);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to approve experiment: experimentId={} error={}", 
                        experimentId, ex.getMessage(), ex);
                cb.setException(ex);
            }
        });
    }

    /**
     * Assigns a campaign entity to either the baseline or variant model
     * for the given experiment. The assignment is deterministic per entity.
     *
     * @param tenantId     tenant
     * @param experimentId A/B experiment identifier
     * @param entityId     campaign ID or audience ID used for variant assignment
     * @return Promise of the assigned model reference ({@code "name:version"})
     */
    public Promise<String> assignModel(DmTenantId tenantId, String experimentId, String entityId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(experimentId, "experimentId required");
        Objects.requireNonNull(entityId, "entityId required");

        return Promise.ofCallback(cb -> {
            try {
                String assigned = abTesting.assignVariant(tenantId.getValue(), experimentId, entityId);
                LOG.debug("[DMOS][AI-Gov] Model assigned for A/B: tenantId={} experiment={} entity={} model={}",
                        tenantId.getValue(), experimentId, entityId, assigned);
                cb.set(assigned);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to assign model variant: experiment={} entity={} error={}",
                        experimentId, entityId, ex.getMessage(), ex);
                cb.setException(ex);
            }
        });
    }

    /**
     * Ends an active A/B experiment.
     */
    public Promise<Void> endExperiment(DmTenantId tenantId, String experimentId) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(experimentId, "experimentId required");

        return Promise.ofCallback(cb -> {
            try {
                abTesting.endExperiment(tenantId.getValue(), experimentId);
                LOG.info("[DMOS][AI-Gov] A/B experiment ended: tenantId={} experimentId={}",
                        tenantId.getValue(), experimentId);
                cb.set(null);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to end experiment: id={} error={}", experimentId, ex.getMessage(), ex);
                cb.setException(ex);
            }
        });
    }
}
