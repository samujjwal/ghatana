/**
 * Role-based authorization utilities for DMOS UI.
 *
 * @doc.type utils
 * @doc.purpose Frontend role helpers for approval authorization
 * @doc.layer frontend
 */

import type { ApprovalRecordResponse } from '@/types/approval';

/**
 * Checks if the user has the required role to approve a given approval.
 *
 * <p>Role hierarchy (least to most privileged):</p>
 * <ul>
 *   <li>None (no roles) → view-only</li>
 *   <li>brand-manager → can approve brand-manager level approvals</li>
 *   <li>marketing-director → can approve medium-risk approvals</li>
 *   <li>exec-sponsor → can approve override/high-risk approvals</li>
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
  return roles.some(role => role.toLowerCase() === requiredRole);
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
  return roles.some(role => approverRoles.includes(role.toLowerCase()));
}
