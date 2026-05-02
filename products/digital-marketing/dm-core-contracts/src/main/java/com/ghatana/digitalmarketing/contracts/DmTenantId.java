package com.ghatana.digitalmarketing.contracts;

import java.util.Objects;
import java.util.UUID;

/**
 * Typed wrapper for a DMOS tenant identifier.
 *
 * <p>Every tenant-scoped DMOS operation requires a non-blank {@code TenantId}. This
 * value object prevents accidental confusion between tenant, workspace, and other
 * string identifiers by enforcing type safety at compile time.</p>
 *
 * <p>Wraps the kernel-level {@link com.ghatana.platform.domain.auth.TenantId} concept
 * in a product-local type so DMOS code does not scatter raw strings.</p>
 *
 * @doc.type class
 * @doc.purpose Typed tenant identifier value object for DMOS operations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class DmTenantId {

    private final String value;

    private DmTenantId(String value) {
        this.value = value;
    }

    /**
     * Creates a {@code DmTenantId} from a raw string.
     *
     * @param value the tenant ID string; must not be blank
     * @throws IllegalArgumentException if {@code value} is null or blank
     */
    public static DmTenantId of(String value) {
        Objects.requireNonNull(value, "tenantId must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        return new DmTenantId(value);
    }

    /** Returns the raw string value. Never {@code null} or blank. */
    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return value.equals(((DmTenantId) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "DmTenantId{" + value + '}';
    }
}
