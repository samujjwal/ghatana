package com.ghatana.digitalmarketing.bridge;

import com.ghatana.aiplatform.registry.ABTestingService;
import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryPort;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 * <p><b>Usage</b>
 * <pre>{@code
 * DmosAiModelGovernanceAdapter governance = new DmosAiModelGovernanceAdapter(
 *     modelRegistryService, abTestingService);
 *
 * // Register a candidate model for DMOS ad-copy generation
 * governance.registerCandidateModel(tenantId, "dmos-adcopy", "v2.0.0");
 *
 * // Define an A/B experiment
 * governance.defineExperiment(tenantId, "adcopy-v2-trial",
 *     "dmos-adcopy:v1.0.0", "dmos-adcopy:v2.0.0", "20%");
 *
 * // Route a campaign's entity to either baseline or variant
 * String assignedModel = governance.assignModel(tenantId, "adcopy-v2-trial", campaignId);
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

    /**
     * @param modelRegistry platform model registry service
     * @param abTesting     platform A/B testing service
     */
    public DmosAiModelGovernanceAdapter(
            ModelRegistryPort modelRegistry,
            ABTestingService abTesting) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry required");
        this.abTesting = Objects.requireNonNull(abTesting, "abTesting required");
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
     * Promotes a staged model to {@link DeploymentStatus#PRODUCTION}.
     * The model must already be registered via {@link #registerCandidateModel}.
     *
     * @param tenantId  tenant owning the model
     * @param modelName model name
     * @param version   version to promote
     * @return Promise of the promoted {@link ModelMetadata}
     */
    public Promise<ModelMetadata> promoteToProduction(
            DmTenantId tenantId, String modelName, String version) {
        Objects.requireNonNull(tenantId, "tenantId required");

        return Promise.ofCallback(cb -> {
            try {
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

                // Re-fetch to get updated metadata
                ModelMetadata updated = modelRegistry.findByName(tenantId.getValue(), modelName, version)
                        .orElse(model);
                LOG.info("[DMOS][AI-Gov] Model promoted to PRODUCTION: tenantId={} model={} version={}",
                        tenantId.getValue(), modelName, version);
                cb.set(updated);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to promote model: model={} version={} error={}",
                        modelName, version, ex.getMessage(), ex);
                cb.setException(ex);
            }
        });
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
     * Defines a new A/B experiment routing a percentage of traffic from
     * {@code baselineModelRef} to {@code variantModelRef}.
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
                ABTestingService.Experiment experiment = new ABTestingService.Experiment(
                        experimentId,
                        experimentId,
                        splitPercent,
                        baselineModelRef,
                        variantModelRef
                );
                abTesting.registerExperiment(tenantId.getValue(), experiment);
                LOG.info("[DMOS][AI-Gov] A/B experiment defined: tenantId={} experimentId={} baseline={} variant={} split={}",
                        tenantId.getValue(), experimentId, baselineModelRef, variantModelRef, splitPercent);
                cb.set(null);
            } catch (Exception ex) {
                LOG.error("[DMOS][AI-Gov] Failed to define experiment: id={} error={}", experimentId, ex.getMessage(), ex);
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
