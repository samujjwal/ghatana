/**
 * Mobile route manifest derived from the canonical PHR route contract
 * RTE-003: Mobile routing driven by route contract
 */

import routeContractJson from "../../../config/phr-route-contract.json";

export type MobileScreenKey =
  | "dashboard"
  | "records"
  | "recordDetail"
  | "consents"
  | "notifications"
  | "emergency"
  | "settings";

interface MobileRouteEntry {
  key: MobileScreenKey;
  label: string;
  hint: string;
  icon: string;
  path: string;
  stability: string;
  surface: readonly string[];
}

interface RouteContractSource {
  readonly routes: readonly RouteContract[];
}

interface RouteContract {
  readonly path: string;
  readonly label: string;
  readonly description: string;
  readonly group: string;
  readonly minimumRole: string;
  readonly personas: readonly string[];
  readonly tiers: readonly string[];
  readonly actions: readonly string[];
  readonly cards: readonly string[];
  readonly stability: string;
  readonly surface?: readonly ("web" | "mobile" | "backend" | "hidden")[];
  readonly i18nKey?: string;
  readonly descriptionI18nKey?: string;
  readonly routeType?: "page" | "detail" | "action" | "system";
}

const canonicalRouteContract =
  routeContractJson as unknown as RouteContractSource;

// Map route contract paths to mobile screen keys
const PATH_TO_SCREEN_KEY: Record<string, MobileScreenKey> = {
  "/dashboard": "dashboard",
  "/records": "records",
  "/records/:recordId": "recordDetail",
  "/consents": "consents",
  "/notifications": "notifications",
  "/emergency": "emergency",
  "/settings": "settings",
};

// Map screen keys to icons
const SCREEN_ICONS: Record<MobileScreenKey, string> = {
  dashboard: "H",
  records: "R",
  recordDetail: "R",
  consents: "C",
  notifications: "N",
  emergency: "E",
  settings: "S",
};

function getMobileScreenKey(path: string): MobileScreenKey | null {
  return PATH_TO_SCREEN_KEY[path] ?? null;
}

function stableMobileRoutes(): readonly RouteContract[] {
  return canonicalRouteContract.routes.filter(
    (route) => route.surface?.includes("mobile") === true && route.stability === "stable",
  );
}

export function getMobileRouteCoverageViolations(): string[] {
  return stableMobileRoutes()
    .filter((route) => getMobileScreenKey(route.path) === null)
    .map((route) => route.path);
}

export function assertMobileRouteCoverage(): void {
  const missingPaths = getMobileRouteCoverageViolations();
  if (missingPaths.length > 0) {
    throw new Error(`Stable mobile routes missing screen keys: ${missingPaths.join(", ")}`);
  }
}

/**
 * Filter routes that are available on mobile surface and are stable
 */
export function getMobileRoutes(): MobileRouteEntry[] {
  assertMobileRouteCoverage();

  return stableMobileRoutes().flatMap((route): MobileRouteEntry[] => {
    if (route.routeType === "detail") {
      return [];
    }

    const screenKey = getMobileScreenKey(route.path);
    if (!screenKey) {
      return [];
    }

    return [
      {
        key: screenKey,
        label: route.label,
        hint: route.description,
        icon: SCREEN_ICONS[screenKey],
        path: route.path,
        stability: route.stability,
        surface: route.surface ?? [],
      },
    ];
  });
}

/**
 * Get screen key for a given path
 */
export function getScreenKeyForPath(path: string): MobileScreenKey | null {
  return getMobileScreenKey(path);
}

/**
 * Check if a route is allowed for mobile
 */
export function isRouteAvailableOnMobile(path: string): boolean {
  const route = canonicalRouteContract.routes.find((r) => r.path === path);
  if (!route) return false;

  return (
    route.surface?.includes("mobile") === true &&
    route.stability === "stable" &&
    getMobileScreenKey(path) !== null
  );
}
