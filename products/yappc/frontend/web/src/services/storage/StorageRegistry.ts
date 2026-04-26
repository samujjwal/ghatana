/**
 * Storage Registry
 *
 * Documents and classifies all browser storage keys used across the application.
 * Sensitivity levels:
 *   - HIGH:   Auth tokens, API keys, personal identifiers (must migrate to secure storage)
 *   - MEDIUM: User preferences, workspace context, personas (can stay in localStorage with TTL)
 *   - LOW:    UI state, feature flags, theme, telemetry (safe in localStorage)
 *   - TEST:   E2E / test-only keys (never in production)
 *
 * @doc.type registry
 * @doc.purpose Classify and govern all browser storage keys
 * @doc.layer product
 * @doc.pattern Registry
 */

export type StorageSensitivity = 'HIGH' | 'MEDIUM' | 'LOW' | 'TEST';
export type StorageBackend = 'localStorage' | 'sessionStorage' | 'cookie-secure' | 'memory' | 'indexedDB';

export interface StorageKeyMeta {
  readonly key: string;
  readonly sensitivity: StorageSensitivity;
  readonly currentBackend: StorageBackend;
  readonly targetBackend: StorageBackend;
  readonly description: string;
  readonly retentionDays: number | 'session' | 'indefinite';
  readonly migrated: boolean;
}

/**
 * Canonical registry of all browser storage keys.
 * Keys not listed here are considered ungoverned and trigger warnings.
 */
