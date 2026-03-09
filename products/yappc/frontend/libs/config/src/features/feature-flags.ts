/**
 * Feature Flags Configuration for YAPPC App Creator
 *
 * Provides feature flag management for gradual rollout of incomplete features.
 * Features can be enabled/disabled via environment variables or runtime config.
 *
 * @doc.type module
 * @doc.purpose Feature flag management for frontend
 * @doc.layer infrastructure
 * @doc.pattern Configuration
 */

// Feature Flag Names
export const FEATURE_FLAGS = {
    // LLM Provider Features
    LLM_OPENAI_ENABLED: 'feature.llm.openai.enabled',
    LLM_ANTHROPIC_ENABLED: 'feature.llm.anthropic.enabled',
    LLM_OLLAMA_ENABLED: 'feature.llm.ollama.enabled',

    // AI Features
    AI_CANVAS_GENERATION: 'feature.ai.canvas.generation',
    AI_CODE_REVIEW: 'feature.ai.code.review',
    AI_REQUIREMENTS_PARSING: 'feature.ai.requirements.parsing',

    // UI Features
    UI_CANVAS_EDITING: 'feature.ui.canvas.editing',
    UI_DARK_MODE: 'feature.ui.dark.mode',
    UI_MOBILE_VIEW: 'feature.ui.mobile.view',
    UI_COMMAND_PALETTE: 'feature.ui.command.palette',
    UI_KEYBOARD_SHORTCUTS: 'feature.ui.keyboard.shortcuts',

    // Integration Features
    INTEGRATION_GITHUB: 'feature.integration.github',
    INTEGRATION_FIGMA: 'feature.integration.figma',

    // Development Features
    DEV_DEBUG_MODE: 'feature.dev.debug',
    DEV_MOCK_SERVICES: 'feature.dev.mock.services',
    DEV_PERFORMANCE_MONITOR: 'feature.dev.performance.monitor',
} as const;

export type FeatureFlagName = typeof FEATURE_FLAGS[keyof typeof FEATURE_FLAGS];

/**
 * Default feature flag values
 */
const DEFAULT_FLAGS: Record<FeatureFlagName, boolean> = {
    // LLM Providers - Off by default, enable via env
    [FEATURE_FLAGS.LLM_OPENAI_ENABLED]: false,
    [FEATURE_FLAGS.LLM_ANTHROPIC_ENABLED]: false,
    [FEATURE_FLAGS.LLM_OLLAMA_ENABLED]: false,

    // AI Features - Off by default
    [FEATURE_FLAGS.AI_CANVAS_GENERATION]: false,
    [FEATURE_FLAGS.AI_CODE_REVIEW]: false,
    [FEATURE_FLAGS.AI_REQUIREMENTS_PARSING]: false,

    // UI Features - On by default
    [FEATURE_FLAGS.UI_CANVAS_EDITING]: true,
    [FEATURE_FLAGS.UI_DARK_MODE]: true,
    [FEATURE_FLAGS.UI_MOBILE_VIEW]: false,
    [FEATURE_FLAGS.UI_COMMAND_PALETTE]: true,
    [FEATURE_FLAGS.UI_KEYBOARD_SHORTCUTS]: true,

    // Integration Features - Off by default
    [FEATURE_FLAGS.INTEGRATION_GITHUB]: false,
    [FEATURE_FLAGS.INTEGRATION_FIGMA]: false,

    // Development Features - Environment-dependent
    [FEATURE_FLAGS.DEV_DEBUG_MODE]: import.meta.env.DEV,
    [FEATURE_FLAGS.DEV_MOCK_SERVICES]: import.meta.env.DEV,
    [FEATURE_FLAGS.DEV_PERFORMANCE_MONITOR]: false,
};

/**
 * Feature flag configuration interface
 */
export interface FeatureFlagConfig {
    flags: Record<FeatureFlagName, boolean>;
    overrides: Record<string, boolean>;
}

/**
 * Get feature flag value from environment variable
 */
function getEnvFlag(name: string): boolean | undefined {
    const envValue = import.meta.env[`VITE_${name.replace(/\./g, '_').toUpperCase()}`];
    if (envValue === 'true' || envValue === '1') return true;
    if (envValue === 'false' || envValue === '0') return false;
    return undefined;
}

