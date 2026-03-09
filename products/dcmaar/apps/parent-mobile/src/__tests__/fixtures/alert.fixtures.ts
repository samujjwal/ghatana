import type { Alert } from '@/types';

export const buildAlert = (overrides: Partial<Alert> = {}): Alert => ({
  id: overrides.id ?? `alert-${Math.random().toString(36).slice(2, 8)}`,
  type: overrides.type ?? 'policy_violation',
  deviceId: overrides.deviceId ?? 'device-1',
  message: overrides.message ?? 'Blocked attempt to open YouTube',
  timestamp: overrides.timestamp ?? new Date(),
  read: overrides.read ?? false,
  severity: overrides.severity ?? 'warning',
});

export const alertsFixture: Alert[] = [
  buildAlert({ id: 'alert-1', message: 'Blocked TikTok', severity: 'critical' }),
  buildAlert({ id: 'alert-2', type: 'device_offline', message: 'Pixel 7 went offline', severity: 'warning' }),
  buildAlert({ id: 'alert-3', type: 'battery_low', message: 'iPad Mini battery low', severity: 'info', read: true }),
];
