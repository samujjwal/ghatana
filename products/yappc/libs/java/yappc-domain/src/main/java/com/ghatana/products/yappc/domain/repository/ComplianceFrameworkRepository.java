package com.ghatana.products.yappc.domain.repository;

import com.ghatana.products.yappc.domain.model.ComplianceFramework;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for ComplianceFramework entity operations.
 *
 * <p>Provides data access operations for compliance frameworks,
 * including built-in framework queries and category filtering.</p>
 *
 * @doc.type interface
 * @doc.purpose Defines data access contract for ComplianceFramework entities
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface ComplianceFrameworkRepository {

    /**
     * Finds a framework by its ID.
     *
     * @param id the framework ID
     * @return promise of the framework if found
     */
    Promise<Optional<ComplianceFramework>> findById(UUID id);

    /**
     * Finds all frameworks with pagination.
     *
     * @param offset the zero-based offset for pagination
     * @param limit  the maximum number of results to return
     * @return promise of a paginated result of frameworks
     */
    Promise<PageResult<ComplianceFramework>> findAll(int offset, int limit);

    /**
     * Finds a framework by its unique name.
     *
     * @param name the framework name
     * @return promise of the framework if found
     */
    Promise<Optional<ComplianceFramework>> findByName(String name);

    /**
     * Finds all built-in frameworks.
     *
     * @return promise of a list of built-in frameworks
     */
    Promise<List<ComplianceFramework>> findBuiltinFrameworks();

    /**
     * Finds frameworks by category.
     *
     * @param category the framework category
     * @param offset   the zero-based offset for pagination
     * @param limit    the maximum number of results to return
     * @return promise of a paginated result of frameworks in the category
     */
    Promise<PageResult<ComplianceFramework>> findByCategory(String category, int offset, int limit);

    /**
     * Finds frameworks enabled by default.
     *
     * @return promise of a list of default-enabled frameworks
     */
    Promise<List<ComplianceFramework>> findEnabledByDefault();

    /**
     * Saves a compliance framework.
     *
     * @param framework the framework to save
     * @return promise of the saved framework
     */
    Promise<ComplianceFramework> save(ComplianceFramework framework);

    /**
     * Deletes a framework by ID.
     *
     * @param id the framework ID
     * @return promise completing when deleted
     */
    Promise<Void> deleteById(UUID id);

    /**
     * Checks if a framework exists by name.
     *
     * @param name the framework name
     * @return promise of true if a framework with the name exists
     */
    Promise<Boolean> existsByName(String name);
}
