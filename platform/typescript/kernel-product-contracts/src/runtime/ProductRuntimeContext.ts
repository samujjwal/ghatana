/**
 * ProductRuntimeContext - canonical runtime context for product execution.
 *
 * Provides the complete runtime context required for product execution,
 * including route contracts, policy registry, plugin manifest, auth context,
 * i18n, a11y, observability, and mobile privacy configuration.
 *
 * Rules:
 * - All fields are readonly at runtime.
 * - Context is resolved by Kernel and passed to product code.
 * - No product-specific business logic here.
 *
 * @doc.type module
 * @doc.purpose Canonical runtime context for product execution
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";
import type { ProductRouteContract } from "../route/ProductRouteContract.js";
import type { ProductPolicy } from "../policy/ProductPolicyContract.js";
import type { KernelPlugin } from "../plugin/KernelPlugin.js";

// ---------------------------------------------------------------------------
// Branded types
// ---------------------------------------------------------------------------

/** Opaque product identifier. */
export type ProductId = string & { readonly __brand: "ProductId" };

/** Opaque tenant identifier. */
export type TenantId = string & { readonly __brand: "TenantId" };

/** Opaque principal identifier. */
export type PrincipalId = string & { readonly __brand: "PrincipalId" };

/** Opaque correlation identifier. */
export type CorrelationId = string & { readonly __brand: "CorrelationId" };

export const ProductIdSchema = z
  .string()
  .trim()
  .min(1)
  .transform((value) => value as ProductId);

export const TenantIdSchema = z
  .string()
  .trim()
  .min(1)
  .transform((value) => value as TenantId);

export const PrincipalIdSchema = z
  .string()
  .trim()
  .min(1)
  .transform((value) => value as PrincipalId);

export const CorrelationIdSchema = z
  .string()
  .trim()
  .min(1)
  .transform((value) => value as CorrelationId);

// ---------------------------------------------------------------------------
// Auth context
// ---------------------------------------------------------------------------

/**
 * Kernel-authenticated session context.
 * Identity is resolved server-side and cannot be spoofed by clients.
 */
export interface KernelAuthContext {
  readonly tenantId: TenantId;
  readonly principalId: PrincipalId;
  readonly role: string;
  readonly persona: string;
  readonly tier: string;
  readonly facilityId: string | null;
  readonly correlationId: CorrelationId;
}

export const KernelAuthContextSchema = z.object({
  tenantId: TenantIdSchema,
  principalId: PrincipalIdSchema,
  role: z.string().trim().min(1),
  persona: z.string().trim().min(1),
  tier: z.string().trim().min(1),
  facilityId: z.string().trim().min(1).nullable(),
  correlationId: CorrelationIdSchema,
}).strict();

// ---------------------------------------------------------------------------
// I18n context
// ---------------------------------------------------------------------------

/**
 * Internationalization context for the product.
 */
export interface I18nContext {
  readonly locale: string;
  readonly timezone: string;
  readonly fallbackLocale: string;
}

export const I18nContextSchema = z.object({
  locale: z.string().trim().min(1),
  timezone: z.string().trim().min(1),
  fallbackLocale: z.string().trim().min(1),
}).strict();

// ---------------------------------------------------------------------------
// A11y context
// ---------------------------------------------------------------------------

/**
 * Accessibility context for the product.
 */
export interface A11yContext {
  readonly reducedMotion: boolean;
  readonly highContrast: boolean;
  readonly screenReaderEnabled: boolean;
  readonly fontSizeScale: number;
}

export const A11yContextSchema = z.object({
  reducedMotion: z.boolean(),
  highContrast: z.boolean(),
  screenReaderEnabled: z.boolean(),
  fontSizeScale: z.number().min(0.5).max(2.0),
}).strict();

// ---------------------------------------------------------------------------
// Observability context
// ---------------------------------------------------------------------------

/**
 * Observability context for the product.
 */
export interface ObservabilityContext {
  readonly tracingEnabled: boolean;
  readonly metricsEnabled: boolean;
  readonly loggingLevel: "debug" | "info" | "warn" | "error";
  readonly auditEnabled: boolean;
}

