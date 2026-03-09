// Browser-compatible UUID generation
function randomUUID() {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  // Fallback for older browsers
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

import { bridgeEnvelopeSchema } from "./schema";
export function createEnvelope(options) {
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
export function createAckEnvelope(input) {
  const payload = {
    ok: input.ok ?? true,
    reason: input.reason,
    receivedAt: new Date().toISOString(),
    details: input.details,
  };
  return createEnvelope({
    payload,
    direction: input.direction,
    kind: "ack",
    correlationId: input.correlationId,
    metadata: input.metadata,
  });
}
export function validateEnvelope(envelope) {
  return bridgeEnvelopeSchema.parse(envelope);
}
export function mergeEnvelopeUpdate(envelope, update) {
  return {
    ...envelope,
    ...update,
    payload: update.payload
      ? { ...envelope.payload, ...update.payload }
      : envelope.payload,
    metadata: update.metadata
      ? { ...envelope.metadata, ...update.metadata }
      : envelope.metadata,
    signature: update.signature ?? envelope.signature,
  };
}
