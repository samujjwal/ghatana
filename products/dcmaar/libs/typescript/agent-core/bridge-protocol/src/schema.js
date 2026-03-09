import { z } from 'zod';
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
    bridgeVersion: z.string().optional(),
    workspaceId: z.string().optional(),
}).extend({
// Allow additional properties
}).catchall(z.unknown());
export const telemetryPayloadSchema = z.object({
    batchId: z.string().min(1),
    collectedAt: z.string().datetime(),
    data: z.record(z.unknown()),
    alerts: z.array(z.record(z.unknown())).optional(),
    meta: z.record(z.unknown()).optional(),
});
export const commandPayloadSchema = z.object({
    commandId: z.string().min(1),
    category: z.union([
        z.literal('config'),
        z.literal('policy'),
        z.literal('action'),
        z.literal('script'),
    ]),
    body: z.record(z.unknown()),
    priority: z.union([
        z.literal('low'),
        z.literal('medium'),
        z.literal('high'),
        z.literal('urgent'),
    ]),
    requestedAt: z.string().datetime(),
    requestedBy: z.string().optional(),
});
export const ackPayloadSchema = z.object({
    ok: z.boolean(),
    reason: z.string().optional(),
    correlationId: z.string().optional(),
    receivedAt: z.string().datetime(),
    details: z.record(z.unknown()).optional(),
});
export const heartbeatPayloadSchema = z.object({
    sequence: z.number().nonnegative(),
    reportedAt: z.string().datetime(),
    status: z.string().optional(),
    meta: z.record(z.unknown()).optional(),
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
