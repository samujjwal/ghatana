package com.ghatana.kernel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to run immediately after module initialize() finishes.
 *
 * @doc.type annotation
 * @doc.purpose Post-initialization lifecycle hook for kernel modules
 * @doc.layer core
 * @doc.pattern Lifecycle Hook
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostInitialize {
}
