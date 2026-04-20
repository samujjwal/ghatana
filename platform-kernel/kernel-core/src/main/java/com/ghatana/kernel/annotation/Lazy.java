package com.ghatana.kernel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link com.ghatana.kernel.module.KernelModule} implementation for lazy
 * initialization.
 *
 * <p>Modules annotated with {@code @Lazy} are wrapped in a
 * {@link com.ghatana.kernel.module.LazyKernelModule} proxy. Their
 * {@code initialize(KernelContext)} call is deferred until the first time a
 * collaborator requests a service or capability from the module, rather than
 * being called eagerly during kernel startup.</p>
 *
 * <p>Use this on heavy modules whose initialization is expensive and which are
 * not always needed during a process lifetime (e.g., optional analytics sinks,
 * feature-flagged extensions).</p>
 *
 * <p><b>Example</b></p>
 * <pre>{@code
 * @Lazy
 * public class HeavyAnalyticsModule extends AbstractKernelModule {
 *     // Initialized only when first accessed
 * }
 * }</pre>
 *
 * @doc.type annotation
 * @doc.purpose Opt-in lazy initialization for kernel modules
 * @doc.layer core
 * @doc.pattern Lazy Loading
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Lazy {
}
