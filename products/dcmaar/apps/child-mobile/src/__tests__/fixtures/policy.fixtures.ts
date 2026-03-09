import type { Policy } from '@/types';

export const buildPolicy = (overrides: Partial<Policy> = {}): Policy => ({
  id: overrides.id ?? `policy-${Math.random().toString(36).slice(2, 8)}`,
  name: overrides.name ?? 'Social Media Lockdown',
  deviceId: overrides.deviceId ?? 'device-1',
  screenTimeLimit: overrides.screenTimeLimit ?? 120,
  blockedApps: overrides.blockedApps ?? ['com.social.app', 'com.video.app'],
  blockedWebsites: overrides.blockedWebsites ?? ['facebook.com', 'tiktok.com'],
  timeRestrictions:
    overrides.timeRestrictions ?? [
      { dayOfWeek: 1, startTime: '21:00', endTime: '07:00' },
      { dayOfWeek: 5, startTime: '22:00', endTime: '08:00' },
    ],
  enabled: overrides.enabled ?? true,
});

export const policiesFixture: Policy[] = [
  buildPolicy({ id: 'policy-1', name: 'Homework Mode', screenTimeLimit: 90 }),
  buildPolicy({ id: 'policy-2', name: 'Bedtime Lock', blockedApps: [], blockedWebsites: [], enabled: false }),
];
