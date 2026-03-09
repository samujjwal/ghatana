import { getNativeUsageOverview, getNativeBlockEvents } from '@/services/nativeUsageBridge';
import type { NativeUsageSession, NativeBlockEvent } from '@/services/nativeUsageBridge';

export interface ChildUsageStats {
    totalScreenTimeSeconds: number;
    sessionCount: number;
    deviceIds: string[];
}

export async function getTodayUsageSessions(): Promise<NativeUsageSession[] | undefined> {
    return getNativeUsageOverview();
}

export async function getRecentBlockEvents(): Promise<NativeBlockEvent[] | undefined> {
    return getNativeBlockEvents();
}

export async function getTodayUsageStats(): Promise<ChildUsageStats | undefined> {
    const sessions = await getNativeUsageOverview();
    if (!sessions || sessions.length === 0) {
        return undefined;
    }

    const totalScreenTimeSeconds = sessions.reduce<number>((acc, session) => {
        return acc + session.durationSeconds;
    }, 0);

    const deviceIds = Array.from(new Set(sessions.map((session) => session.deviceId)));

    return {
        totalScreenTimeSeconds,
        sessionCount: sessions.length,
        deviceIds,
    };
}
