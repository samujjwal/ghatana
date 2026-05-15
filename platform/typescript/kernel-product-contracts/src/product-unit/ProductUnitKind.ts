/**
 * ProductUnitKind - classifies the kind of ProductUnit for governance and lifecycle planning.
 *
 * This type determines how Kernel treats a ProductUnit regarding lifecycle profiles,
 * governance requirements, and provider selection. Product-neutral naming is used
 * to avoid coupling to specific product implementations.
 *
 * @doc.type type
 * @doc.purpose ProductUnit classification for governance and lifecycle
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

/**
 * The kind of a ProductUnit, determining its governance and lifecycle treatment.
 */
export type ProductUnitKind =
  | "business-product"
  | "platform-provider"
  | "shared-service"
  | "demo-example"
  | "domain-pack"
  | "sdk"
  | "plugin"
  | "data-pipeline"
  | "agent-runtime"
  | "external-application";

export const PRODUCT_UNIT_KINDS = [
  "business-product",
  "platform-provider",
  "shared-service",
  "demo-example",
  "domain-pack",
  "sdk",
  "plugin",
  "data-pipeline",
  "agent-runtime",
  "external-application",
] as const satisfies readonly ProductUnitKind[];

/**
 * Type guard to check if a string is a valid ProductUnitKind.
 */
export function isProductUnitKind(value: unknown): value is ProductUnitKind {
  return typeof value === "string" && PRODUCT_UNIT_KINDS.includes(value as ProductUnitKind);
}

/**
 * Gets a human-readable label for a ProductUnitKind.
 */
export function getProductUnitKindLabel(kind: ProductUnitKind): string {
  const labels: Record<ProductUnitKind, string> = {
    "business-product": "Business Product",
    "platform-provider": "Platform Provider",
    "shared-service": "Shared Service",
    "demo-example": "Demo Example",
    "domain-pack": "Domain Pack",
    sdk: "SDK",
    plugin: "Plugin",
    "data-pipeline": "Data Pipeline",
    "agent-runtime": "Agent Runtime",
    "external-application": "External Application",
  };
  return labels[kind];
}
