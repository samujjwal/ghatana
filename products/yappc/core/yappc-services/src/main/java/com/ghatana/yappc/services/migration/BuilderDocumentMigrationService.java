/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.migration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import java.util.Optional;

/**
 * Service for migrating old builder documents to the new PageArtifactDocument format.
 * Provides conflict resolution and clear choices for UI to handle migration scenarios.
 *
 * @doc.type interface
 * @doc.purpose Service for migrating old builder documents with conflict resolution
 * @doc.layer product
 * @doc.pattern Service
 */
public interface BuilderDocumentMigrationService {

    /**
     * Detects if a builder document needs migration.
     *
     * @param oldBuilderDocument The old builder document content
     * @param currentRegistryVersion The current registry version
     * @return true if migration is needed, false otherwise
     */
    boolean needsMigration(@NotNull String oldBuilderDocument, @NotNull String currentRegistryVersion);

    /**
     * Migrates an old builder document to the new format.
     *
     * @param oldBuilderDocument The old builder document content
     * @param oldRegistryVersion The old registry version
     * @param newRegistryVersion The new registry version
     * @param migrationStrategy The migration strategy to apply
     * @return MigrationResult containing the migrated document and any conflicts
     */
    MigrationResult migrate(
            @NotNull String oldBuilderDocument,
            @NotNull String oldRegistryVersion,
            @NotNull String newRegistryVersion,
            @NotNull MigrationStrategy migrationStrategy
    );

    /**
     * Validates that a migrated document is compatible with the current registry.
     *
     * @param migratedDocument The migrated document
     * @param targetRegistryVersion The target registry version
     * @return Validation result
     */
    ValidationResult validate(@NotNull String migratedDocument, @NotNull String targetRegistryVersion);

    /**
     * Result of a migration operation.
     */
    record MigrationResult(
            @NotNull String migratedDocument,
            @NotNull boolean success,
            @NotNull List<MigrationConflict> conflicts,
            @Nullable String error,
            @NotNull String migrationId
    ) {
        public MigrationResult {
            if (migratedDocument == null || migratedDocument.isBlank()) {
                throw new IllegalArgumentException("migratedDocument is required");
            }
            if (migrationId == null || migrationId.isBlank()) {
                throw new IllegalArgumentException("migrationId is required");
            }
        }
    }

    /**
     * Migration conflict that requires user resolution.
     */
    record MigrationConflict(
            @NotNull String conflictId,
            @NotNull ConflictType type,
            @NotNull String description,
            @NotNull String location,
            @NotNull List<ConflictResolutionOption> resolutionOptions,
            @Nullable String recommendedResolution
    ) {
        public MigrationConflict {
            if (conflictId == null || conflictId.isBlank()) {
                throw new IllegalArgumentException("conflictId is required");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("description is required");
            }
            if (location == null || location.isBlank()) {
                throw new IllegalArgumentException("location is required");
            }
        }
    }

    /**
     * Type of migration conflict.
     */
    enum ConflictType {
        VERSION_MISMATCH,
        MISSING_PROPERTY,
        DEPRECATED_PROPERTY,
        INCOMPATIBLE_TYPE,
        REQUIRED_FIELD_MISSING
    }

    /**
     * Resolution option for a conflict.
     */
    record ConflictResolutionOption(
            @NotNull String optionId,
            @NotNull String description,
            @NotNull ResolutionAction action,
            @Nullable String transformedValue
    ) {
        public ConflictResolutionOption {
            if (optionId == null || optionId.isBlank()) {
                throw new IllegalArgumentException("optionId is required");
            }
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("description is required");
            }
        }
    }

    /**
     * Action to take for conflict resolution.
     */
    enum ResolutionAction {
        KEEP_ORIGINAL,
        USE_DEFAULT,
        USE_TRANSFORMED,
        MANUAL_INPUT,
        SKIP_COMPONENT
    }

    /**
     * Validation result for a migrated document.
     */
    record ValidationResult(
            @NotNull boolean valid,
            @NotNull List<String> errors,
            @NotNull List<String> warnings
    ) {
        public ValidationResult {
            if (errors == null) {
                throw new IllegalArgumentException("errors cannot be null");
            }
            if (warnings == null) {
                throw new IllegalArgumentException("warnings cannot be null");
            }
        }
    }

    /**
     * Migration strategy to apply.
     */
    enum MigrationStrategy {
        /**
         * Automatically resolve all conflicts using defaults.
         */
        AUTO,

        /**
         * Require manual conflict resolution.
         */
        MANUAL,

        /**
         * Fail on any conflict.
         */
        STRICT,

        /**
         * Skip conflicting components and migrate only compatible parts.
         */
        PARTIAL
    }
}
