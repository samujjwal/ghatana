/**
 * Environment utilities for the extension
 */

declare module '@shared/utils/environment' {
  /**
   * Checks if the code is running in a service worker context
   * @returns {boolean} True if running in a service worker
   */
  export function isServiceWorker(): boolean;

  /**
   * Checks if the code is running in the browser extension context
   * @returns {boolean} True if running in browser extension
   */
  export function isExtensionContext(): boolean;

  /**
   * Gets the current environment (development, production, test)
   * @returns {string} The current environment
   */
  export function getEnvironment(): 'development' | 'production' | 'test';
}
