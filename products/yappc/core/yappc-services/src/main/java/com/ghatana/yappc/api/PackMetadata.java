/**
 * Canonical Pack Metadata Contract
 * 
 * Single source of truth for pack metadata structure.
 * Defines the canonical schema for packs used in scaffolding.
 * 
 * @doc.type class
 * @doc.purpose Canonical pack metadata schema
 * @doc.layer product
 * @doc.pattern DTO
 */

package com.ghatana.yappc.api;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canonical pack metadata schema.
 */
public final class PackMetadata {

    private final String packId;
    private final String packName;
    private final String packVersion;
    private final String description;
    private final PackType packType;
    private final PackInfo info;
    private final PackStructure structure;
    private final List<PackDependency> dependencies;
    private final List<TemplateVariable> templateVariables;
    private final PackValidation validation;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final String updatedBy;

    public PackMetadata(
            @NotNull String packId,
            @NotNull String packName,
            @NotNull String packVersion,
            String description,
            @NotNull PackType packType,
            @NotNull PackInfo info,
            @NotNull PackStructure structure,
            @NotNull List<PackDependency> dependencies,
            @NotNull List<TemplateVariable> templateVariables,
            @NotNull PackValidation validation,
            @NotNull Instant createdAt,
            @NotNull Instant updatedAt,
            @NotNull String createdBy,
            @NotNull String updatedBy
    ) {
        this.packId = packId;
        this.packName = packName;
        this.packVersion = packVersion;
        this.description = description;
        this.packType = packType;
        this.info = info;
        this.structure = structure;
        this.dependencies = dependencies;
        this.templateVariables = templateVariables;
        this.validation = validation;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    public String packId() {
        return packId;
    }

    public String packName() {
        return packName;
    }

    public String packVersion() {
        return packVersion;
    }

    public String description() {
        return description;
    }

    public PackType packType() {
        return packType;
    }

    public PackInfo info() {
        return info;
    }

    public PackStructure structure() {
        return structure;
    }

    public List<PackDependency> dependencies() {
        return dependencies;
    }

    public List<TemplateVariable> templateVariables() {
        return templateVariables;
    }

    public PackValidation validation() {
        return validation;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public String createdBy() {
        return createdBy;
    }

    public String updatedBy() {
        return updatedBy;
    }

    /**
     * Pack type enum.
     */
    public enum PackType {
        SCAFFOLD,
        TEMPLATE,
        COMPONENT,
        LIBRARY,
        PLUGIN
    }

    /**
     * Pack info.
     */
    public record PackInfo(
            String author,
            String organization,
            String license,
            String homepage,
            String repository,
            List<String> keywords,
            Map<String, String> customMetadata
    ) {}

    /**
     * Pack structure.
     */
    public record PackStructure(
            String rootPath,
            List<PackFile> files,
            List<PackDirectory> directories,
            Map<String, String> structureRules
    ) {}

    /**
     * Pack file.
     */
    public record PackFile(
            String filePath,
            String fileType,
            String templatePath,
            boolean isTemplate,
            Map<String, String> properties
    ) {}

    /**
     * Pack directory.
     */
    public record PackDirectory(
            String directoryPath,
            boolean isOptional,
            List<String> allowedFileTypes
    ) {}

    /**
     * Pack dependency.
     */
    public record PackDependency(
            String dependencyId,
            String dependencyName,
            String dependencyVersion,
            DependencyType type,
            boolean isRequired,
            String compatibilityConstraint
    ) {
        public enum DependencyType {
            PACK,
            LIBRARY,
            TOOL,
            RUNTIME
        }
    }

    /**
     * Template variable.
     */
    public record TemplateVariable(
            String variableName,
            String variableType,
            String defaultValue,
            boolean isRequired,
            String validationPattern,
            String description,
            Map<String, String> options
    ) {}

    /**
     * Pack validation rules.
     */
    public record PackValidation(
            List<String> requiredFiles,
            List<String> forbiddenFiles,
            Map<String, String> fileConstraints,
            Map<String, String> directoryConstraints
    ) {}
}
