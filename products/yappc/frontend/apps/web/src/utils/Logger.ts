/**
 * Structured Logging System
 * 
 * Replaces all console.log/warn/error statements with proper logging
 * Environment-aware with different log levels and output formats
 */

export enum LogLevel {
    DEBUG = 0,
    INFO = 1,
    WARN = 2,
    ERROR = 3,
    FATAL = 4,
}

export interface LogEntry {
    timestamp: string;
    level: LogLevel;
    message: string;
    context?: string;
    metadata?: Record<string, unknown>;
    userId?: string;
    sessionId?: string;
    component?: string;
    action?: string;
}

export interface LoggerConfig {
    level: LogLevel;
    enableConsole: boolean;
    enableRemote: boolean;
    remoteEndpoint?: string;
    enablePerformance: boolean;
    enableUserTracking: boolean;
}

/**
 * Main Logger Class
 */
export class Logger {
    private static instance: Logger;
    private config: LoggerConfig;
    private logBuffer: LogEntry[] = [];
    private maxBufferSize = 1000;

    private constructor() {
        this.config = this.getDefaultConfig();
    }

    public static getInstance(): Logger {
        if (!Logger.instance) {
            Logger.instance = new Logger();
        }
        return Logger.instance;
    }

    private getDefaultConfig(): LoggerConfig {
        const isDevelopment = import.meta.env.DEV;
        const isTest = import.meta.env.MODE === 'test';

        return {
            level: isDevelopment ? LogLevel.DEBUG : LogLevel.INFO,
            enableConsole: !isTest,
            enableRemote: !isDevelopment && !isTest,
            enablePerformance: isDevelopment,
            enableUserTracking: !isTest,
        };
    }

    /**
     * Configure logger settings
     */
    public configure(config: Partial<LoggerConfig>): void {
        this.config = { ...this.config, ...config };
    }

    /**
     * Debug level logging
     */
    public debug(message: string, context?: string, metadata?: Record<string, unknown>): void {
        this.log(LogLevel.DEBUG, message, context, metadata);
    }

    /**
     * Info level logging
     */
    public info(message: string, context?: string, metadata?: Record<string, unknown>): void {
        this.log(LogLevel.INFO, message, context, metadata);
    }

    /**
     * Warning level logging
     */
    public warn(message: string, context?: string, metadata?: Record<string, unknown>): void {
        this.log(LogLevel.WARN, message, context, metadata);
    }

    /**
     * Error level logging
     */
    public error(message: string, context?: string, metadata?: Record<string, unknown>): void {
        this.log(LogLevel.ERROR, message, context, metadata);
    }

    /**
     * Fatal level logging
     */
    public fatal(message: string, context?: string, metadata?: Record<string, unknown>): void {
        this.log(LogLevel.FATAL, message, context, metadata);
    }

    /**
     * Performance logging
     */
    public performance(operation: string, duration: number, metadata?: Record<string, unknown>): void {
        if (!this.config.enablePerformance) return;

        this.info(`Performance: ${operation}`, 'performance', {
            duration,
            operation,
            ...metadata,
        });
    }

    /**
     * User action logging
     */
    public userAction(action: string, userId?: string, metadata?: Record<string, unknown>): void {
        if (!this.config.enableUserTracking) return;

        this.info(`User Action: ${action}`, 'user', {
            action,
            userId,
            timestamp: new Date().toISOString(),
            ...metadata,
        });
    }

    /**
     * Component-specific logging
     */
    public component(component: string, message: string, level: LogLevel = LogLevel.INFO, metadata?: Record<string, unknown>): void {
        this.log(level, message, component, {
            component,
            ...metadata,
        });
    }

    /**
     * API logging
     */
    public api(method: string, url: string, status: number, duration?: number, metadata?: Record<string, unknown>): void {
        const level = status >= 400 ? LogLevel.ERROR : LogLevel.INFO;
        this.log(level, `API ${method} ${url} - ${status}`, 'api', {
            method,
            url,
            status,
            duration,
            ...metadata,
        });
    }

    /**
     * Canvas-specific logging
     */
    public canvas(action: string, nodeId?: string, metadata?: Record<string, unknown>): void {
        this.info(`Canvas: ${action}`, 'canvas', {
            action,
            nodeId,
            timestamp: new Date().toISOString(),
            ...metadata,
        });
    }

    /**
     * Collaboration logging
     */
    public collaboration(action: string, userId?: string, metadata?: Record<string, unknown>): void {
        this.info(`Collaboration: ${action}`, 'collaboration', {
            action,
            userId,
            timestamp: new Date().toISOString(),
            ...metadata,
        });
    }

    /**
     * Core logging method
     */
    private log(level: LogLevel, message: string, context?: string, metadata?: Record<string, unknown>): void {
        if (level < this.config.level) return;

        const logEntry: LogEntry = {
            timestamp: new Date().toISOString(),
            level,
            message,
            context,
            metadata,
            component: this.extractComponent(),
            sessionId: this.getSessionId(),
        };

        // Add to buffer
        this.addToBuffer(logEntry);

        // Output based on configuration
        if (this.config.enableConsole) {
            this.outputToConsole(logEntry);
        }

        if (this.config.enableRemote && this.config.remoteEndpoint) {
            this.sendToRemote(logEntry);
        }
    }