/**
 * Feature Flags Hook Return Type
 */
export interface UseFeatureFlagsReturn {
    isEnabled: (flag: FeatureFlagName) => boolean;
    getValue: (flag: FeatureFlagName) => boolean;
    setOverride: (flag: string, value: boolean) => void;
    clearOverride: (flag: string) => void;
    isOverridden: (flag: string) => boolean;
    getAllFlags: () => Record<FeatureFlagName, boolean>;
    reload: () => void;
}

/**
 * Create feature flags context value
 */
export function createFeatureFlags(): UseFeatureFlagsReturn {
    // Runtime overrides (can be set by user preferences or testing)
    const overrides = new Map<string, boolean>();

    // Initialize flags with environment variable overrides
    const getInitialValue = (flag: FeatureFlagName): boolean => {
        // Check runtime override first
        if (overrides.has(flag)) {
            return overrides.get(flag)!;
        }

        // Check environment variable
        const envValue = getEnvFlag(flag);
        if (envValue !== undefined) {
            return envValue;
        }

        // Return default
        return DEFAULT_FLAGS[flag];
    };

    const getAllFlagsInternal = (): Record<FeatureFlagName, boolean> => {
        const flags = {} as Record<FeatureFlagName, boolean>;
        for (const flag of Object.values(FEATURE_FLAGS) as FeatureFlagName[]) {
            flags[flag] = getInitialValue(flag);
        }
        return flags;
    };

    return {
        isEnabled: (flag: FeatureFlagName): boolean => {
            return getInitialValue(flag);
        },

        getValue: (flag: FeatureFlagName): boolean => {
            return getInitialValue(flag);
        },

        setOverride: (flag: string, value: boolean): void => {
            overrides.set(flag, value);
        },

        clearOverride: (flag: string): void => {
            overrides.delete(flag);
        },

        isOverridden: (flag: string): boolean => {
            return overrides.has(flag);
        },

        getAllFlags: (): Record<FeatureFlagName, boolean> => {
            return getAllFlagsInternal();
        },

        reload: (): void => {
            // Clear cache if needed - triggers re-read of env vars
            // For now, this just triggers a re-computation
        },
    };
}

/**
 * React Context for Feature Flags
 */
export const FeatureFlagsContext = createContext<UseFeatureFlagsReturn | null>(null);

/**
 * Provider component for feature flags
 */
export function FeatureFlagsProvider({
    children,
}: {
    children: React.ReactNode;
}): JSX.Element {
    const featureFlags = useMemo(() => createFeatureFlags(), []);

    return (
        <FeatureFlagsContext.Provider value= { featureFlags } >
        { children }
        </FeatureFlagsContext.Provider>
  );
}

/**
 * Hook to access feature flags
 */
export function useFeatureFlags(): UseFeatureFlagsReturn {
    const context = useContext(FeatureFlagsContext);
    if (!context) {
        throw new Error('useFeatureFlags must be used within FeatureFlagsProvider');
    }
    return context;
}

/**
 * Hook to check if a specific feature is enabled
 */
export function useFeatureEnabled(flag: FeatureFlagName): boolean {
    const { isEnabled } = useFeatureFlags();
    return isEnabled(flag);
}

/**
 * Conditional component that only renders if feature is enabled
 */
export function Feature({
    flag,
    children,
    fallback = null,
}: {
    flag: FeatureFlagName;
    children: React.ReactNode;
    fallback?: React.ReactNode;
}): JSX.Element | null {
    const isEnabled = useFeatureEnabled(flag);
    return isEnabled ? <>{ children } < /> : <>{fallback}</ >;
}

/**
 * Feature flag configuration for external config files
 */
export const FEATURE_CONFIG_TEMPLATE = {
    features: {
        llm: {
            openai: false,
            anthropic: false,
            ollama: false,
        },
        ai: {
            canvasGeneration: false,
            codeReview: false,
            requirementsParsing: false,
        },
        ui: {
            canvasEditing: true,
            darkMode: true,
            mobileView: false,
            commandPalette: true,
            keyboardShortcuts: true,
        },
        integrations: {
            github: false,
            figma: false,
        },
        dev: {
            debugMode: import.meta.env.DEV,
            mockServices: import.meta.env.DEV,
            performanceMonitor: false,
        },
    },
};
