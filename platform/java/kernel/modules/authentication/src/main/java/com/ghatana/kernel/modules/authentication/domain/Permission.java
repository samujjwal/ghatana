/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.domain;

import java.util.Objects;

/**
 * Generic permission definition.
 *
 * <p>Contains permission information in a product-agnostic format.
 * Permissions are used to control access to resources and actions.</p>
 *
 * @doc.type class
 * @doc.purpose Generic permission definition - resource, action, conditions
 * @doc.layer kernel
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class Permission {

    private final String resource;
    private final String action;
    private final String condition;

    private Permission(Builder builder) {
        this.resource = Objects.requireNonNull(builder.resource, "resource");
        this.action = Objects.requireNonNull(builder.action, "action");
        this.condition = Objects.requireNonNullElse(builder.condition, "");
    }

    /**
     * Gets the resource this permission applies to.
     *
     * @return the resource
     */
    public String getResource() {
        return resource;
    }

    /**
     * Gets the action this permission allows.
     *
     * @return the action
     */
    public String getAction() {
        return action;
    }

    /**
     * Gets the condition for this permission.
     *
     * @return the condition (may be empty)
     */
    public String getCondition() {
        return condition;
    }

    /**
     * Checks if this permission matches a resource and action.
     *
     * @param resource the resource to check
     * @param action   the action to check
     * @return true if this permission matches
     */
    public boolean matches(String resource, String action) {
        boolean resourceMatches = this.resource.equals("*") || this.resource.equals(resource);
        boolean actionMatches = this.action.equals("*") || this.action.equals(action);
        
        return resourceMatches && actionMatches;
    }

    /**
     * Checks if this permission matches with conditions.
     *
     * @param resource  the resource to check
     * @param action    the action to check
     * @param condition the condition to check
     * @return true if this permission matches with conditions
     */
    public boolean matches(String resource, String action, String condition) {
        return matches(resource, action) && 
               (this.condition.isEmpty() || this.condition.equals(condition));
    }

    /**
     * Creates a new builder for Permission.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for Permission.
     */
    public static final class Builder {
        private String resource;
        private String action;
        private String condition;

        private Builder() {}

        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder condition(String condition) {
            this.condition = condition;
            return this;
        }

        public Permission build() {
            return new Permission(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Permission that = (Permission) o;
        return Objects.equals(resource, that.resource) &&
               Objects.equals(action, that.action) &&
               Objects.equals(condition, that.condition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, action, condition);
    }

    @Override
    public String toString() {
        if (condition.isEmpty()) {
            return String.format("Permission{resource='%s', action='%s'}", resource, action);
        } else {
            return String.format("Permission{resource='%s', action='%s', condition='%s'}", resource, action, condition);
        }
    }
}
