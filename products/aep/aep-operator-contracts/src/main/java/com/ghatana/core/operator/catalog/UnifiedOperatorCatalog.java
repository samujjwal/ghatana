package com.ghatana.core.operator.catalog;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.UnifiedOperator;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified, pluggable implementation of {@link OperatorCatalog}.
 *
 * <p>This is the <b>recommended production catalog</b>. It supports:
 * <ul>
 *   <li><b>Manual registration</b> — operators added via {@link #register(UnifiedOperator)}</li>
 *   <li><b>ServiceLoader discovery</b> — via {@link #withServiceLoaderDiscovery()}, which scans
 *       the classpath for {@link com.ghatana.core.operator.spi.OperatorProvider} implementations</li>
 * </ul>
 *
 * <p>Create an instance with default (manual-only) registration:
 * <pre>{@code
 * OperatorCatalog catalog = new UnifiedOperatorCatalog();
 * }</pre>
 *
 * <p>Or load all operators discovered via {@link ServiceLoader}:
 * <pre>{@code
 * OperatorCatalog catalog = UnifiedOperatorCatalog.withServiceLoaderDiscovery();
 * }</pre>
 *
 * <p>{@link DefaultOperatorCatalog} and {@link InMemoryOperatorCatalog} are kept for
 * backward compatibility. New code should use {@code UnifiedOperatorCatalog} instead.
 *
 * @see OperatorCatalog
 * @see com.ghatana.core.operator.spi.OperatorProvider
 *
 * @doc.type class
 * @doc.purpose Unified operator catalog with pluggable discovery strategies
 * @doc.layer core
 * @doc.pattern Registry
 * @since 1.1.0
 */
public final class UnifiedOperatorCatalog implements OperatorCatalog {

    private static final Logger logger = LoggerFactory.getLogger(UnifiedOperatorCatalog.class);

    private final ConcurrentHashMap<OperatorId, UnifiedOperator> operators = new ConcurrentHashMap<>();

    /**
     * Creates an empty catalog. Operators must be added via {@link #register(UnifiedOperator)}.
     */
    public UnifiedOperatorCatalog() {}

    /**
     * Creates a catalog pre-loaded with operators discovered via {@link ServiceLoader}.
     *
     * <p>Loads all {@link com.ghatana.core.operator.spi.OperatorProvider} implementations on
     * the classpath and asks each to supply its operators.
     *
     * @return catalog pre-populated with all discovered operators
     */
    public static UnifiedOperatorCatalog withServiceLoaderDiscovery() {
        UnifiedOperatorCatalog catalog = new UnifiedOperatorCatalog();
        int loaded = 0;
        for (com.ghatana.core.operator.spi.OperatorProvider provider
                : ServiceLoader.load(com.ghatana.core.operator.spi.OperatorProvider.class)) {
            if (!provider.isEnabled()) continue;
            for (com.ghatana.core.operator.OperatorId operatorId : provider.getOperatorIds()) {
                try {
                    UnifiedOperator op = provider.createOperator(operatorId,
                        com.ghatana.core.operator.OperatorConfig.empty());
                    catalog.operators.put(op.getId(), op);
                    loaded++;
                } catch (Exception e) {
                    logger.warn("OperatorProvider {} failed to create operator {}: {}",
                        provider.getProviderId(), operatorId, e.getMessage(), e);
                }
            }
        }
        logger.info("UnifiedOperatorCatalog loaded {} operator(s) via ServiceLoader", loaded);
        return catalog;
    }

    @Override
    public Promise<Void> register(UnifiedOperator operator) {
        Objects.requireNonNull(operator, "operator must not be null");
        Objects.requireNonNull(operator.getId(), "operator ID must not be null");

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
        Objects.requireNonNull(operatorId, "operatorId must not be null");
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
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        UnifiedOperator operator = operators.get(operatorId);
        if (operator == null) {
            logger.debug("Operator not found: {}", operatorId);
        }
        return Promise.of(operator);
    }

    @Override
    public Promise<Optional<UnifiedOperator>> get(OperatorId operatorId) {
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        return Promise.of(Optional.ofNullable(operators.get(operatorId)));
    }

    /** @return number of registered operators */
    public int size() { return operators.size(); }

    /** @return {@code true} if no operators are registered */
    public boolean isEmpty() { return operators.isEmpty(); }

    /** @return unmodifiable view of all registered operator IDs */
    public Set<OperatorId> getRegisteredIds() {
        return Collections.unmodifiableSet(operators.keySet());
    }

    /** @return unmodifiable view of all registered operators */
    public Collection<UnifiedOperator> getAll() {
        return Collections.unmodifiableCollection(operators.values());
    }

    /** Removes all registered operators. Primarily for testing. */
    public void clear() {
        operators.clear();
        logger.info("Cleared all operators from UnifiedOperatorCatalog");
    }

    @Override
    public String toString() {
        return "UnifiedOperatorCatalog{operators=" + operators.size() + "}";
    }
}
