/**
 * Console-based execution logger
 */
export class ConsoleExecutionLogger {
  private prefix: string;

  constructor(prefix: string = '[Kernel]') {
    this.prefix = prefix;
  }

  info(message: string, meta?: Record<string, unknown>): void {
    console.log(`${this.prefix} INFO: ${message}`, meta ? JSON.stringify(meta) : '');
  }

  warn(message: string, meta?: Record<string, unknown>): void {
    console.warn(`${this.prefix} WARN: ${message}`, meta ? JSON.stringify(meta) : '');
  }

  error(message: string, meta?: Record<string, unknown>): void {
    console.error(`${this.prefix} ERROR: ${message}`, meta ? JSON.stringify(meta) : '');
  }

  debug(message: string, meta?: Record<string, unknown>): void {
    if (process.env.DEBUG === 'true') {
      console.debug(`${this.prefix} DEBUG: ${message}`, meta ? JSON.stringify(meta) : '');
    }
  }
}

/**
 * File-based execution logger
 */
export class FileExecutionLogger {
  private logFile: string;
  private buffer: string[] = [];

  constructor(logFile: string) {
    this.logFile = logFile;
  }

  async info(message: string, meta?: Record<string, unknown>): Promise<void> {
    await this.write('INFO', message, meta);
  }

  async warn(message: string, meta?: Record<string, unknown>): Promise<void> {
    await this.write('WARN', message, meta);
  }

  async error(message: string, meta?: Record<string, unknown>): Promise<void> {
    await this.write('ERROR', message, meta);
  }

  async debug(message: string, meta?: Record<string, unknown>): Promise<void> {
    if (process.env.DEBUG === 'true') {
      await this.write('DEBUG', message, meta);
    }
  }

  private async write(
    level: string,
    message: string,
    meta?: Record<string, unknown>,
  ): Promise<void> {
    const timestamp = new Date().toISOString();
    const logEntry = JSON.stringify({
      timestamp,
      level,
      message,
      meta,
    });

    this.buffer.push(logEntry);

    // Flush buffer if it gets too large
    if (this.buffer.length >= 100) {
      await this.flush();
    }
  }

  async flush(): Promise<void> {
    if (this.buffer.length === 0) {
      return;
    }

    const { promises } = await import('node:fs');
    await promises.appendFile(this.logFile, this.buffer.join('\n') + '\n');
    this.buffer = [];
  }
}
