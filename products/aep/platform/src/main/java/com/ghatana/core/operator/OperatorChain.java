package com.ghatana.core.operator;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Chain of operators that execute sequentially.
 *
 * <p><b>Purpose</b><br>
 * Enables composing multiple operators into a single processing chain where each operator's
 * output becomes the next operator's input. Provides a fluent API for building complex
 * processing pipelines.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * OperatorChain chain = OperatorChain.create(filterOp)
 *     .then(enrichOp)
 *     .then(transformOp)
 *     .then(sinkOp);
 *
 * // Execute chain
 * OperatorResult result = chain.process(event).getResult();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Core abstraction for operator composition. Enables declarative pipeline construction
 * where operators are chained together to form complex processing graphs.
 *
 * <p><b>Semantics</b><br>
 * <ul>
 *   <li>Sequential execution: Operators execute in order</li>
 *   <li>Event correlation: Preserves correlation IDs through chain</li>
 *   <li>Short-circuit: Chain stops on first failure</li>
 *   <li>Backpressure: Propagates backpressure through chain</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Implementation must be thread-safe for concurrent access. Chain structure is immutable
 * after construction. Each process() call is isolated.
 *
 * <p><b>Performance</b><br>
 * Target: <100μs overhead per operator in chain (excluding operator processing time)
 *
 * @see UnifiedOperator
 * @see OperatorComposer
 * @doc.type interface
 * @doc.purpose Sequential operator composition
 * @doc.layer core
 * @doc.pattern Chain of Responsibility
 */
public interface OperatorChain extends UnifiedOperator {

    /**
     * Append operator to end of chain.
     *
     * <p>Creates new chain with additional operator. Original chain unchanged.
     *
     * @param next Operator to append
     * @return New chain with operator appended
     * @throws NullPointerException if next is null
     */
    OperatorChain then(UnifiedOperator next);

    /**
     * Get all operators in chain (ordered).
     *
     * <p>Returns immutable list of operators in execution order.
     *
     * @return List of operators (never null, may be empty)
     */
    List<UnifiedOperator> getOperators();

    /**
     * Get number of operators in chain.
     *
     * @return Operator count (>= 0)
     */
    default int size() {
        return getOperators().size();
    }

    /**
     * Check if chain is empty.
     *
     * @return true if no operators in chain
     */
    default boolean isEmpty() {
        return getOperators().isEmpty();
    }

    /**
     * Create new chain starting with given operator.
     *
     * @param first First operator in chain
     * @return New chain
     * @throws NullPointerException if first is null
     */
    static OperatorChain create(UnifiedOperator first) {
        return new SimpleOperatorChain(first);
    }

    /**
     * Create empty chain.
     *
     * @return Empty chain
     */
    static OperatorChain empty() {
        return new SimpleOperatorChain();
    }

    /**
     * Create chain from multiple operators.
     *
     * @param operators Operators to chain (in order)
     * @return New chain
     * @throws NullPointerException if operators is null or contains null
     */
    static OperatorChain of(UnifiedOperator... operators) {
        OperatorChain chain = empty();
        for (UnifiedOperator op : operators) {
            chain = chain.then(op);
        }
        return chain;
    }

    /**
     * Create chain from list of operators.
     *
     * @param operators Operators to chain (in order)
     * @return New chain
     * @throws NullPointerException if operators is null or contains null
     */
    static OperatorChain of(List<UnifiedOperator> operators) {
        OperatorChain chain = empty();
        for (UnifiedOperator op : operators) {
            chain = chain.then(op);
        }
        return chain;
    }
}

