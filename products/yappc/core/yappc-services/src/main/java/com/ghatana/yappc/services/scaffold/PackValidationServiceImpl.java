/**
 * Pack Validation Service Implementation
 * 
 * Production-grade implementation of pack validation service.
 * Validates pack structure, metadata, and template variables.
 * 
 * @doc.type class
 * @doc.purpose Pack validation implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.scaffold;

import com.ghatana.yappc.api.PackMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Production-grade implementation of pack validation service.
 */
public final class PackValidationServiceImpl implements PackValidationService {

    private static final Logger log = LoggerFactory.getLogger(PackValidationServiceImpl.class);

    @Override
    public PackValidationResult validatePack(PackMetadata packMetadata) {
        log.info("Validating pack: packId={}, version={}", packMetadata.packId(), packMetadata.packVersion());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Validate required fields
        if (packMetadata.packId() == null || packMetadata.packId().isBlank()) {
            errors.add("Pack ID is required");
        }
        if (packMetadata.packName() == null || packMetadata.packName().isBlank()) {
            errors.add("Pack name is required");
        }
        if (packMetadata.packVersion() == null || packMetadata.packVersion().isBlank()) {
            errors.add("Pack version is required");
        }
        if (!isValidVersion(packMetadata.packVersion())) {
            errors.add("Pack version must follow semantic versioning (e.g., 1.0.0)");
        }

        // Validate structure
        PackValidationResult structureResult = validateStructure(packMetadata);
        if (!structureResult.isValid()) {
            errors.addAll(structureResult.errors());
        }
        warnings.addAll(structureResult.warnings());

        // Validate template variables
        PackValidationResult variablesResult = validateTemplateVariables(packMetadata);
        if (!variablesResult.isValid()) {
            errors.addAll(variablesResult.errors());
        }
        warnings.addAll(variablesResult.warnings());

        boolean isValid = errors.isEmpty();
        if (isValid) {
            log.info("Pack validation passed: packId={}", packMetadata.packId());
        } else {
            log.warn("Pack validation failed: packId={}, errors={}", packMetadata.packId(), errors);
        }

        return new PackValidationResult(isValid, errors, warnings);
    }

    @Override
    public PackValidationResult validateStructure(PackMetadata packMetadata) {
        log.debug("Validating pack structure: packId={}", packMetadata.packId());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (packMetadata.structure() == null) {
            errors.add("Pack structure is required");
            return new PackValidationResult(false, errors, warnings);
        }

        // Validate structure
        if (packMetadata.structure().rootPath() == null || packMetadata.structure().rootPath().isBlank()) {
            errors.add("Root path is required");
        }

        // Validate files
        if (packMetadata.structure().files() == null || packMetadata.structure().files().isEmpty()) {
            warnings.add("Pack has no files defined");
        } else {
            for (PackMetadata.PackFile file : packMetadata.structure().files()) {
                if (file.filePath() == null || file.filePath().isBlank()) {
                    errors.add("File path is required for all files");
                }
                if (file.fileType() == null || file.fileType().isBlank()) {
                    errors.add("File type is required for: " + file.filePath());
                }
            }
        }

        // Validate directories
        if (packMetadata.structure().directories() != null) {
            for (PackMetadata.PackDirectory dir : packMetadata.structure().directories()) {
                if (dir.directoryPath() == null || dir.directoryPath().isBlank()) {
                    errors.add("Directory path is required for all directories");
                }
            }
        }

        // Validate against validation rules
        if (packMetadata.validation() != null) {
            PackMetadata.PackValidation validation = packMetadata.validation();
            
            // Check required files
            if (validation.requiredFiles() != null) {
                for (String requiredFile : validation.requiredFiles()) {
                    boolean fileExists = packMetadata.structure().files().stream()
                            .anyMatch(f -> f.filePath().equals(requiredFile));
                    if (!fileExists) {
                        errors.add("Required file missing: " + requiredFile);
                    }
                }
            }
        }

        boolean isValid = errors.isEmpty();
        if (!isValid) {
            log.debug("Structure validation failed: errors={}", errors);
        }

        return new PackValidationResult(isValid, errors, warnings);
    }

    @Override
    public PackValidationResult validateTemplateVariables(PackMetadata packMetadata) {
        log.debug("Validating template variables: packId={}", packMetadata.packId());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (packMetadata.templateVariables() == null || packMetadata.templateVariables().isEmpty()) {
            warnings.add("Pack has no template variables defined");
            return new PackValidationResult(true, errors, warnings);
        }

        for (PackMetadata.TemplateVariable variable : packMetadata.templateVariables()) {
            if (variable.variableName() == null || variable.variableName().isBlank()) {
                errors.add("Variable name is required");
            }
            if (variable.variableType() == null || variable.variableType().isBlank()) {
                errors.add("Variable type is required for: " + variable.variableName());
            }
            if (variable.isRequired() && (variable.defaultValue() == null || variable.defaultValue().isBlank())) {
                errors.add("Default value is required for required variable: " + variable.variableName());
            }
            if (variable.validationPattern() != null && !isValidRegex(variable.validationPattern())) {
                errors.add("Invalid validation pattern for: " + variable.variableName());
            }
        }

        // Check for duplicate variable names
        long uniqueCount = packMetadata.templateVariables().stream()
                .map(PackMetadata.TemplateVariable::variableName)
                .distinct()
                .count();
        if (uniqueCount != packMetadata.templateVariables().size()) {
            errors.add("Duplicate variable names detected");
        }

        boolean isValid = errors.isEmpty();
        if (!isValid) {
            log.debug("Template variable validation failed: errors={}", errors);
        }

        return new PackValidationResult(isValid, errors, warnings);
    }

    private boolean isValidVersion(String version) {
        return version.matches("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$");
    }

    private boolean isValidRegex(String pattern) {
        try {
            java.util.regex.Pattern.compile(pattern);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
