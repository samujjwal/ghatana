import { z } from "zod";

export const PRODUCT_LIFECYCLE_PHASES = [
  "create",
  "bootstrap",
  "dev",
  "validate",
  "test",
  "build",
  "package",
  "release",
  "deploy",
  "verify",
  "promote",
  "rollback",
  "operate",
  "retire",
] as const;

export type ProductLifecyclePhase =
  (typeof PRODUCT_LIFECYCLE_PHASES)[number];

export const ProductLifecyclePhaseSchema = z.enum(PRODUCT_LIFECYCLE_PHASES);

export function validateProductLifecyclePhase(
  value: unknown
): value is ProductLifecyclePhase {
  return ProductLifecyclePhaseSchema.safeParse(value).success;
}
