import type { ZodType } from 'zod';
import { zodToJsonSchema } from 'zod-to-json-schema';

export function toJsonSchema(schema: ZodType<unknown>): any {
  return zodToJsonSchema(schema as never);
}
