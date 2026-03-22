package com.ghatana.core.operator.catalog;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.UnifiedOperator;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Catalog for registering and discovering unified operators.
 *
 * <p>Provides a central registry where operators can be registered,
 * unregistered, and looked up by their unique identifier.</p>
 *
 * @doc.type interface
 * @doc.purpose Central registry interface for discovering and managing unified operators by ID
 * @doc.layer core
 * @doc.pattern Service
 */
public interface OperatorCatalog {

    /**
     * Registers an operator in the catalog.
     *
     * @param operator the operator to register (never null)
     * @return promise completing when registration is done
     */
    Promise<Void> register(UnifiedOperator operator);

    /**
     * Unregisters an operator from the catalog.
     *
     * @param operatorId the ID of the operator to unregister (never null)
     * @return promise completing when unregistration is done
     */
    Promise<Void> unregister(OperatorId operatorId);

    /**
     * Looks up an operator by its ID.
     *
     * @param operatorId the ID to look up
     * @return promise resolving to the operator, or null if not found
     */
    Promise<UnifiedOperator> lookup(OperatorId operatorId);

    /**
     * Looks up an operator by ID, returning an {@link Optional}.
     *
     * @param operatorId the operator ID to look up
     * @return promise resolving to an optional containing the operator if found
     */
    default Promise<Optional<UnifiedOperator>> get(OperatorId operatorId) {
        return lookup(operatorId).map(Optional::ofNullable);
    }
}