    /**
     * Output to console with appropriate formatting
     */
    private outputToConsole(entry: LogEntry): void {
        const timestamp = new Date(entry.timestamp).toLocaleTimeString();
        const prefix = `[${timestamp}] [${LogLevel[entry.level]}]`;
        const context = entry.context ? `[${entry.context}]` : '';
        const message = `${prefix} ${context} ${entry.message}`;

        switch (entry.level) {
            case LogLevel.DEBUG:
                console.debug(message, entry.metadata || '');
                break;
            case LogLevel.INFO:
                console.info(message, entry.metadata || '');
                break;
            case LogLevel.WARN:
                console.warn(message, entry.metadata || '');
                break;
            case LogLevel.ERROR:
            case LogLevel.FATAL:
                console.error(message, entry.metadata || '');
                break;
        }
    }

    /**
     * Send to remote logging service
     */
    private async sendToRemote(entry: LogEntry): Promise<void> {
        if (!this.config.remoteEndpoint) return;

        try {
            await fetch(this.config.remoteEndpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(entry),
            });
        } catch (error) {
            // Fail silently to avoid infinite loops
            console.warn('Failed to send log to remote service:', error);
        }
    }

    /**
     * Add to circular buffer
     */
    private addToBuffer(entry: LogEntry): void {
        this.logBuffer.push(entry);
        if (this.logBuffer.length > this.maxBufferSize) {
            this.logBuffer.shift();
        }
    }

    /**
     * Get recent logs
     */
    public getRecentLogs(count: number = 100): LogEntry[] {
        return this.logBuffer.slice(-count);
    }

    /**
     * Get logs by level
     */
    public getLogsByLevel(level: LogLevel): LogEntry[] {
        return this.logBuffer.filter(entry => entry.level === level);
    }

    /**
     * Clear log buffer
     */
    public clearLogs(): void {
        this.logBuffer = [];
    }

    /**
     * Extract component name from call stack
     */
    private extractComponent(): string {
        const stack = new Error().stack;
        if (!stack) return 'unknown';

        const lines = stack.split('\n');
        // Look for the first non-logger call in the stack
        for (const line of lines) {
            if (line.includes('Logger.') || line.includes('console.')) continue;
            if (line.includes('node_modules')) continue;

            const match = line.match(/at\s+(.+?)\s+\(/);
            if (match) {
                return match[1].split('.')[0];
            }
        }

        return 'unknown';
    }

    /**
     * Get or create session ID
     */
    private getSessionId(): string {
        let sessionId = sessionStorage.getItem('logger-session-id');
        if (!sessionId) {
            sessionId = `session-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
            sessionStorage.setItem('logger-session-id', sessionId);
        }
        return sessionId;
    }

    /**
     * Create context-specific logger
     */
    public createContext(context: string): {
        debug: (message: string, metadata?: Record<string, unknown>) => void;
        info: (message: string, metadata?: Record<string, unknown>) => void;
        warn: (message: string, metadata?: Record<string, unknown>) => void;
        error: (message: string, metadata?: Record<string, unknown>) => void;
        fatal: (message: string, metadata?: Record<string, unknown>) => void;
    } {
        return {
            debug: (message: string, metadata?: Record<string, unknown>) =>
                this.debug(message, context, metadata),
            info: (message: string, metadata?: Record<string, unknown>) =>
                this.info(message, context, metadata),
            warn: (message: string, metadata?: Record<string, unknown>) =>
                this.warn(message, context, metadata),
            error: (message: string, metadata?: Record<string, unknown>) =>
                this.error(message, context, metadata),
            fatal: (message: string, metadata?: Record<string, unknown>) =>
                this.fatal(message, context, metadata),
        };
    }
}

/**
 * Global logger instance
 */
export const logger = Logger.getInstance();

/**
 * Convenience exports for common logging patterns
 */
export const createLogger = (context: string) => logger.createContext(context);

/**
 * Performance measurement utility
 */
export function measurePerformance<T>(
    operation: string,
    fn: () => T,
    metadata?: Record<string, unknown>
): T {
    const start = performance.now();
    try {
        const result = fn();
        const duration = performance.now() - start;
        logger.performance(operation, duration, metadata);
        return result;
    } catch (error) {
        const duration = performance.now() - start;
        logger.error(`Performance error in ${operation}`, 'performance', {
            duration,
            error: error instanceof Error ? error.message : String(error),
            ...metadata,
        });
        throw error;
    }
}

/**
 * Async performance measurement
 */
export async function measureAsyncPerformance<T>(
    operation: string,
    fn: () => Promise<T>,
    metadata?: Record<string, unknown>
): Promise<T> {
    const start = performance.now();
    try {
        const result = await fn();
        const duration = performance.now() - start;
        logger.performance(operation, duration, metadata);
        return result;
    } catch (error) {
        const duration = performance.now() - start;
        logger.error(`Performance error in ${operation}`, 'performance', {
            duration,
            error: error instanceof Error ? error.message : String(error),
            ...metadata,
        });
        throw error;
    }
}

export default logger;
