import { beforeAll, beforeEach, afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('@ghatana/dcmaar-browser-extension-core', () => import('../test/mocks/browserCoreMock'));

vi.mock('webextension-polyfill', () => {
  const api = {
    storage: {
      local: {
        get: vi.fn().mockResolvedValue({}),
        set: vi.fn().mockResolvedValue(undefined),
      },
      sync: {
        get: vi.fn().mockResolvedValue({}),
        set: vi.fn().mockResolvedValue(undefined),
      },
    },
    runtime: {
      getURL: vi.fn(),
      sendMessage: vi.fn(),
    },
    declarativeNetRequest: {
      updateDynamicRules: vi.fn(),
    },
    tabs: {
      create: vi.fn(),
      query: vi.fn(),
    },
  };

  return {
    default: api,
    ...api,
  };
});

import { WebsiteBlocker, WebsiteCategory, type BlockingPolicy } from '../blocker/WebsiteBlocker';

// storageMap will be available at runtime from the mock
let storageMap: any;

describe('WebsiteBlocker', () => {
  beforeAll(async () => {
    // Get storageMap from the mock at runtime
    const mock = await import('../test/mocks/browserCoreMock');
    storageMap = (mock as any).__storageStore;
  });

  beforeEach(() => {
    storageMap?.clear();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-03-25T15:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('allows whitelisted domains even when category is blocked', async () => {
    const blocker = new WebsiteBlocker();
    const policy: BlockingPolicy = {
      id: 'policy-1',
      name: 'Block social',
      enabled: true,
      blockedCategories: [WebsiteCategory.SOCIAL],
      blockedDomains: [],
      allowedDomains: ['instagram.com'],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };
    await blocker.savePolicies([policy]);

    const result = await blocker.shouldBlock('https://instagram.com/feed');
    expect(result.blocked).toBe(false);
  });

  it('blocks domains by category within time window', async () => {
    // Set to 2:00 PM local time on a Monday (March 25, 2024)
    // Using local time to match how WebsiteBlocker.isInTimeWindow() works
    const monday2PM = new Date('2024-03-25T14:00:00');
    vi.setSystemTime(monday2PM);

    const blocker = new WebsiteBlocker();
    const policy: BlockingPolicy = {
      id: 'policy-1',
      name: 'School hours block',
      enabled: true,
      blockedCategories: [WebsiteCategory.SOCIAL],
      blockedDomains: [],
      allowedDomains: [],
      timeWindows: [
        {
          daysOfWeek: [1, 2, 3, 4, 5], // Monday-Friday
          startMinutes: 8 * 60, // 8:00 AM
          endMinutes: 16 * 60, // 4:00 PM
          isBlocked: true,
        },
      ],
      blockReason: 'Blocked during school hours',
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    await blocker.savePolicies([policy]);

    const result = await blocker.shouldBlock('https://facebook.com/home');
    expect(result.blocked).toBe(true);
    expect(result.reason).toContain('school');

    const events = storageMap.get('guardian:block_events');
    expect(events).toHaveLength(1);
    expect(events[0].domain).toBe('facebook.com');
  });

  it('does not block outside of configured time windows', async () => {
    // Set to 10:00 PM local time on a Monday (outside the 8AM-4PM window)
    const monday10PM = new Date('2024-03-25T22:00:00');
    vi.setSystemTime(monday10PM);

    const blocker = new WebsiteBlocker();
    const policy: BlockingPolicy = {
      id: 'policy-1',
      name: 'School hours block',
      enabled: true,
      blockedCategories: [WebsiteCategory.SOCIAL],
      blockedDomains: [],
      allowedDomains: [],
      timeWindows: [
        {
          daysOfWeek: [1, 2, 3, 4, 5], // Monday-Friday
          startMinutes: 8 * 60, // 8:00 AM
          endMinutes: 16 * 60, // 4:00 PM
          isBlocked: true,
        },
      ],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    await blocker.savePolicies([policy]);
    const result = await blocker.shouldBlock('https://facebook.com/home');
    expect(result.blocked).toBe(false);
  });

  it('syncs policies from backend API', async () => {
    const mockPolicies: BlockingPolicy[] = [
      {
        id: 'policy-remote',
        name: 'Remote policy',
        enabled: true,
        blockedCategories: [WebsiteCategory.STREAMING],
        blockedDomains: [],
        allowedDomains: [],
        createdAt: Date.now(),
        updatedAt: Date.now(),
      },
    ];

    const fetchMock = vi.spyOn(globalThis as any, 'fetch').mockResolvedValue({
      ok: true,
      json: () => Promise.resolve(mockPolicies),
    } as any);

    const blocker = new WebsiteBlocker();
    await blocker.syncPoliciesFromBackend('https://api.guardian.test', 'device-123');
    const result = await blocker.shouldBlock('https://netflix.com/watch');

    expect(fetchMock).toHaveBeenCalledWith('https://api.guardian.test/api/policies?deviceId=device-123');
    expect(result.blocked).toBe(true);
  });

  it('updates declarative net request rules when policies change', async () => {
    const blocker = new WebsiteBlocker();
    const { declarativeNetRequest } = (globalThis as any).__chromeMocks;
    declarativeNetRequest.updateDynamicRules.mockClear();

    const policy: BlockingPolicy = {
      id: 'policy-block-domain',
      name: 'Block YouTube',
      enabled: true,
      blockedCategories: [],
      blockedDomains: ['youtube.com'],
      allowedDomains: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    await blocker.savePolicies([policy]);

    expect(declarativeNetRequest.updateDynamicRules).toHaveBeenCalledWith(
      expect.objectContaining({
        addRules: expect.arrayContaining([
          expect.objectContaining({
            condition: expect.objectContaining({ urlFilter: '||youtube.com' }),
          }),
        ]),
      })
    );
  });
});
