/**
 * ProductShape - product-neutral capability shape for lifecycle planning.
 *
 * @doc.type type
 * @doc.purpose Classify ProductUnits by lifecycle capability shape without product leakage
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

export type ProductShape =
  | "web-api"
  | "regulated-healthcare"
  | "marketing-ops"
  | "backend-heavy"
  | "mobile-api"
  | "platform-provider"
  | "artifact-intelligence"
  | "data-plane"
  | "sdk-library"
  | "external-repo";

export const PRODUCT_SHAPES = [
  "web-api",
  "regulated-healthcare",
  "marketing-ops",
  "backend-heavy",
  "mobile-api",
  "platform-provider",
  "artifact-intelligence",
  "data-plane",
  "sdk-library",
  "external-repo",
] as const satisfies readonly ProductShape[];

/**
 * Type guard to check if a string is a supported ProductShape.
 */
export function isProductShape(value: unknown): value is ProductShape {
  return typeof value === "string" && PRODUCT_SHAPES.includes(value as ProductShape);
}
