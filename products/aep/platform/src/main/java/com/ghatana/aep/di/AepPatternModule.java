/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.di;

import com.ghatana.pattern.compiler.PatternCompiler;
import com.ghatana.pattern.operator.registry.OperatorRegistry;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * ActiveJ DI module for AEP pattern detection components.
 *
 * <p>Provides the pattern engine stack — operator registry, pattern compiler,
 * and NFA-based detection infrastructure:
 * <ul>
 *   <li>{@link OperatorRegistry} — registry for pattern detection operators (SEQ, AND, OR, etc.)</li>
 *   <li>{@link PatternCompiler} — multi-phase compiler: validation → AST → DAG → optimization → detection plan</li>
 * </ul>
 *
 * <p><b>Note on PatternDetectionAgent:</b> Pattern agents are created per-pattern
 * via the builder pattern ({@code PatternDetectionAgent.builder()}) and are
 * <em>not</em> singletons. The compiler and registry provided by this module are
 * used as factories for agent creation. See
 * {@link com.ghatana.pattern.engine.agent.PatternDetectionAgent} for builder usage.
 *
 * <p><b>Dependencies:</b> Requires {@link MeterRegistry} from the observability
 * layer (provided by {@link AepObservabilityModule}).
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new AepObservabilityModule(),
 *     new AepPatternModule()
 * );
 * PatternCompiler compiler = injector.getInstance(PatternCompiler.class);
 * OperatorRegistry registry = injector.getInstance(OperatorRegistry.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for pattern detection pipeline
 * @doc.layer product
 * @doc.pattern Module
 * @see PatternCompiler
 * @see OperatorRegistry
 */
public class AepPatternModule extends AbstractModule {

    /**
     * Provides the pattern operator registry.
     *
     * <p>Returns a fresh {@link OperatorRegistry} instance. Built-in operators
     * (SEQ, AND, OR, NOT, etc.) should be registered after injection via
     * the {@code register(Operator)} method, typically during application startup.
     *
     * <p>This registry is distinct from the pipeline {@link com.ghatana.core.operator.catalog.OperatorCatalog}
     * — it handles <em>pattern detection</em> operators, while the catalog handles
     * <em>pipeline stage</em> operators.
     *
     * @return singleton operator registry
     */
    @Provides
    OperatorRegistry operatorRegistry() {
        return new OperatorRegistry();
    }

    /**
     * Provides the pattern compiler.
     *
     * <p>The compiler orchestrates a multi-phase pipeline:
     * <ol>
     *   <li>Validation — verify schema, operators, permissions</li>
     *   <li>AST building — create abstract syntax tree from operator tree</li>
     *   <li>DAG generation — build directed acyclic graph</li>
     *   <li>Optimization — reduce, reorder, fuse operators</li>
     *   <li>Plan creation — produce an executable {@code DetectionPlan}</li>
     * </ol>
     *
     * <p>Internally constructs {@code ValidationEngine}, {@code ASTBuilder},
     * {@code DAGBuilder}, and {@code DAGOptimizer} from the provided dependencies.
     *
     * @param operatorRegistry operator registry for operator lookup and validation
     * @param meterRegistry    Micrometer meter registry for compilation metrics
     * @return singleton pattern compiler
     */
    @Provides
    PatternCompiler patternCompiler(OperatorRegistry operatorRegistry, MeterRegistry meterRegistry) {
        return new PatternCompiler(operatorRegistry, meterRegistry);
    }
}
