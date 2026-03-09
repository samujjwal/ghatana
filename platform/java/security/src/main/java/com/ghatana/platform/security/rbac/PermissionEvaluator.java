package com.ghatana.platform.security.rbac;

import java.util.Set;
/**
 * Permission evaluator.
 *
 * @doc.type interface
 * @doc.purpose Permission evaluator
 * @doc.layer core
 * @doc.pattern Interface
 */

public interface PermissionEvaluator {
    boolean hasPermission(String role, String permission);
    boolean hasPermission(String userId, Set<String> roles, String permission);
}
