import { z } from "zod";

export interface ProductArtifact {
  readonly type: string;
  readonly packaging?: string;
  readonly required?: boolean;
  readonly paths?: readonly string[];
}

export const ProductArtifactSchema = z
  .object({
    type: z.string().trim().min(1),
    packaging: z.string().trim().min(1).optional(),
    required: z.boolean().optional(),
    paths: z.array(z.string().trim().min(1)).optional(),
  })
  .strict();

export function validateProductArtifact(
  value: unknown
): value is ProductArtifact {
  return ProductArtifactSchema.safeParse(value).success;
}
