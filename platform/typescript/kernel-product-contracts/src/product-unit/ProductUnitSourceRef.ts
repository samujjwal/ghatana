/**
 * ProductUnitSourceRef - source acquisition reference for ProductUnits.
 *
 * @doc.type interface
 * @doc.purpose Reference monorepo, external repository, archive, generated artifact, or source provider input
 * @doc.layer kernel-product-contracts
 * @doc.pattern ValueObject
 */

import { z } from "zod";

export const PRODUCT_UNIT_SOURCE_REF_KINDS = [
  "monorepo-path",
  "github-ref",
  "gitlab-ref",
  "archive",
  "generated-artifact",
  "external-source-provider",
] as const;

export type ProductUnitSourceRefKind =
  (typeof PRODUCT_UNIT_SOURCE_REF_KINDS)[number];

export interface ProductUnitSourceRef {
  readonly kind: ProductUnitSourceRefKind;
  readonly ref: string;
  readonly displayName?: string | undefined;
  readonly providerId?: string | undefined;
  readonly digest?: string | undefined;
  readonly acquiredAt?: string | undefined;
  readonly metadata?: Record<string, unknown> | undefined;
}

const NonEmptyStringSchema = z.string().trim().min(1);

export const ProductUnitSourceRefSchema = z
  .object({
    kind: z.enum(PRODUCT_UNIT_SOURCE_REF_KINDS),
    ref: NonEmptyStringSchema,
    displayName: NonEmptyStringSchema.optional(),
    providerId: NonEmptyStringSchema.optional(),
    digest: NonEmptyStringSchema.optional(),
    acquiredAt: z.string().datetime({ offset: true }).optional(),
    metadata: z.record(z.string(), z.unknown()).optional(),
  })
  .strict()
  .superRefine((sourceRef, context) => {
    if (
      sourceRef.kind === "external-source-provider" &&
      sourceRef.providerId === undefined
    ) {
      context.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["providerId"],
        message: "external-source-provider source refs require providerId",
      });
    }
  });

export function isProductUnitSourceRef(
  value: unknown,
): value is ProductUnitSourceRef {
  return ProductUnitSourceRefSchema.safeParse(value).success;
}
