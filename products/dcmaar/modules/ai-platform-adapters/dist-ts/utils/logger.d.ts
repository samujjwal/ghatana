/**
 * Simple logger utility for Agent connectors
 * Follows Desktop pattern but simplified for Agent
 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error';
export interface LoggerOptions {
    level?: LogLevel;
    context?: string;
}
export declare class Logger {
    private context;
    private level;
    constructor(context: string, options?: LoggerOptions);
    private shouldLog;
    private formatMessage;
    debug(message: string, meta?: unknown): void;
    info(message: string, meta?: unknown): void;
    warn(message: string, meta?: unknown): void;
    error(message: string, meta?: unknown): void;
    setLevel(level: LogLevel): void;
}
//# sourceMappingURL=logger.d.ts.map