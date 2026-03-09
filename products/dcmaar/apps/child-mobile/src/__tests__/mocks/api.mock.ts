import { deviceListFixture } from '../fixtures/device.fixtures';
import { policiesFixture, buildPolicy } from '../fixtures/policy.fixtures';
import { alertsFixture } from '../fixtures/alert.fixtures';
import type { Policy } from '@/types';

const clone = <T>(payload: T): T => JSON.parse(JSON.stringify(payload));

export const apiMock = {
  getDevices: jest.fn(async () => clone(deviceListFixture)),
  getUsageData: jest.fn(async () => []),
  getPolicies: jest.fn(async (deviceId?: string) => {
    if (!deviceId) return clone(policiesFixture);
    return clone(policiesFixture.filter(policy => policy.deviceId === deviceId));
  }),
  createPolicy: jest.fn(async (policy: Omit<Policy, 'id'>) =>
    buildPolicy({ ...policy, id: 'policy-new' })
  ),
  updatePolicy: jest.fn(async (id: string, updates: Partial<Policy>) => {
    const existing = policiesFixture.find(policy => policy.id === id) ?? buildPolicy({ id });
    return buildPolicy({ ...existing, ...updates });
  }),
  deletePolicy: jest.fn(async () => undefined),
  getAlerts: jest.fn(async () => clone(alertsFixture)),
  markAlertRead: jest.fn(async () => undefined),
};

export const api = apiMock;
export default { api, apiMock };