export const STORAGE_REGISTRY: Record<string, StorageKeyMeta> = {
  'auth-session': {
    key: 'auth-session',
    sensitivity: 'HIGH',
    currentBackend: 'localStorage',
    targetBackend: 'cookie-secure',
    description: 'Structured auth session (token, refreshToken, expiresAt)',
    retentionDays: 1,
    migrated: false,
  },
  'auth_token': {
    key: 'auth_token',
    sensitivity: 'HIGH',
    currentBackend: 'localStorage',
    targetBackend: 'cookie-secure',
    description: 'Legacy raw auth token (superseded by auth-session)',
    retentionDays: 1,
    migrated: false,
  },
  'api_key': {
    key: 'api_key',
    sensitivity: 'HIGH',
    currentBackend: 'localStorage',
    targetBackend: 'cookie-secure',
    description: 'User API key for external integrations',
    retentionDays: 'session',
    migrated: false,
  },
  'onboarding_complete': {
    key: 'onboarding_complete',
    sensitivity: 'MEDIUM',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'Flag indicating onboarding flow completion',
    retentionDays: 'indefinite',
    migrated: true, // OK as-is; server-backed migration tracked in P2-1
  },
  'yappc:currentWorkspaceId': {
    key: 'yappc:currentWorkspaceId',
    sensitivity: 'MEDIUM',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'Last active workspace ID for session restoration',
    retentionDays: 'session',
    migrated: true,
  },
  'yappc_primary_persona': {
    key: 'yappc_primary_persona',
    sensitivity: 'MEDIUM',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'Selected user persona identifier',
    retentionDays: 'indefinite',
    migrated: true, // OK as-is; server-backed migration tracked in P2-1
  },
  'yappc_active_personas': {
    key: 'yappc_active_personas',
    sensitivity: 'MEDIUM',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'List of active persona identifiers',
    retentionDays: 'indefinite',
    migrated: true, // OK as-is; server-backed migration tracked in P2-1
  },
  'yappc_persona_selection': {
    key: 'yappc_persona_selection',
    sensitivity: 'MEDIUM',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'Serialized persona state (active, primary, disabled virtual)',
    retentionDays: 'indefinite',
    migrated: true,
  },
  'theme': {
    key: 'theme',
    sensitivity: 'LOW',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'UI theme preference (light/dark/system)',
    retentionDays: 'indefinite',
    migrated: true,
  },
  'yappc:dismissed-features': {
    key: 'yappc:dismissed-features',
    sensitivity: 'LOW',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'Feature-discovery tips the user has dismissed',
    retentionDays: 'indefinite',
    migrated: true,
  },
  'yappc_last_opened_projects': {
    key: 'yappc_last_opened_projects',
    sensitivity: 'LOW',
    currentBackend: 'localStorage',
    targetBackend: 'indexedDB',
    description: 'Recently opened project IDs for quick access',
    retentionDays: 30,
    migrated: false,
  },
  'current-workspace': {
    key: 'current-workspace',
    sensitivity: 'MEDIUM',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'Legacy workspace ID storage (duplicates yappc:currentWorkspaceId)',
    retentionDays: 'session',
    migrated: true,
  },
  'canvas-state': {
    key: 'canvas-state',
    sensitivity: 'MEDIUM',
    currentBackend: 'localStorage',
    targetBackend: 'indexedDB',
    description: 'Serialized canvas diagram state (can be large)',
    retentionDays: 7,
    migrated: false,
  },
  'canvas-onboarding-completed': {
    key: 'canvas-onboarding-completed',
    sensitivity: 'LOW',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'Canvas-specific onboarding completion flag',
    retentionDays: 'indefinite',
    migrated: true,
  },
  'canvas-feature-progress-filter': {
    key: 'canvas-feature-progress-filter',
    sensitivity: 'LOW',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'Canvas feature list filter preference',
    retentionDays: 'session',
    migrated: true,
  },
  'canvas:ghostDismissed_v1': {
    key: 'canvas:ghostDismissed_v1',
    sensitivity: 'LOW',
    currentBackend: 'localStorage',
    targetBackend: 'localStorage',
    description: 'Ghost node hint dismissal flag',
    retentionDays: 'indefinite',
    migrated: true,
  },
  'E2E_FORCE_NETWORK_ERROR': {
    key: 'E2E_FORCE_NETWORK_ERROR',
    sensitivity: 'TEST',
    currentBackend: 'localStorage',
    targetBackend: 'memory',
    description: 'E2E test flag to simulate network errors',
    retentionDays: 'session',
    migrated: false,
  },
  'E2E_RETRY_ATTEMPTED': {
    key: 'E2E_RETRY_ATTEMPTED',
    sensitivity: 'TEST',
    currentBackend: 'localStorage',
    targetBackend: 'memory',
    description: 'E2E test flag to prevent immediate re-error after reload',
    retentionDays: 'session',
    migrated: false,
  },
  'e2e:mockProjects': {
    key: 'e2e:mockProjects',
    sensitivity: 'TEST',
    currentBackend: 'localStorage',
    targetBackend: 'memory',
    description: 'E2E mock project data',
    retentionDays: 'session',
    migrated: false,
  },
  'e2e:mockWorkspaces': {
    key: 'e2e:mockWorkspaces',
    sensitivity: 'TEST',
    currentBackend: 'localStorage',
    targetBackend: 'memory',
    description: 'E2E mock workspace data',
    retentionDays: 'session',
    migrated: false,
  },
} as const;

export type KnownStorageKey = keyof typeof STORAGE_REGISTRY;

/**
 * Returns metadata for a known storage key, or null for ungoverned keys.
 */
export function getStorageMeta(key: string): StorageKeyMeta | null {
  return STORAGE_REGISTRY[key] ?? null;
}

/**
 * Returns true if the key is a known governed key.
 */
export function isKnownStorageKey(key: string): key is KnownStorageKey {
  return key in STORAGE_REGISTRY;
}

/**
 * Returns all keys that should NOT be in localStorage in production.
 * (HIGH sensitivity keys and TEST keys)
 */
export function getKeysRequiringMigration(): StorageKeyMeta[] {
  return Object.values(STORAGE_REGISTRY).filter(
    (meta) => meta.sensitivity === 'HIGH' || meta.sensitivity === 'TEST'
  );
}

/**
 * Returns all HIGH sensitivity keys currently in localStorage.
 */
export function getHighSensitivityKeys(): StorageKeyMeta[] {
  return Object.values(STORAGE_REGISTRY).filter((meta) => meta.sensitivity === 'HIGH');
}
