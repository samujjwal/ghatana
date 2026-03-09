package com.ghatana.security.config;

import javax.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to mark security-related configuration bindings.
 * This can be used to disambiguate between different Config instances
 * when multiple are available in the dependency graph.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Qualifier
public @interface SecurityConfig {
    // Marker annotation for security configuration
}
