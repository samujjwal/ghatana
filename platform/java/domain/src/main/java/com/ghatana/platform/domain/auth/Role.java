/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;

/**
 * Immutable value object representing an RBAC role.
 *
 * <p>Roles group permissions together for easier user assignment.
 * While any string is valid, conventional names include ADMIN, USER,
 * OWNER, EDITOR, VIEWER, and SERVICE_ACCOUNT.
 *
 * @doc.type class
 * @doc.purpose RBAC role value object
 * @doc.layer core
 * @doc.pattern Value Object
 * @see Permission
 */
public final class Role {

    /** Full platform access, can manage users/roles. */
    public static final Role ADMIN = new Role("ADMIN");
    /** Standard user access. */
    public static final Role USER = new Role("USER");
    /** Owns resources, can manage access to owned items. */
    public static final Role OWNER = new Role("OWNER");
    /** Can create/edit content. */
    public static final Role EDITOR = new Role("EDITOR");
    /** Read-only access. */
    public static final Role VIEWER = new Role("VIEWER");
    /** For application-to-application access. */
    public static final Role SERVICE_ACCOUNT = new Role("SERVICE_ACCOUNT");

    private final String name;

    /**
     * Creates a role with the given name.
     *
     * @param name role name, must not be null or blank
     */
    public Role(String name) {
        this.name = Objects.requireNonNull(name, "role name cannot be null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("role name cannot be empty");
        }
    }

    /** @return the role name */
    public String getName() { return name; }

    /** @return true if this is the ADMIN role */
    public boolean isAdmin() { return "ADMIN".equals(name); }

    /** @return true if this is the OWNER role */
    public boolean isOwner() { return "OWNER".equals(name); }

    /** @return true if this is the VIEWER role */
    public boolean isViewer() { return "VIEWER".equals(name); }

    /** @return true if this is the SERVICE_ACCOUNT role */
    public boolean isServiceAccount() { return "SERVICE_ACCOUNT".equals(name); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role role)) return false;
        return Objects.equals(name, role.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name); }

    @Override
    public String toString() { return name; }
}
