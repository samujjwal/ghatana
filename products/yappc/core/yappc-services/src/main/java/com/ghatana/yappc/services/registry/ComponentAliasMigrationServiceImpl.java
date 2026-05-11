/**
 * Component Alias Migration Service Implementation
 * 
 * Production-grade implementation of component alias migration service.
 * Manages component alias migrations for backward compatibility.
 * 
 * @doc.type class
 * @doc.purpose Component alias migration implementation
 * @doc.layer product
 * @doc.pattern Service
 */

package com.ghatana.yappc.services.registry;

import com.ghatana.yappc.api.ComponentRegistryContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-grade implementation of component alias migration service.
 * Uses in-memory storage for demonstration; should be replaced with database persistence.
 */
public final class ComponentAliasMigrationServiceImpl implements ComponentAliasMigrationService {

    private static final Logger log = LoggerFactory.getLogger(ComponentAliasMigrationServiceImpl.class);

    // In-memory storage for demonstration - replace with database persistence
    private final Map<String, ComponentRegistryContract.ComponentAlias> aliasStorage = new ConcurrentHashMap<>();
    private final Map<String, List<String>> componentToAliasesMap = new ConcurrentHashMap<>();

    @Override
    public MigrationResult migrateAlias(String aliasName) {
        log.info("Migrating alias: aliasName={}", aliasName);

        ComponentRegistryContract.ComponentAlias alias = aliasStorage.get(aliasName);

        if (alias == null) {
            log.warn("Alias not found: aliasName={}", aliasName);
            return new MigrationResult(
                    false,
                    null,
                    null,
                    "Alias not found",
                    List.of("Alias not found in registry")
            );
        }

        List<String> warnings = new ArrayList<>();

        if (alias.isDeprecated()) {
            warnings.add(String.format("Alias is deprecated since: %s", alias.deprecatedAt()));
            warnings.add(alias.migrationNotes());
        }

        log.info("Alias migration successful: aliasName={}, targetComponentId={}", 
                aliasName, alias.targetComponentId());

        return new MigrationResult(
                true,
                alias.targetComponentId(),
                alias.targetVersion(),
                alias.migrationNotes(),
                warnings
        );
    }

    @Override
    public ComponentRegistryContract.ComponentAlias createAlias(
            String aliasName,
            String targetComponentId,
            String targetVersion,
            String migrationNotes
    ) {
        log.info("Creating component alias: aliasName={}, targetComponentId={}", 
                aliasName, targetComponentId);

        if (aliasStorage.containsKey(aliasName)) {
            log.warn("Alias already exists: aliasName={}", aliasName);
            throw new IllegalArgumentException("Alias already exists: " + aliasName);
        }

        ComponentRegistryContract.ComponentAlias alias = new ComponentRegistryContract.ComponentAlias(
                aliasName,
                targetComponentId,
                targetVersion,
                Instant.now(),
                false,
                null,
                migrationNotes
        );

        aliasStorage.put(aliasName, alias);
        componentToAliasesMap.computeIfAbsent(targetComponentId, k -> new ArrayList<>()).add(aliasName);

        log.info("Component alias created successfully: aliasName={}", aliasName);
        return alias;
    }

    @Override
    public void deprecateAlias(String aliasName, String deprecationNotes) {
        log.info("Deprecating component alias: aliasName={}", aliasName);

        ComponentRegistryContract.ComponentAlias alias = aliasStorage.get(aliasName);

        if (alias == null) {
            log.warn("Cannot deprecate non-existent alias: aliasName={}", aliasName);
            throw new IllegalArgumentException("Alias not found: " + aliasName);
        }

        if (alias.isDeprecated()) {
            log.warn("Alias is already deprecated: aliasName={}", aliasName);
            return;
        }

        ComponentRegistryContract.ComponentAlias deprecatedAlias = new ComponentRegistryContract.ComponentAlias(
                alias.aliasName(),
                alias.targetComponentId(),
                alias.targetVersion(),
                alias.createdAt(),
                true,
                Instant.now(),
                deprecationNotes
        );

        aliasStorage.put(aliasName, deprecatedAlias);

        log.info("Component alias deprecated successfully: aliasName={}", aliasName);
    }

    @Override
    public List<ComponentRegistryContract.ComponentAlias> getAliasesForComponent(String componentId) {
        log.debug("Getting aliases for component: componentId={}", componentId);

        List<String> aliasNames = componentToAliasesMap.getOrDefault(componentId, List.of());
        List<ComponentRegistryContract.ComponentAlias> aliases = new ArrayList<>();

        for (String aliasName : aliasNames) {
            ComponentRegistryContract.ComponentAlias alias = aliasStorage.get(aliasName);
            if (alias != null) {
                aliases.add(alias);
            }
        }

        return aliases;
    }

    /**
     * Checks if an alias exists.
     * 
     * @param aliasName The alias name to check
     * @return true if the alias exists, false otherwise
     */
    public boolean aliasExists(String aliasName) {
        return aliasStorage.containsKey(aliasName);
    }

    /**
     * Gets an alias by name.
     * 
     * @param aliasName The alias name
     * @return The component alias, or null if not found
     */
    public ComponentRegistryContract.ComponentAlias getAlias(String aliasName) {
        return aliasStorage.get(aliasName);
    }

    /**
     * Removes an alias.
     * 
     * @param aliasName The alias name to remove
     */
    public void removeAlias(String aliasName) {
        log.info("Removing component alias: aliasName={}", aliasName);

        ComponentRegistryContract.ComponentAlias alias = aliasStorage.remove(aliasName);

        if (alias != null) {
            List<String> aliases = componentToAliasesMap.get(alias.targetComponentId());
            if (aliases != null) {
                aliases.remove(aliasName);
            }
        }

        log.info("Component alias removed successfully: aliasName={}", aliasName);
    }
}
