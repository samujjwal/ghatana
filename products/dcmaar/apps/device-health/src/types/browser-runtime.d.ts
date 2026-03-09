/**
 * Type definitions for Browser Extension Runtime APIs
 * These supplement the built-in types for chrome.* and browser.* APIs
 */

declare global {
  // Chrome Extension Runtime API types
  interface Window {
    browser?: typeof chrome;
    __dcmaarContentScriptLoaded?: boolean;
    chrome?: {
      runtime?: {
        sendMessage?: (msg: unknown, cb?: (r: unknown) => void) => void;
        connect?: (opts?: { name?: string }) => ChromePort;
        onMessage?: {
          addListener: (listener: (message: unknown, sender: unknown, sendResponse: (response?: unknown) => void) => void) => void;
          removeListener: (listener: unknown) => void;
        };
        onConnect?: {
          addListener: (listener: (port: ChromePort) => void) => void;
          removeListener: (listener: unknown) => void;
        };
        connectNative?: (name: string) => ChromeNativePort;
        id?: string;
        lastError?: { message: string };
      };
      storage?: {
        local?: {
          get: (keys?: string | string[] | Record<string, unknown> | null) => Promise<Record<string, unknown>>;
          set: (items: Record<string, unknown>) => Promise<void>;
          remove: (keys: string | string[]) => Promise<void>;
          clear: () => Promise<void>;
          getBytesInUse?: (keys?: string | string[]) => Promise<number>;
        };
        sync?: {
          get: (keys?: string | string[] | Record<string, unknown> | null) => Promise<Record<string, unknown>>;
          set: (items: Record<string, unknown>) => Promise<void>;
          remove: (keys: string | string[]) => Promise<void>;
          clear: () => Promise<void>;
          getBytesInUse?: (keys?: string | string[]) => Promise<number>;
        };
        session?: {
          get: (keys?: string | string[] | Record<string, unknown> | null) => Promise<Record<string, unknown>>;
          set: (items: Record<string, unknown>) => Promise<void>;
          remove: (keys: string | string[]) => Promise<void>;
          clear: () => Promise<void>;
        };
      };
      tabs?: {
        query: (queryInfo: Record<string, unknown>) => Promise<ChromeTab[]>;
        get: (tabId: number) => Promise<ChromeTab>;
        create: (createProperties: Record<string, unknown>) => Promise<ChromeTab>;
        update: (tabId?: number, updateProperties?: Record<string, unknown>) => Promise<ChromeTab>;
        remove: (tabIds: number | number[]) => Promise<void>;
        sendMessage: (tabId: number, message: unknown, options?: Record<string, unknown>) => Promise<unknown>;
      };
      webNavigation?: {
        onCompleted?: {
          addListener: (listener: (details: { tabId: number; frameId: number; url: string }) => void) => void;
          removeListener: (listener: unknown) => void;
        };
      };
    };
  }

  interface ChromePort {
    name: string;
    onMessage: {
      addListener: (listener: (message: unknown) => void) => void;
      removeListener: (listener: (message: unknown) => void) => void;
    };
    onDisconnect?: {
      addListener: (listener: () => void) => void;
      removeListener: (listener: () => void) => void;
    };
    postMessage: (message: unknown) => void;
    disconnect?: () => void;
  }

  interface ChromeNativePort extends ChromePort {
    error?: string;
  }

  interface ChromeTab {
    id?: number;
    index: number;
    windowId: number;
    openerTabId?: number;
    selected?: boolean;
    highlighted: boolean;
    active: boolean;
    pinned: boolean;
    audible?: boolean;
    discarded: boolean;
    autoDiscardable: boolean;
    mutedInfo?: {
      muted: boolean;
      reason?: string;
      extensionId?: string;
    };
    url?: string;
    pendingUrl?: string;
    title?: string;
    favIconUrl?: string;
    status?: 'loading' | 'complete';
    incognito: boolean;
    width?: number;
    height?: number;
    sessionId?: string;
  }

  // Service worker global context extensions
  interface ServiceWorkerGlobalScope extends WorkerGlobalScope {
    __dcmaar_set_test_endpoint?: (endpoint: string) => { ok: boolean };
    overrideEndpoint?: string;
  }

  // Node.js filesystem API types for test environments
  namespace NodeJS {
    interface Global {
      require?: (id: string) => unknown;
    }
  }

  // Test environment specific types
  interface TestFileSystemHandle {
    kind: 'file' | 'directory';
    name: string;
    createWritable?: () => Promise<TestWritableFileStream>;
    queryPermission?: () => Promise<PermissionState>;
    requestPermission?: () => Promise<PermissionState>;
    getFile?: () => Promise<File>;
  }

  interface TestWritableFileStream {
    write: (data: string | BufferSource | Blob) => Promise<void>;
    close: () => Promise<void>;
  }

  // Extension-specific globals that may be injected during tests
  interface Window {
    __dcmaar_extension_test_hooks?: {
      getTestSink?: () => Promise<unknown>;
      ingestEvent?: (event: unknown) => Promise<unknown>;
      [key: string]: unknown;
    };
  }

  // Browser environment detection
  interface Navigator {
    userAgentData?: {
      brands: Array<{ brand: string; version: string }>;
      mobile: boolean;
      platform: string;
    };
  }
}

export {};