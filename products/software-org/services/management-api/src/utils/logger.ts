/**
 * Minimal structured logger for management-api services.
 *
 * Outputs newline-delimited JSON to stdout (info/debug) and stderr (warn/error),
 * which is compatible with Loki, Datadog, and other log aggregators.
 * In production, replace with the fastify logger instance passed from the route layer.
 *
 * @doc.type class
 * @doc.purpose Structured JSON logger for Node.js services
 * @doc.layer product
 * @doc.pattern Utility
 */

type LogLevel = 'debug' | 'info' | 'warn' | 'error';

const LEVEL_PRIORITY: Record<LogLevel, number> = {
  debug: 10,
  info: 20,
  warn: 30,
  error: 40,
};

const minLevel: LogLevel =
  (process.env['LOG_LEVEL'] as LogLevel | undefined) ?? 'info';

function write(level: LogLevel, name: string, msg: string, extra?: unknown): void {
  if (LEVEL_PRIORITY[level] < LEVEL_PRIORITY[minLevel]) return;

  const entry: Record<string, unknown> = {
    level,
    time: new Date().toISOString(),
    name,
    msg,
  };
  if (extra !== undefined) {
    entry['data'] = extra;
  }

  const line = JSON.stringify(entry);
  if (level === 'warn' || level === 'error') {
    process.stderr.write(line + '\n');
  } else {
    process.stdout.write(line + '\n');
  }
}

export interface Logger {
  debug(msg: string, extra?: unknown): void;
  info(msg: string, extra?: unknown): void;
  warn(msg: string, extra?: unknown): void;
  error(msg: string, extra?: unknown): void;
}

/**
 * Create a logger bound to the given component name.
 *
 * @param name Component / service name that appears in every log line
 */
export function createLogger(name: string): Logger {
  return {
    debug: (msg, extra) => write('debug', name, msg, extra),
    info:  (msg, extra) => write('info',  name, msg, extra),
    warn:  (msg, extra) => write('warn',  name, msg, extra),
    error: (msg, extra) => write('error', name, msg, extra),
  };
}
