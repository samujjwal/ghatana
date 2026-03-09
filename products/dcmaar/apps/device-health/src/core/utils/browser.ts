import browser from 'webextension-polyfill';

export function hasBrowserStorage(): boolean {
  try {
    return typeof browser !== 'undefined' && !!browser.storage && !!browser.storage.local;
  } catch {
    return false;
  }
}

export function getBrowser(): typeof browser | undefined {
  try {
    return typeof browser !== 'undefined' ? browser : undefined;
  } catch {
    return undefined;
  }
}

export function isWindowContext(): boolean {
  return typeof window !== 'undefined';
}

export function isDocumentAvailable(): boolean {
  return typeof document !== 'undefined' && !!document;
}

export function getGlobal(): typeof globalThis {
  return globalThis;
}
