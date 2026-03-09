/**
 * Help Components
 * 
 * Components for user assistance, onboarding, and feature discovery.
 * 
 * @doc.type module
 * @doc.purpose User assistance and onboarding
 * @doc.layer components
 */

export {
  FeatureDiscoveryProvider,
  FeatureDiscoveryTooltip,
  FeatureBadge,
  useFeatureDiscovery,
} from './FeatureDiscovery';

// Note: FeatureDiscoveryProps is not exported from FeatureDiscovery.tsx as it's a local type
// If needed externally, it should be exported from the component file first