export const ObservabilityContextSchema = z.object({
  tracingEnabled: z.boolean(),
  metricsEnabled: z.boolean(),
  loggingLevel: z.enum(["debug", "info", "warn", "error"]),
  auditEnabled: z.boolean(),
}).strict();

// ---------------------------------------------------------------------------
// Mobile privacy context
// ---------------------------------------------------------------------------

/**
 * Mobile privacy context for PHI-sensitive data.
 */
export interface MobilePrivacyContext {
  readonly encryptedCacheEnabled: boolean;
  readonly offlinePhiAllowed: boolean;
  readonly biometricRequired: boolean;
  readonly sessionTtlSeconds: number;
}

export const MobilePrivacyContextSchema = z.object({
  encryptedCacheEnabled: z.boolean(),
  offlinePhiAllowed: z.boolean(),
  biometricRequired: z.boolean(),
  sessionTtlSeconds: z.number().min(60),
}).strict();

// ---------------------------------------------------------------------------
// ProductRuntimeContext
// ---------------------------------------------------------------------------

/**
 * Complete runtime context for product execution.
 *
 * This context is resolved by the Kernel and passed to product code
 * for use in route handlers, services, and UI components.
 */
export interface ProductRuntimeContext {
  /** Product identifier. */
  readonly productId: ProductId;

  /** Complete route contract for the product. */
  readonly routeContract: ProductRouteContract;

  /** Policy registry for the product. */
  readonly policyRegistry: readonly ProductPolicy[];

  /** Available plugins for the product. */
  readonly plugins: readonly KernelPlugin[];

  /** Kernel-authenticated session context. */
  readonly authContext: KernelAuthContext;

  /** Internationalization context. */
  readonly i18nContext: I18nContext;

  /** Accessibility context. */
  readonly a11yContext: A11yContext;

  /** Observability context. */
  readonly observabilityContext: ObservabilityContext;

  /** Mobile privacy context. */
  readonly mobilePrivacyContext: MobilePrivacyContext;
}

export const ProductRuntimeContextSchema = z.object({
  productId: ProductIdSchema,
  routeContract: z.custom<ProductRouteContract>(),
  policyRegistry: z.array(z.custom<ProductPolicy>()),
  plugins: z.array(z.custom<KernelPlugin>()),
  authContext: KernelAuthContextSchema,
  i18nContext: I18nContextSchema,
  a11yContext: A11yContextSchema,
  observabilityContext: ObservabilityContextSchema,
  mobilePrivacyContext: MobilePrivacyContextSchema,
}).strict();

// ---------------------------------------------------------------------------
// Validation functions
// ---------------------------------------------------------------------------

export function validateKernelAuthContext(
  value: unknown
): value is KernelAuthContext {
  return KernelAuthContextSchema.safeParse(value).success;
}

export function validateI18nContext(value: unknown): value is I18nContext {
  return I18nContextSchema.safeParse(value).success;
}

export function validateA11yContext(value: unknown): value is A11yContext {
  return A11yContextSchema.safeParse(value).success;
}

export function validateObservabilityContext(
  value: unknown
): value is ObservabilityContext {
  return ObservabilityContextSchema.safeParse(value).success;
}

export function validateMobilePrivacyContext(
  value: unknown
): value is MobilePrivacyContext {
  return MobilePrivacyContextSchema.safeParse(value).success;
}

export function validateProductRuntimeContext(
  value: unknown
): value is ProductRuntimeContext {
  return ProductRuntimeContextSchema.safeParse(value).success;
}

// ---------------------------------------------------------------------------
// Type guards
// ---------------------------------------------------------------------------

export function isKernelAuthContext(value: unknown): value is KernelAuthContext {
  return validateKernelAuthContext(value);
}

export function isI18nContext(value: unknown): value is I18nContext {
  return validateI18nContext(value);
}

export function isA11yContext(value: unknown): value is A11yContext {
  return validateA11yContext(value);
}

export function isObservabilityContext(
  value: unknown
): value is ObservabilityContext {
  return validateObservabilityContext(value);
}

export function isMobilePrivacyContext(
  value: unknown
): value is MobilePrivacyContext {
  return validateMobilePrivacyContext(value);
}

export function isProductRuntimeContext(
  value: unknown
): value is ProductRuntimeContext {
  return validateProductRuntimeContext(value);
}
