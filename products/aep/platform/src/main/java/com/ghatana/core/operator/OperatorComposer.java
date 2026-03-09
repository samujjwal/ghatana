package com.ghatana.core.operator;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.core.operator.OperatorType;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Composes operators into complex processing graphs.
 *
 * <p><b>Purpose</b><br>
 * Provides high-level API for composing operators into sophisticated processing patterns
 * including sequential chains, parallel execution, conditional branching, and fan-out/fan-in.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * OperatorComposer composer = new OperatorComposer();
 *
 * // Sequential: op1 → op2 → op3
 * UnifiedOperator sequential = composer.sequential(op1, op2, op3);
 *
 * // Parallel: all operators process event simultaneously
 * UnifiedOperator parallel = composer.parallel(op1, op2, op3);
 *
 * // Conditional: route based on predicate
 * UnifiedOperator conditional = composer.conditional(
 *     event -> event.getType().equals("urgent"),
 *     urgentHandler,
 *     normalHandler
 * );
 *
 * // Fan-out: broadcast to multiple operators
 * UnifiedOperator fanOut = composer.fanOut(op1, op2, op3);
 * }</pre>
 *
 * <p><b>Composition Patterns</b><br>
 * <ul>
 *   <li><b>Sequential:</b> Operators execute in order, output → input</li>
 *   <li><b>Parallel:</b> All operators execute simultaneously</li>
 *   <li><b>Conditional:</b> Route to one of two operators based on predicate</li>
 *   <li><b>Fan-out:</b> Broadcast event to multiple operators</li>
 *   <li><b>Fan-in:</b> Merge outputs from multiple operators</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Composed operators are immutable after construction.
 *
 * @see OperatorChain
 * @see UnifiedOperator
 * @doc.type class
 * @doc.purpose High-level operator composition API
 * @doc.layer core
 * @doc.pattern Composite, Builder
 */
public class OperatorComposer {

    /**
     * Compose operators into sequential chain.
     *
     * <p>Each operator's output becomes next operator's input.
     *
     * @param operators Operators to chain (in order)
     * @return Composite operator
     * @throws NullPointerException if operators is null or contains null
     */
    public UnifiedOperator sequential(UnifiedOperator... operators) {
        Objects.requireNonNull(operators, "Operators array cannot be null");
        return OperatorChain.of(operators);
    }

    /**
     * Compose operators into sequential chain.
     *
     * @param operators Operators to chain (in order)
     * @return Composite operator
     * @throws NullPointerException if operators is null or contains null
     */
    public UnifiedOperator sequential(List<UnifiedOperator> operators) {
        Objects.requireNonNull(operators, "Operators list cannot be null");
        return OperatorChain.of(operators);
    }

    /**
     * Execute operators in parallel.
     *
     * <p>All operators process the same input event simultaneously.
     * Results are collected and merged.
     *
     * @param operators Operators to execute in parallel
     * @return Composite operator that executes all in parallel
     * @throws NullPointerException if operators is null or contains null
     */
    public UnifiedOperator parallel(UnifiedOperator... operators) {
        Objects.requireNonNull(operators, "Operators array cannot be null");
        return new ParallelOperator(Arrays.asList(operators));
    }

    /**
     * Execute operators in parallel.
     *
     * @param operators Operators to execute in parallel
     * @return Composite operator that executes all in parallel
     * @throws NullPointerException if operators is null or contains null
     */
    public UnifiedOperator parallel(List<UnifiedOperator> operators) {
        Objects.requireNonNull(operators, "Operators list cannot be null");
        return new ParallelOperator(operators);
    }

    /**
     * Route event conditionally to one of two operators.
     *
     * <p>If predicate matches, event goes to ifTrue operator.
     * Otherwise, event goes to ifFalse operator.
     *
     * @param condition Predicate to evaluate
     * @param ifTrue Operator to use if condition is true
     * @param ifFalse Operator to use if condition is false
     * @return Composite operator with conditional routing
     * @throws NullPointerException if any parameter is null
     */
    public UnifiedOperator conditional(Predicate<Event> condition,
                                      UnifiedOperator ifTrue,
                                      UnifiedOperator ifFalse) {
        Objects.requireNonNull(condition, "Condition cannot be null");
        Objects.requireNonNull(ifTrue, "IfTrue operator cannot be null");
        Objects.requireNonNull(ifFalse, "IfFalse operator cannot be null");
        return new ConditionalOperator(condition, ifTrue, ifFalse);
    }

