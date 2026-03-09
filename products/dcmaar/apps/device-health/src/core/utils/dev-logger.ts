/**
 * Development Logger Utility
 * Provides conditional logging based on environment
 */

/**
 * Check if we're in development mode
 */
const isDevelopment = (): boolean => {
  return process.env.NODE_ENV === 'development' || process.env.VITE_ENABLE_TEST_HOOKS === 'true';
};

/**
 * Development logger that only logs in development mode
 */
export const devLog = {
  /**
   * Log informational messages
   */
  log: (...args: unknown[]): void => {
    if (isDevelopment()) {
      console.info('[DCMAR]', ...args);
    }
  },

  /**
   * Log warning messages
   */
  warn: (...args: unknown[]): void => {
    if (isDevelopment()) {
      console.warn('[DCMAR]', ...args);
    }
  },

  /**
   * Log error messages (always logged)
   */
  error: (...args: unknown[]): void => {
    console.error('[DCMAR]', ...args);
  },

  /**
   * Log debug messages (only in development)
   */
  debug: (...args: unknown[]): void => {
    if (isDevelopment()) {
      console.debug('[DCMAR]', ...args);
    }
  },

  /**
   * Log informational messages (always logged)
   */
  info: (...args: unknown[]): void => {
    console.info('[DCMAR]', ...args);
  },
};

/**
 * Production-safe logger
 * Only logs errors and warnings in production
 */
export const prodLog = {
  error: (...args: unknown[]): void => {
    console.error('[DCMAR]', ...args);
  },

  warn: (...args: unknown[]): void => {
    console.warn('[DCMAR]', ...args);
  },

  info: (...args: unknown[]): void => {
    if (isDevelopment()) {
      console.info('[DCMAR]', ...args);
    }
  },
};

export default devLog;
