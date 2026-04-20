/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class, method, or field as intended for development/testing use only.
 *
 * <p>Elements marked with this annotation should not be used in production
 * environments. They may be:
 * <ul>
 *   <li>Simple placeholder implementations that need to be replaced with production-grade code</li>
 *   <li>Components with known limitations or performance issues</li>
 *   <li>Experimental features not yet ready for production use</li>
 * </ul>
 *
 * @doc.type annotation
 * @doc.purpose Marks development-only code that should not be used in production
 * @doc.layer product
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface DevelopmentOnly {
    /**
     * Optional reason why this is marked as development-only.
     */
    String reason() default "";
}
