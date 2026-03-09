package com.ghatana.virtualorg.framework.norm;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Registry for organizational norms.
 *
 * <p><b>Purpose</b><br>
 * Manages the registration, lookup, and lifecycle of norms.
 * Acts as the "law book" of the organization.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * NormRegistry registry = new InMemoryNormRegistry();
 * registry.register(Norm.obligation("respond-p1")
 *     .action("acknowledge")
 *     .deadline(Duration.ofMinutes(15))
 *     .build());
 *
 * List<Norm> obligations = registry.getObligations("Engineer");
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Norm management contract
 * @doc.layer platform
 * @doc.pattern Registry
 */
public interface NormRegistry {

    /**
     * Registers a new norm.
     *
     * @param norm the norm to register
     * @return promise completing when registration is done
     */
    Promise<Void> register(Norm norm);

    /**
     * Unregisters a norm by ID.
     *
     * @param normId the norm ID
     * @return promise with true if removed, false if not found
     */
    Promise<Boolean> unregister(String normId);

    /**
     * Gets a norm by ID.
     *
     * @param normId the norm ID
     * @return promise with optional norm
     */
    Promise<Optional<Norm>> get(String normId);

    /**
     * Gets all active norms.
     *
     * @return promise with list of active norms
     */
    Promise<List<Norm>> getAll();

    /**
     * Gets all obligations for a role.
     *
     * @param role the role name (null for all roles)
     * @return promise with list of obligations
     */
    Promise<List<Norm>> getObligations(String role);

    /**
     * Gets all prohibitions for a role.
     *
     * @param role the role name (null for all roles)
     * @return promise with list of prohibitions
     */
    Promise<List<Norm>> getProhibitions(String role);

    /**
     * Gets all permissions for a role.
     *
     * @param role the role name (null for all roles)
     * @return promise with list of permissions
     */
    Promise<List<Norm>> getPermissions(String role);

    /**
     * Checks if an action is permitted for a role.
     *
     * @param action the action to check
     * @param role the role performing the action
     * @return promise with true if permitted
     */
    Promise<Boolean> isPermitted(String action, String role);

    /**
     * Checks if an action is prohibited for a role.
     *
     * @param action the action to check
     * @param role the role performing the action
     * @return promise with true if prohibited
     */
    Promise<Boolean> isProhibited(String action, String role);
}
