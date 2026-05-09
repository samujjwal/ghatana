/**
 * Role-based authorization utilities for DMOS UI.
 *
 * @doc.type utils
 * @doc.purpose Frontend role helpers for approval authorization
 * @doc.layer frontend
 */

// DMOS-P1-12: Valid role constants
export const VALID_ROLES = [
  'brand-manager',
  'marketing-director',
  'exec-sponsor',
  'admin',
  'viewer',
] as const;

export type ValidRole = typeof VALID_ROLES[number];

export const ROLE_ORDER: Readonly<Record<ValidRole, number>> = {
  viewer: 0,
  'brand-manager': 1,
  'marketing-director': 2,
  'exec-sponsor': 3,
  admin: 4,
};

const DEFAULT_ROLE: ValidRole = 'viewer';

/**
 * Validates that all roles are from the known set of valid roles.
 *
 * @param roles the roles to validate
 * @returns true if all roles are valid, false otherwise
 */
export function validateRoles(roles: string[]): boolean {
  if (roles.length === 0) {
    return false; // Empty roles are invalid per DMOS-P1-12
  }
  return roles.every(role => VALID_ROLES.includes(role as ValidRole));
}

/**
 * Normalizes and filters roles to only include valid roles.
 *
 * @param roles the roles to normalize
 * @returns array of valid, normalized roles (lowercase)
 */
export function normalizeRoles(roles: string[]): string[] {
  return roles
    .map(role => role.toLowerCase().trim())
    .filter(role => VALID_ROLES.includes(role as ValidRole));
}

export function getHighestRole(roles: readonly string[]): ValidRole {
  const normalized = normalizeRoles([...roles]) as ValidRole[];
  if (normalized.length === 0) {
    return DEFAULT_ROLE;
  }

  return normalized.reduce<ValidRole>((highest, candidate) => (
    ROLE_ORDER[candidate] > ROLE_ORDER[highest] ? candidate : highest
  ), DEFAULT_ROLE);
}

export function hasMinimumRole(roles: readonly string[], minimumRole: ValidRole): boolean {
  const highestRole = getHighestRole(roles);
  return ROLE_ORDER[highestRole] >= ROLE_ORDER[minimumRole];
}
