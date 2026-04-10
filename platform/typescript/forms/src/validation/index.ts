import { z } from 'zod';

/**
 * Creates a Zod-validated form schema factory.
 * Use with react-hook-form's zodResolver.
 */
export function createFormSchema<T extends z.ZodTypeAny>(schema: T): T {
  return schema;
}

export { z };
export type { ZodSchema } from 'zod';
