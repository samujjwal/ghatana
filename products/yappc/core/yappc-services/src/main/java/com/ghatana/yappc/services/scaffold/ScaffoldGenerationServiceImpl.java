/**
 * Scaffold Generation Service Implementation
 * 
 * Production-grade implementation of scaffold generation service.
 * Generates scaffolds from packs with validation and dependency resolution.
 * 
 * @doc.type class
 * @doc.purpose Scaffold generation implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.scaffold;

import com.ghatana.yappc.api.PackMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Production-grade implementation of scaffold generation service.
 */
public final class ScaffoldGenerationServiceImpl implements ScaffoldGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldGenerationServiceImpl.class);

    private final PackValidationService validationService;
    private final DependencyResolutionService dependencyService;
    private final TemplateRenderingService templateService;

    public ScaffoldGenerationServiceImpl(
            PackValidationService validationService,
            DependencyResolutionService dependencyService,
            TemplateRenderingService templateService
    ) {
        this.validationService = validationService;
        this.dependencyService = dependencyService;
        this.templateService = templateService;
    }

    @Override
    public ScaffoldGenerationResult generateScaffold(PackMetadata packMetadata, Map<String, Object> variables, String outputLocation) {
        log.info("Generating scaffold: packId={}, outputLocation={}", packMetadata.packId(), outputLocation);

        List<GeneratedFile> generatedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Render templates
        PackRenderingResult renderResult = templateService.renderPack(packMetadata, variables);
        if (!renderResult.success()) {
            errors.addAll(renderResult.errors());
            return new ScaffoldGenerationResult(false, null, outputLocation, List.of(), errors, warnings);
        }
        warnings.addAll(renderResult.warnings());

        // Convert rendered files to generated files with checksums
        for (RenderedFile renderedFile : renderResult.renderedFiles()) {
            String checksum = calculateChecksum(renderedFile.renderedContent());
            generatedFiles.add(new GeneratedFile(
                    renderedFile.filePath(),
                    renderedFile.renderedContent(),
                    renderedFile.renderedContent().getBytes(StandardCharsets.UTF_8).length,
                    checksum
            ));
        }

        String scaffoldId = "scaffold-" + java.util.UUID.randomUUID().toString();

        boolean success = errors.isEmpty();
        if (success) {
            log.info("Scaffold generation successful: scaffoldId={}, fileCount={}", 
                    scaffoldId, generatedFiles.size());
        } else {
            log.warn("Scaffold generation failed: packId={}, errors={}", packMetadata.packId(), errors);
        }

        return new ScaffoldGenerationResult(success, scaffoldId, outputLocation, generatedFiles, errors, warnings);
    }

    @Override
    public ScaffoldGenerationResult generateScaffoldWithValidation(PackMetadata packMetadata, Map<String, Object> variables, String outputLocation) {
        log.info("Generating scaffold with validation: packId={}", packMetadata.packId());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate pack
        PackValidationResult validationResult = validationService.validatePack(packMetadata);
        if (!validationResult.isValid()) {
            errors.addAll(validationResult.errors());
            return new ScaffoldGenerationResult(false, null, outputLocation, List.of(), errors, warnings);
        }
        warnings.addAll(validationResult.warnings());

        // Resolve dependencies
        DependencyResolutionResult dependencyResult = dependencyService.resolveDependencies(packMetadata);
        if (!dependencyResult.success()) {
            errors.addAll(dependencyResult.errors());
            return new ScaffoldGenerationResult(false, null, outputLocation, List.of(), errors, warnings);
        }
        warnings.addAll(dependencyResult.warnings());

        // Check for circular dependencies
        if (dependencyService.hasCircularDependencies(packMetadata)) {
            errors.add("Circular dependencies detected");
            return new ScaffoldGenerationResult(false, null, outputLocation, List.of(), errors, warnings);
        }

        // Validate compatibility
        DependencyValidationResult compatibilityResult = dependencyService.validateCompatibility(packMetadata);
        if (!compatibilityResult.isValid()) {
            errors.addAll(compatibilityResult.incompatibilities());
            return new ScaffoldGenerationResult(false, null, outputLocation, List.of(), errors, warnings);
        }
        warnings.addAll(compatibilityResult.warnings());

        // Generate scaffold
        return generateScaffold(packMetadata, variables, outputLocation);
    }

    private String calculateChecksum(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Error calculating checksum", e);
            return "unknown";
        }
    }
}
