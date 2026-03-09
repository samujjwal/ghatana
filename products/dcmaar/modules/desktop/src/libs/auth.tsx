/**
 * DEPRECATED: This file is kept for backward compatibility only
 * Auth system has been migrated to Jotai atoms
 * 
 * @see src/atoms/authAtoms.ts - Atom definitions with RBAC
 * @see src/hooks/useStores.ts - Backward-compatible hooks (useAuthStore, usePermissions, useSession)
 */

import React from 'react';

// Re-export hooks from new implementation
export { 
  useAuthStore, 
  usePermissions, 
  useSession 
} from '../hooks/useStores';

// Re-export types
export type { 
  User,
  Role, 
  Permission,
  ROLE_PERMISSIONS
} from '../atoms/authAtoms';

// Protected Route HOC (unchanged - uses new hooks internally)
export function withAuth<P extends object>(
  Component: React.ComponentType<P>,
  requiredPermission?: import('../atoms/authAtoms').Permission
) {
  return function ProtectedComponent(props: P) {
    const { usePermissions } = require('../hooks/useStores');
    const { isAuthenticated, hasPermission } = usePermissions();

    if (!isAuthenticated) {
      return <div>Please log in to access this feature.</div>;
    }

    if (requiredPermission && !hasPermission(requiredPermission)) {
      return <div>You don't have permission to access this feature.</div>;
    }

    return <Component {...props} />;
  };
}
