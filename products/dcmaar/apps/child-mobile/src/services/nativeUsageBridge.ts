// Native usage/blocks bridge for child-mobile.
// Backed by platform-specific native modules (Android/iOS) when available.
// Callers should treat a non-empty array as native data and `undefined` as
// "no native data" and fall back to existing HTTP/gRPC API hooks. Errors and
// empty arrays are normalized to `undefined` here to keep hooks simple.

export interface NativeUsageSession {
    id: string;
    deviceId: string;
    childId: string;
    itemName: string;
    durationSeconds: number;
    timestamp: string;
}

export interface NativeBlockEvent {
    id: string;
    deviceId: string;
    childId: string;
    reason: string;
    timestamp: string;
}

interface RNUsageModule {
    getUsageOverview(startTime: number, endTime: number): Promise<NativeUsageSession[]>;
}

interface RNBlockModule {
    getBlockEvents(): Promise<NativeBlockEvent[]>;
}

interface RNChildNativeModules {
    RNUsageModule?: RNUsageModule;
    RNBlockModule?: RNBlockModule;
}

let usageModule: RNUsageModule | null = null;
let blockModule: RNBlockModule | null = null;

try {
    const { NativeModules } = require('react-native');
    const modules = NativeModules as RNChildNativeModules;
    usageModule = modules.RNUsageModule ?? null;
    blockModule = modules.RNBlockModule ?? null;
} catch {
    usageModule = null;
    blockModule = null;
}

export async function getNativeUsageOverview(): Promise<NativeUsageSession[] | undefined> {
    if (!usageModule) {
        return undefined;
    }

    const startOfDay = new Date();
    startOfDay.setHours(0, 0, 0, 0);
    const now = Date.now();

    try {
        const sessions = await usageModule.getUsageOverview(startOfDay.getTime(), now);
        if (!sessions || sessions.length === 0) {
            return undefined;
        }
        return sessions;
    } catch {
        return undefined;
    }
}

export async function getNativeBlockEvents(): Promise<NativeBlockEvent[] | undefined> {
    if (!blockModule) {
        return undefined;
    }

    try {
        const events = await blockModule.getBlockEvents();
        if (!events || events.length === 0) {
            return undefined;
        }
        return events;
    } catch {
        return undefined;
    }
}
