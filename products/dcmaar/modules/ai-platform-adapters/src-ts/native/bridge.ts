import { getNativeBinding, getNativeBindingError } from './binding';

export interface BridgeStats {
  batchesProcessed: number;
  eventsProcessed: number;
  usingRealClient: boolean;
  startedAt: string;
  uptimeMs: number;
  lastError?: string | null;
}

export interface NativeBridge {
  submitBatch(batchJson: string): Promise<number>;
  submitEvent(eventJson: string): Promise<void>;
  getStats(): Promise<string> | string;
  healthCheck(): Promise<boolean>;
  getVersion(): string;
}

type NativeModule = {
  AgentBridge?: new () => NativeBridge;
};

let cachedCtor: (new () => NativeBridge) | null = null;
let lastLoadError: unknown | null = null;

function resolveConstructor(): (new () => NativeBridge) | null {
  if (cachedCtor) {
    return cachedCtor;
  }

  const module = getNativeBinding() as NativeModule | null;

  if (!module) {
    lastLoadError = getNativeBindingError();
    return null;
  }

  if (!module || typeof module.AgentBridge !== 'function') {
    lastLoadError = new Error('AgentBridge export was not found on native module');
    return null;
  }

  cachedCtor = module.AgentBridge;
  return cachedCtor;
}

export function createNativeBridge(): NativeBridge | null {
  const ctor = resolveConstructor();
  if (!ctor) {
    return null;
  }

  try {
    return new ctor();
  } catch (error) {
    lastLoadError = error;
    return null;
  }
}

export function getLastBridgeError(): unknown | null {
  return lastLoadError;
}

export async function parseBridgeStats(
  bridge: NativeBridge
): Promise<BridgeStats | null> {
  try {
    const stats = await bridge.getStats();
    if (typeof stats === 'string') {
      return JSON.parse(stats) as BridgeStats;
    }
    return stats as unknown as BridgeStats;
  } catch {
    return null;
  }
}
