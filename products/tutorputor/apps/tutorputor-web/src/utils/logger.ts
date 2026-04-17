/**
 * Simple logger utility for frontend applications
 * Provides structured logging with component/module names
 */

import {
  captureClientError,
  captureClientMessage,
} from './errorTracking.js';

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
    const renderedMessage = `${timestamp} ${prefix} ${level.toUpperCase()}: ${message}`;
    
    // In development, log to console
    if (isDev) {
      const consoleMethod = level === 'error' ? console.error 
        : level === 'warn' ? console.warn 
        : level === 'debug' ? console.debug 
        : console.log;
      
      consoleMethod(renderedMessage, ...args);
    }

    if (!isDev) {
      const context = args.length > 0 ? { args } : undefined;
      if (level === 'error') {
        const firstArgument = args[0];
        if (firstArgument instanceof Error) {
          captureClientError(firstArgument, { namespace, message, args: args.slice(1) });
        } else {
          captureClientMessage(level, renderedMessage, { namespace, args });
        }

        console.error(renderedMessage, ...args);
        return;
      }

      if (level === 'warn') {
        captureClientMessage(level, renderedMessage, { namespace, args });
      }

      if (context) {
        void context;
      }
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
