import type { ProductRouteCapability } from '@ghatana/product-shell';
import { normalizeRoles, type ValidRole } from '@/lib/role-utils';
import {
  dmosRouteManifest as generatedRouteManifest,
  DMOS_ROLE_ORDER,
  type DmosRouteManifestEntry,
} from '@/generated/routeManifest.generated';

export type { DmosRouteManifestEntry };
export { DMOS_ROLE_ORDER };

const DEFAULT_AUTHENTICATED_ROLE: ValidRole = 'viewer';

const ROUTE_METADATA: Readonly<Record<string, Pick<DmosRouteManifestEntry, 'personas' | 'tiers' | 'cards'>>> = {
  '/workspaces/:workspaceId/dashboard': {
    personas: ['analyst', 'approver', 'executive'],
    tiers: ['core'],
    cards: ['launch-readiness', 'approval-queue', 'workflow-health'],
  },
  '/workspaces/:workspaceId/approvals': {
    personas: ['approver', 'executive'],
    tiers: ['core'],
    cards: ['pending-approvals', 'decision-sla'],
  },
  '/workspaces/:workspaceId/approvals/:requestId': {
    personas: ['approver', 'executive'],
    tiers: ['core'],
    cards: ['approval-context', 'evidence-snapshot'],
  },
  '/workspaces/:workspaceId/ai-actions': {
    personas: ['analyst', 'approver', 'executive'],
    tiers: ['core'],
    cards: ['recent-ai-actions', 'audit-filters'],
  },
  '/workspaces/:workspaceId/ai-actions/:actionId': {
    personas: ['analyst', 'approver', 'executive'],
    tiers: ['core'],
    cards: ['action-evidence', 'approval-trace'],
  },
  '/workspaces/:workspaceId/campaigns': {
    personas: ['planner', 'approver'],
    tiers: ['growth'],
    cards: ['campaign-summary', 'launch-checklist'],
  },
  '/workspaces/:workspaceId/strategy': {
    personas: ['planner', 'approver'],
    tiers: ['growth'],
    cards: ['strategy-brief', 'approval-dependencies'],
  },
  '/workspaces/:workspaceId/budget': {
    personas: ['approver', 'executive'],
    tiers: ['growth'],
    cards: ['budget-recommendations', 'approval-readiness'],
  },
  '/workspaces/:workspaceId/funnel-analytics': {
    personas: ['planner', 'approver', 'executive'],
    tiers: ['growth'],
    cards: ['funnel-overview'],
  },
  '/workspaces/:workspaceId/attribution': {
    personas: ['planner', 'approver', 'executive'],
    tiers: ['growth'],
    cards: ['attribution-model'],
  },
  '/workspaces/:workspaceId/roi-roas': {
    personas: ['approver', 'executive'],
    tiers: ['growth'],
    cards: ['roi-summary', 'roas-breakdown'],
  },
  '/workspaces/:workspaceId/ai-optimization': {
    personas: ['planner', 'analyst', 'approver'],
    tiers: ['enterprise'],
    cards: ['next-best-actions', 'anomaly-detection', 'budget-reallocation'],
  },
  '/workspaces/:workspaceId/self-marketing-funnel': {
    personas: ['planner', 'approver'],
    tiers: ['enterprise'],
    cards: ['funnel-stages', 'trial-onboarding'],
  },
  '/workspaces/:workspaceId/market-research': {
    personas: ['planner', 'analyst'],
    tiers: ['enterprise'],
    cards: ['trend-analysis', 'buyer-personas'],
  },
  '/workspaces/:workspaceId/advanced-channels': {
    personas: ['planner', 'approver'],
    tiers: ['enterprise'],
    cards: ['programmatic-dsp', 'ctv-campaigns', 'influencer-roster'],
  },
  '/workspaces/:workspaceId/localization': {
    personas: ['planner'],
    tiers: ['enterprise'],
    cards: ['locale-list', 'translation-status'],
  },
  '/workspaces/:workspaceId/agency': {
    personas: ['approver', 'executive'],
    tiers: ['enterprise'],
    cards: ['client-roster', 'white-label-reports'],
  },
};

const ROUTE_ACTIONS: Readonly<Record<string, readonly string[]>> = {
  '/workspaces/:workspaceId/dashboard': ['view-dashboard'],
  '/workspaces/:workspaceId/approvals': ['review-approval'],
  '/workspaces/:workspaceId/approvals/:requestId': ['approve', 'reject'],
  '/workspaces/:workspaceId/ai-actions': ['view-audit-log'],
  '/workspaces/:workspaceId/ai-actions/:actionId': ['view-audit-log'],
  '/workspaces/:workspaceId/campaigns': ['launch-campaign'],
  '/workspaces/:workspaceId/strategy': ['submit-strategy', 'approve-strategy'],
  '/workspaces/:workspaceId/budget': ['submit-budget', 'approve-budget'],
  '/workspaces/:workspaceId/funnel-analytics': ['view-funnel'],
  '/workspaces/:workspaceId/attribution': ['view-attribution'],
  '/workspaces/:workspaceId/roi-roas': ['view-roi'],
  '/workspaces/:workspaceId/ai-optimization': ['view-recommendations', 'approve-optimizations'],
  '/workspaces/:workspaceId/self-marketing-funnel': ['manage-funnel'],
  '/workspaces/:workspaceId/market-research': ['view-research'],
  '/workspaces/:workspaceId/advanced-channels': ['manage-channels'],
  '/workspaces/:workspaceId/localization': ['manage-locales'],
  '/workspaces/:workspaceId/agency': ['manage-agency'],
};

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

function toUiPath(apiPath: string): string {
  const path = apiPath.replace(/^\/v1/, '');
  if (path.includes('/approvals/:requestId')) {
    return '/workspaces/:workspaceId/approvals/:requestId';
  }
  if (path.includes('/ai-actions')) {
    return path.includes('/:actionId')
      ? '/workspaces/:workspaceId/ai-actions/:actionId'
      : '/workspaces/:workspaceId/ai-actions';
  }
  if (path.includes('/campaigns')) {
    return '/workspaces/:workspaceId/campaigns';
  }
  if (path.includes('/strategy')) {
    return '/workspaces/:workspaceId/strategy';
  }
  if (path.includes('/budget')) {
    return '/workspaces/:workspaceId/budget';
  }
  return path;
}

function mergeRouteActions(
  existing: DmosRouteManifestEntry,
  next: DmosRouteManifestEntry,
): DmosRouteManifestEntry {
  const actions = Array.from(new Set([...(existing.actions ?? []), ...(next.actions ?? [])]));
  return { ...existing, actions };
}

function buildUiRouteManifest(): readonly DmosRouteManifestEntry[] {
  const byPath = new Map<string, DmosRouteManifestEntry>();

  generatedRouteManifest.forEach((route: DmosRouteManifestEntry) => {
    const path = toUiPath(route.path);
    const uiRoute: DmosRouteManifestEntry = {
      ...route,
      ...ROUTE_METADATA[path],
      path,
      discoverable: route.discoverable ?? (path.includes('/:requestId') || path.includes('/:actionId')),
    };
    const existing = byPath.get(path);
    byPath.set(path, existing ? mergeRouteActions(existing, uiRoute) : uiRoute);
  });

  return Array.from(byPath.values()).map((route) => ({
    ...route,
    actions: ROUTE_ACTIONS[route.path] ?? route.actions,
  }));
}

export const dmosRouteManifest: readonly DmosRouteManifestEntry[] = buildUiRouteManifest();

export type DmosRouteCapability = ProductRouteCapability;
