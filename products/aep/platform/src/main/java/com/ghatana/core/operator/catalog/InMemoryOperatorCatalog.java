package com.ghatana.core.operator.catalog;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.UnifiedOperator;
import io.activej.promise.Promise;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link OperatorCatalog} for testing and
 * lightweight deployments.
 *
 * <p>Thread-safe via {@link ConcurrentHashMap}.</p>
 */
public class InMemoryOperatorCatalog implements OperatorCatalog {

    private final ConcurrentHashMap<OperatorId, UnifiedOperator> operators = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> register(UnifiedOperator operator) {
        operators.put(operator.getId(), operator);
        return Promise.complete();
    }

    @Override
    public Promise<Void> unregister(OperatorId operatorId) {
        operators.remove(operatorId);
        return Promise.complete();
    }

    @Override
    public Promise<UnifiedOperator> lookup(OperatorId operatorId) {
        return Promise.of(operators.get(operatorId));
    }

    /**
     * Looks up an operator by ID, returning an {@link Optional}.
     *
     * @param operatorId the operator ID to look up
     * @return promise resolving to an optional containing the operator if found
     */
    public Promise<Optional<UnifiedOperator>> get(OperatorId operatorId) {
        return Promise.of(Optional.ofNullable(operators.get(operatorId)));
    }

    /**
     * Returns the number of operators in this catalog.
     */
    public int size() {
        return operators.size();
    }

    /**
     * Checks if the catalog contains an operator with the given ID.
     */
    public boolean contains(OperatorId operatorId) {
        return operators.containsKey(operatorId);
    }

    /**
     * Clears all operators from the catalog.
     */
    public void clear() {
        operators.clear();
    }
}
