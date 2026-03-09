// Type definitions for webextension-polyfill
// Project: https://github.com/mozilla/webextension-polyfill

// Import the webextension-polyfill types
import 'webextension-polyfill';

// Extend the global browser namespace with additional types
declare global {
  // Make browser available globally
  const browser: typeof import('webextension-polyfill');

  // Extend the browser namespace with any additional types we need
  namespace browser.runtime {
    // Add any additional runtime types here if needed
    interface Port {
      name: string;
      disconnect: () => void;
      error?: Error;
      onDisconnect: {
        addListener: (listener: (port: browser.runtime.Port) => void) => void;
      };
      onMessage: {
        addListener: (listener: (message: any) => void) => void;
      };
      postMessage: (message: any) => void;
      sender?: any;
    }

    interface MessageSender {
      id?: string;
      url?: string;
      origin?: string;
      tab?: any;
      frameId?: number;
    }
  }
}

export {};
