// Global type declarations for the extension

// For WebExtension browser API
declare namespace browser {
  interface StorageArea {
    get(keys: string | string[] | object): Promise<Record<string, any>>;
    set(items: Record<string, any>): Promise<void>;
    remove(keys: string | string[]): Promise<void>;
    clear(): Promise<void>;
  }

  interface Storage {
    local: StorageArea;
    sync: StorageArea;
  }

  interface Runtime {
    sendMessage(
      message: any,
      responseCallback?: (response: any) => void
    ): void;
    onMessage: {
      addListener(callback: (message: any, sender: any, sendResponse: (response?: any) => void) => void): void;
      removeListener(callback: Function): void;
    };
    getManifest(): any;
  }

  const storage: Storage;
  const runtime: Runtime;
}

// For Vite environment variables
interface ImportMetaEnv {
  readonly VITE_APP_NAME: string;
  readonly VITE_APP_VERSION: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
