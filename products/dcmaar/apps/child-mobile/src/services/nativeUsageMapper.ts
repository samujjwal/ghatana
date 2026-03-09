import type { Device, Alert } from '@/types';
import type { NativeUsageSession, NativeBlockEvent } from './nativeUsageBridge';

export function mapNativeUsageToDevices(sessions: NativeUsageSession[]): Device[] {
    const byDevice = new Map<string, NativeUsageSession>();

    sessions.forEach((session) => {
        if (!byDevice.has(session.deviceId)) {
            byDevice.set(session.deviceId, session);
        }
    });

    return Array.from(byDevice.values()).map((session) => ({
        id: session.deviceId,
        name: session.itemName || 'Child device',
        type: 'android',
        status: 'online',
        lastSync: new Date(session.timestamp),
        childName: session.childId,
    }));
}

export function mapNativeBlocksToAlerts(events: NativeBlockEvent[]): Alert[] {
    return events.map((event) => ({
        id: event.id,
        type: 'policy_violation',
        deviceId: event.deviceId,
        message: event.reason,
        timestamp: new Date(event.timestamp),
        read: false,
        severity: 'warning',
    }));
}
