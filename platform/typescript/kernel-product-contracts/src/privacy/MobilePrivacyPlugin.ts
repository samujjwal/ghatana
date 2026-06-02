/**
 * Kernel Mobile Privacy Plugin
 *
 * Provides encrypted PHI cache adapter contract for mobile applications.
 * Ensures PHI is stored only through this adapter with proper TTL, session binding,
 * restricted field stripping, and automatic clearing on security events.
 *
 * @doc.type module
 * @doc.purpose Encrypted PHI cache adapter for mobile applications
 * @doc.layer platform
 * @doc.pattern Plugin, Adapter
 */

export type MobilePrivacyClearReason =
  | "logout"
  | "session-clear"
  | "session-expired"
  | "session-scope-changed"
  | "consent-revoked"
  | "role-changed"
  | "persona-changed"
  | "facility-changed";

/**
 * Configuration for encrypted PHI cache entries.
 */
export interface PhiCacheEntryConfig {
  /** Time-to-live in seconds. Must be set for all PHI entries. */
  readonly ttl: number;
  /** Session ID to bind this entry to. Entry is invalid if session changes. */
  readonly sessionId: string;
  /** List of PHI field names to strip before caching. Fields not in this list are stored. */
  readonly allowedFields: readonly string[];
  /** Whether this entry contains sensitive PHI requiring encryption. */
  readonly isSensitive: boolean;
}

/**
 * Encrypted PHI cache entry with metadata.
 */
export interface PhiCacheEntry<T = unknown> {
  /** The cached data (with restricted fields only). */
  readonly data: T;
  /** Session ID this entry is bound to. */
  readonly sessionId: string;
  /** Timestamp when this entry was created. */
  readonly createdAt: number;
  /** Timestamp when this entry expires. */
  readonly expiresAt: number;
  /** Whether this entry contains sensitive PHI. */
  readonly isSensitive: boolean;
}

/**
 * Result of a PHI cache operation.
 */
export interface PhiCacheResult<T> {
  /** The cached entry if found and valid. */
  readonly entry: PhiCacheEntry<T> | null;
  /** Whether the entry was expired. */
  readonly expired: boolean;
  /** Whether the entry was session-bound to a different session. */
  readonly sessionMismatch: boolean;
}

/**
 * Contract for encrypted PHI cache adapter.
 *
 * Implementations must:
 * - Encrypt sensitive PHI at rest
 * - Enforce TTL
 * - Bind entries to session
 * - Strip restricted fields
 * - Support clearing by reason
 */
export interface EncryptedPhiCacheAdapter {
  /** Unique identifier for this cache adapter. */
  readonly cacheName: string;

  /**
   * Store PHI data with encryption and restrictions.
   *
   * @param key - Cache key (must not contain PHI)
   * @param data - Data to cache
   * @param config - Cache configuration
   * @returns Promise resolving when data is stored
   */
  set<T>(key: string, data: T, config: PhiCacheEntryConfig): Promise<void>;

  /**
   * Retrieve PHI data with validation.
   *
   * @param key - Cache key
   * @param currentSessionId - Current session ID for binding validation
   * @returns Promise resolving to cache result
   */
  get<T>(key: string, currentSessionId: string): Promise<PhiCacheResult<T>>;

  /**
   * Clear all PHI cache entries.
   *
   * @returns Promise resolving when cache is cleared
   */
  clear(): Promise<void>;

  /**
   * Clear entries bound to a specific session.
   *
   * @param sessionId - Session ID to clear
   * @returns Promise resolving when entries are cleared
   */
  clearBySession(sessionId: string): Promise<void>;

  /**
   * Clear expired entries.
   *
   * @returns Promise resolving when expired entries are cleared
   */
  clearExpired(): Promise<void>;
}

/**
 * Cache clearer for integration with mobile privacy state management.
 */
export interface MobilePrivacyCacheClearer {
  readonly cacheName: string;
  clear(): Promise<void>;
}

/**
 * Error thrown when mobile privacy cache clearing fails.
 */
export class MobilePrivacyClearError extends Error {
  constructor(
    public readonly reason: MobilePrivacyClearReason,
    public readonly failedCaches: readonly string[],
  ) {
    super(`Failed to clear mobile privacy caches: ${failedCaches.join(", ")}`);
    this.name = "MobilePrivacyClearError";
  }
}

/**
 * Error thrown when PHI cache validation fails.
 */
export class PhiCacheValidationError extends Error {
  constructor(
    public readonly key: string,
    public readonly reason: "expired" | "session-mismatch" | "invalid-config",
  ) {
    super(`PHI cache validation failed for key ${key}: ${reason}`);
    this.name = "PhiCacheValidationError";
  }
}

/**
 * Clear mobile privacy state across all registered cache clearers.
 *
 * @param reason - Reason for clearing
 * @param clearers - Cache clearers to invoke
 * @param reporter - Reporter for logging
 * @returns Promise resolving when all caches are cleared
 * @throws MobilePrivacyClearError if any cache clear fails
 */
export async function clearKernelMobilePrivacyState(
  reason: MobilePrivacyClearReason,
  clearers: readonly MobilePrivacyCacheClearer[],
  reporter: Pick<Console, "debug" | "warn"> = console,
): Promise<void> {
  const failedCaches: string[] = [];

  for (const clearer of clearers) {
    try {
      await clearer.clear();
    } catch {
      failedCaches.push(clearer.cacheName);
    }
  }

  if (failedCaches.length > 0) {
    reporter.warn(
      `[kernel.mobilePrivacy] clear_failed reason=${reason} caches=${failedCaches.join(",")}`,
    );
    throw new MobilePrivacyClearError(reason, failedCaches);
  }

  reporter.debug(`[kernel.mobilePrivacy] cleared reason=${reason}`);
}

/**
 * Strip restricted fields from data before caching.
 *
 * @param data - Data to strip
 * @param allowedFields - Fields that are allowed (others are removed)
 * @returns Data with only allowed fields
 */
export function stripRestrictedFields<T extends Record<string, unknown>>(
  data: T,
  allowedFields: readonly string[],
): Partial<T> {
  const result: Record<string, unknown> = {};
  for (const field of allowedFields) {
    if (field in data) {
      result[field] = data[field];
    }
  }
  return result as Partial<T>;
}

/**
 * Validate cache entry configuration.
 *
 * @param config - Configuration to validate
 * @throws Error if configuration is invalid
 */
export function validatePhiCacheConfig(config: PhiCacheEntryConfig): void {
  if (config.ttl <= 0) {
    throw new Error("TTL must be positive");
  }
  if (config.ttl > 86400) {
    throw new Error("TTL must not exceed 24 hours (86400 seconds)");
  }
  if (!config.sessionId || config.sessionId.length === 0) {
    throw new Error("Session ID is required");
  }
  if (!config.allowedFields || config.allowedFields.length === 0) {
    throw new Error("At least one allowed field must be specified");
  }
}

/**
 * Check if a cache entry is expired.
 *
 * @param entry - Cache entry to check
 * @returns True if entry is expired
 */
export function isEntryExpired(entry: PhiCacheEntry): boolean {
  return Date.now() > entry.expiresAt;
}

/**
 * Check if a cache entry is session-bound to the current session.
 *
 * @param entry - Cache entry to check
 * @param currentSessionId - Current session ID
 * @returns True if entry is bound to current session
 */
export function isEntrySessionBound(entry: PhiCacheEntry, currentSessionId: string): boolean {
  return entry.sessionId === currentSessionId;
}
