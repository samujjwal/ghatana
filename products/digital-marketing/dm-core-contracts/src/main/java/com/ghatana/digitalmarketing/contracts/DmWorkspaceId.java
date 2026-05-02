package com.ghatana.digitalmarketing.contracts;

import java.util.Objects;

/**
 * Typed wrapper for a DMOS workspace identifier.
 *
 * <p>Workspaces are the primary multi-tenancy boundary for DMOS business accounts.
 * A tenant may own one or more workspaces. All campaign, brand, contact, content,
 * and connector operations are scoped to a workspace.</p>
 *
 * @doc.type class
 * @doc.purpose Typed workspace identifier value object for DMOS operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class DmWorkspaceId {

    private final String value;

    private DmWorkspaceId(String value) {
        this.value = value;
    }

    /**
     * Creates a {@code DmWorkspaceId} from a raw string.
     *
     * @param value the workspace ID string; must not be blank
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public static DmWorkspaceId of(String value) {
        Objects.requireNonNull(value, "workspaceId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        return new DmWorkspaceId(value);
    }

    /** Returns the raw string value. Never {@code null} or blank. */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return value.equals(((DmWorkspaceId) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "DmWorkspaceId{" + value + '}';
    }
}
