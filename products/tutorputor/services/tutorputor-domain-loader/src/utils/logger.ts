/**
 * Logger utility for tutorputor-domain-loader.
 *
 * Provides a structured, Pino-compatible logger interface backed by console
 * so the module can be used without a Pino dependency in test environments.
 *
 * @doc.type module
 * @doc.purpose Lightweight structured logger factory
 * @doc.layer product
 * @doc.pattern Utility
 */

export interface Logger {
  info(obj: Record<string, unknown>, msg?: string): void;
  warn(obj: Record<string, unknown>, msg?: string): void;
  error(obj: Record<string, unknown>, msg?: string): void;
  debug(obj: Record<string, unknown>, msg?: string): void;
  child(bindings: Record<string, unknown>): Logger;
}

/**
 * Create a named logger instance.
 *
 * In production this can be swapped for a real Pino instance by replacing
 * this factory; the call-sites remain unchanged.
 */
export function createLogger(name: string): Logger {
  const prefix = `[${name}]`;

  const format = (obj: Record<string, unknown>, msg?: string): string => {
    const extras = Object.keys(obj).length > 0 ? ` ${JSON.stringify(obj)}` : "";
    return msg ? `${prefix} ${msg}${extras}` : `${prefix}${extras}`;
  };

  const logger: Logger = {
    info: (obj, msg) => console.info(format(obj, msg)),
    warn: (obj, msg) => console.warn(format(obj, msg)),
    error: (obj, msg) => console.error(format(obj, msg)),
    debug: (obj, msg) => {
      if (process.env["LOG_LEVEL"] === "debug") {
        console.debug(format(obj, msg));
      }
    },
    child: (bindings) => createLogger(`${name}(${JSON.stringify(bindings)})`),
  };

  return logger;
}
