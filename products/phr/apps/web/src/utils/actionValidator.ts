import { phrRouteContracts, type PhrRouteContract } from '../phrRouteContracts';

/**
 * Action Validator (WEB-04)
 * Ensures only actions with real backend API endpoints are visible
 * Actions without apiEndpoint are considered demo-only and should be hidden
 */

export interface ActionDefinition {
  id: string;
  label: string;
  onClick: () => void;
  routePath?: string;
}

/**
 * Check if an action is valid (has a real backend API endpoint)
 */
export function isActionValid(actionId: string, routePath: string): boolean {
  const route = phrRouteContracts.find((r) => r.path === routePath);
  if (!route) {
    return false;
  }

  // Action must be listed in route contract
  if (!route.actions.includes(actionId)) {
    return false;
  }

  // Route must have an API endpoint
  if (!route.apiEndpoint) {
    return false;
  }

  // Route must be stable
  if (route.stability !== 'stable') {
    return false;
  }

  return true;
}

/**
 * Filter actions to only include those with real backend APIs
 */
export function filterValidActions(
  actions: ActionDefinition[],
  routePath: string
): ActionDefinition[] {
  return actions.filter((action) => isActionValid(action.id, routePath));
}

/**
 * Get all valid actions for a route from the contract
 */
export function getValidRouteActions(routePath: string): readonly string[] {
  const route = phrRouteContracts.find((r) => r.path === routePath);
  if (!route || !route.apiEndpoint || route.stability !== 'stable') {
    return [];
  }

  return route.actions;
}
