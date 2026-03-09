// Minimal type definitions for Chrome extension APIs
export {};

declare global {
  namespace chrome {
    namespace runtime {
      interface MessageSender {
        id?: string;
        url?: string;
        origin?: string;
      }

      interface InstalledDetails {
        reason: 'install' | 'update' | 'chrome_update' | 'shared_module_update';
        previousVersion?: string;
        id?: string;
      }

      interface RuntimeInstalledEvent extends chrome.events.Event<(details: InstalledDetails) => void> {}
      interface ExtensionMessageEvent extends chrome.events.Event<(
        message: any,
        sender: MessageSender,
        sendResponse: (response?: any) => void
      ) => boolean | void> {}
    }

    namespace events {
      interface Event<T extends Function> {
        addListener(callback: T): void;
        removeListener(callback: T): void;
        hasListener(callback: T): boolean;
        hasListeners(): boolean;
      }
    }
  }
}
