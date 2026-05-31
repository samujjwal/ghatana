/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.cli;

import com.ghatana.yappc.kernel.ProductUnitIntentExporter;
import com.ghatana.yappc.kernel.ProductUnitIntentValidationService;
import com.ghatana.yappc.kernel.ProductUnitIntentExporter.ExportException;
import com.ghatana.yappc.kernel.ProductUnitKernelContractRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Service for handling ProductUnitIntent creation and validation commands.
 * Extracted from CreateCommand to maintain SRP and separate CLI parsing from business logic.
 *
 * @doc.type class
 * @doc.purpose Service for ProductUnitIntent creation and validation
 * @doc.layer product
 * @doc.pattern Service
 */
public class ProductUnitIntentCommandService {

    private static final Logger log = LoggerFactory.getLogger(ProductUnitIntentCommandService.class);

    private final ProductUnitIntentExporter exporter;
    private final ProductUnitIntentValidationService validator;
    private final ProductUnitKernelContractRegistry contractRegistry;

    public ProductUnitIntentCommandService() {
        this(new ProductUnitIntentExporter(), new ProductUnitIntentValidationService(), new ProductUnitKernelContractRegistry());
    }

    public ProductUnitIntentCommandService(
            ProductUnitIntentExporter exporter,
            ProductUnitIntentValidationService validator,
            ProductUnitKernelContractRegistry contractRegistry
    ) {
        this.exporter = exporter;
        this.validator = validator;
        this.contractRegistry = contractRegistry;
    }

    /**
     * Validates kernel-product-unit creation parameters.
     *
     * @param projectName the project name
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @param lifecycleProfile the lifecycle profile
     * @return validation result with error messages if invalid
     */
    public ValidationResult validateParameters(
            String projectName,
            String tenantId,
            String workspaceId,
            String projectId,
            String lifecycleProfile
    ) {
        if (projectName == null || projectName.isBlank()) {
            return ValidationResult.createInvalid("Project name is required for kernel-product-unit target.");
        }

        if (tenantId == null || tenantId.isBlank()) {
            return ValidationResult.createInvalid("--tenant-id is required for kernel-product-unit target.");
        }
        if ("default-tenant".equalsIgnoreCase(tenantId)) {
            return ValidationResult.createInvalid("'default-tenant' is not a valid tenant ID. Use a real tenant identifier.");
        }
        if (!isValidIdentifier(tenantId)) {
            return ValidationResult.createInvalid("Invalid tenant ID '" + tenantId + "'. Must contain only alphanumeric characters, hyphens, and underscores.");
        }

        if (workspaceId == null || workspaceId.isBlank()) {
            return ValidationResult.createInvalid("--workspace-id is required for kernel-product-unit target.");
        }
        if (!isValidIdentifier(workspaceId)) {
            return ValidationResult.createInvalid("Invalid workspace ID '" + workspaceId + "'. Must contain only alphanumeric characters, hyphens, and underscores.");
        }

        if (projectId == null || projectId.isBlank()) {
            return ValidationResult.createInvalid("--project-id is required for kernel-product-unit target.");
        }
        if (!isValidIdentifier(projectId)) {
            return ValidationResult.createInvalid("Invalid project ID '" + projectId + "'. Must contain only alphanumeric characters, hyphens, and underscores.");
        }

        if (lifecycleProfile == null || lifecycleProfile.isBlank()) {
            return ValidationResult.createInvalid("--lifecycle-profile is required for kernel-product-unit target.");
        }

        return ValidationResult.createValid();
    }

