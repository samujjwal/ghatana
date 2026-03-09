// Core type definitions

export interface BatchEnvelope<T = unknown> {
  batch: T[];
  timestamp: number;
  metadata?: Record<string, unknown>;
}

export interface Signed<T = unknown> {
  payload: T;
  signature: string;
  publicKey?: string;
  algorithm?: string;
  timestamp?: number;
}
