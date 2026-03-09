import { z } from 'zod';

// Version negotiation constants
export const BRIDGE_PROTOCOL_VERSION = '1.0.0';
export const MIN_SUPPORTED_VERSION = '1.0.0';
export const SUPPORTED_VERSIONS = ['1.0.0'] as const;

// Handshake schema for version negotiation
export const handshakeSchema = z.object({
  version: z.string().regex(/^\d+\.\d+\.\d+$/, 'Must be valid semver'),
  // capabilities: map of capabilityName -> enabled
  capabilities: z.record(z.string(), z.boolean()).optional(),
  clientId: z.string().uuid().optional(),
  timestamp: z.string().datetime(),
});

export type HandshakePayload = z.infer<typeof handshakeSchema>;

export const bridgeDirectionSchema = z.union([
  z.literal('extension→desktop'),
  z.literal('desktop→extension'),
]);

export const bridgeMessageKindSchema = z.union([
  z.literal('telemetry'),
  z.literal('command'),
  z.literal('ack'),
  z.literal('heartbeat'),
]);

export const bridgeSignatureSchema = z.object({
  kid: z.string().min(1),
  value: z.string().min(16),
});

export const bridgeMetadataSchema = z.object({
  bridgeVersion: z.string().regex(/^\d+\.\d+\.\d+$/).optional(),
  workspaceId: z.string().uuid().optional(),
  sessionId: z.string().uuid().optional(),
  rateLimit: z.object({
    maxRequestsPerMinute: z.number().positive(),
    burstSize: z.number().positive(),
  }).optional(),
}).catchall(z.unknown());

export const telemetryPayloadSchema = z.object({
  batchId: z.string().uuid(),
  collectedAt: z.string().datetime(),
  data: z.record(z.string(), z.unknown()),
  alerts: z.array(z.record(z.string(), z.unknown())).optional(),
  meta: z.record(z.string(), z.unknown()).optional(),
  // Size constraints for backpressure handling
  estimatedSizeBytes: z.number().positive().optional(),
});

export const commandPayloadSchema = z.object({
  commandId: z.string().uuid(),
  category: z.union([
    z.literal('config'),
    z.literal('policy'),
    z.literal('action'),
    z.literal('script'),
  ]),
  body: z.record(z.string(), z.unknown()),
  priority: z.union([
    z.literal('low'),
    z.literal('medium'),
    z.literal('high'),
    z.literal('urgent'),
  ]),
  requestedAt: z.string().datetime(),
  requestedBy: z.string().optional(),
  timeoutMs: z.number().positive().optional(),
  retryPolicy: z.object({
    maxAttempts: z.number().int().positive(),
    backoffMs: z.number().positive(),
  }).optional(),
});

export const ackPayloadSchema = z.object({
  ok: z.boolean(),
  reason: z.string().optional(),
  correlationId: z.string().optional(),
  receivedAt: z.string().datetime(),
  details: z.record(z.string(), z.unknown()).optional(),
});

export const heartbeatPayloadSchema = z.object({
  sequence: z.number().nonnegative(),
  reportedAt: z.string().datetime(),
  status: z.string().optional(),
  meta: z.record(z.string(), z.unknown()).optional(),
});

export const bridgePayloadSchema = z.union([
  telemetryPayloadSchema,
  commandPayloadSchema,
  ackPayloadSchema,
  heartbeatPayloadSchema,
]);

export const bridgeEnvelopeSchema = z.object({
  id: z.string().uuid(),
  issuedAt: z.string().datetime(),
  direction: bridgeDirectionSchema,
  kind: bridgeMessageKindSchema,
  payload: bridgePayloadSchema,
  correlationId: z.string().uuid().optional(),
  metadata: bridgeMetadataSchema.optional(),
  signature: bridgeSignatureSchema.optional(),
});
