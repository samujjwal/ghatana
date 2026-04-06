/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.annotation;

import java.lang.annotation.*;

/**
 * Marks an API element as internal to the kernel implementation.
 *
 * <p>Types annotated with {@code @KernelInternal} are implementation details
 * and MUST NOT be used by external consumers (products, plugins, adapters).
 * The only public registry contract is {@link com.ghatana.kernel.registry.KernelRegistry}.</p>
 *
 * <p>Internal APIs may change or be removed without notice. External code
 * should use the canonical public contracts instead.</p>
 *
 * @doc.type annotation
 * @doc.purpose Machine-readable marker for kernel-internal APIs (Decision D4)
 * @doc.layer core
 * @doc.pattern Marker
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface KernelInternal {

    /**
     * Optional note explaining what public API to use instead.
     *
     * @return replacement guidance
     */
    String value() default "";
}
