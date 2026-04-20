package com.ghatana.kernel.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method to run after module stop() completes.
 *
 * @doc.type annotation
 * @doc.purpose Post-stop lifecycle hook for kernel modules
 * @doc.layer core
 * @doc.pattern Lifecycle Hook
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PostStop {
}
