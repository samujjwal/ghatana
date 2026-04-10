/**
 * @ghatana/config — public API.
 *
 * Platform-level configuration management, environment variable validation,
 * and feature flag support for Ghatana applications.
 *
 * @example
 * // Environment validation
 * import { loadEnv, BaseEnvSchema } from '@ghatana/config';
 * const MyEnv = BaseEnvSchema.extend({ DATABASE_URL: z.string().url() });
 * export const env = loadEnv(MyEnv);
 *
 * @example
 * // Typed config object
 * import { createConfig } from '@ghatana/config';
 * const config = createConfig(MyConfigSchema, rawInput);
 *
 * @example
 * // Feature flags
 * import { createFeatureFlags } from '@ghatana/config';
 * const flags = createFeatureFlags({ newUI: { type: 'boolean', enabled: true } });
 * flags.isEnabled('newUI'); // true
 */

// Schema utilities
export {
  createConfig,
  ConfigValidationError,
  nonEmptyString,
  urlString,
  positiveInt,
  portNumber,
} from "./schema.js";
export type { Config } from "./schema.js";

// Environment validation
export {
  loadEnv,
  NodeEnvSchema,
  BaseEnvSchema,
} from "./env.js";
export type { NodeEnv, BaseEnv } from "./env.js";

// Feature flags
export {
  createFeatureFlags,
} from "./feature-flags.js";
export type {
  BooleanFlag,
  RolloutFlag,
  VariantFlag,
  FlagDefinition,
  FlagMap,
  FlagEvaluationContext,
  FeatureFlags,
} from "./feature-flags.js";
