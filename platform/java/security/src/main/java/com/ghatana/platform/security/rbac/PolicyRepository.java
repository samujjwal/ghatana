package com.ghatana.platform.security.rbac;

import com.ghatana.core.database.repository.Repository;

import java.util.List;

/**
 * Repository for managing policies.
 *
 * <p>Extends the canonical {@link Repository} from platform:database for standard CRUD
 * operations while adding policy-specific query methods.</p>
 *
 * @doc.type interface
 * @doc.purpose Repository for managing policies with canonical Repository base
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface PolicyRepository extends Repository<Policy, String> {

    /**
     * Finds all policies for the specified role.
     *
     * @param role The role
     * @return The policies
     */
    List<Policy> findByRole(String role);

    /**
     * Finds all policies for the specified resource.
     *
     * @param resource The resource
     * @return The policies
     */
    List<Policy> findByResource(String resource);
}
