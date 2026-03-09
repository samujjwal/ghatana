package com.ghatana.platform.security.rbac;

/**
 * Standard role definitions for the application.
 * These roles are used for role-based access control (RBAC).
 *
 * @doc.type class
 * @doc.purpose Standard role constant definitions for RBAC
 * @doc.layer security
 * @doc.pattern ValueObject
 */
public final class Role {
    /**
     * System administrator with full access to all resources.
     */
    public static final String ADMIN = "admin";
    
    /**
     * Regular authenticated user with standard permissions.
     */
    public static final String USER = "user";
    
    /**
     * Guest user with limited read-only access.
     */
    public static final String GUEST = "guest";
    
    /**
     * Service account for system-to-system communication.
     */
    public static final String SERVICE = "service";
    
    /**
     * Read-only access to resources.
     */
    public static final String READER = "reader";
    
    /**
     * Read and write access to resources.
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
        // Prevent instantiation
    }
    
    /**
     * Checks if the given role is a valid system role.
     *
     * @param role the role to check
     * @return true if the role is a valid system role, false otherwise
     */
    public static boolean isValidRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return false;
        }
        
        return role.equals(ADMIN) ||
               role.equals(USER) ||
               role.equals(GUEST) ||
               role.equals(SERVICE) ||
               role.equals(READER) ||
               role.equals(WRITER) ||
               role.equals(AUDITOR) ||
               role.equals(DEVELOPER);
    }
    
    public static String name(String role) {
        return role; // Since roles are already strings, just return the input
    }
}
