/**
 * @ghatana/config — feature flag management.
 *
 * Lightweight, type-safe feature flag system for Ghatana applications.
 * Supports boolean, rollout-percentage, and variant-based flags.
 *
 * @example
 * const flags = createFeatureFlags({
 *   newDashboard: { type: "boolean", enabled: true },
 *   betaFeature: { type: "rollout", percentage: 20 },
 *   theme: { type: "variant", variants: ["light", "dark"], default: "light" },
 * });
 *
 * flags.isEnabled("newDashboard"); // true
 * flags.isEnabled("betaFeature", { userId: "user-123" }); // true/false (deterministic)
 * flags.getVariant("theme"); // "light"
 */

// ─────────────────────────────────────────
// Types
// ─────────────────────────────────────────

/** A simple boolean on/off flag. */
export interface BooleanFlag {
  type: "boolean";
  enabled: boolean;
}

/** A percentage rollout flag. Enable for ~N% of users (deterministic by userId). */
export interface RolloutFlag {
  type: "rollout";
  percentage: number; // 0-100
}

/** A variant flag returning one of a set of named values. */
export interface VariantFlag<V extends string = string> {
  type: "variant";
  variants: readonly V[];
  default: V;
  /** Optional: map userId hash buckets to specific variants. */
  overrides?: Partial<Record<V, string[]>>;
}

export type FlagDefinition = BooleanFlag | RolloutFlag | VariantFlag;

export type FlagMap = Record<string, FlagDefinition>;

export interface FlagEvaluationContext {
  /** User identifier — used for deterministic rollout/variant evaluation. */
  userId?: string;
}

export interface FeatureFlags<F extends FlagMap> {
  /**
   * Check if a flag is enabled for the given context.
   * Always returns `true` for boolean flags with `enabled: true`.
   * Returns a deterministic result for rollout flags based on userId.
   */
  isEnabled(flagName: keyof F, context?: FlagEvaluationContext): boolean;

  /**
   * Get the active variant for a variant flag.
   * Returns the flag's default variant when no context is provided.
   */
  getVariant<K extends keyof F>(
    flagName: K,
    context?: FlagEvaluationContext
  ): F[K] extends VariantFlag<infer V> ? V : never;

  /** Return a snapshot of all flag states for the given context. */
  getAll(context?: FlagEvaluationContext): Record<keyof F, boolean | string>;
}

// ─────────────────────────────────────────
// createFeatureFlags
// ─────────────────────────────────────────

/**
 * Create a typed feature flag registry from a flag definition map.
 */
export function createFeatureFlags<F extends FlagMap>(flags: F): FeatureFlags<F> {
  function isEnabled(flagName: keyof F, context?: FlagEvaluationContext): boolean {
    const flag = flags[flagName as string];
    if (!flag) return false;

    if (flag.type === "boolean") {
      return flag.enabled;
    }

    if (flag.type === "rollout") {
      const userId = context?.userId ?? "";
      const bucket = hashToBucket(String(flagName) + userId);
      return bucket < flag.percentage;
    }

    if (flag.type === "variant") {
      // Variant flags are "enabled" when the resolved variant is not the default
      const resolved = resolveVariant(flag, context?.userId ?? "");
      return resolved !== flag.default;
    }

    return false;
  }

  function getVariant<K extends keyof F>(
    flagName: K,
    context?: FlagEvaluationContext
  ): F[K] extends VariantFlag<infer V> ? V : never {
    const flag = flags[flagName as string];
    if (!flag || flag.type !== "variant") {
      throw new Error(`Flag "${String(flagName)}" is not a variant flag`);
    }
    return resolveVariant(flag, context?.userId ?? "") as F[K] extends VariantFlag<infer V>
      ? V
      : never;
  }

  function getAll(context?: FlagEvaluationContext): Record<keyof F, boolean | string> {
    const result = {} as Record<keyof F, boolean | string>;
    for (const key of Object.keys(flags) as Array<keyof F>) {
      const flag = flags[key as string];
      if (flag.type === "variant") {
        result[key] = resolveVariant(flag, context?.userId ?? "");
      } else {
        result[key] = isEnabled(key, context);
      }
    }
    return result;
  }

  return { isEnabled, getVariant, getAll };
}

// ─────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────

/**
 * Deterministic hash → 0..99 bucket for rollout flags.
 * Uses a simple djb2 variant — not cryptographically secure, designed for
 * consistent user bucketing only.
 */
function hashToBucket(input: string): number {
  let hash = 5381;
  for (let i = 0; i < input.length; i++) {
    hash = ((hash << 5) + hash) ^ input.charCodeAt(i);
    hash = hash & hash; // Convert to 32-bit integer
  }
  return Math.abs(hash) % 100;
}

function resolveVariant<V extends string>(
  flag: VariantFlag<V>,
  userId: string
): V {
  if (flag.overrides) {
    for (const [variant, userIds] of Object.entries(flag.overrides)) {
      if (Array.isArray(userIds) && userIds.includes(userId)) {
        return variant as V;
      }
    }
  }
  const bucket = hashToBucket(userId);
  const perVariant = Math.floor(100 / flag.variants.length);
  const index = Math.min(
    Math.floor(bucket / perVariant),
    flag.variants.length - 1
  );
  return flag.variants[index] ?? flag.default;
}
