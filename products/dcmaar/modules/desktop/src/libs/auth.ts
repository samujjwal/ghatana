/**
 * DEPRECATED: This file is kept for backward compatibility only
 * Auth system has been migrated to Jotai atoms
 * 
 * @see src/atoms/authAtoms.ts - Atom definitions
 * @see src/hooks/useStores.ts - Backward-compatible hooks
 */

export { useAuthStore, useLegacyPermissions as usePermissions } from '../hooks/useStores';
export type { 
  AuthUser,
  PermissionEntry,
  PermissionKey
} from '../atoms/authAtoms';
