/**
 * Logger stub for service worker (JS version)
 */
export function createLogger(namespace) {
  const log = (level, message, ...args) => {
    const prefix = `[${namespace}]`;
    console.log(`${new Date().toISOString()} ${prefix} ${level.toUpperCase()}: ${message}`, ...args);
  };
  return {
    debug: (msg, ...args) => log('debug', msg, ...args),
    info: (msg, ...args) => log('info', msg, ...args),
    warn: (msg, ...args) => log('warn', msg, ...args),
    error: (msg, ...args) => log('error', msg, ...args),
  };
}