    /**
     * Fan-out: broadcast event to multiple operators.
     *
     * <p>Same event is sent to all operators. All outputs are collected.
     *
     * @param operators Operators to broadcast to
     * @return Composite operator that broadcasts to all
     * @throws NullPointerException if operators is null or contains null
     */
    public UnifiedOperator fanOut(UnifiedOperator... operators) {
        Objects.requireNonNull(operators, "Operators array cannot be null");
        return new FanOutOperator(Arrays.asList(operators));
    }

    /**
     * Fan-out: broadcast event to multiple operators.
     *
     * @param operators Operators to broadcast to
     * @return Composite operator that broadcasts to all
     * @throws NullPointerException if operators is null or contains null
     */
    public UnifiedOperator fanOut(List<UnifiedOperator> operators) {
        Objects.requireNonNull(operators, "Operators list cannot be null");
        return new FanOutOperator(operators);
    }

    // ========================================================================
    // INNER CLASSES - Composite Operator Implementations
    // ========================================================================

    /**
     * Parallel operator - executes all operators simultaneously.
     */
    private static class ParallelOperator extends AbstractOperator {
        private final List<UnifiedOperator> operators;

        ParallelOperator(List<UnifiedOperator> operators) {
            super(
                OperatorId.of("ghatana", "composite", "parallel", "1.0.0"),
                OperatorType.STREAM,  // Composite operator type
                "Parallel Operator",
                "Executes operators in parallel",
                List.of("parallel", "composite"),
                null  // MeterRegistry can be null for composite operators
            );
            this.operators = new ArrayList<>(operators);
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            // Execute all operators in parallel
            List<Promise<OperatorResult>> promises = new ArrayList<>();
            for (UnifiedOperator operator : operators) {
                promises.add(operator.process(event));
            }

            // Wait for all to complete and merge results
            return Promises.toList(promises)
                .map(results -> {
                    // Collect all output events
                    List<Event> allOutputs = new ArrayList<>();
                    for (OperatorResult result : results) {
                        if (result.isSuccess()) {
                            allOutputs.addAll(result.getOutputEvents());
                        }
                    }
                    return OperatorResult.of(allOutputs);
                });
        }

        @Override
        protected Promise<Void> doInitialize(OperatorConfig config) {
            List<Promise<Void>> promises = new ArrayList<>();
            for (UnifiedOperator op : operators) {
                promises.add(op.initialize(config));
            }
            return Promises.toList(promises).map(list -> null);
        }

        @Override
        protected Promise<Void> doStart() {
            List<Promise<Void>> promises = new ArrayList<>();
            for (UnifiedOperator op : operators) {
                promises.add(op.start());
            }
            return Promises.toList(promises).map(list -> null);
        }

        @Override
        protected Promise<Void> doStop() {
            List<Promise<Void>> promises = new ArrayList<>();
            for (UnifiedOperator op : operators) {
                promises.add(op.stop());
            }
            return Promises.toList(promises).map(list -> null);
        }

        @Override
        public Event toEvent() {
            var payload = new java.util.HashMap<String, Object>();
            payload.put("type", "operator.parallel");
            payload.put("name", getName());
            payload.put("version", getVersion());
            payload.put("description", getDescription());

            var config = new java.util.HashMap<String, Object>();
            config.put("operatorCount", operators.size());
            config.put("operatorNames", operators.stream()
                    .map(UnifiedOperator::getName)
                    .collect(java.util.stream.Collectors.toList()));
            payload.put("config", config);

            payload.put("capabilities", java.util.List.of("parallel", "composite"));

            var headers = new java.util.HashMap<String, String>();
            headers.put("operatorId", getId().toString());
            headers.put("tenantId", getId().getNamespace());

            return com.ghatana.platform.domain.domain.event.GEvent.builder()
                    .type("operator.registered")
                    .headers(headers)
                    .payload(payload)
                    .time(com.ghatana.platform.domain.domain.event.EventTime.now())
                    .build();
        }
    }

    /**
     * Conditional operator - routes based on predicate.
     */
    private static class ConditionalOperator extends AbstractOperator {
        private final Predicate<Event> condition;
        private final UnifiedOperator ifTrue;
        private final UnifiedOperator ifFalse;

        ConditionalOperator(Predicate<Event> condition,
                          UnifiedOperator ifTrue,
                          UnifiedOperator ifFalse) {
            super(
                OperatorId.of("ghatana", "composite", "conditional", "1.0.0"),
                OperatorType.STREAM,
                "Conditional Operator",
                "Routes based on condition",
                List.of("conditional", "routing", "composite"),
                null
            );
            this.condition = condition;
            this.ifTrue = ifTrue;
            this.ifFalse = ifFalse;
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            boolean matches = condition.test(event);
            UnifiedOperator selected = matches ? ifTrue : ifFalse;
            return selected.process(event);
        }

