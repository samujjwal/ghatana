import { z } from "zod";

export const ProductDeploymentSchema = z
  .object({
    target: z.string().trim().min(1),
    environment: z.string().trim().min(1),
  })
  .strict();

export interface ProductDeployment {
  readonly target: string;
  readonly environment: string;
}

export function validateProductDeployment(value: unknown): value is ProductDeployment {
  return ProductDeploymentSchema.safeParse(value).success;
}
