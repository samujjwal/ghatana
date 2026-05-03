/**
 * Role-based authorization utilities for DMOS UI.
 *
 * @doc.type utils
 * @doc.purpose Frontend role helpers for approval authorization
 * @doc.layer frontend
 */

import type { ApprovalRecordResponse } from '@/types/approval';

// DMOS-P1-12: Valid role constants
export const VALID_ROLES = [
  'brand-manager',
  'marketing-director',
  'exec-sponsor',
  'admin',
  'viewer',
] as const;

export type ValidRole = typeof VALID_ROLES[number];

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

/**
 * Checks if the user has the required role to approve a given approval.
 *
 * <p>Role hierarchy (least to most privileged):</p>
 * <ul>
 *   <li>viewer → view-only, no approval rights</li>
 *   <li>brand-manager → can approve brand-manager level approvals</li>
 *   <li>marketing-director → can approve medium-risk approvals</li>
 *   <li>exec-sponsor → can approve override/high-risk approvals</li>
 *   <li>admin → can approve any approval</li>
 * </ul>
 *
 * @param roles the user's roles from auth context
 * @param approval the approval record being considered
 * @returns true if the user has sufficient role to approve this approval
 */
export function canApprove(roles: string[], approval: ApprovalRecordResponse | null): boolean {
  if (!approval || roles.length === 0) {
    return false;
  }

  const requiredRole = approval.requiredApproverRole.toLowerCase();
  const normalizedRoles = normalizeRoles(roles);
  
  // Admin can approve anything
  if (normalizedRoles.includes('admin')) {
    return true;
  }

  return normalizedRoles.some(role => role === requiredRole);
}

/**
 * Checks if the user has any approver role.
 *
 * @param roles the user's roles from auth context
 * @returns true if the user has any approver-related role
 */
export function hasApproverRole(roles: string[]): boolean {
  if (roles.length === 0) {
    return false;
  }
  const approverRoles = ['brand-manager', 'marketing-director', 'exec-sponsor', 'admin'];
  const normalizedRoles = normalizeRoles(roles);
  return normalizedRoles.some(role => approverRoles.includes(role));
}
