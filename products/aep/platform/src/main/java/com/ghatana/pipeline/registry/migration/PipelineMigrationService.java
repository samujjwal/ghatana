package com.ghatana.pipeline.registry.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.aep.domain.pipeline.AgentSpec;
import com.ghatana.aep.domain.pipeline.PipelineSpec;
import com.ghatana.aep.domain.pipeline.PipelineStageSpec;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.pipeline.registry.model.PipelineConfig;
import com.ghatana.pipeline.registry.model.PipelineStage;
import com.ghatana.pipeline.registry.repository.PipelineRepository;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for migrating pipelines from legacy JSON format to structured configuration.
 *
 * <p>Handles parsing legacy PipelineSpec JSON and converting it to the new
 * PipelineConfig and PipelineStage domain models.
 *
 * @doc.type class
 * @doc.purpose Migration logic for pipeline configurations
 * @doc.layer product
 * @doc.pattern Service
 */
@Slf4j
@RequiredArgsConstructor
public class PipelineMigrationService {

    @Inject
    private final PipelineRepository pipelineRepository;
    
    private final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    /**
     * Migrate all legacy pipelines in the repository.
     *
     * @param dryRun if true, do not save changes
     * @return report of migration results
     */
    public Promise<MigrationReport> migrateAll(boolean dryRun) {
        MigrationReport report = MigrationReport.builder()
                .dryRun(dryRun)
                .build();

        // This is a simplified approach - in reality we might paginate or stream
        // Assuming list() supports finding all (e.g. page size -1 or large number)
        // For safety, let's just paginate through huge results or handle reasonable batch

        return pipelineRepository.findAll(null, null, null, 1, 1000)
                .then(page -> {
                    report.setTotalPipelines((int) page.totalElements());
                    List<Promise<Void>> migrations = new ArrayList<>();

                    for (Pipeline pipeline : page.content()) {
                        if (pipeline.hasStructuredConfig()) {
                            report.incrementSkipped();
                            continue;
                        }

                        migrations.add(migratePipeline(pipeline, dryRun)
                                .map((res, e) -> {
                                    if (e != null) {
                                        report.addFailure(pipeline.getId(), "Migration error", e);
                                    } else {
                                        report.incrementMigrated();
                                    }
                                    return null;
                                }));
                    }

                    return io.activej.promise.Promises.all(migrations);
                })
                .map(v -> {
                    report.complete();
                    return report;
                });
    }

    /**
     * Migrate a single pipeline.
     *
     * @param pipeline the pipeline to migrate
     * @param dryRun if true, do not save
     * @return promise that completes when done
     */
    public Promise<Void> migratePipeline(Pipeline pipeline, boolean dryRun) {
        if (pipeline.getConfig() == null || pipeline.getConfig().isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("No legacy config found"));
        }

        try {
            PipelineSpec legacySpec = objectMapper.readValue(pipeline.getConfig(), PipelineSpec.class);

            // Convert to structured format
            PipelineConfig structuredConfig = convertConfig(legacySpec);
            List<PipelineStage> stages = convertStages(legacySpec);

            pipeline.setStructuredConfig(structuredConfig);
            pipeline.setStages(stages);

            if (!dryRun) {
                return pipelineRepository.save(pipeline).toVoid();
            } else {
                log.info("Dry run: Would have migrated pipeline {}", pipeline.getId());
                return Promise.complete();
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to parse legacy config for pipeline {}", pipeline.getId(), e);
            return Promise.ofException(new IllegalArgumentException("Invalid legacy config JSON", e));
        } catch (Exception e) {
            log.error("Failed to migrate pipeline {}", pipeline.getId(), e);
            return Promise.ofException(e);
        }
    }

    private PipelineConfig convertConfig(PipelineSpec legacySpec) {
        // Infer configuration from legacy spec
        // Legacy spec doesn't strictly define inputs/outputs at top level, often implied by stages

        List<String> inputs = new ArrayList<>();
        List<String> outputs = new ArrayList<>();

        // Simple heuristic: first stage agents might imply inputs, last stage agents outputs
        // For now, we leave them empty or use defaults if available in map

        return PipelineConfig.builder()
                .inputs(inputs)
                .outputs(outputs)
                .build();
    }

    private List<PipelineStage> convertStages(PipelineSpec legacySpec) {
        List<PipelineStage> stages = new ArrayList<>();
        if (legacySpec.getStages() == null) {
            return stages;
        }

        String previousStageId = null;

        for (PipelineStageSpec legacyStage : legacySpec.getStages()) {
            String stageId = UUID.randomUUID().toString();
            String name = legacyStage.getName();

            // Map legacy stage type or content to new stage type
            PipelineStage.StageType type = inferStageType(legacyStage);

            // Extract operator ID and config
            // Legacy stages might have list of agents/connectors.
            // New model expects single operator per stage (simplification)
            // Strategy: Create a stage for the first/primary agent or connector

            String operatorId = null;
            Map<String, String> config = new HashMap<>();

            if (legacyStage.getConnectors() != null && !legacyStage.getConnectors().isEmpty()) {
                ConnectorSpec conn = legacyStage.getConnectors().get(0);
                operatorId = conn.getType() + "-connector"; // Synthetic ID or map from type
                config.put("connectorId", conn.getId());
                config.put("endpoint", conn.getEndpoint());
            } else if (legacyStage.getConnectorIds() != null && !legacyStage.getConnectorIds().isEmpty()) {
                operatorId = "managed-connector";
                config.put("connectorId", legacyStage.getConnectorIds().get(0));
            } else if (legacyStage.getWorkflow() != null && !legacyStage.getWorkflow().isEmpty()) {
                AgentSpec agent = legacyStage.getWorkflow().get(0);
                operatorId = agent.getAgent(); // Use agent class as operator ID
                config.put("role", agent.getRole());
                config.put("agentId", agent.getId());
                // Map other agent props to config if needed
            } else {
                operatorId = "noop";
            }

            PipelineStage stage = PipelineStage.builder()
                    .id(stageId)
                    .name(name != null ? name : "stage-" + stages.size())
                    .type(type)
                    .operatorId(operatorId)
                    .config(config)
                    .enabled(true)
                    .timeout(Duration.ofSeconds(30)) // Default
                    .build();

            // Sequential dependency
            if (previousStageId != null) {
                stage.addDependency(previousStageId);
            }

            stages.add(stage);
            previousStageId = stageId;
        }

        return stages;
    }

    private PipelineStage.StageType inferStageType(PipelineStageSpec legacyStage) {
        String name = legacyStage.getName() != null ? legacyStage.getName().toLowerCase() : "";
        String type = legacyStage.getStageType();

        if ("CONNECTOR".equalsIgnoreCase(type) ||
            (legacyStage.getConnectors() != null && !legacyStage.getConnectors().isEmpty()) ||
            (legacyStage.getConnectorIds() != null && !legacyStage.getConnectorIds().isEmpty())) {
            // Source or Sink?
            if (name.contains("ingest") || name.contains("source")) return PipelineStage.StageType.CUSTOM;
            // Note: StageType enum might need to be expanded or mapped carefully.
            // Using CUSTOM for generic connector for now if specific types don't match.
            return PipelineStage.StageType.CUSTOM;
        }

        if (name.contains("filter")) return PipelineStage.StageType.FILTER;
        if (name.contains("enrich")) return PipelineStage.StageType.MAP;
        if (name.contains("valid")) return PipelineStage.StageType.FILTER;
        if (name.contains("aggreg")) return PipelineStage.StageType.AGGREGATE;

        return PipelineStage.StageType.CUSTOM;
    }
}

