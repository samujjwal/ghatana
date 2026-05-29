import { z } from "zod";

export interface ProductGate {
  readonly gateId: string;
  readonly required: boolean;
  readonly phase: string;
}

export const ProductGateSchema = z
  .object({
    gateId: z.string().trim().min(1),
    required: z.boolean(),
    phase: z.string().trim().min(1),
  })
  .strict();

export function validateProductGate(value: unknown): value is ProductGate {
  return ProductGateSchema.safeParse(value).success;
}
