import { z } from "zod";

export const ProductEnvironmentSchema = z
  .object({
    name: z.string().trim().min(1),
    target: z.string().trim().min(1).optional(),
    variables: z.record(z.string(), z.string()).optional(),
  })
  .strict();

export interface ProductEnvironment {
  readonly name: string;
  readonly target?: string;
  readonly variables?: Readonly<Record<string, string>>;
}

export function validateProductEnvironment(value: unknown): value is ProductEnvironment {
  return ProductEnvironmentSchema.safeParse(value).success;
}
