/**
 * Telemetry Service
 *
 * Batches and sends GuardianEvents to the backend events API.
 * Implements the Sprint 5 connector-based telemetry & error reporting design.
 *
 * @module services/telemetryService
 */

import { atom, createStore } from "jotai";
// Note: @react-native-community/netinfo must be installed as a dependency
// npm install @react-native-community/netinfo
// For now, we use a fallback if not available
let NetInfo: any;
try {
  NetInfo = require("@react-native-community/netinfo").default;
} catch {
  // Fallback for environments without NetInfo
  NetInfo = {
    addEventListener: () => () => {},
    fetch: () => Promise.resolve({ isConnected: true }),
  };
}

/**
 * Guardian event envelope (matches backend GuardianEvent contract).
 */
export interface GuardianEventPayload {
  schema_version: number;
  event_id: string;
  kind: "usage" | "block" | "policy" | "system" | "alert";
  subtype: string;
  occurred_at: string;
  source: {
    agent_type: string;
    agent_version: string;
    device_id: string;
    child_id?: string;
    session_id?: string;
  };
  context?: Record<string, unknown>;
  payload?: Record<string, unknown>;
  privacy?: {
    pii_level: "none" | "low" | "medium" | "high";
    contains_raw_content: boolean;
  };
}

/**
 * Telemetry service configuration.
 */
export interface TelemetryConfig {
  apiBaseUrl: string;
  deviceId: string;
  childId?: string;
  agentType?: string;
  agentVersion?: string;
  getAuthToken: () => string | null;
  batchSize?: number;
  flushIntervalMs?: number;
  maxBufferSize?: number;
  bufferWhenOffline?: boolean;
}

/**
 * Telemetry service state.
 */
interface TelemetryState {
  buffer: GuardianEventPayload[];
  isOnline: boolean;
  lastFlushTime: number | null;
  totalSent: number;
  totalErrors: number;
}

const DEFAULT_BATCH_SIZE = 10;
const DEFAULT_FLUSH_INTERVAL_MS = 30000;
const DEFAULT_MAX_BUFFER_SIZE = 100;

const telemetryStore = createStore();
const bufferAtom = atom<GuardianEventPayload[]>([]);
const isOnlineAtom = atom(true);
const lastFlushAtom = atom<number | null>(null);
const totalSentAtom = atom(0);
const totalErrorsAtom = atom(0);

/** Zustand-compatible singleton accessor. */
export const useTelemetryStore = {
  getState: (): TelemetryState => ({
    buffer: telemetryStore.get(bufferAtom),
    isOnline: telemetryStore.get(isOnlineAtom),
    lastFlushTime: telemetryStore.get(lastFlushAtom),
    totalSent: telemetryStore.get(totalSentAtom),
    totalErrors: telemetryStore.get(totalErrorsAtom),
  }),
  setState: (partial: Partial<TelemetryState>) => {
    if (partial.buffer !== undefined)
      telemetryStore.set(bufferAtom, partial.buffer);
    if (partial.isOnline !== undefined)
      telemetryStore.set(isOnlineAtom, partial.isOnline);
    if (partial.lastFlushTime !== undefined)
      telemetryStore.set(lastFlushAtom, partial.lastFlushTime);
    if (partial.totalSent !== undefined)
      telemetryStore.set(totalSentAtom, partial.totalSent);
    if (partial.totalErrors !== undefined)
      telemetryStore.set(totalErrorsAtom, partial.totalErrors);
  },
};

// Private state
let config: TelemetryConfig | null = null;
let flushInterval: ReturnType<typeof setInterval> | null = null;
let netInfoUnsubscribe: (() => void) | null = null;

/**
 * Initialize the telemetry service.
 */
export function initTelemetry(telemetryConfig: TelemetryConfig): void {
  config = {
    ...telemetryConfig,
    agentType: telemetryConfig.agentType ?? "mobile_agent",
    agentVersion: telemetryConfig.agentVersion ?? "1.0.0",
    batchSize: telemetryConfig.batchSize ?? DEFAULT_BATCH_SIZE,
    flushIntervalMs:
      telemetryConfig.flushIntervalMs ?? DEFAULT_FLUSH_INTERVAL_MS,
    maxBufferSize: telemetryConfig.maxBufferSize ?? DEFAULT_MAX_BUFFER_SIZE,
    bufferWhenOffline: telemetryConfig.bufferWhenOffline ?? true,
  };
}

/**
 * Start the telemetry service (periodic flush and network monitoring).
 */
