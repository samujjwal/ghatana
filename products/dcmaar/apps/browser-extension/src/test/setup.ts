import { vi } from 'vitest';
import '@testing-library/jest-dom/vitest';

type Listener<T extends any[]> = (...args: T) => void;

function createChromeEvent<T extends any[]>() {
  const handlers: Listener<T>[] = [];
  return {
    addListener: vi.fn((handler: Listener<T>) => {
      handlers.push(handler);
    }),
    removeListener: vi.fn((handler: Listener<T>) => {
      const index = handlers.indexOf(handler);
      if (index >= 0) handlers.splice(index, 1);
    }),
    hasListener: vi.fn((handler: Listener<T>) => handlers.includes(handler)),
    dispatch: (...args: T) => {
      handlers.slice().forEach((handler) => handler(...args));
    },
  };
}

const tabsOnUpdated = createChromeEvent<[number, { url?: string }, unknown]>();
const webRequestOnBeforeRequest = createChromeEvent<[chrome.webRequest.WebRequestDetails]>();
const alarmsOnAlarm = createChromeEvent<[chrome.alarms.Alarm]>();
const updateDynamicRules = vi.fn();

const runtime = {
  getURL: vi.fn((path: string) => `chrome-extension://test/${path}`),
  sendMessage: vi.fn(() => Promise.resolve()),
};

const alarms = {
  create: vi.fn(),
  onAlarm: alarmsOnAlarm,
};

// @ts-expect-error - assign mock chrome to global scope for tests
globalThis.chrome = {
  tabs: {
    onUpdated: tabsOnUpdated,
    update: vi.fn(),
  },
  webRequest: {
    onBeforeRequest: webRequestOnBeforeRequest,
  },
  runtime,
  alarms,
  declarativeNetRequest: {
    updateDynamicRules,
  },
} as typeof chrome;

Object.assign(globalThis, {
  __chromeMocks: {
    tabsOnUpdated,
    webRequestOnBeforeRequest,
    alarmsOnAlarm,
    runtime,
    declarativeNetRequest: {
      updateDynamicRules,
    },
  },
});

if (!globalThis.fetch) {
  globalThis.fetch = vi.fn();
}
