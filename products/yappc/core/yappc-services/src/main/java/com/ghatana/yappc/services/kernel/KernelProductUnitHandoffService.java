package com.ghatana.yappc.services.kernel;

import com.ghatana.yappc.kernel.ProductUnitIntentExporter;
import com.ghatana.yappc.kernel.ProductUnitIntentValidationService;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend service adapter for generating Kernel ProductUnitIntent payloads.
 *
 * @doc.type class
 * @doc.purpose Generates and validates Kernel ProductUnitIntent payloads for API handoff
 * @doc.layer product
 * @doc.pattern Service
 */
public final class KernelProductUnitHandoffService {

    private final ProductUnitIntentExporter exporter;
    private final ProductUnitIntentValidationService validator;

    public KernelProductUnitHandoffService() {
        this(new ProductUnitIntentExporter(), new ProductUnitIntentValidationService());
    }

    public KernelProductUnitHandoffService(
            @NotNull ProductUnitIntentExporter exporter,
            @NotNull ProductUnitIntentValidationService validator) {
        this.exporter = exporter;
        this.validator = validator;
    }

    /**
     * Generates and validates a Kernel ProductUnitIntent for backend/API handoff.
     *
     * @doc.type method
     * @doc.purpose Build Kernel ProductUnitIntent payloads from saved YAPPC project state
     * @doc.contract Throws ExportException when required input is missing or Kernel validation fails
     */
    public HandoffResult generate(@NotNull HandoffRequest request) throws ProductUnitIntentExporter.ExportException {
        if (request == null) {
            throw new ProductUnitIntentExporter.ExportException("request is required");
        }
        ProductUnitIntentExporter.Request exportRequest = ProductUnitIntentExporter.Request.builder()
                .tenantId(requireNonBlank(request.tenantId(), "tenantId"))
                .workspaceId(requireNonBlank(request.workspaceId(), "workspaceId"))
                .projectId(requireNonBlank(request.projectId(), "projectId"))
                .projectName(requireNonBlank(request.projectName(), "projectName"))
                .targetType("kernel-product-unit")
                .surfaces(request.surfaces())
                .runtimeProvider(defaultIfBlank(request.runtimeProvider(), "ghatana-file-registry"))
            .sourceProvider(requireNonBlank(request.sourceProvider(), "sourceProvider"))
                .lifecycleProfile(defaultIfBlank(request.lifecycleProfile(), "standard-web-api-product"))
                .sourcePhase(defaultIfBlank(request.sourcePhase(), "generate"))
                .metadata(buildMetadata(request))
                .build();

        ProductUnitIntentExporter.ExportResult exportResult = exporter.buildIntent(exportRequest);
        ProductUnitIntentValidationService.ValidationResult validation = validator.validate(exportResult.intent());
        if (!validation.isValid()) {
            throw new ProductUnitIntentExporter.ExportException(
                    "Generated ProductUnitIntent failed Kernel contract validation: "
                            + String.join("; ", validation.errors()));
        }

        return new HandoffResult(
                exportResult.intentId(),
                exportResult.intent(),
                true,
                validation.errors(),
                request.correlationId());
    }

    private static Map<String, Object> buildMetadata(HandoffRequest request) {
        Map<String, Object> metadata = new HashMap<>();
        if (request.metadata() != null) {
            metadata.putAll(request.metadata());
        }
        if (request.correlationId() != null && !request.correlationId().isBlank()) {
            metadata.put("correlationId", request.correlationId());
        }
        return metadata;
    }

    private static String requireNonBlank(String value, String fieldName) throws ProductUnitIntentExporter.ExportException {
        if (value == null || value.isBlank()) {
            throw new ProductUnitIntentExporter.ExportException(fieldName + " is required");
        }
        return value;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    public record HandoffRequest(
            String tenantId,
            String workspaceId,
            String projectId,
            String projectName,
            List<String> surfaces,
            String runtimeProvider,
                String sourceProvider,
            String lifecycleProfile,
            String sourcePhase,
            Map<String, Object> metadata,
            String correlationId
    ) {
    }

    public record HandoffResult(
            String intentId,
            Map<String, Object> productUnitIntent,
            boolean valid,
            List<String> validationErrors,
            String correlationId
    ) {
    }
}
