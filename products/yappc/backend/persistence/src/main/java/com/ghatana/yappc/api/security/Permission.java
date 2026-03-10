/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - Permission
 * 
 * Represents a permission with path pattern and allowed HTTP methods.
 * Used for role-based access control and fine-grained permissions.
 */

package com.ghatana.yappc.api.security;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

/**
 * Permission definition for access control.
 * 
 * A permission consists of:
 * - A path pattern (supports wildcards and /** suffix)
 * - A list of allowed HTTP methods
 * - An optional description
  *
 * @doc.type class
 * @doc.purpose permission
 * @doc.layer product
 * @doc.pattern Service
 */
public class Permission {
    
    private final String pathPattern;
    private final List<String> methods;
    private final String description;
    
    public Permission(@NotNull String pathPattern, 
                      @NotNull List<String> methods,
                      @NotNull String description) {
        this.pathPattern = Objects.requireNonNull(pathPattern, "pathPattern");
        this.methods = List.copyOf(Objects.requireNonNull(methods, "methods"));
        this.description = Objects.requireNonNull(description, "description");
    }
    
    public Permission(@NotNull String pathPattern, @NotNull List<String> methods) {
        this(pathPattern, methods, "");
    }
    
    /**
     * Gets the path pattern for this permission.
     */
    @NotNull
    public String getPathPattern() {
        return pathPattern;
    }
    
    /**
     * Gets the list of allowed HTTP methods.
     */
    @NotNull
    public List<String> getMethods() {
        return methods;
    }
    
    /**
     * Gets the description of this permission.
     */
    @NotNull
    public String getDescription() {
        return description;
    }
    
    /**
     * Checks if this permission allows the given HTTP method.
     */
    public boolean allowsMethod(@NotNull String method) {
        return methods.isEmpty() || methods.contains(method);
    }
    
    /**
     * Checks if this permission matches the given path.
     */
    public boolean matchesPath(@NotNull String path) {
        if (pathPattern.endsWith("/**")) {
            String prefix = pathPattern.substring(0, pathPattern.length() - 3);
            return path.startsWith(prefix);
        }
        return path.equals(pathPattern) || path.matches(pathPattern.replace("*", ".*"));
    }
    
    /**
     * Checks if this permission allows access to the given path and method.
     */
    public boolean allows(@NotNull String path, @NotNull String method) {
        return matchesPath(path) && allowsMethod(method);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return pathPattern.equals(that.pathPattern) && 
               methods.equals(that.methods);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(pathPattern, methods);
    }
    
    @Override
    public String toString() {
        return "Permission{" +
                "pathPattern='" + pathPattern + '\'' +
                ", methods=" + methods +
                ", description='" + description + '\'' +
                '}';
    }
    
    /**
     * Creates a permission for all methods on a path.
     */
    public static Permission allMethods(@NotNull String pathPattern) {
        return new Permission(pathPattern, List.of());
    }
    
    /**
     * Creates a permission for specific methods on a path.
     */
    public static Permission methods(@NotNull String pathPattern, @NotNull String... methods) {
        return new Permission(pathPattern, List.of(methods));
    }
    
    /**
     * Creates a read-only permission.
     */
    public static Permission readOnly(@NotNull String pathPattern) {
        return new Permission(pathPattern, List.of("GET", "HEAD", "OPTIONS"));
    }
    
    /**
     * Creates a write-only permission.
     */
    public static Permission writeOnly(@NotNull String pathPattern) {
        return new Permission(pathPattern, List.of("POST", "PUT", "PATCH", "DELETE"));
    }
    
    /**
     * Creates a full CRUD permission.
     */
    public static Permission crud(@NotNull String pathPattern) {
        return new Permission(pathPattern, List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"));
    }
}