export function startTelemetry(): void {
  if (!config) {
    console.error("[TelemetryService] Not initialized");
    return;
  }

  // Start periodic flush
  flushInterval = setInterval(() => {
    void flushEvents();
  }, config.flushIntervalMs);

  // Monitor network state
  netInfoUnsubscribe = NetInfo.addEventListener(
    (state: { isConnected: boolean | null }) => {
      const isOnline = state.isConnected ?? false;
      useTelemetryStore.setState({ isOnline });

      // Flush when coming back online
      if (isOnline) {
        void flushEvents();
      }
    },
  );

  // Check initial network state
  NetInfo.fetch().then((state: { isConnected: boolean | null }) => {
    useTelemetryStore.setState({ isOnline: state.isConnected ?? false });
  });
}

/**
 * Stop the telemetry service.
 */
export async function stopTelemetry(): Promise<void> {
  if (flushInterval) {
    clearInterval(flushInterval);
    flushInterval = null;
  }

  if (netInfoUnsubscribe) {
    netInfoUnsubscribe();
    netInfoUnsubscribe = null;
  }

  // Final flush
  await flushEvents();
}

/**
 * Send a raw event (adds to buffer).
 */
export async function sendEvent(event: GuardianEventPayload): Promise<void> {
  if (!config) {
    return;
  }

  const state = useTelemetryStore.getState();
  const newBuffer = [...state.buffer, event];
  if (newBuffer.length > config.maxBufferSize!) {
    newBuffer.shift();
  }
  useTelemetryStore.setState({ buffer: newBuffer });

  // Auto-flush if batch size reached
  if (useTelemetryStore.getState().buffer.length >= config.batchSize!) {
    await flushEvents();
  }
}

/**
 * Send a custom event with automatic envelope wrapping.
 */
export async function sendCustomEvent(partial: {
  kind: GuardianEventPayload["kind"];
  subtype: string;
  context?: Record<string, unknown>;
  payload?: Record<string, unknown>;
  piiLevel?: "none" | "low" | "medium" | "high";
}): Promise<void> {
  if (!config) {
    return;
  }

  const event: GuardianEventPayload = {
    schema_version: 1,
    event_id: `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    kind: partial.kind,
    subtype: partial.subtype,
    occurred_at: new Date().toISOString(),
    source: {
      agent_type: config.agentType!,
      agent_version: config.agentVersion!,
      device_id: config.deviceId,
      child_id: config.childId,
    },
    context: partial.context,
    payload: partial.payload,
    privacy: {
      pii_level: partial.piiLevel ?? "none",
      contains_raw_content: false,
    },
  };

  await sendEvent(event);
}

/**
 * Send a command execution event.
 */
export async function sendCommandEvent(
  commandId: string,
  status: "started" | "completed" | "failed",
  errorReason?: string,
): Promise<void> {
  await sendCustomEvent({
    kind: "system",
    subtype: `command_execution_${status}`,
    context: { command_id: commandId },
    payload: { status, error_reason: errorReason },
  });
}

/**
 * Send an agent error event.
 */
export async function sendErrorEvent(
  errorType: string,
  message: string,
  details?: Record<string, unknown>,
): Promise<void> {
  await sendCustomEvent({
    kind: "system",
    subtype: "agent_error",
    payload: { error_type: errorType, message, details },
  });
}

/**
 * Flush buffered events to backend.
 */
export async function flushEvents(): Promise<void> {
  if (!config) {
    return;
  }

  const state = useTelemetryStore.getState();

  if (state.buffer.length === 0) {
    return;
  }

  if (!state.isOnline && config.bufferWhenOffline) {
    console.debug("[TelemetryService] Offline, skipping flush");
    return;
  }

  const token = config.getAuthToken();
  if (!token) {
    console.warn("[TelemetryService] No auth token, skipping flush");
    return;
  }

  // Take current buffer and clear
  const events = [...state.buffer];
  useTelemetryStore.setState({ buffer: [] });

  try {
    const url = `${config.apiBaseUrl}/api/events`;

    const response = await fetch(url, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ events }),
    });

    if (!response.ok) {
      throw new Error(`Events API returned ${response.status}`);
    }

    const currentState = useTelemetryStore.getState();
    useTelemetryStore.setState({
      lastFlushTime: Date.now(),
      totalSent: currentState.totalSent + events.length,
    });

    console.debug(`[TelemetryService] Flushed ${events.length} events`);
  } catch (error) {
    console.error("[TelemetryService] Flush error:", error);

    const currentState = useTelemetryStore.getState();
    useTelemetryStore.setState({
      totalErrors: currentState.totalErrors + 1,
    });

    // Re-buffer events on failure (up to max)
    if (config.bufferWhenOffline) {
      const bufferedState = useTelemetryStore.getState();
      const remaining = config.maxBufferSize! - bufferedState.buffer.length;
      if (remaining > 0) {
        useTelemetryStore.setState({
          buffer: [...events.slice(0, remaining), ...bufferedState.buffer],
        });
      }
    }
  }
}

/**
 * Get current buffer size.
 */
export function getBufferSize(): number {
  return useTelemetryStore.getState().buffer.length;
}
