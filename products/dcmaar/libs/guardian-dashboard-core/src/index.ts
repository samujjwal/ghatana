export * from "./roles";
export * from "./hooks";

// Export utils as namespace for backward compatibility
import {
    setDashboardFetch,
    getDashboardFetch,
    type DashboardFetch,
    getFeaturesForRole,
    getFeatureComponent,
    preloadFeature,
    preloadRoleFeatures,
    type FeatureName
} from "./utils";

// Export individual functions at top level
export { setDashboardFetch, getDashboardFetch };
export type { DashboardFetch, FeatureName };

// Export utils namespace for grouped access
export const DashboardUtils = {
    setDashboardFetch,
    getDashboardFetch,
    getFeaturesForRole,
    getFeatureComponent,
    preloadFeature,
    preloadRoleFeatures
};
