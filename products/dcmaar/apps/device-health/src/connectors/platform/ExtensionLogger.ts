/**
 * @fileoverview Extension-specific logger with console output and optional log aggregation.
 *
 * Provides structured logging for extension contexts (background, content, popup)
 * with log levels, context tagging, and optional remote log forwarding.
 *
 * **Platform**: Browser Extensions (Chrome, Firefox, Edge, Safari)
 * **API**: console.log, console.warn, console.error, console.debug
 *
 * @module connectors/platform/ExtensionLogger
 * @since Phase 2.1
 */

/**
 * Log level enumeration.
 */
export enum LogLevel {
  DEBUG = 0,
  INFO = 1,
  WARN = 2,
  ERROR = 3,
}

/**
 * Structured log entry.
 */
export interface LogEntry {
  level: LogLevel;
  message: string;
  context: string;
  timestamp: number;
  data?: Record<string, unknown>;
  error?: Error;
}

/**
 * Extension logger with structured logging and context tagging.
 *
 * **Features**:
 * - Log levels (DEBUG, INFO, WARN, ERROR)
 * - Context tagging for easy filtering
 * - Structured log entries
 * - Optional log history
 * - Configurable log level filtering
 *
 * **Usage**:
 * ```typescript
 * const logger = new ExtensionLogger({
 *   context: 'ConnectorManager',
 *   minLevel: LogLevel.INFO
 * });
 *
 * logger.info('Starting connector', { connectorId: 'ws-sink-1' });
 * logger.error('Connection failed', { error: err });
 * logger.debug('Received event', { event });
 * ```
 */
export class ExtensionLogger {
  private readonly context: string;
  private minLevel: LogLevel;
  private readonly enableHistory: boolean;
  private readonly maxHistorySize: number;
  private history: LogEntry[] = [];

  /**
   * Creates a new ExtensionLogger.
   *
   * @param options - Configuration options
   */
  constructor(options: {
    /**
     * Logger context (e.g., component name, connector ID)
     */
    context: string;

    /**
     * Minimum log level to output
     * @default LogLevel.INFO
     */
    minLevel?: LogLevel;

    /**
     * Enable log history retention
     * @default false
     */
    enableHistory?: boolean;

    /**
     * Maximum number of log entries to retain in history
     * @default 1000
     */
    maxHistorySize?: number;
  }) {
    this.context = options.context;
    this.minLevel = options.minLevel ?? LogLevel.INFO;
    this.enableHistory = options.enableHistory ?? false;
    this.maxHistorySize = options.maxHistorySize ?? 1000;
  }

  /**
   * Logs a debug message.
   */
  debug(message: string, data?: Record<string, unknown>): void {
    this.log(LogLevel.DEBUG, message, data);
  }

  /**
   * Logs an info message.
   */
  info(message: string, data?: Record<string, unknown>): void {
    this.log(LogLevel.INFO, message, data);
  }

  /**
   * Logs a warning message.
   */
  warn(message: string, data?: Record<string, unknown>): void {
    this.log(LogLevel.WARN, message, data);
  }

  /**
   * Logs an error message.
   */
  error(message: string, error?: Error | unknown, data?: Record<string, unknown>): void {
    const errorObj = error instanceof Error ? error : undefined;
    this.log(LogLevel.ERROR, message, data, errorObj);
  }

  /**
   * Internal logging method.
   */
  private log(
    level: LogLevel,
    message: string,
    data?: Record<string, unknown>,
    error?: Error
  ): void {
    // Check minimum log level
    if (level < this.minLevel) {
      return;
    }

    const entry: LogEntry = {
      level,
      message,
      context: this.context,
      timestamp: Date.now(),
      data,
      error,
    };

    // Add to history if enabled
    if (this.enableHistory) {
      this.history.push(entry);

      // Trim history if exceeds max size
      if (this.history.length > this.maxHistorySize) {
        this.history = this.history.slice(-this.maxHistorySize);
      }
    }

    // Output to console
    this.outputToConsole(entry);
  }

  /**
   * Outputs log entry to browser console.
   */
  private outputToConsole(entry: LogEntry): void {
    const prefix = `[${LogLevel[entry.level]}] [${entry.context}]`;
    const message = `${prefix} ${entry.message}`;

    switch (entry.level) {
      case LogLevel.DEBUG:
        console.debug(message, entry.data || '');
        break;

      case LogLevel.INFO:
        console.info(message, entry.data || '');
        break;

      case LogLevel.WARN:
        console.warn(message, entry.data || '');
        break;

      case LogLevel.ERROR:
        if (entry.error) {
          console.error(message, entry.data || '', entry.error);
        } else {
          console.error(message, entry.data || '');
        }
        break;
    }
  }

  /**
   * Sets the minimum log level.
   */
  setMinLevel(level: LogLevel): void {
    this.minLevel = level;
  }

  /**
   * Gets the current minimum log level.
   */
  getMinLevel(): LogLevel {
    return this.minLevel;
  }

  /**
   * Gets the log history.
   */
  getHistory(): ReadonlyArray<LogEntry> {
    return this.history;
  }

  /**
   * Clears the log history.
   */
  clearHistory(): void {
    this.history = [];
  }

  /**
   * Creates a child logger with additional context.
   *
   * @param childContext - Additional context to append
   * @returns New logger instance
   */
  createChild(childContext: string): ExtensionLogger {
    return new ExtensionLogger({
      context: `${this.context}:${childContext}`,
      minLevel: this.minLevel,
      enableHistory: this.enableHistory,
      maxHistorySize: this.maxHistorySize,
    });
  }
}
