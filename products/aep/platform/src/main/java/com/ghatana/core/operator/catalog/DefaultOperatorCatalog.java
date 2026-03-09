package com.ghatana.core.operator.catalog;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.UnifiedOperator;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link OperatorCatalog}.
 *
 * <p><b>Purpose</b><br>
 * Provides a fast, concurrent operator registry for development, testing, and
 * single-node deployments. Operators are stored in a {@link ConcurrentHashMap}
 * keyed by {@link OperatorId}. All operations are non-blocking and return
 * immediately-resolved {@link Promise} instances.
 *
 * <p><b>Architecture Role</b><br>
 * The default catalog implementation used by {@link com.ghatana.core.pipeline.PipelineExecutionEngine}
 * to resolve pipeline stage operator IDs to live {@link UnifiedOperator} instances.
 * In distributed deployments, this would be replaced by a cluster-aware implementation.
 *
 * <p><b>Thread Safety</b><br>
 * All operations are thread-safe via {@link ConcurrentHashMap}. Registrations are
 * visible to subsequent lookups without external synchronization.
 *
 * <p><b>Usage</b>
 * <pre>{@code
 * DefaultOperatorCatalog catalog = new DefaultOperatorCatalog();
 * catalog.register(myFilterOperator).getResult();
 * catalog.register(myEnrichOperator).getResult();
 *
 * UnifiedOperator op = catalog.lookup(OperatorId.parse("stream:filter:v1")).getResult();
 * }</pre>
 *
 * @see OperatorCatalog
 * @see UnifiedOperator
 *
 * @doc.type class
 * @doc.purpose In-memory thread-safe operator catalog
 * @doc.layer core
 * @doc.pattern Registry
 */
public class DefaultOperatorCatalog implements OperatorCatalog {

    private static final Logger logger = LoggerFactory.getLogger(DefaultOperatorCatalog.class);

    private final ConcurrentHashMap<OperatorId, UnifiedOperator> operators = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> register(UnifiedOperator operator) {
        Objects.requireNonNull(operator, "operator cannot be null");
        Objects.requireNonNull(operator.getId(), "operator ID cannot be null");

        OperatorId id = operator.getId();
        UnifiedOperator previous = operators.putIfAbsent(id, operator);
        if (previous != null) {
            logger.warn("Operator already registered, replacing: {}", id);
            operators.put(id, operator);
        }
        logger.info("Registered operator: {} (type={}, name={})", id, operator.getType(), operator.getName());
        return Promise.complete();
    }

    @Override
    public Promise<Void> unregister(OperatorId operatorId) {
        Objects.requireNonNull(operatorId, "operatorId cannot be null");
        UnifiedOperator removed = operators.remove(operatorId);
        if (removed != null) {
            logger.info("Unregistered operator: {}", operatorId);
        } else {
            logger.debug("Attempted to unregister non-existent operator: {}", operatorId);
        }
        return Promise.complete();
    }

    @Override
    public Promise<UnifiedOperator> lookup(OperatorId operatorId) {
        Objects.requireNonNull(operatorId, "operatorId cannot be null");
        UnifiedOperator operator = operators.get(operatorId);
        if (operator == null) {
            logger.debug("Operator not found: {}", operatorId);
        }
        return Promise.of(operator);
    }

    @Override
    public Promise<Optional<UnifiedOperator>> get(OperatorId operatorId) {
        Objects.requireNonNull(operatorId, "operatorId cannot be null");
        return Promise.of(Optional.ofNullable(operators.get(operatorId)));
    }

    /**
     * Returns the number of registered operators.
     *
     * @return count of registered operators
     */
    public int size() {
        return operators.size();
    }

    /**
     * Returns true if the catalog contains no operators.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return operators.isEmpty();
    }

    /**
     * Returns an unmodifiable view of all registered operator IDs.
     *
     * @return set of registered operator IDs
     */
    public Set<OperatorId> getRegisteredIds() {
        return Collections.unmodifiableSet(operators.keySet());
    }

    /**
     * Returns an unmodifiable view of all registered operators.
     *
     * @return collection of registered operators
     */
    public Collection<UnifiedOperator> getAll() {
        return Collections.unmodifiableCollection(operators.values());
    }

    /**
     * Removes all registered operators.
     */
    public void clear() {
        operators.clear();
        logger.info("Cleared all operators from catalog");
    }

    @Override
    public String toString() {
        return String.format("DefaultOperatorCatalog{operators=%d}", operators.size());
    }
}
