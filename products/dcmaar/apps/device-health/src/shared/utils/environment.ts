/**
 * Environment detection utilities
 *
 * These utilities help determine the current execution context (browser, service worker, etc.)
 * in a way that's safe for all environments.
 */

// Type declarations for Web Worker and Service Worker globals
declare global {
  interface WorkerGlobalScope {
    importScripts: (url: string) => void;
  }
}

/**
 * Type representing the possible execution environments
 */
export type Environment = 'service-worker' | 'browser' | 'extension' | 'node' | 'unknown';

/**
 * Detects if the code is running in a service worker context
 */
export const isServiceWorker = (): boolean => {
  try {
    return (
      typeof (globalThis as unknown as { importScripts?: unknown }).importScripts === 'function' &&
      (typeof window === 'undefined' ||
        typeof (navigator as unknown as { serviceWorker?: unknown }).serviceWorker === 'undefined')
    );
  } catch {
    return false;
  }
};

/**
 * Detects if the code is running in a regular browser context
 */
export const isBrowserContext = (): boolean => {
  try {
    return (
      typeof window !== 'undefined' && typeof window.document !== 'undefined' && !isServiceWorker()
    );
  } catch (e) {
    return false;
  }
};

/**
 * Detects if the code is running in a browser extension context
 */
export const isExtensionContext = (): boolean => {
  try {
    return (
      typeof chrome !== 'undefined' &&
      !!chrome.runtime &&
      typeof chrome.runtime.id === 'string' &&
      chrome.runtime.id.length > 0
    );
  } catch (e) {
    return false;
  }
};

/**
 * Gets the current execution environment
 */
export const getEnvironment = (): Environment => {
  if (isServiceWorker()) return 'service-worker';
  if (isExtensionContext()) return 'extension';
  if (isBrowserContext()) return 'browser';
  if (typeof process !== 'undefined' && process.versions?.node) return 'node';
  return 'unknown';
};

/**
 * Safely checks if the code is running in a browser environment
 * (either regular browser or extension)
 */
export const isBrowserEnvironment = (): boolean => {
  return isBrowserContext() || isExtensionContext();
};

/**
 * Safely checks if the code is running in a Web Worker context
 */
export const isWebWorker = (): boolean => {
  try {
    // Check if we're in a Web Worker by checking for importScripts
    return (
      typeof (globalThis as unknown as { importScripts?: unknown }).importScripts === 'function' &&
      typeof window === 'undefined'
    );
  } catch {
    return false;
  }
};
