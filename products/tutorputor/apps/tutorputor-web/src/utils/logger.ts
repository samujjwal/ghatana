/**
 * Simple logger utility for frontend applications
 * Provides structured logging with component/module names
 */

export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

export interface Logger {
  debug: (message: string, ...args: unknown[]) => void;
  info: (message: string, ...args: unknown[]) => void;
  warn: (message: string, ...args: unknown[]) => void;
  error: (message: string, ...args: unknown[]) => void;
}

/**
 * Create a namespaced logger instance
 * @param namespace - The namespace for the logger (e.g., component name)
 * @returns Logger instance with debug, info, warn, error methods
 */
export function createLogger(namespace: string): Logger {
  const isDev = process.env.NODE_ENV === 'development';
  
  const logWithPrefix = (level: LogLevel, message: string, ...args: unknown[]) => {
    const prefix = `[${namespace}]`;
    const timestamp = new Date().toISOString();
    
    // In development, log to console
    if (isDev) {
      const consoleMethod = level === 'error' ? console.error 
        : level === 'warn' ? console.warn 
        : level === 'debug' ? console.debug 
        : console.log;
      
      consoleMethod(`${timestamp} ${prefix} ${level.toUpperCase()}: ${message}`, ...args);
    }
    
    // In production, could send to logging service
    // TODO: Integrate with Sentry or other error tracking for production
    if (level === 'error' && !isDev) {
      // Send to error tracking service
      console.error(`${timestamp} ${prefix} ERROR: ${message}`, ...args);
    }
  };

  return {
    debug: (message: string, ...args: unknown[]) => {
      if (isDev) {
        logWithPrefix('debug', message, ...args);
      }
    },
    info: (message: string, ...args: unknown[]) => logWithPrefix('info', message, ...args),
    warn: (message: string, ...args: unknown[]) => logWithPrefix('warn', message, ...args),
    error: (message: string, ...args: unknown[]) => logWithPrefix('error', message, ...args),
  };
}

/**
 * Default logger instance for general use
 */
export const logger = createLogger('app');
