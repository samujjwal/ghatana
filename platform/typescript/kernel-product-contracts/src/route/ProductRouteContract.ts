/**
 * K-001: Kernel product route contract schema.
 * Product route metadata is validated once and projected into UI manifests,
 * backend entitlement payloads, and route capability summaries.
 */

import { z } from "zod";

export const RouteStabilityValues = ["stable", "preview", "blocked", "hidden"] as const;
export const RouteGroupValues = [
  "care",
  "governance",
  "clinical",
  "administrative",
  "profile",
  "dashboard",
  "system",
  "emergency",
  "provider",
  "caregiver",
  "fchv",
] as const;

export const RouteHttpMethodValues = ["GET", "POST", "PUT", "DELETE", "PATCH"] as const;
export const RouteActionVisibilityValues = ["public", "authenticated", "role-restricted"] as const;

export type RouteStability = (typeof RouteStabilityValues)[number];
export type RouteGroup = (typeof RouteGroupValues)[number];

const RouteStabilitySchema = z.enum(RouteStabilityValues);
const RouteGroupSchema = z.enum(RouteGroupValues);

export const RouteMetadataSchema = z
  .object({
    apiEndpoint: z.string().trim().min(1).optional(),
    policyId: z.string().trim().min(1).optional(),
    testId: z.string().trim().min(1).optional(),
    featureFlag: z.string().trim().min(1).optional(),
    introducedAt: z.string().trim().min(1).optional(),
  })
  .strict();

export const RouteActionSchema = z
  .object({
    id: z.string().trim().min(1),
    label: z.string().trim().min(1),
    endpoint: z.string().trim().min(1),
    method: z.enum(RouteHttpMethodValues),
    policyId: z.string().trim().min(1).optional(),
    idempotent: z.boolean().optional(),
    confirmationRequired: z.boolean().optional(),
    visibility: z.enum(RouteActionVisibilityValues).optional(),
  })
  .strict();

// Allow string references for actions/cards (product-specific action IDs)
export const RouteActionRefSchema = z.string().trim().min(1);
export const RouteCardRefSchema = z.string().trim().min(1);

export const RouteCardSchema = z
  .object({
    id: z.string().trim().min(1),
    title: z.string().trim().min(1),
    description: z.string().trim().min(1),
    icon: z.string().trim().min(1).optional(),
    badge: z.string().trim().min(1).optional(),
  })
  .strict();

export const ProductRouteSchema = z
  .object({
    path: z.string().trim().min(1).startsWith("/"),
    label: z.string().trim().min(1),
    description: z.string().trim().min(1),
    group: RouteGroupSchema,
    minimumRole: z.string().trim().min(1),
    personas: z.array(z.string().trim().min(1)).optional(),
    tiers: z.array(z.string().trim().min(1)).optional(),
    actions: z.array(z.union([RouteActionSchema, RouteActionRefSchema])).optional(),
    cards: z.array(z.union([RouteCardSchema, RouteCardRefSchema])).optional(),
    stability: RouteStabilitySchema,
    featureFlag: z.boolean().optional(),
    metadata: RouteMetadataSchema.optional(),
    // Product-specific extensions
    surface: z.array(z.enum(["web", "mobile", "backend", "hidden"])).optional(),
    i18nKey: z.string().trim().min(1).optional(),
    descriptionI18nKey: z.string().trim().min(1).optional(),
    routeType: z.enum(["page", "detail", "action", "system"]).optional(),
    visibilityReason: z.string().trim().min(1).optional(),
    emergencyAction: z.boolean().optional(),
    // Allow direct placement of metadata fields for backward compatibility
    apiEndpoint: z.string().trim().min(1).optional(),
    policyId: z.string().trim().min(1).optional(),
    testId: z.string().trim().min(1).optional(),
  })
  .strict()
  .superRefine((route, context) => {
    if (route.stability !== "stable") {
      return;
    }

    // Check metadata object first, then direct fields
    const apiEndpoint = route.metadata?.apiEndpoint ?? route.apiEndpoint;
    const policyId = route.metadata?.policyId ?? route.policyId;
    const testId = route.metadata?.testId ?? route.testId;

    const missingMetadata = [
      apiEndpoint ? undefined : "apiEndpoint",
      policyId ? undefined : "policyId",
      testId ? undefined : "testId",
    ].filter((value): value is string => value !== undefined);

    if (missingMetadata.length > 0) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["metadata"],
        message: `Stable route ${route.path} is missing ${missingMetadata.join(", ")}`,
      });
    }
  });

export const ProductRouteContractSchema = z
  .object({
    version: z.string().trim().min(1).optional(),
    schemaVersion: z.string().trim().min(1).optional(),
    product: z.string().trim().min(1).optional(),
    roleOrder: z.record(z.string().trim().min(1), z.number().int().nonnegative()),
    routes: z.array(ProductRouteSchema).min(1),
  })
  .strict()
  .superRefine((contract, context) => {
    if (!contract.version && !contract.schemaVersion) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["version"],
        message: "Route contract must declare version or schemaVersion",
      });
    }

    const seenPaths = new Set<string>();

    contract.routes.forEach((route, index) => {
      if (seenPaths.has(route.path)) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["routes", index, "path"],
          message: `Duplicate route path ${route.path}`,
        });
      }
      seenPaths.add(route.path);

      if (contract.roleOrder[route.minimumRole] === undefined) {
        context.addIssue({
          code: z.ZodIssueCode.custom,
          path: ["routes", index, "minimumRole"],
          message: `Route ${route.path} references unknown minimumRole ${route.minimumRole}`,
        });
      }
    });
  })
  .transform(contract => ({
    ...contract,
    version: contract.version ?? contract.schemaVersion ?? '',
  }));

export type RouteAction = z.infer<typeof RouteActionSchema>;
export type RouteCard = z.infer<typeof RouteCardSchema>;
export type RouteMetadata = z.infer<typeof RouteMetadataSchema>;
export type ProductRoute = z.infer<typeof ProductRouteSchema>;
export type ProductRouteContract = z.infer<typeof ProductRouteContractSchema>;

export type ProductRouteCapability = {
  path: string;
  stability: RouteStability;
  directLinkAllowed: boolean;
  discoverable: boolean;
  minimumRole: string;
  featureFlag?: boolean;
  apiEndpoint?: string;
  policyId?: string;
  testId?: string;
};

export const ProductRouteCapabilitySchema = z
  .object({
    path: z.string().trim().min(1).startsWith("/"),
    stability: RouteStabilitySchema,
    directLinkAllowed: z.boolean(),
    discoverable: z.boolean(),
    minimumRole: z.string().trim().min(1),
    featureFlag: z.boolean().optional(),
    apiEndpoint: z.string().trim().min(1).optional(),
    policyId: z.string().trim().min(1).optional(),
    testId: z.string().trim().min(1).optional(),
  })
  .strict();

export function isRouteStability(value: unknown): value is RouteStability {
  return RouteStabilitySchema.safeParse(value).success;
}

export function isRouteGroup(value: unknown): value is RouteGroup {
  return RouteGroupSchema.safeParse(value).success;
}

export function parseProductRouteContract(contract: unknown): ProductRouteContract {
  return ProductRouteContractSchema.parse(contract);
}

export function validateProductRouteContract(contract: unknown): contract is ProductRouteContract {
  return ProductRouteContractSchema.safeParse(contract).success;
}

export function validateProductRouteCapability(value: unknown): value is ProductRouteCapability {
  return ProductRouteCapabilitySchema.safeParse(value).success;
}
