import type { Ack } from './interfaces';

export function isNegativeAck(
  ack: Ack
): ack is Extract<Ack, { ok: false; error: string; retryable?: boolean }> {
  return ack.ok === false;
}
