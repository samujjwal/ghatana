import browser from 'webextension-polyfill';

import { hasBrowserStorage } from '../../core/utils/browser';

import { CONFIG_STORAGE_KEY, DEFAULT_CONFIG, mergeConfig, type ExtensionConfig } from './index';

async function readFromBrowserStorage(): Promise<Partial<ExtensionConfig> | undefined> {
  if (!hasBrowserStorage()) {
    return undefined;
  }

  try {
    const result = await browser.storage.local.get(CONFIG_STORAGE_KEY);
    return result[CONFIG_STORAGE_KEY] as Partial<ExtensionConfig> | undefined;
  } catch (error) {
    console.warn('[DCMAAR Config] Failed to read browser.storage.local', error);
    return undefined;
  }
}

function readFromLocalStorage(): Partial<ExtensionConfig> | undefined {
  try {
    if (typeof localStorage === 'undefined') {
      return undefined;
    }
    const stored = localStorage.getItem(CONFIG_STORAGE_KEY);
    return stored ? (JSON.parse(stored) as Partial<ExtensionConfig>) : undefined;
  } catch (error) {
    console.warn('[DCMAAR Config] Failed to read localStorage', error);
    return undefined;
  }
}

async function persistToBrowserStorage(config: ExtensionConfig): Promise<void> {
  if (!hasBrowserStorage()) {
    return;
  }

  try {
    await browser.storage.local.set({ [CONFIG_STORAGE_KEY]: config });
  } catch (error) {
    console.warn('[DCMAAR Config] Failed to write browser.storage.local', error);
  }
}

function persistToLocalStorage(config: ExtensionConfig): void {
  try {
    if (typeof localStorage === 'undefined') {
      return;
    }
    localStorage.setItem(CONFIG_STORAGE_KEY, JSON.stringify(config));
  } catch (error) {
    console.warn('[DCMAAR Config] Failed to write localStorage', error);
  }
}

export async function loadConfig(): Promise<ExtensionConfig> {
  const defaultConfig = JSON.parse(JSON.stringify(DEFAULT_CONFIG)) as ExtensionConfig;
  const browserConfig = await readFromBrowserStorage();
  const localConfig = readFromLocalStorage();

  // Browser storage takes precedence, then localStorage
  const merged = mergeConfig<ExtensionConfig>(defaultConfig, localConfig ?? {});
  const finalConfig = mergeConfig<ExtensionConfig>(merged, browserConfig ?? {});
  return finalConfig;
}

export async function saveConfig(config: ExtensionConfig): Promise<void> {
  persistToLocalStorage(config);
  await persistToBrowserStorage(config);
}

export type ConfigChangeHandler = (config: ExtensionConfig) => void;

export function subscribeToConfigChanges(handler: ConfigChangeHandler): () => void {
  if (!hasBrowserStorage()) {
    return () => {};
  }

  const listener = (changes: Record<string, { newValue?: unknown }>, areaName: string) => {
    if (areaName !== 'local') {
      return;
    }

    const change = changes[CONFIG_STORAGE_KEY];
    if (!change) {
      return;
    }

    const overrides = (change.newValue as Partial<ExtensionConfig>) ?? {};
    const updated = mergeConfig<ExtensionConfig>(DEFAULT_CONFIG, overrides);
    handler(updated);
  };

  try {
    browser.storage.onChanged.addListener(listener);
    return () => {
      try {
        browser.storage.onChanged.removeListener(listener);
      } catch (error) {
        console.warn('[DCMAAR Config] Failed to remove storage listener', error);
      }
    };
  } catch (error) {
    console.warn('[DCMAAR Config] Failed to subscribe to storage changes', error);
    return () => {};
  }
}
