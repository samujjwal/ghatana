package com.ghatana.pattern.api.codegen;

import com.ghatana.platform.domain.domain.event.EventType;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.pattern.api.exception.EventClassCompilationException;
import com.ghatana.pattern.api.model.PatternSpecification;

import java.util.Optional;

/**
 * Public entry point for runtime event class generation.
 */
public interface EventClassCompiler {

    /**
     * Generates (or retrieves) a {@link GEvent} subclass for the supplied request.
     *
     * @param eventType canonical event metadata
     * @param specification pattern definition driving derived fields
     * @param options compiler feature flags (use {@link CompileOptions#defaults()} when unsure)
     */
    Class<? extends GEvent> compileClass(EventType eventType,
                                         PatternSpecification specification,
                                         CompileOptions options) throws EventClassCompilationException;

    /**
     * Generates (or retrieves) a {@link GEventLike} implementation for the supplied request.
     *
     * @param eventType canonical event metadata
     * @param specification pattern definition driving derived fields
     * @param options compiler feature flags (use {@link CompileOptions#defaults()} when unsure)
     */
    Class<? extends GEventLike> compileRecord(EventType eventType,
                                              PatternSpecification specification,
                                              CompileOptions options) throws EventClassCompilationException;

    /**
     * @return cached class (if present) without triggering compilation.
     */
    Optional<Class<?>> findInCache(GeneratedTypeKey key);

    /**
     * Computes the stable key describing a compilation request.
     */
    GeneratedTypeKey keyOf(EventType eventType, PatternSpecification specification);
}
