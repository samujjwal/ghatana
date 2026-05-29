/**
 * K-006: Kernel request correlation primitive.
 * Shared web/mobile/backend correlation ID shape and propagation helpers.
 */

import { z } from "zod";

export const CorrelationIdSchema = z.string().trim().min(1).max(127);
export const CorrelationSourceSchema = z.enum(['web', 'mobile', 'backend', 'external']);

export const CorrelationContextSchema = z
  .object({
    correlationId: CorrelationIdSchema,
    tenantId: z.string().trim().min(1).optional(),
    principalId: z.string().trim().min(1).optional(),
    sessionId: z.string().trim().min(1).optional(),
    timestamp: z.string().trim().datetime(),
    source: CorrelationSourceSchema,
  })
  .strict();

export const CorrelationHeadersSchema = z
  .object({
    'X-Correlation-ID': CorrelationIdSchema,
    'X-Tenant-ID': z.string().trim().min(1).optional(),
    'X-Principal-ID': z.string().trim().min(1).optional(),
    'X-Session-ID': z.string().trim().min(1).optional(),
    'X-Source': CorrelationSourceSchema.optional(),
  })
  .strict();

export type CorrelationId = string;

export type CorrelationContext = z.infer<typeof CorrelationContextSchema>;

export type CorrelationHeaders = z.infer<typeof CorrelationHeadersSchema>;

export function generateCorrelationId(): CorrelationId {
  const timestamp = Date.now().toString(36);
  const random = Math.random().toString(36).substring(2, 11);
  return `${timestamp}-${random}`;
}

export function createCorrelationContext(
  correlationId?: CorrelationId,
  tenantId?: string,
  principalId?: string,
  sessionId?: string,
  source: CorrelationContext['source'] = 'web'
): CorrelationContext {
  const result: CorrelationContext = {
    correlationId: correlationId || generateCorrelationId(),
    timestamp: new Date().toISOString(),
    source,
  };
  
  if (tenantId) result.tenantId = tenantId;
  if (principalId) result.principalId = principalId;
  if (sessionId) result.sessionId = sessionId;
  
  return result;
}

export function correlationContextToHeaders(context: CorrelationContext): CorrelationHeaders {
  const headers: CorrelationHeaders = {
    'X-Correlation-ID': context.correlationId,
  };
  
  if (context.tenantId) headers['X-Tenant-ID'] = context.tenantId;
  if (context.principalId) headers['X-Principal-ID'] = context.principalId;
  if (context.sessionId) headers['X-Session-ID'] = context.sessionId;
  headers['X-Source'] = context.source;
  
  return headers;
}

export function headersToCorrelationContext(headers: Headers | Record<string, string>): CorrelationContext | null {
  const correlationId = headers instanceof Headers 
    ? headers.get('X-Correlation-ID')
    : headers['X-Correlation-ID'];
  
  if (!correlationId) return null;
  
  const tenantId = headers instanceof Headers 
    ? headers.get('X-Tenant-ID')
    : headers['X-Tenant-ID'];
  const principalId = headers instanceof Headers 
    ? headers.get('X-Principal-ID')
    : headers['X-Principal-ID'];
  const sessionId = headers instanceof Headers 
    ? headers.get('X-Session-ID')
    : headers['X-Session-ID'];
  const source = (headers instanceof Headers 
    ? headers.get('X-Source')
    : headers['X-Source']) as CorrelationContext['source'] || 'web';
  
  const result: CorrelationContext = {
    correlationId,
    timestamp: new Date().toISOString(),
    source,
  };
  
  if (tenantId) result.tenantId = tenantId;
  if (principalId) result.principalId = principalId;
  if (sessionId) result.sessionId = sessionId;
  
  return result;
}

export function isValidCorrelationId(value: unknown): value is CorrelationId {
  return CorrelationIdSchema.safeParse(value).success;
}

export function validateCorrelationContext(value: unknown): value is CorrelationContext {
  return CorrelationContextSchema.safeParse(value).success;
}

export function validateCorrelationHeaders(value: unknown): value is CorrelationHeaders {
  return CorrelationHeadersSchema.safeParse(value).success;
}
