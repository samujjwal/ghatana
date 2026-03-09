/**
 * Core TypeScript interfaces for the telemetry pipeline as per the extension-telemetry-plan.md
 */

export type TelemetryItemType = 'event' | 'metric' | 'timing' | 'log';

export interface TelemetryItem {
  id: string; // uuid
  type: TelemetryItemType;
  name: string;
  ts: number; // epoch ms
  payload: Record<string, unknown>;
}

export interface TelemetryEnvelope {
  id: string; // uuid for the envelope
  sourceId: string; // extension instance id
  ts: number; // envelope creation time
  items: TelemetryItem[];
  meta?: { sdkVersion?: string; env?: string };
}

export interface Config {
  sinkUrl: string;
  authToken?: string;
  batchSize: number;
  batchIntervalMs: number;
  maxBufferItems: number;
  retry: { maxAttempts: number; baseMs: number; factor: number };
}

// Metric primitives (derived by Processor)
export interface Counter {
  name: string;
  value: number;
}

export interface Gauge {
  name: string;
  value: number;
}

export interface Histogram {
  name: string;
  buckets: number[];
  counts: number[];
}

export interface Timing {
  name: string;
  startTs: number;
  endTs: number;
  durationMs: number;
}

// Raw event types from content script
export interface RawEvent {
  type: string;
  timestamp: number;
  data: Record<string, unknown>;
}

// Processing result
export interface ProcessingResult {
  items: TelemetryItem[];
  metrics: {
    counters: Counter[];
    gauges: Gauge[];
    histograms: Histogram[];
    timings: Timing[];
  };
}
