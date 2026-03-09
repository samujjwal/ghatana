// Type definitions for Chrome extension API
declare namespace chrome {
  namespace runtime {
    interface Port {
      postMessage: (message: any) => void;
      onMessage: {
        addListener: (callback: (message: any, port: Port) => void) => void;
        removeListener: (callback: (message: any, port: Port) => void) => void;
      };
      onDisconnect: {
        addListener: (callback: (port: Port) => void) => void;
      };
    }

    const onConnect: {
      addListener: (callback: (port: Port) => void) => void;
    };

    const onMessage: {
      addListener: (
        callback: (message: any, sender: any, sendResponse: (response?: any) => void) => void
      ) => void;
    };

    function sendMessage(
      message: any,
      responseCallback?: (response: any) => void
    ): void;
  }

  namespace storage {
    interface StorageArea {
      get(keys: string | string[] | object, callback: (items: { [key: string]: any }) => void): void;
      set(items: object, callback?: () => void): void;
      remove(keys: string | string[], callback?: () => void): void;
      clear(callback?: () => void): void;
    }

    const local: StorageArea;
    const sync: StorageArea;
  }

  const runtime: typeof chrome.runtime;
  const storage: typeof chrome.storage;
}

// Make chrome available globally
declare const chrome: typeof globalThis.chrome;
