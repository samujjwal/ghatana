/**
 * Import Validation Service Implementation
 * 
 * Production-grade implementation of import validation service.
 * Validates import sources and data before processing.
 * 
 * @doc.type class
 * @doc.purpose Import validation implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.import_;

import com.ghatana.yappc.api.ImportController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Production-grade implementation of import validation service.
 */
public final class ImportValidationServiceImpl implements ImportValidationService {

    private static final Logger log = LoggerFactory.getLogger(ImportValidationServiceImpl.class);

    private static final Set<String> ALLOWED_SOURCE_TYPES = Set.of(
            "github",
            "gitlab",
            "bitbucket",
            "file",
            "url",
            "figma",
            "sketch",
            "xd"
    );

    @Override
    public ImportValidationResult validateImportSource(ImportController.ImportRequest request) {
        log.info("Validating import source: sourceType={}, projectId={}", 
                request.sourceType(), request.projectId());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate source type
        if (!ALLOWED_SOURCE_TYPES.contains(request.sourceType())) {
            errors.add(String.format("Invalid source type: %s. Allowed types: %s", 
                    request.sourceType(), ALLOWED_SOURCE_TYPES));
        }

        // Validate project ID
        if (request.projectId() == null || request.projectId().isBlank()) {
            errors.add("Project ID is required");
        }

        // Validate source URL or data
        if (request.sourceUrl() == null && request.sourceData() == null) {
            errors.add("Either sourceUrl or sourceData is required");
        }

        if (request.sourceUrl() != null && !isValidUrl(request.sourceUrl())) {
            errors.add("Invalid source URL format");
        }

        if (request.sourceData() != null && request.sourceData().isBlank()) {
            errors.add("Source data cannot be blank");
        }

        // Validate source data if provided
        if (request.sourceData() != null) {
            ImportValidationResult dataResult = validateSourceData(request.sourceData(), request.sourceType());
            if (!dataResult.isValid()) {
                errors.addAll(dataResult.errors());
            }
            warnings.addAll(dataResult.warnings());
        }

        boolean isValid = errors.isEmpty();
        if (isValid) {
            log.info("Import source validation passed: sourceType={}, projectId={}", 
                    request.sourceType(), request.projectId());
        } else {
            log.warn("Import source validation failed: sourceType={}, projectId={}, errors={}", 
                    request.sourceType(), request.projectId(), errors);
        }

        return new ImportValidationResult(isValid, errors, warnings);
    }

    @Override
    public ImportValidationResult validateSourceData(String sourceData, String sourceType) {
        log.debug("Validating source data: sourceType={}, dataLength={}", 
                sourceType, sourceData != null ? sourceData.length() : 0);

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (sourceData == null || sourceData.isBlank()) {
            errors.add("Source data is required");
            return new ImportValidationResult(false, errors, warnings);
        }

        // Validate data size
        if (sourceData.length() > 10_000_000) { // 10MB limit
            errors.add("Source data exceeds maximum size limit (10MB)");
        }

        // Validate based on source type
        switch (sourceType) {
            case "github", "gitlab", "bitbucket" -> validateGitSource(sourceData, errors, warnings);
            case "figma", "sketch", "xd" -> validateDesignSource(sourceData, errors, warnings);
            case "file", "url" -> validateFileSource(sourceData, errors, warnings);
            default -> warnings.add("No specific validation for source type: " + sourceType);
        }

        boolean isValid = errors.isEmpty();
        if (!isValid) {
            log.debug("Source data validation failed: errors={}", errors);
        }

        return new ImportValidationResult(isValid, errors, warnings);
    }

    private void validateGitSource(String sourceData, List<String> errors, List<String> warnings) {
        // Basic JSON validation for git sources
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.readTree(sourceData);
        } catch (Exception e) {
            errors.add("Source data is not valid JSON");
        }
    }

    private void validateDesignSource(String sourceData, List<String> errors, List<String> warnings) {
        // Basic validation for design sources
        if (sourceData.length() > 5_000_000) { // 5MB limit for design files
            warnings.add("Design source data is large, may impact performance");
        }
    }

    private void validateFileSource(String sourceData, List<String> errors, List<String> warnings) {
        // Basic validation for file sources
        if (sourceData.startsWith("<?xml") || sourceData.startsWith("<!DOCTYPE")) {
            warnings.add("XML source detected, ensure proper escaping");
        }
    }

    private boolean isValidUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            uri.toURL();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
