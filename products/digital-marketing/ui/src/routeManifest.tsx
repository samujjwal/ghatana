import {
  createRouteAccessEvaluator,
  resolveHighestRole,
  type ProductRouteCapability,
} from '@ghatana/product-shell';
import { normalizeRoles, type ValidRole } from '@/lib/role-utils';
import {
  dmosRouteManifest as generatedRouteManifest,
  DMOS_ROLE_ORDER,
  type DmosRouteManifestEntry,
} from '@/generated/routeManifest.generated';
import routeCapabilitiesContract from '../../dm-api/src/main/resources/contracts/dmos-route-capabilities.json';

export type { DmosRouteManifestEntry };
export { DMOS_ROLE_ORDER };

export const dmosRouteAccess = createRouteAccessEvaluator(DMOS_ROLE_ORDER);

const DEFAULT_AUTHENTICATED_ROLE: ValidRole = 'viewer';

interface DmosRouteCapabilityContract {
  readonly routes: ReadonlyArray<{
    readonly path: string;
    readonly label: string;
    readonly minimumRole: ValidRole;
    readonly personas: readonly string[];
    readonly tiers: readonly string[];
    readonly actions: readonly string[];
    readonly cards: readonly string[];
    readonly capabilityKey?: string;
  }>;
}

const dmosRouteCapabilityContract = routeCapabilitiesContract as DmosRouteCapabilityContract;

function getHighestRole(roles: readonly string[]): ValidRole {
  const normalized = normalizeRoles([...roles]);
  return resolveHighestRole(normalized, DMOS_ROLE_ORDER, DEFAULT_AUTHENTICATED_ROLE) as ValidRole;
}

export function getHighestDmosRole(roles: readonly string[]): ValidRole {
  return getHighestRole(roles);
}

export function isRouteAllowedForRoles(
  route: Pick<DmosRouteManifestEntry, 'minimumRole'>,
  roles: readonly string[],
): boolean {
  const currentRole = getHighestRole(roles);
  return dmosRouteAccess.isRouteAllowed(route, currentRole);
}

export function resolveDmosRoutePath(path: string, workspaceId: string | null | undefined): string {
  const resolvedWorkspaceId = workspaceId?.trim() || 'workspace';
  return path.replaceAll(':workspaceId', resolvedWorkspaceId);
}

function mergeRouteActions(
  existing: DmosRouteManifestEntry,
  next: DmosRouteManifestEntry,
): DmosRouteManifestEntry {
  const actions = Array.from(new Set([...(existing.actions ?? []), ...(next.actions ?? [])]));
  return { ...existing, actions };
}

function buildUiRouteManifest(): readonly DmosRouteManifestEntry[] {
  const generatedByUiPath = new Map<string, DmosRouteManifestEntry>();

  generatedRouteManifest.forEach((route: DmosRouteManifestEntry) => {
    const existing = generatedByUiPath.get(route.uiPath);
    const generatedRoute: DmosRouteManifestEntry = {
      ...route,
      path: route.uiPath,
      discoverable: route.discoverable ?? (route.uiPath.includes('/:requestId') || route.uiPath.includes('/:actionId')),
    };
    generatedByUiPath.set(route.uiPath, existing ? mergeRouteActions(existing, generatedRoute) : generatedRoute);
  });

  return dmosRouteCapabilityContract.routes.map((contractRoute) => {
    const generatedRoute = generatedByUiPath.get(contractRoute.path);
    if (!generatedRoute) {
      throw new Error(`Generated DMOS route element is missing for UI path ${contractRoute.path}`);
    }

    return {
      ...generatedRoute,
      path: contractRoute.path,
      label: contractRoute.label,
      minimumRole: contractRoute.minimumRole,
      personas: contractRoute.personas,
      tiers: contractRoute.tiers,
      actions: contractRoute.actions,
      cards: contractRoute.cards,
      capabilityKey: contractRoute.capabilityKey,
    };
  });
}

export const dmosRouteManifest: readonly DmosRouteManifestEntry[] = buildUiRouteManifest();

export type DmosRouteCapability = ProductRouteCapability;
