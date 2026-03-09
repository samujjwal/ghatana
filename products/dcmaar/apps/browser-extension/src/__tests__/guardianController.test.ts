import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@ghatana/dcmaar-browser-extension-core', () => import('../test/mocks/browserCoreMock'));

vi.mock('webextension-polyfill', () => {
  const api = {
    storage: {
      local: {
        get: vi.fn(),
        set: vi.fn(),
      },
    },
    runtime: {
      getURL: vi.fn(),
      sendMessage: vi.fn(),
    },
    tabs: {
      create: vi.fn(),
      query: vi.fn(),
    },
    declarativeNetRequest: {
      updateDynamicRules: vi.fn(),
    },
  };
  return {
    default: api,
    ...api,
  };
});

import { UnifiedBrowserEventCapture } from '@ghatana/dcmaar-browser-extension-core';
import { WebsiteCategory } from '../blocker/WebsiteBlocker';
import { GuardianController } from '../controller/GuardianController';

// storageMap will be available at runtime from the mock, but TypeScript doesn't know about it
// We'll access it via dynamic import in tests
let storageMap: any;

const getRouter = (controller: GuardianController) => (controller as any).router;
const getEventCapture = (controller: GuardianController) => (controller as any).events as UnifiedBrowserEventCapture;

describe('GuardianController', () => {
  beforeAll(async () => {
    // Get storageMap from the mock at runtime
    const mock = await import('../test/mocks/browserCoreMock');
    storageMap = (mock as any).__storageStore;
  });

  beforeEach(() => {
    storageMap?.clear();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-03-25T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('initializes metrics and event collection', async () => {
    const controller = new GuardianController();
    await controller.initialize();

    const router = getRouter(controller);
    const stateResponse = await router.dispatch('GET_STATE');

    expect(stateResponse.success).toBe(true);
    expect(stateResponse.data.initialized).toBe(true);
    expect(stateResponse.data.metricsCollecting).toBe(true);
    expect(stateResponse.data.eventsCapturing).toBe(true);
  });

  it('stores captured events via UnifiedBrowserEventCapture', async () => {
    const controller = new GuardianController();
    await controller.initialize();

    const eventsCapture = getEventCapture(controller);

    // Emit event - callback is async and will store to storage
    (eventsCapture as any).emit({ type: 'navigation', timestamp: Date.now() });

    await vi.waitFor(async () => {
      const router = getRouter(controller);
      const eventsResponse = await router.dispatch('GET_EVENTS');

      expect(eventsResponse.success).toBe(true);
      expect(eventsResponse.data).toHaveLength(1);
    }, { timeout: 100 });
  });

  it('clears stored data via message router', async () => {
    const controller = new GuardianController();
    await controller.initialize();

    const eventsCapture = getEventCapture(controller);

    // Emit data
    (eventsCapture as any).emit({ type: 'test', timestamp: Date.now() });

    // Wait for async storage to complete using vi.waitFor
    const router = getRouter(controller);
    await vi.waitFor(async () => {
      const events = (storageMap as any).get('guardian-events');
      expect(events).toBeDefined();
      expect(events.length).toBeGreaterThan(0);
    }, { timeout: 100 });

    // Spy on pipeline storage clearAll
    const pipeline = (controller as any).pipeline;
    if (pipeline && pipeline.storageSink) {
      vi.spyOn(pipeline.storageSink, 'clearAll').mockResolvedValue(undefined as any);
    }

    // Now clear the data
    await router.dispatch('CLEAR_DATA');

    const events = (storageMap as any).get('guardian-events');
    expect(events).toBeUndefined();

    if (pipeline && pipeline.storageSink && (pipeline.storageSink.clearAll as any).mock) {
      expect(pipeline.storageSink.clearAll).toHaveBeenCalled();
    }
  });

  it('updates configuration through router', async () => {
    const controller = new GuardianController();
    await controller.initialize();

    const router = getRouter(controller);
    const updateResponse = await router.dispatch('UPDATE_CONFIG', { collectionInterval: 60000 });

    expect(updateResponse.success).toBe(true);
    expect(updateResponse.data.collectionInterval).toBe(60000);
  });

  it('returns analytics summary', async () => {
    const controller = new GuardianController();
    await controller.initialize();

    // Replace pipeline with a fake storage sink so analytics uses predictable data
    (controller as any).pipeline = {
      storageSink: {
        getUsageRange: vi.fn().mockResolvedValue([
          {
            date: '2024-03-25',
            totalTime: 2000,
            domains: {
              'example.com': {
                visits: 4,
                time: 2000,
                lastVisit: Date.now(),
              },
            },
          },
        ]),
        getRawEvents: vi.fn().mockResolvedValue([]),
        clearAll: vi.fn().mockResolvedValue(undefined),
      },
    };

    const router = getRouter(controller);
    const response = await router.dispatch('GET_ANALYTICS');

    expect(response.success).toBe(true);
    expect(response.data.webUsage.last7d).toBe(4);
    expect(response.data.timeSpent.last7d).toBe(2000);
  });

  it('syncs policies using background message router', async () => {
    const controller = new GuardianController();
    await controller.initialize();

    const router = getRouter(controller);
    const fetchMock = vi.spyOn(globalThis as any, 'fetch').mockResolvedValue({
      ok: true,
      json: () => Promise.resolve([
        {
          id: 'policy-sync',
          name: 'Synced Policy',
          enabled: true,
          blockedCategories: [WebsiteCategory.SOCIAL],
          blockedDomains: [],
          allowedDomains: [],
          createdAt: Date.now(),
          updatedAt: Date.now(),
        },
      ]),
    } as any);

    const response = await router.dispatch('SYNC_POLICIES', {
      apiUrl: 'https://guardian.test',
      deviceId: 'device-42',
    });

    expect(fetchMock).toHaveBeenCalledWith('https://guardian.test/api/policies?deviceId=device-42');
    expect(response.success).toBe(true);
    expect(response.data).toHaveLength(1);
    expect(response.data[0].name).toBe('Synced Policy');
  });

  it('evaluates policy via EVALUATE_POLICY using WebsiteBlocker', async () => {
    const controller = new GuardianController();
    await controller.initialize();

    const shouldBlockSpy = vi
      .spyOn((controller as any).blocker, 'shouldBlock')
      .mockResolvedValue({
        blocked: true,
        reason: 'test-rule',
        policyId: 'policy-1',
      });

    const router = getRouter(controller);
    const response = await router.dispatch('EVALUATE_POLICY', { url: 'https://example.com' });

    expect(response.success).toBe(true);
    expect(response.data.decision).toBe('block');
    expect(response.data.reason).toBe('test-rule');
    expect(response.data.policyId).toBe('policy-1');
    expect(shouldBlockSpy).toHaveBeenCalledWith('https://example.com');
  });
});
