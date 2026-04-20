package com.ghatana.kernel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field in a {@link com.ghatana.kernel.plugin.KernelPlugin} implementation
 * for automatic dependency injection from {@link com.ghatana.kernel.context.KernelContext}.
 *
 * <p>Fields annotated with {@code @PluginInject} are resolved during the plugin's
 * {@code initialize(KernelContext)} call by {@link com.ghatana.kernel.plugin.PluginInjector}.
 * The injector looks up each field type via {@code KernelContext.getDependency(Class)}
 * or, when {@link #optional()} is {@code true}, via {@code getOptionalDependency(Class)}.</p>
 *
 * <p><b>Example</b></p>
 * <pre>{@code
 * public class BillingPlugin extends AbstractKernelModule implements KernelPlugin {
 *
 *     @PluginInject
 *     private LedgerService ledgerService;
 *
 *     @PluginInject(optional = true)
 *     private AuditEventEmitter auditEmitter;
 * }
 * }</pre>
 *
 * @doc.type annotation
 * @doc.purpose Field-level dependency injection for kernel plugins from KernelContext
 * @doc.layer core
 * @doc.pattern Dependency Injection
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface PluginInject {

    /**
     * When {@code true}, the injector will not throw if the dependency is absent
     * in the {@link com.ghatana.kernel.context.KernelContext}. The field will remain
     * {@code null} and the plugin is expected to handle the optional case.
     *
     * @return whether the dependency is optional (default {@code false})
     */
    boolean optional() default false;
}
