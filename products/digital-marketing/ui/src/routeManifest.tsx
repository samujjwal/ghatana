import React from 'react';
import type { ProductRouteCapability } from '@ghatana/product-shell';
import { VALID_ROLES, type ValidRole, normalizeRoles } from '@/lib/role-utils';

function lazyNamedPage<T>(loader: () => Promise<T>, exportName: keyof T): React.LazyExoticComponent<React.ComponentType> {
  return React.lazy(async () => {
    const module = await loader();
    return { default: module[exportName] as React.ComponentType };
  });
}

const DashboardPage = lazyNamedPage(() => import('@/pages/DashboardPage'), 'DashboardPage');
const ApprovalQueuePage = lazyNamedPage(() => import('@/pages/ApprovalQueuePage'), 'ApprovalQueuePage');
const ApprovalDetailPage = lazyNamedPage(() => import('@/pages/ApprovalDetailPage'), 'ApprovalDetailPage');
const AiActionLogPage = lazyNamedPage(() => import('@/pages/AiActionLogPage'), 'AiActionLogPage');
const CampaignsPage = lazyNamedPage(() => import('@/pages/CampaignsPage'), 'CampaignsPage');
const StrategyPage = lazyNamedPage(() => import('@/pages/StrategyPage'), 'StrategyPage');
const BudgetPage = lazyNamedPage(() => import('@/pages/BudgetPage'), 'BudgetPage');
const FunnelAnalyticsPage = lazyNamedPage(() => import('@/pages/FunnelAnalyticsPage'), 'FunnelAnalyticsPage');
const AttributionPage = lazyNamedPage(() => import('@/pages/AttributionPage'), 'AttributionPage');
const RoiRoasPage = lazyNamedPage(() => import('@/pages/RoiRoasPage'), 'RoiRoasPage');
const SelfMarketingFunnelPage = lazyNamedPage(() => import('@/pages/SelfMarketingFunnelPage'), 'SelfMarketingFunnelPage');
const MarketResearchPage = lazyNamedPage(() => import('@/pages/MarketResearchPage'), 'MarketResearchPage');
const AdvancedChannelsPage = lazyNamedPage(() => import('@/pages/AdvancedChannelsPage'), 'AdvancedChannelsPage');
const LocalizationPage = lazyNamedPage(() => import('@/pages/LocalizationPage'), 'LocalizationPage');
const AgencyOperationsPage = lazyNamedPage(() => import('@/pages/AgencyOperationsPage'), 'AgencyOperationsPage');
const AiOptimizationPage = lazyNamedPage(() => import('@/pages/AiOptimizationPage'), 'AiOptimizationPage');

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
  {
    path: '/workspaces/:workspaceId/funnel-analytics',
    label: 'Funnel Analytics',
    description: 'Full-funnel conversion analytics and stage drop-off reporting.',
    group: 'Reporting',
    minimumRole: 'brand-manager',
    personas: ['planner', 'approver', 'executive'],
    tiers: ['growth'],
    actions: ['view-funnel'],
    cards: ['funnel-overview'],
    iconName: 'chart-bar',
    lifecycle: 'boundary',
    capabilityKey: 'dmos.reporting',
    element: <FunnelAnalyticsPage />,
  },
  {
    path: '/workspaces/:workspaceId/attribution',
    label: 'Attribution',
    description: 'Multi-touch attribution models and channel credit distribution.',
    group: 'Reporting',
    minimumRole: 'brand-manager',
    personas: ['planner', 'approver', 'executive'],
    tiers: ['growth'],
    actions: ['view-attribution'],
    cards: ['attribution-model'],
    iconName: 'share-nodes',
    lifecycle: 'boundary',
    capabilityKey: 'dmos.reporting',
    element: <AttributionPage />,
  },
  {
    path: '/workspaces/:workspaceId/roi-roas',
    label: 'ROI & ROAS',
    description: 'Return on investment and return on ad spend dashboards.',
    group: 'Reporting',
    minimumRole: 'marketing-director',
    personas: ['approver', 'executive'],
    tiers: ['growth'],
    actions: ['view-roi'],
    cards: ['roi-summary', 'roas-breakdown'],
    iconName: 'trending-up',
    lifecycle: 'boundary',
    capabilityKey: 'dmos.reporting',
    element: <RoiRoasPage />,
  },
  // ── P3 Roadmap Routes ────────────────────────────────────────────────────
  {
    path: '/workspaces/:workspaceId/ai-optimization',
    label: 'AI Optimization',
    description: 'AI-driven next-best-action recommendations, anomaly detection, and budget optimization.',
    group: 'Intelligence',
    minimumRole: 'brand-manager',
    personas: ['planner', 'analyst', 'approver'],
    tiers: ['enterprise'],
    actions: ['view-recommendations', 'approve-optimizations'],
    cards: ['next-best-actions', 'anomaly-detection', 'budget-reallocation'],
    iconName: 'sparkles',
    lifecycle: 'boundary',
    capabilityKey: 'dmos.ai_optimization',
    element: <AiOptimizationPage />,
  },
  {
    path: '/workspaces/:workspaceId/self-marketing-funnel',
    label: 'Self-Marketing Funnel',
    description: 'Product-led growth funnel management and trial onboarding flows.',
    group: 'Growth',
    minimumRole: 'brand-manager',
    personas: ['planner', 'approver'],
    tiers: ['enterprise'],
    actions: ['manage-funnel'],
    cards: ['funnel-stages', 'trial-onboarding'],
    iconName: 'funnel',
    lifecycle: 'boundary',
    capabilityKey: 'dmos.self_marketing',
    element: <SelfMarketingFunnelPage />,
  },
  {
    path: '/workspaces/:workspaceId/market-research',
    label: 'Market Research',
    description: 'Trend analysis, buyer persona generation, and competitive intelligence.',
    group: 'Intelligence',
    minimumRole: 'brand-manager',
    personas: ['planner', 'analyst'],
    tiers: ['enterprise'],
    actions: ['view-research'],
    cards: ['trend-analysis', 'buyer-personas'],
    iconName: 'search',
    lifecycle: 'boundary',
    capabilityKey: 'dmos.market_research',
    element: <MarketResearchPage />,
  },
  {
    path: '/workspaces/:workspaceId/advanced-channels',
    label: 'Advanced Channels',
    description: 'Programmatic advertising, Connected TV, and influencer management.',
    group: 'Execution',
    minimumRole: 'marketing-director',
    personas: ['planner', 'approver'],
    tiers: ['enterprise'],
    actions: ['manage-channels'],
    cards: ['programmatic-dsp', 'ctv-campaigns', 'influencer-roster'],
    iconName: 'broadcast',
    lifecycle: 'boundary',
    capabilityKey: 'dmos.advanced_channels',
    element: <AdvancedChannelsPage />,
  },
  {
    path: '/workspaces/:workspaceId/localization',
    label: 'Localization',
    description: 'Multi-language campaign support and region-specific compliance controls.',
    group: 'Execution',
    minimumRole: 'brand-manager',
    personas: ['planner'],
    tiers: ['enterprise'],
    actions: ['manage-locales'],
    cards: ['locale-list', 'translation-status'],
    iconName: 'globe',
    lifecycle: 'boundary',
    capabilityKey: 'dmos.localization',
    element: <LocalizationPage />,
  },
  {
    path: '/workspaces/:workspaceId/agency',
    label: 'Agency Operations',
    description: 'Client onboarding, white-label reports, and multi-client workspace management.',
    group: 'Agency',
    minimumRole: 'admin',
    personas: ['approver', 'executive'],
    tiers: ['enterprise'],
    actions: ['manage-agency'],
    cards: ['client-roster', 'white-label-reports'],
    iconName: 'briefcase',
    lifecycle: 'boundary',
    capabilityKey: 'dmos.agency',
    element: <AgencyOperationsPage />,
  },
];
