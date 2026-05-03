package com.ghatana.platform.security.annotation;

import com.ghatana.platform.governance.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility for checking method-level security annotations.
 *
 * <p>This class provides methods to verify that a Principal has the required
 * roles or permissions as specified by security annotations on a method or class.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * MethodSecurityChecker checker = new MethodSecurityChecker(principal);
 * boolean allowed = checker.checkAccess(handler.getClass(), method);
 * }</pre>
 *
 * <p><b>Supported Annotations:</b></p>
 * <ul>
 *   <li>{@link Secured} - Requires authentication only</li>
 *   <li>{@link RequiresRole} - Requires specific role(s)</li>
 *   <li>{@link RequiresPermission} - Requires specific permission(s)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Utility for checking method-level security annotations
 * @doc.layer security
 * @doc.pattern Utility
 */
public class MethodSecurityChecker {

    private static final Logger log = LoggerFactory.getLogger(MethodSecurityChecker.class);

    private final Principal principal;

    /**
     * Creates a security checker for the given principal.
     *
     * @param principal the principal to check (required)
     * @throws NullPointerException if principal is null
     */
    public MethodSecurityChecker(Principal principal) {
        this.principal = principal;
    }

    /**
     * Checks if the principal has access to the given method based on security annotations.
     *
     * <p>This method checks annotations on both the method and the declaring class.
     * If either the method or class has security annotations, they must all be satisfied.</p>
     *
     * @param targetClass the class declaring the method (required)
     * @param method the method to check (required)
     * @return true if the principal has access, false otherwise
     * @throws NullPointerException if targetClass or method is null
     */
    public boolean checkAccess(Class<?> targetClass, Method method) {
        // Check class-level annotations
        if (!checkClassLevelAccess(targetClass)) {
            return false;
        }

        // Check method-level annotations
        return checkMethodLevelAccess(method);
    }

    /**
     * Checks if the principal has access to the given class based on security annotations.
     *
     * @param targetClass the class to check (required)
     * @return true if the principal has access, false otherwise
     * @throws NullPointerException if targetClass is null
     */
    public boolean checkClassLevelAccess(Class<?> targetClass) {
        // Check @Secured annotation
        if (targetClass.isAnnotationPresent(Secured.class)) {
            if (principal == null) {
                log.debug("Access denied to class {}: @Secured requires authentication", targetClass.getSimpleName());
                return false;
            }
            log.debug("Access granted to class {}: @Secured satisfied", targetClass.getSimpleName());
        }

        // Check @RequiresRole annotation
        RequiresRole classRoleAnnotation = targetClass.getAnnotation(RequiresRole.class);
        if (classRoleAnnotation != null) {
            return checkRoleAnnotation(classRoleAnnotation, targetClass.getSimpleName());
        }

        // Check @RequiresPermission annotation
        RequiresPermission classPermissionAnnotation = targetClass.getAnnotation(RequiresPermission.class);
        if (classPermissionAnnotation != null) {
            return checkPermissionAnnotation(classPermissionAnnotation, targetClass.getSimpleName());
        }

        return true;
    }

    /**
     * Checks if the principal has access to the given method based on security annotations.
     *
     * @param method the method to check (required)
     * @return true if the principal has access, false otherwise
     * @throws NullPointerException if method is null
     */
    public boolean checkMethodLevelAccess(Method method) {
        // Check @Secured annotation
        if (method.isAnnotationPresent(Secured.class)) {
            if (principal == null) {
                log.debug("Access denied to method {}: @Secured requires authentication", method.getName());
                return false;
            }
            log.debug("Access granted to method {}: @Secured satisfied", method.getName());
        }

        // Check @RequiresRole annotation
        RequiresRole methodRoleAnnotation = method.getAnnotation(RequiresRole.class);
        if (methodRoleAnnotation != null) {
            return checkRoleAnnotation(methodRoleAnnotation, method.getName());
        }

        // Check @RequiresPermission annotation
        RequiresPermission methodPermissionAnnotation = method.getAnnotation(RequiresPermission.class);
        if (methodPermissionAnnotation != null) {
            return checkPermissionAnnotation(methodPermissionAnnotation, method.getName());
        }

        return true;
    }

    private boolean checkRoleAnnotation(RequiresRole annotation, String context) {
        if (principal == null) {
            log.debug("Access denied to {}: @RequiresRole requires authentication", context);
            return false;
        }

        String[] requiredRoles = annotation.value();
        boolean requireAll = annotation.requireAll();

        List<String> rolesList = principal.getRoles();
        Set<String> principalRoles = rolesList != null 
            ? rolesList.stream().map(String::toUpperCase).collect(java.util.stream.Collectors.toSet())
            : Set.of();
        if (principalRoles.isEmpty()) {
            log.debug("Access denied to {}: principal has no roles", context);
            return false;
        }

        boolean hasAccess;
        if (requireAll) {
            // AND semantics: must have ALL roles
            hasAccess = Arrays.stream(requiredRoles)
                .allMatch(role -> principalRoles.contains(role.toUpperCase()));
            if (hasAccess) {
                log.debug("Access granted to {}: principal has all required roles {}", context, Arrays.toString(requiredRoles));
            } else {
                log.debug("Access denied to {}: principal missing required roles (has: {}, needs: {})",
                    context, principalRoles, Arrays.toString(requiredRoles));
            }
        } else {
            // OR semantics: must have AT LEAST ONE role
            hasAccess = Arrays.stream(requiredRoles)
                .anyMatch(role -> principalRoles.contains(role.toUpperCase()));
            if (hasAccess) {
                log.debug("Access granted to {}: principal has at least one required role from {}", context, Arrays.toString(requiredRoles));
            } else {
                log.debug("Access denied to {}: principal missing all required roles (has: {}, needs: {})",
                    context, principalRoles, Arrays.toString(requiredRoles));
            }
        }

        return hasAccess;
    }

    private boolean checkPermissionAnnotation(RequiresPermission annotation, String context) {
        if (principal == null) {
            log.debug("Access denied to {}: @RequiresPermission requires authentication", context);
            return false;
        }

        String value = annotation.value();
        String[] anyOf = annotation.anyOf();
        boolean requireAll = annotation.requireAll();

        // Build list of permissions to check
        String[] permissionsToCheck;
        if (anyOf != null && anyOf.length > 0) {
            permissionsToCheck = anyOf;
        } else if (value != null && !value.isEmpty()) {
            permissionsToCheck = new String[]{value};
        } else {
            log.debug("Access denied to {}: @RequiresPermission has no permissions specified", context);
            return false;
        }

        // Permission checking uses roles-as-permissions: a principal is granted a permission
        // if their role set contains the permission name (role parity).
        // Full AccessControlService integration can be wired via constructor if needed.
        Set<String> principalRoles = principal.getRoles().stream()
            .map(String::toUpperCase)
            .collect(java.util.stream.Collectors.toSet());
        boolean hasAccess;
        if (requireAll && permissionsToCheck.length > 1) {
            hasAccess = Arrays.stream(permissionsToCheck)
                .allMatch(p -> principalRoles.contains(p.toUpperCase()));
        } else {
            hasAccess = Arrays.stream(permissionsToCheck)
                .anyMatch(p -> principalRoles.contains(p.toUpperCase()));
        }
        if (!hasAccess) {
            log.debug("Access denied to {}: principal missing required permissions {} (has roles: {})",
                context, Arrays.toString(permissionsToCheck), principalRoles);
        }
        return hasAccess;
    }
}
