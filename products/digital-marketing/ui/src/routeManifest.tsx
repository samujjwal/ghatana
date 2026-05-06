import React from 'react';
import type { ProductRouteCapability } from '@ghatana/product-shell';
import { DashboardPage } from '@/pages/DashboardPage';
import { ApprovalQueuePage } from '@/pages/ApprovalQueuePage';
import { ApprovalDetailPage } from '@/pages/ApprovalDetailPage';
import { AiActionLogPage } from '@/pages/AiActionLogPage';
import { CampaignsPage } from '@/pages/CampaignsPage';
import { StrategyPage } from '@/pages/StrategyPage';
import { BudgetPage } from '@/pages/BudgetPage';
import { VALID_ROLES, type ValidRole, normalizeRoles } from '@/lib/role-utils';

export interface DmosRouteManifestEntry extends ProductRouteCapability {
  readonly element: React.ReactElement;
  readonly capabilityKey?: string;
}

export const DMOS_ROLE_ORDER: Readonly<Record<ValidRole, number>> = {
  viewer: 0,
  'brand-manager': 1,
  'marketing-director': 2,
  'exec-sponsor': 3,
  admin: 4,
};

const DEFAULT_AUTHENTICATED_ROLE: ValidRole = 'viewer';

function getHighestRole(roles: readonly string[]): ValidRole {
  const normalized = normalizeRoles([...roles]);
  if (normalized.length === 0) {
    return DEFAULT_AUTHENTICATED_ROLE;
  }

  return normalized.reduce<ValidRole>((highest, candidate) => {
    const role = candidate as ValidRole;
    return DMOS_ROLE_ORDER[role] > DMOS_ROLE_ORDER[highest] ? role : highest;
  }, DEFAULT_AUTHENTICATED_ROLE);
}

export function getHighestDmosRole(roles: readonly string[]): ValidRole {
  return getHighestRole(roles);
}

export function isRouteAllowedForRoles(
  route: Pick<DmosRouteManifestEntry, 'minimumRole'>,
  roles: readonly string[],
): boolean {
  if (!route.minimumRole) {
    return true;
  }

  const currentRole = getHighestRole(roles);
  return DMOS_ROLE_ORDER[currentRole] >= DMOS_ROLE_ORDER[route.minimumRole as ValidRole];
}

export function resolveDmosRoutePath(path: string, workspaceId: string | null | undefined): string {
  const resolvedWorkspaceId = workspaceId?.trim() || 'workspace';
  return path.replaceAll(':workspaceId', resolvedWorkspaceId);
}

export const dmosRouteManifest: readonly DmosRouteManifestEntry[] = [
  {
    path: '/workspaces/:workspaceId/dashboard',
    label: 'Dashboard',
    description: 'Workspace status, approvals, and launch readiness.',
    group: 'Overview',
    minimumRole: 'viewer',
    personas: ['analyst', 'approver', 'executive'],
    tiers: ['core'],
    actions: ['view-dashboard'],
    cards: ['launch-readiness', 'approval-queue', 'workflow-health'],
    iconName: 'layout-dashboard',
    lifecycle: 'stable',
    element: <DashboardPage />,
  },
  {
    path: '/workspaces/:workspaceId/approvals',
    label: 'Approvals',
    description: 'Pending approvals and decision workflow queue.',
    group: 'Governance',
    minimumRole: 'viewer',
    personas: ['approver', 'executive'],
    tiers: ['core'],
    actions: ['review-approval'],
    cards: ['pending-approvals', 'decision-sla'],
    iconName: 'shield-check',
    lifecycle: 'stable',
    element: <ApprovalQueuePage />,
  },
  {
    path: '/workspaces/:workspaceId/approvals/:requestId',
    label: 'Approval Detail',
    description: 'Request detail, snapshot review, and decision flow.',
    group: 'Governance',
    minimumRole: 'viewer',
    personas: ['approver', 'executive'],
    tiers: ['core'],
    actions: ['approve', 'reject'],
    cards: ['approval-context', 'evidence-snapshot'],
    iconName: 'file-search',
    lifecycle: 'stable',
    discoverable: false,
    element: <ApprovalDetailPage />,
  },
  {
    path: '/workspaces/:workspaceId/ai-actions',
    label: 'AI Action Log',
    description: 'Traceable AI decision and recommendation history.',
    group: 'Governance',
    minimumRole: 'viewer',
    personas: ['analyst', 'approver', 'executive'],
    tiers: ['core'],
    actions: ['view-audit-log'],
    cards: ['recent-ai-actions', 'audit-filters'],
    iconName: 'sparkles',
    lifecycle: 'stable',
    element: <AiActionLogPage />,
  },
  {
    path: '/workspaces/:workspaceId/ai-actions/:actionId',
    label: 'AI Action Detail',
    description: 'Single AI action evidence and approval trail.',
    group: 'Governance',
    minimumRole: 'viewer',
    personas: ['analyst', 'approver', 'executive'],
    tiers: ['core'],
    actions: ['view-audit-log'],
    cards: ['action-evidence', 'approval-trace'],
    iconName: 'sparkles',
    lifecycle: 'stable',
    discoverable: false,
    element: <AiActionLogPage />,
  },
  {
    path: '/workspaces/:workspaceId/campaigns',
    label: 'Campaigns',
    description: 'Campaign planning and orchestration.',
    group: 'Execution',
    minimumRole: 'brand-manager',
    personas: ['planner', 'approver'],
    tiers: ['growth'],
    actions: ['launch-campaign'],
    cards: ['campaign-summary', 'launch-checklist'],
    iconName: 'megaphone',
    lifecycle: 'stable',
    capabilityKey: 'dmos.campaigns',
    element: <CampaignsPage />,
  },
  {
    path: '/workspaces/:workspaceId/strategy',
    label: 'Strategy',
    description: 'Strategy generation, review, and approvals.',
    group: 'Execution',
    minimumRole: 'brand-manager',
    personas: ['planner', 'approver'],
    tiers: ['growth'],
    actions: ['submit-strategy', 'approve-strategy'],
    cards: ['strategy-brief', 'approval-dependencies'],
    iconName: 'target',
    lifecycle: 'stable',
    capabilityKey: 'dmos.strategy',
    element: <StrategyPage />,
  },
  {
    path: '/workspaces/:workspaceId/budget',
    label: 'Budget',
    description: 'Budget recommendations and approval decisions.',
    group: 'Execution',
    minimumRole: 'marketing-director',
    personas: ['approver', 'executive'],
    tiers: ['growth'],
    actions: ['submit-budget', 'approve-budget'],
    cards: ['budget-recommendations', 'approval-readiness'],
    iconName: 'wallet',
    lifecycle: 'stable',
    capabilityKey: 'dmos.budget',
    element: <BudgetPage />,
  },
];
