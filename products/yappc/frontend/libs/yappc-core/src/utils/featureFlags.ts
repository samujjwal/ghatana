/**
 *
 */
type FeatureFlags = {
  [key: string]: boolean;
};

const DEFAULT_FEATURES: FeatureFlags = {
  newDashboard: false,
  darkMode: true,
  experimentalAPIs: false,
};

/**
 *
 */
export function getFeatureFlags(): FeatureFlags {
  try {
    const flags = process.env.NEXT_PUBLIC_FEATURE_FLAGS;
    if (!flags) return DEFAULT_FEATURES;
    
    const parsed = JSON.parse(flags);
    return { ...DEFAULT_FEATURES, ...parsed };
  } catch (error) {
    console.error('Error parsing feature flags:', error);
    return DEFAULT_FEATURES;
  }
}

/**
 *
 */
export function isFeatureEnabled(feature: string): boolean {
  const flags = getFeatureFlags();
  return flags[feature] === true;
}

// Example usage:
// if (isFeatureEnabled('newDashboard')) {
//   // Show new dashboard
// } else {
//   // Show old dashboard
// }
