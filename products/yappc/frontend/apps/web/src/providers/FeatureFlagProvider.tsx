import { GrowthBook, GrowthBookProvider } from '@growthbook/growthbook-react';
import { useEffect, useMemo } from 'react';
import { useAtomValue } from 'jotai';
import { currentUserAtom } from '../stores/user.store';

/**
 * Feature flag configuration for YAPPC.
 * 
 * Replaces ad-hoc VITE_FEATURE_* environment variables with a centralized
 * GrowthBook-based feature flag system.
 * 
 * @doc.type component
 * @doc.purpose Feature flag management
 * @doc.layer infrastructure
 * @doc.pattern Provider
 */

// Environment configuration
const GROWTHBOOK_API_HOST = import.meta.env.VITE_GROWTHBOOK_API_HOST || 'https://cdn.growthbook.io';
const GROWTHBOOK_CLIENT_KEY = import.meta.env.VITE_GROWTHBOOK_CLIENT_KEY || '';
const ENABLE_DEV_MODE = import.meta.env.DEV;

/**
 * Feature flag keys used in YAPPC.
 * Centralized enum to prevent typos and enable type-safe feature checks.
 */
export enum FeatureFlag {
  // Onboarding & UX
  ONBOARDING = 'onboarding',
  CANVAS_CALM_MODE = 'canvas-calm-mode',
  COMMAND_PALETTE = 'command-palette',
  
  // AI Features
  AI_SUGGESTIONS = 'ai-suggestions',
  AI_CANVAS_ASSISTANT = 'ai-canvas-assistant',
  AI_CODE_REVIEW = 'ai-code-review',
  
  // Collaboration
  REAL_TIME_COLLAB = 'real-time-collaboration',
  CANVAS_COMMENTS = 'canvas-comments',
  
  // Advanced Features
  APPROVAL_WORKFLOWS = 'approval-workflows',
  AGENT_ORCHESTRATION = 'agent-orchestration',
  CANVAS_VERSIONING = 'canvas-versioning',
  
  // Experimental
  CANVAS_3D_MODE = 'canvas-3d-mode',
  VOICE_COMMANDS = 'voice-commands',
}

/**
 * Default feature flag values for local development.
 * Used when GrowthBook is not configured or unavailable.
 */
const DEFAULT_FEATURES: Record<FeatureFlag, boolean> = {
  // Onboarding & UX - enabled by default
  [FeatureFlag.ONBOARDING]: true,
  [FeatureFlag.CANVAS_CALM_MODE]: false,
  [FeatureFlag.COMMAND_PALETTE]: true,
  
  // AI Features - enabled in dev, controlled in prod
  [FeatureFlag.AI_SUGGESTIONS]: ENABLE_DEV_MODE,
  [FeatureFlag.AI_CANVAS_ASSISTANT]: ENABLE_DEV_MODE,
  [FeatureFlag.AI_CODE_REVIEW]: false,
  
  // Collaboration - enabled
  [FeatureFlag.REAL_TIME_COLLAB]: true,
  [FeatureFlag.CANVAS_COMMENTS]: true,
  
  // Advanced Features
  [FeatureFlag.APPROVAL_WORKFLOWS]: true,
  [FeatureFlag.AGENT_ORCHESTRATION]: ENABLE_DEV_MODE,
  [FeatureFlag.CANVAS_VERSIONING]: false,
  
  // Experimental - disabled by default
  [FeatureFlag.CANVAS_3D_MODE]: false,
  [FeatureFlag.VOICE_COMMANDS]: false,
};

/**
 * Creates and configures a GrowthBook instance.
 */
function createGrowthBookInstance(): GrowthBook {
  const gb = new GrowthBook({
    apiHost: GROWTHBOOK_API_HOST,
    clientKey: GROWTHBOOK_CLIENT_KEY,
    enableDevMode: ENABLE_DEV_MODE,
    
    // Track feature usage for analytics
    trackingCallback: (experiment: unknown, result: unknown) => {
      if (ENABLE_DEV_MODE) {
        console.log('[FeatureFlags] Experiment viewed:', {
          experimentId: experiment.key,
          variationId: result.variationId,
          value: result.value,
        });
      }
      
      // NOTE: Send to analytics service
      // analytics.track('Feature Flag Viewed', { ... });
    },
    
    // Fallback to default features if GrowthBook unavailable
    features: Object.entries(DEFAULT_FEATURES).reduce((acc, [key, value]) => {
      acc[key] = { defaultValue: value };
      return acc;
    }, {} as Record<string, { defaultValue: boolean }>),
  });

  return gb;
}

/**
 * Feature Flag Provider component.
 * Wraps the app with GrowthBook context and manages feature flag state.
 */
export function FeatureFlagProvider({ children }: { children: React.ReactNode }) {
  const currentUser = useAtomValue(currentUserAtom);
  
  const growthbook = useMemo(() => createGrowthBookInstance(), []);

  useEffect(() => {
    // Set user attributes for targeting
    if (currentUser) {
      growthbook.setAttributes({
        id: currentUser.id,
        email: currentUser.email,
        name: currentUser.name,
        // Add more attributes as needed for targeting
        // role: currentUser.role,
        // tenantId: currentUser.tenantId,
      });
    }

    // Load features from GrowthBook API
    if (GROWTHBOOK_CLIENT_KEY) {
      growthbook.loadFeatures().catch((error: unknown) => {
        console.warn('[FeatureFlags] Failed to load features from GrowthBook:', error);
        console.info('[FeatureFlags] Using default feature values');
      });
    } else {
      console.info('[FeatureFlags] GrowthBook not configured, using default features');
    }

    return () => {
      growthbook.destroy();
    };
  }, [growthbook, currentUser]);

  return <GrowthBookProvider growthbook={growthbook}>{children}</GrowthBookProvider>;
}

/**
 * Hook to check if a feature is enabled.
 * 
 * @example
 * ```tsx
 * const { isFeatureEnabled } = useFeatureFlag();
 * 
 * if (isFeatureEnabled(FeatureFlag.AI_SUGGESTIONS)) {
 *   return <AISuggestionsPanel />;
 * }
 * ```
 */
export function useFeatureFlag() {
  const growthbook = useMemo(() => createGrowthBookInstance(), []);

  const isFeatureEnabled = (flag: FeatureFlag): boolean => {
    return growthbook.isOn(flag);
  };

  const getFeatureValue = <T,>(flag: FeatureFlag, defaultValue: T): T => {
    return growthbook.getFeatureValue(flag, defaultValue);
  };

  return {
    isFeatureEnabled,
    getFeatureValue,
    growthbook,
  };
}

/**
 * Backward compatibility: Environment variable feature flags.
 * 
 * @deprecated Use FeatureFlag enum and useFeatureFlag hook instead.
 */
export const LEGACY_ENV_FLAGS = {
  ONBOARDING: import.meta.env.VITE_FEATURE_ONBOARDING !== 'false',
  MOCK_AUTH: import.meta.env.VITE_MOCK_AUTH === 'true',
} as const;
