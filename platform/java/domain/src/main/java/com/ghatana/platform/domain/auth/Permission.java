/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.util.Objects;

/**
 * Immutable value object representing an RBAC permission.
 *
 * <p>Permissions follow a hierarchical naming pattern: {@code resource.action}
 * (e.g., {@code document.read}, {@code user.delete}, {@code role.manage}).
 *
 * @doc.type class
 * @doc.purpose RBAC permission value object
 * @doc.layer core
 * @doc.pattern Value Object
 * @see Role
 */
public final class Permission {

    // Document permissions
    public static final Permission DOCUMENT_READ = new Permission("document.read");
    public static final Permission DOCUMENT_WRITE = new Permission("document.write");
    public static final Permission DOCUMENT_DELETE = new Permission("document.delete");
    public static final Permission DOCUMENT_ALL = new Permission("document.*");

    // User permissions
    public static final Permission USER_READ = new Permission("user.read");
    public static final Permission USER_CREATE = new Permission("user.create");
    public static final Permission USER_UPDATE = new Permission("user.update");
    public static final Permission USER_DELETE = new Permission("user.delete");
    public static final Permission USER_ALL = new Permission("user.*");

    // Role permissions
    public static final Permission ROLE_MANAGE = new Permission("role.manage");
    public static final Permission ROLE_READ = new Permission("role.read");

    // Settings permissions
    public static final Permission SETTINGS_ADMIN = new Permission("settings.admin");

    private final String name;

    /**
     * Creates a permission with the given name.
     *
     * @param name permission name in {@code resource.action} format, must not be null or blank
     */
    public Permission(String name) {
        this.name = Objects.requireNonNull(name, "permission name cannot be null");
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("permission name cannot be empty");
        }
    }

    /** @return the permission name */
    public String getName() { return name; }

    /** @return the resource portion (before the first dot) */
    public String getResource() {
        int dotIndex = name.indexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }

    /** @return the action portion (after the first dot) */
    public String getAction() {
        int dotIndex = name.indexOf('.');
        return dotIndex > 0 ? name.substring(dotIndex + 1) : "";
    }

    /** @return true if this permission grants read access to the given resource */
    public boolean allowsRead(String resource) {
        return (resource + ".read").equals(name) || (resource + ".*").equals(name);
    }

    /** @return true if this permission grants write access to the given resource */
    public boolean allowsWrite(String resource) {
        return (resource + ".write").equals(name) || (resource + ".*").equals(name);
    }

    /** @return true if this permission grants delete access to the given resource */
    public boolean allowsDelete(String resource) {
        return (resource + ".delete").equals(name) || (resource + ".*").equals(name);
    }

    /** @return true if this is a wildcard permission for the given resource */
    public boolean allowsAll(String resource) {
        return (resource + ".*").equals(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission that)) return false;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() { return Objects.hash(name); }

    @Override
    public String toString() { return name; }
}
