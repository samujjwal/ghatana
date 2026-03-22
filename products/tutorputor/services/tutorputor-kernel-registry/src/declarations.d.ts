declare module '@hono/zod-validator' {
  import type { z } from 'zod';
  export function zValidator(target: string, schema: z.ZodSchema): any;
}
