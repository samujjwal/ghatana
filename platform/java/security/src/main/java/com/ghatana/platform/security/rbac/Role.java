package com.ghatana.platform.security.rbac;

/**
 * Standard role name constants for RBAC filter/annotation infrastructure.
 *
 * <p><b>Deprecation notice:</b> New code should use the canonical domain value-object
 * {@link com.ghatana.platform.domain.auth.Role}, which is a proper typed class supporting
 * equality, hashing, and additional role metadata (e.g. {@code isAdmin()}, {@code isOwner()}).
 * This utility class is retained only for annotation processors and legacy string-based
 * RBAC checks that cannot easily accept typed objects.
 *
 * <p>Standard role name constants defined here are aligned with the domain constants in
 * {@link com.ghatana.platform.domain.auth.Role}:
 * <ul>
 *   <li>{@link #ADMIN} = {@code "ADMIN"} → {@code com.ghatana.platform.domain.auth.Role.ADMIN}</li>
 *   <li>{@link #USER}  = {@code "USER"}  → {@code com.ghatana.platform.domain.auth.Role.USER}</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Standard role-name constants for string-based RBAC infrastructure
 * @doc.layer platform
 * @doc.pattern Constants
 * @see com.ghatana.platform.domain.auth.Role
 * @deprecated Prefer {@link com.ghatana.platform.domain.auth.Role} typed value objects.
 *             This class is kept for annotation/filter infrastructure only.
 */
@Deprecated
public final class Role {
    /**
     * System administrator with full access to all resources.
     * @see com.ghatana.platform.domain.auth.Role#ADMIN
     */
    public static final String ADMIN = "ADMIN";

    /**
     * Regular authenticated user with standard permissions.
     * @see com.ghatana.platform.domain.auth.Role#USER
     */
    public static final String USER = "USER";

    /**
     * Guest user with limited read-only access.
     */
    public static final String GUEST = "guest";

    /**
     * Service account for system-to-system communication.
     * @see com.ghatana.platform.domain.auth.Role#SERVICE_ACCOUNT
     */
    public static final String SERVICE = "service";

    /**
     * Read-only access to resources.
     * @see com.ghatana.platform.domain.auth.Role#VIEWER
     */
    public static final String READER = "reader";

    /**
     * Read and write access to resources.
     * @see com.ghatana.platform.domain.auth.Role#EDITOR
     */
    public static final String WRITER = "writer";

    /**
     * Auditor with read access to audit logs and security events.
     */
    public static final String AUDITOR = "auditor";

    /**
     * Developer with elevated permissions for development and testing.
     */
    public static final String DEVELOPER = "developer";

    private Role() {
        // Prevent instantiation — use com.ghatana.platform.domain.auth.Role for typed instances
    }

    /**
     * Checks if the given role name is one of the standard system roles.
     *
     * @param role the role name to check, may be null
     * @return {@code true} if the role is a recognised standard system role
     */
    public static boolean isValidRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return false;
        }
        return role.equalsIgnoreCase(ADMIN) ||
               role.equalsIgnoreCase(USER) ||
               role.equalsIgnoreCase(GUEST) ||
               role.equalsIgnoreCase(SERVICE) ||
               role.equalsIgnoreCase(READER) ||
               role.equalsIgnoreCase(WRITER) ||
               role.equalsIgnoreCase(AUDITOR) ||
               role.equalsIgnoreCase(DEVELOPER);
    }

    /**
     * Passthrough helper retained for method-reference compatibility with
     * {@code Stream.map(Role::name)} on {@code Stream<String>}.
     *
     * @param role the role name string
     * @return the same role name string
     * @deprecated Use {@link com.ghatana.platform.domain.auth.Role#getName()} on a typed role object
     */
    @Deprecated
    public static String name(String role) {
        return role;
    }
}
