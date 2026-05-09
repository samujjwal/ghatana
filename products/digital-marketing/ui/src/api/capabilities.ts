/**
 * P1-016: Backend Capability API client.
 *
 * Fetches runtime capabilities from the backend to determine
 * feature and route availability without requiring rebuilds.
 *
 * P1-037: Extended to include content generation capabilities.
 *
 * @doc.type api
 * @doc.purpose Backend capability API client for runtime feature gating
 * @doc.layer frontend
 */

import { apiRequest } from '@/lib/http-client';

/**
 * P1-037: Standard capability keys for DMOS features.
 * P0-004: Added missing capability keys for new route manifest features.
 */
export const CapabilityKeys = {
  // Core features
  CAMPAIGNS: 'dmos.campaigns',
  STRATEGY: 'dmos.strategy',
  BUDGET: 'dmos.budget',
  APPROVALS: 'dmos.approvals',
  AI_ACTIONS: 'dmos.ai_actions',

  // P1-037: Content generation backend-only capabilities
  AD_COPY_GENERATION: 'dmos.ad_copy_generation',
  LANDING_PAGE_GENERATION: 'dmos.landing_page_generation',
  EMAIL_DRAFT_GENERATION: 'dmos.email_draft_generation',
  SOW_GENERATION: 'dmos.sow_generation',

  // P0-004: New route manifest capability keys
  REPORTING: 'dmos.reporting',
  AI_OPTIMIZATION: 'dmos.ai_optimization',
  SELF_MARKETING: 'dmos.self_marketing',
  MARKET_RESEARCH: 'dmos.market_research',
  ADVANCED_CHANNELS: 'dmos.advanced_channels',
  LOCALIZATION: 'dmos.localization',
  AGENCY: 'dmos.agency',
} as const;

export type CapabilityKey = typeof CapabilityKeys[keyof typeof CapabilityKeys];

export interface Capability {
  key: CapabilityKey;
  enabled: boolean;
  description?: string;
  requiresRole?: string;
  tier?: string;
}

export interface WorkspaceCapabilities {
  workspaceId: string;
  capabilities: Capability[];
  lastUpdated: string;
}

/**
 * Fetches all capabilities for a workspace from the backend.
 */
export async function getWorkspaceCapabilities(
  workspaceId: string,
): Promise<WorkspaceCapabilities> {
  return apiRequest<WorkspaceCapabilities>(
    `/v1/workspaces/${encodeURIComponent(workspaceId)}/capabilities`,
  );
}

/**
 * Checks if a specific capability is enabled for the workspace.
 */
export async function isCapabilityEnabled(
  workspaceId: string,
  capabilityKey: string,
): Promise<boolean> {
  try {
    const capabilities = await getWorkspaceCapabilities(workspaceId);
    const capability = capabilities.capabilities.find((c) => c.key === capabilityKey);
    return capability?.enabled ?? false;
  } catch (error) {
    // Fail closed: if we can't determine capability status, assume disabled
    console.error(`Failed to check capability ${capabilityKey}:`, error);
    return false;
  }
}

/**
 * P1-4: Explicit action-to-capability mapping for route action permissions.
 * Replaces naive string matching with backend-defined grants.
 */
const ACTION_CAPABILITY_GRANTS: Record<string, CapabilityKey[]> = {
  // Campaign actions
  'create': [CapabilityKeys.CAMPAIGNS],
  'launch': [CapabilityKeys.CAMPAIGNS],
  'pause': [CapabilityKeys.CAMPAIGNS],
  'complete': [CapabilityKeys.CAMPAIGNS],
  'archive': [CapabilityKeys.CAMPAIGNS],
  'rollback': [CapabilityKeys.CAMPAIGNS],
  'duplicate': [CapabilityKeys.CAMPAIGNS],
  'list': [CapabilityKeys.CAMPAIGNS],
  'get': [CapabilityKeys.CAMPAIGNS],

  // Strategy actions
  'generate': [CapabilityKeys.STRATEGY],
  'submit': [CapabilityKeys.STRATEGY],
  'approve': [CapabilityKeys.STRATEGY, CapabilityKeys.APPROVALS],

  // Budget actions
  'recommend': [CapabilityKeys.BUDGET],
  'approve-budget': [CapabilityKeys.BUDGET, CapabilityKeys.APPROVALS],

  // AI actions
  'optimize': [CapabilityKeys.AI_OPTIMIZATION],
  'log': [CapabilityKeys.AI_ACTIONS],

  // Content generation actions
  'generate-ad-copy': [CapabilityKeys.AD_COPY_GENERATION],
  'generate-landing-page': [CapabilityKeys.LANDING_PAGE_GENERATION],
  'generate-email-draft': [CapabilityKeys.EMAIL_DRAFT_GENERATION],
  'generate-sow': [CapabilityKeys.SOW_GENERATION],
};

/**
 * P1-4: Checks if a route action is permitted based on backend capability grants.
 * Uses explicit action-to-capability mapping instead of naive string matching.
 */
export async function isRouteActionPermitted(
  workspaceId: string,
  action: string,
): Promise<boolean> {
  try {
    const capabilities = await getWorkspaceCapabilities(workspaceId);
    const requiredCapabilities = ACTION_CAPABILITY_GRANTS[action.toLowerCase()];
    
    if (!requiredCapabilities || requiredCapabilities.length === 0) {
      // Unknown action - fail closed
      return false;
    }

    // Check if any of the required capabilities are enabled
    return requiredCapabilities.some(capKey => {
      const capability = capabilities.capabilities.find((c) => c.key === capKey);
      return capability?.enabled ?? false;
    });
  } catch (error) {
    console.error(`Failed to check action permission ${action}:`, error);
    return false;
  }
}
