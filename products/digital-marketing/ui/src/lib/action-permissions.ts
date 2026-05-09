/**
 * Canonical DMOS action-to-role permissions.
 *
 * Mirrors route-manifest action keys and augments page-level mutation actions
 * so UI controls can enforce action-level permissions consistently.
 *
 * @doc.type utils
 * @doc.purpose Action-level permission checks for DMOS UI controls
 * @doc.layer frontend
 */
import { hasMinimumRole, type ValidRole } from '@/lib/role-utils';

export const ACTION_MINIMUM_ROLES = {
  'view-dashboard': 'viewer',
  'review-approval': 'viewer',
  'approve': 'brand-manager',
  'reject': 'brand-manager',
  'view-audit-log': 'viewer',
  'launch-campaign': 'brand-manager',
  'submit-strategy': 'brand-manager',
  'approve-strategy': 'marketing-director',
  'submit-budget': 'marketing-director',
  'approve-budget': 'exec-sponsor',
  'view-funnel': 'brand-manager',
  'view-attribution': 'brand-manager',
  'view-roi': 'marketing-director',
  'view-recommendations': 'brand-manager',
  'approve-optimizations': 'marketing-director',
  'manage-funnel': 'brand-manager',
  'view-research': 'brand-manager',
  'manage-channels': 'marketing-director',
  'manage-locales': 'brand-manager',
  'manage-agency': 'admin',

  // Page-level mutations not explicitly listed in route-manifest actions.
  'create-campaign': 'brand-manager',
  'pause-campaign': 'brand-manager',
  'complete-campaign': 'brand-manager',
  'archive-campaign': 'brand-manager',
  'rollback-campaign': 'brand-manager',
  'duplicate-campaign': 'brand-manager',
  'generate-strategy': 'brand-manager',
  'generate-budget': 'marketing-director',
} as const satisfies Record<string, ValidRole>;

export type DmosAction = keyof typeof ACTION_MINIMUM_ROLES;

export function canPerformAction(roles: readonly string[], action: DmosAction): boolean {
  return hasMinimumRole(roles, ACTION_MINIMUM_ROLES[action]);
}
