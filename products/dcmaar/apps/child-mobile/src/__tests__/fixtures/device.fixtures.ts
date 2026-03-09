import type { Device } from '@/types';

export const buildDevice = (overrides: Partial<Device> = {}): Device => ({
  id: overrides.id ?? `device-${Math.random().toString(36).slice(2, 8)}`,
  name: overrides.name ?? 'Child Tablet',
  type: overrides.type ?? 'android',
  status: overrides.status ?? 'online',
  lastSync: overrides.lastSync ?? new Date(),
  childName: overrides.childName ?? 'Jamie',
  batteryLevel: overrides.batteryLevel ?? 76,
  location: overrides.location ?? {
    latitude: 37.7749,
    longitude: -122.4194,
    address: 'San Francisco, CA',
  },
});

export const deviceListFixture: Device[] = [
  buildDevice({ id: 'device-0', name: 'Child Tablet', type: 'android' }),
  buildDevice({ id: 'device-1', name: 'iPad Mini', type: 'ios' }),
  buildDevice({ id: 'device-2', name: 'Pixel 7', type: 'android', status: 'offline', batteryLevel: 32 }),
  buildDevice({ id: 'device-3', name: 'Windows Laptop', type: 'windows', status: 'online', batteryLevel: 58 }),
];
