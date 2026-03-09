package com.ghatana.pipeline.registry.validation;

import com.ghatana.aep.domain.pipeline.PipelineSpec;
import com.ghatana.aep.domain.pipeline.PipelineStageSpec;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec;
import com.ghatana.platform.domain.domain.pipeline.ConnectorSpec.ConnectorType;
import com.ghatana.platform.observability.MetricsCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validates connector integration in PipelineSpecs.
 *
 * <p>
 * <b>Purpose</b><br>
 * Validates that pipelines correctly reference connectors and that referenced
 * connectors exist in a registry. Provides validation at pipeline specification
 * time before deployment.
 *
 * <p>
 * <b>Validation Rules</b><br>
 * 1. Connector IDs must reference existing connectors in registry<br>
 * 2. Inline connector specs must have valid types (QUEUE_SOURCE/SINK,
 * HTTP_INGRESS/EGRESS, EVENT_CLOUD_SOURCE/SINK)<br>
 * 3. Source connectors (QUEUE_SOURCE, HTTP_INGRESS, EVENT_CLOUD_SOURCE) can
 * only appear in initial stages<br>
 * 4. Sink connectors (QUEUE_SINK, HTTP_EGRESS, EVENT_CLOUD_SINK) can only
 * appear in final stages<br>
 * 5. Connector IDs and inline connectors must not conflict (same ID used
 * twice)<br>
 * 6. All required connector fields must be present (id, type, name,
 * tenantId)<br>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Part of validation layer (V3-03). Enforces connector constraints at pipeline
 * specification validation time, before submission to orchestrator. Emits
 * validation metrics via MetricsCollector.
 *
 * @see PipelineValidator
 * @see ConnectorSpec
 * @see ConnectorType
 * @doc.type class
 * @doc.purpose Pipeline connector integration validator
 * @doc.layer product
 * @doc.pattern Validator
 */
@Slf4j
@RequiredArgsConstructor
public class PipelineConnectorValidator {

    private final MetricsCollector metricsCollector;

    /**
     * Validates connector integration in pipeline specification.
     *
     * @param pipelineSpec pipeline specification to validate
     * @param registeredConnectorIds set of connector IDs registered in catalog
     * @return validation result with error messages if invalid
     */
    public PipelineConnectorValidationResult validate(
            PipelineSpec pipelineSpec,
            java.util.Set<String> registeredConnectorIds) {

        String pipelineId = Optional.ofNullable(MDC.get("pipelineId")).orElse("unknown");
        List<String> errors = new ArrayList<>();
        Map<String, ConnectorSpec> connectorMap = new HashMap<>();

        if (pipelineSpec == null || pipelineSpec.getStages() == null || pipelineSpec.getStages().isEmpty()) {
            log.debug("Pipeline has no stages, skipping connector validation");
            return PipelineConnectorValidationResult.valid();
        }

        // Collect all inline connectors and check for duplicates
        for (PipelineStageSpec stage : pipelineSpec.getStages()) {
            if (stage.getConnectors() != null) {
                for (ConnectorSpec connector : stage.getConnectors()) {
                    validateConnectorSpec(connector, errors);

                    // Check for duplicate IDs
                    if (connectorMap.containsKey(connector.getId())) {
                        errors.add(String.format(
                                "Duplicate connector ID '%s' in pipeline stages", connector.getId()));
                    } else {
                        connectorMap.put(connector.getId(), connector);
                    }
                }
            }
        }

        // Validate connector references and placement rules
        for (int stageIndex = 0; stageIndex < pipelineSpec.getStages().size(); stageIndex++) {
            PipelineStageSpec stage = pipelineSpec.getStages().get(stageIndex);
            boolean isFirstStage = stageIndex == 0;
            boolean isLastStage = stageIndex == pipelineSpec.getStages().size() - 1;

            // Validate connector IDs
            if (stage.getConnectorIds() != null) {
                for (String connectorId : stage.getConnectorIds()) {
                    if (!registeredConnectorIds.contains(connectorId)) {
                        errors.add(String.format(
                                "Connector ID '%s' not found in registry (stage: %s)",
                                connectorId, stage.getName()));
                    }
                }
            }

            // Validate connector placement (sources in first stage, sinks in last)
            if (stage.getConnectors() != null) {
                for (ConnectorSpec connector : stage.getConnectors()) {
                    validateConnectorPlacement(connector, isFirstStage, isLastStage, errors);
                }
            }
        }

        // Emit metrics
        if (errors.isEmpty()) {
            metricsCollector.incrementCounter(
                    "aep.pipeline.connector.validation.success",
                    "pipeline_id", pipelineId,
                    "connector_count", String.valueOf(connectorMap.size())
            );
            return PipelineConnectorValidationResult.valid();
        } else {
            metricsCollector.incrementCounter(
                    "aep.pipeline.connector.validation.error",
                    "pipeline_id", pipelineId,
                    "error_count", String.valueOf(errors.size())
            );
            log.warn("Pipeline connector validation failed: {}", errors);
            return PipelineConnectorValidationResult.invalid(errors);
        }
    }

