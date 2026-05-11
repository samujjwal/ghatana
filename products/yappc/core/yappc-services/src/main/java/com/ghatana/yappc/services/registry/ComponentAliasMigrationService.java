/**
 * Component Alias Migration Service
 * 
 * Manages component alias migrations for backward compatibility.
 * Handles migration of deprecated component aliases to current components.
 * 
 * @doc.type interface
 * @doc.purpose Component alias migration
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.registry;

import com.ghatana.yappc.api.ComponentRegistryContract;

import java.util.Map;

/**
 * Service interface for managing component alias migrations.
 */
public interface ComponentAliasMigrationService {

    /**
     * Migrates a component alias to the target component.
     * 
     * @param aliasName The alias name to migrate
     * @return Migration result containing the target component ID and any migration notes
     */
    MigrationResult migrateAlias(String aliasName);

    /**
     * Creates a new component alias.
     * 
     * @param aliasName The alias name
     * @param targetComponentId The target component ID
     * @param targetVersion The target component version
     * @param migrationNotes Notes about the migration
     * @return The created component alias
     */
    ComponentRegistryContract.ComponentAlias createAlias(
            String aliasName,
            String targetComponentId,
            String targetVersion,
            String migrationNotes
    );

    /**
     * Deprecates a component alias.
     * 
     * @param aliasName The alias name to deprecate
     * @param deprecationNotes Notes about the deprecation
     */
    void deprecateAlias(String aliasName, String deprecationNotes);

    /**
     * Gets all active aliases for a component.
     * 
     * @param componentId The component ID
     * @return List of active aliases for the component
     */
    java.util.List<ComponentRegistryContract.ComponentAlias> getAliasesForComponent(String componentId);

    /**
     * Migration result.
     */
    record MigrationResult(
            boolean success,
            String targetComponentId,
            String targetVersion,
            String migrationNotes,
            java.util.List<String> warnings
    ) {}
}
