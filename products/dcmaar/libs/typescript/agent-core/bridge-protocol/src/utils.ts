import { randomUUID } from 'node:crypto';
import { bridgeEnvelopeSchema } from './schema';
import type {
  AckEnvelopeInput,
  BridgeEnvelope,
  BridgeEnvelopeUpdate,
  BridgeMetadata,
  BridgeSignature,
  BridgeDirection,
  BridgeMessageKind,
} from './types';

export function createEnvelope<TPayload extends object>(
  options: {
    payload: TPayload;
    direction: BridgeDirection;
    kind: BridgeMessageKind;
    correlationId?: string;
    metadata?: BridgeMetadata;
    signature?: BridgeSignature;
    id?: string;
    issuedAt?: string;
  },
): BridgeEnvelope<TPayload> {
  return {
    id: options.id ?? randomUUID(),
    issuedAt: options.issuedAt ?? new Date().toISOString(),
    direction: options.direction,
    kind: options.kind,
    payload: options.payload,
    correlationId: options.correlationId,
    metadata: options.metadata,
    signature: options.signature,
  };
}

export function createAckEnvelope(
  input: AckEnvelopeInput,
): BridgeEnvelope<{ ok: boolean; reason?: string; receivedAt: string; details?: Record<string, unknown> }> {
  const payload = {
    ok: input.ok ?? true,
    reason: input.reason,
    receivedAt: new Date().toISOString(),
    details: input.details,
  };

  return createEnvelope({
    payload,
    direction: input.direction,
    kind: 'ack',
    correlationId: input.correlationId,
    metadata: input.metadata,
  });
}

export function validateEnvelope(envelope: unknown) {
  return bridgeEnvelopeSchema.parse(envelope);
}

export function mergeEnvelopeUpdate<TPayload extends object>(
  envelope: BridgeEnvelope<TPayload>,
  update: BridgeEnvelopeUpdate<TPayload>,
): BridgeEnvelope<TPayload> {
  return {
    ...envelope,
    ...update,
    payload: update.payload ? { ...envelope.payload, ...update.payload } : envelope.payload,
    metadata: update.metadata ? { ...envelope.metadata, ...update.metadata } : envelope.metadata,
    signature: update.signature ?? envelope.signature,
  };
}