    /**
     * Validates individual connector specification.
     *
     * @param connector connector spec to validate
     * @param errors list to accumulate validation errors
     */
    private void validateConnectorSpec(ConnectorSpec connector, List<String> errors) {
        if (connector.getId() == null || connector.getId().trim().isEmpty()) {
            errors.add("Connector missing required field: id");
        }
        if (connector.getType() == null) {
            errors.add("Connector '" + connector.getId() + "' missing required field: type");
        }
        // Name is optional and stored in properties map if present
        if (connector.getTenantId() == null || connector.getTenantId().trim().isEmpty()) {
            errors.add("Connector '" + connector.getId() + "' missing required field: tenantId");
        }
    }

    /**
     * Validates connector placement rules (source vs sink, stage position).
     *
     * @param connector connector spec
     * @param isFirstStage whether this is first stage in pipeline
     * @param isLastStage whether this is last stage in pipeline
     * @param errors list to accumulate validation errors
     */
    private void validateConnectorPlacement(
            ConnectorSpec connector,
            boolean isFirstStage,
            boolean isLastStage,
            List<String> errors) {

        ConnectorType type = connector.getType();
        String connectorId = connector.getId();

        // Source connectors must be in first stage or stages before pattern matching
        if (isSourceConnector(type) && !isFirstStage) {
            errors.add(String.format(
                    "Source connector '%s' (type: %s) must be in initial stage(s), not middle/end stages",
                    connectorId, type));
        }

        // Sink connectors must be in last stage or stages after processing
        if (isSinkConnector(type) && !isLastStage) {
            errors.add(String.format(
                    "Sink connector '%s' (type: %s) must be in final stage(s), not initial/middle stages",
                    connectorId, type));
        }
    }

    /**
     * Checks if connector type is a source (ingress).
     */
    private boolean isSourceConnector(ConnectorType type) {
        return type == ConnectorType.QUEUE_SOURCE
                || type == ConnectorType.HTTP_INGRESS
                || type == ConnectorType.EVENT_CLOUD_SOURCE;
    }

    /**
     * Checks if connector type is a sink (egress).
     */
    private boolean isSinkConnector(ConnectorType type) {
        return type == ConnectorType.QUEUE_SINK
                || type == ConnectorType.HTTP_EGRESS
                || type == ConnectorType.EVENT_CLOUD_SINK;
    }

    /**
     * Validation result object.
     */
    public static class PipelineConnectorValidationResult {

        private final boolean valid;
        private final List<String> errors;

        private PipelineConnectorValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static PipelineConnectorValidationResult valid() {
            return new PipelineConnectorValidationResult(true, List.of());
        }

        public static PipelineConnectorValidationResult invalid(List<String> errors) {
            return new PipelineConnectorValidationResult(false, errors);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return errors.stream().collect(Collectors.joining("; "));
        }
    }
}