    /**
     * Creates a ProductUnitIntent from the given parameters.
     *
     * @param projectName the project name
     * @param tenantId the tenant ID
     * @param workspaceId the workspace ID
     * @param projectId the project ID
     * @param lifecycleProfile the lifecycle profile
     * @param runtimeProvider the runtime provider
     * @param surfaces the requested surfaces
     * @param intentOutputPath the output path for the intent file
     * @return creation result with success status and error messages if failed
     */
    public CreationResult createIntent(
            String projectName,
            String tenantId,
            String workspaceId,
            String projectId,
            String lifecycleProfile,
            String runtimeProvider,
            String sourceProvider,
            List<String> surfaces,
            Path intentOutputPath,
            String correlationId
    ) {
        String productUnitId = toKebabCase(projectId);

        if (surfaces.isEmpty()) {
            return CreationResult.createInvalid("At least one --surface is required for kernel-product-unit target.");
        }
        List<String> unknownSurfaces = surfaces.stream()
                .filter(surface -> !contractRegistry.isSurfaceKnown(surface))
                .toList();
        if (!unknownSurfaces.isEmpty()) {
            return CreationResult.createInvalid("Unknown --surface value(s): " + String.join(", ", unknownSurfaces));
        }

        ProductUnitIntentExporter.Request request = ProductUnitIntentExporter.Request.builder()
                .projectId(productUnitId)
                .projectName(projectName)
                .targetType("kernel-product-unit")
                .surfaces(surfaces)
                .runtimeProvider(runtimeProvider)
                .sourceProvider(sourceProvider)
                .lifecycleProfile(lifecycleProfile)
                .tenantId(tenantId)
                .workspaceId(workspaceId)
                .sourcePhase("generate")
                .correlationId(correlationId)
                .build();

        try {
            ProductUnitIntentExporter.ExportResult exportResult = exporter.buildIntent(request);
            ProductUnitIntentValidationService.ValidationResult validationResult = validator.validate(exportResult.intent());

            if (!validationResult.isValid()) {
                return CreationResult.createInvalid(validationResult.errors());
            }

            exporter.export(request, intentOutputPath);

            log.info("\n✅ ProductUnitIntent generated successfully");
            log.info("   Intent file: {}", intentOutputPath.toAbsolutePath());
            log.info("   ProductUnit ID: {}", productUnitId);
            log.info("   Surfaces: {}", surfaces);
            log.info("\n📋 Next steps:");
            log.info("   pnpm kernel product create --from-intent {}", intentOutputPath);

            return CreationResult.createSuccess(productUnitId, intentOutputPath, surfaces);
        } catch (ExportException e) {
            log.error("Failed to export ProductUnitIntent: {}", e.getMessage());
            return CreationResult.createInvalid("Failed to export ProductUnitIntent: " + e.getMessage());
        }
    }

        public CreationResult createIntent(
            String projectName,
            String tenantId,
            String workspaceId,
            String projectId,
            String lifecycleProfile,
            String runtimeProvider,
            List<String> surfaces,
            Path intentOutputPath
        ) {
        return createIntent(
            projectName,
            tenantId,
            workspaceId,
            projectId,
            lifecycleProfile,
            runtimeProvider,
            runtimeProvider,
            surfaces,
            intentOutputPath,
            null
        );
        }

    private boolean isValidIdentifier(String value) {
        return value != null && value.matches("^[a-zA-Z0-9_-]+$");
    }

    private String toKebabCase(String value) {
        return value.toLowerCase().replace("_", "-");
    }

    /**
     * Validation result for kernel-product-unit parameters.
     */
    public record ValidationResult(boolean valid, String error) {
        public static ValidationResult createValid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult createInvalid(String error) {
            return new ValidationResult(false, error);
        }
    }

    /**
     * Creation result for ProductUnitIntent generation.
     */
    public record CreationResult(boolean success, String productUnitId, Path intentPath, List<String> surfaces, List<String> errors) {
        public static CreationResult createSuccess(String productUnitId, Path intentPath, List<String> surfaces) {
            return new CreationResult(true, productUnitId, intentPath, surfaces, List.of());
        }

        public static CreationResult createInvalid(String error) {
            return new CreationResult(false, null, null, List.of(), List.of(error));
        }

        public static CreationResult createInvalid(List<String> errors) {
            return new CreationResult(false, null, null, List.of(), errors);
        }
    }
}
