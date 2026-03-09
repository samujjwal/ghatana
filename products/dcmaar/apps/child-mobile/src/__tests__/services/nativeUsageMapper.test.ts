import { mapNativeUsageToDevices, mapNativeBlocksToAlerts } from '@/services/nativeUsageMapper';
import type { NativeUsageSession, NativeBlockEvent } from '@/services/nativeUsageBridge';
import type { Device, Alert } from '@/types';

describe('nativeUsageMapper', () => {
    it('maps native usage sessions to unique devices', () => {
        const sessions: NativeUsageSession[] = [
            {
                id: 's1',
                deviceId: 'device-1',
                childId: 'child-1',
                itemName: "Emma's iPhone",
                durationSeconds: 300,
                timestamp: '2024-01-01T10:00:00Z',
            },
            {
                id: 's2',
                deviceId: 'device-1',
                childId: 'child-1',
                itemName: "Emma's iPhone",
                durationSeconds: 120,
                timestamp: '2024-01-01T11:00:00Z',
            },
            {
                id: 's3',
                deviceId: 'device-2',
                childId: 'child-2',
                itemName: "Liam's Android",
                durationSeconds: 60,
                timestamp: '2024-01-01T09:00:00Z',
            },
        ];

        const devices = mapNativeUsageToDevices(sessions);

        expect(devices).toHaveLength(2);

        const device1 = devices.find((d: Device) => d.id === 'device-1');
        const device2 = devices.find((d: Device) => d.id === 'device-2');

        expect(device1).toBeDefined();
        expect(device1?.childName).toBe('child-1');
        expect(device1?.name).toBe("Emma's iPhone");

        expect(device2).toBeDefined();
        expect(device2?.childName).toBe('child-2');
        expect(device2?.name).toBe("Liam's Android");
    });

    it('maps native block events to alerts', () => {
        const events: NativeBlockEvent[] = [
            {
                id: 'b1',
                deviceId: 'device-1',
                childId: 'child-1',
                reason: 'Blocked Instagram',
                timestamp: '2024-01-01T12:00:00Z',
            },
            {
                id: 'b2',
                deviceId: 'device-2',
                childId: 'child-2',
                reason: 'Blocked YouTube',
                timestamp: '2024-01-01T13:00:00Z',
            },
        ];

        const alerts = mapNativeBlocksToAlerts(events);

        expect(alerts).toHaveLength(2);

        const alert1 = alerts.find((a: Alert) => a.id === 'b1');
        const alert2 = alerts.find((a: Alert) => a.id === 'b2');

        expect(alert1).toBeDefined();
        expect(alert1?.type).toBe('policy_violation');
        expect(alert1?.deviceId).toBe('device-1');
        expect(alert1?.message).toBe('Blocked Instagram');

        expect(alert2).toBeDefined();
        expect(alert2?.type).toBe('policy_violation');
        expect(alert2?.deviceId).toBe('device-2');
        expect(alert2?.message).toBe('Blocked YouTube');
    });
});