        @Override
        protected Promise<Void> doInitialize(OperatorConfig config) {
            return Promises.toList(
                ifTrue.initialize(config),
                ifFalse.initialize(config)
            ).map(list -> null);
        }

        @Override
        protected Promise<Void> doStart() {
            return Promises.toList(
                ifTrue.start(),
                ifFalse.start()
            ).map(list -> null);
        }

        @Override
        protected Promise<Void> doStop() {
            return Promises.toList(
                ifTrue.stop(),
                ifFalse.stop()
            ).map(list -> null);
        }

        @Override
        public Event toEvent() {
            var payload = new java.util.HashMap<String, Object>();
            payload.put("type", "operator.conditional");
            payload.put("name", getName());
            payload.put("version", getVersion());
            payload.put("description", getDescription());

            var config = new java.util.HashMap<String, Object>();
            config.put("ifTrueOperator", ifTrue.getName());
            config.put("ifFalseOperator", ifFalse.getName());
            payload.put("config", config);

            payload.put("capabilities", java.util.List.of("conditional", "routing", "composite"));

            var headers = new java.util.HashMap<String, String>();
            headers.put("operatorId", getId().toString());
            headers.put("tenantId", getId().getNamespace());

            return com.ghatana.platform.domain.domain.event.GEvent.builder()
                    .type("operator.registered")
                    .headers(headers)
                    .payload(payload)
                    .time(com.ghatana.platform.domain.domain.event.EventTime.now())
                    .build();
        }
    }

    /**
     * Fan-out operator - broadcasts to all operators.
     */
    private static class FanOutOperator extends AbstractOperator {
        private final List<UnifiedOperator> operators;

        FanOutOperator(List<UnifiedOperator> operators) {
            super(
                OperatorId.of("ghatana", "composite", "fan-out", "1.0.0"),
                OperatorType.STREAM,
                "Fan-Out Operator",
                "Broadcasts to multiple operators",
                List.of("fan-out", "broadcast", "composite"),
                null
            );
            this.operators = new ArrayList<>(operators);
        }

        @Override
        public Promise<OperatorResult> process(Event event) {
            // Broadcast to all operators
            List<Promise<OperatorResult>> promises = new ArrayList<>();
            for (UnifiedOperator operator : operators) {
                promises.add(operator.process(event));
            }

            // Collect all results
            return Promises.toList(promises)
                .map(results -> {
                    List<Event> allOutputs = new ArrayList<>();
                    for (OperatorResult result : results) {
                        allOutputs.addAll(result.getOutputEvents());
                    }
                    return OperatorResult.of(allOutputs);
                });
        }

        @Override
        protected Promise<Void> doInitialize(OperatorConfig config) {
            List<Promise<Void>> promises = new ArrayList<>();
            for (UnifiedOperator op : operators) {
                promises.add(op.initialize(config));
            }
            return Promises.toList(promises).map(list -> null);
        }

        @Override
        protected Promise<Void> doStart() {
            List<Promise<Void>> promises = new ArrayList<>();
            for (UnifiedOperator op : operators) {
                promises.add(op.start());
            }
            return Promises.toList(promises).map(list -> null);
        }

        @Override
        protected Promise<Void> doStop() {
            List<Promise<Void>> promises = new ArrayList<>();
            for (UnifiedOperator op : operators) {
                promises.add(op.stop());
            }
            return Promises.toList(promises).map(list -> null);
        }

        @Override
        public Event toEvent() {
            var payload = new java.util.HashMap<String, Object>();
            payload.put("type", "operator.fanout");
            payload.put("name", getName());
            payload.put("version", getVersion());
            payload.put("description", getDescription());

            var config = new java.util.HashMap<String, Object>();
            config.put("operatorCount", operators.size());
            config.put("operatorNames", operators.stream()
                    .map(UnifiedOperator::getName)
                    .collect(java.util.stream.Collectors.toList()));
            payload.put("config", config);

            payload.put("capabilities", java.util.List.of("fanout", "broadcast", "composite"));

            var headers = new java.util.HashMap<String, String>();
            headers.put("operatorId", getId().toString());
            headers.put("tenantId", getId().getNamespace());

            return com.ghatana.platform.domain.domain.event.GEvent.builder()
                    .type("operator.registered")
                    .headers(headers)
                    .payload(payload)
                    .time(com.ghatana.platform.domain.domain.event.EventTime.now())
                    .build();
        }
    }
}

