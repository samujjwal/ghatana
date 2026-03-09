/**
 * Sentry Mock for Testing
 *
 * Provides mock Sentry error tracking for testing
 */

export interface CapturedError {
  error: Error;
  timestamp: Date;
  tags?: Record<string, string>;
  extra?: Record<string, any>;
}

export interface CapturedMessage {
  message: string;
  level: 'info' | 'warning' | 'error' | 'fatal';
  timestamp: Date;
  tags?: Record<string, string>;
}

/**
 * Sentry mock class
 */
class SentryMock {
  private capturedErrors: CapturedError[] = [];
  private capturedMessages: CapturedMessage[] = [];
  private initialized: boolean = false;

  /**
   * Mock Sentry initialization
   */
  init(dsn: string): void {
    this.initialized = true;
  }

  /**
   * Mock capture exception
   */
  captureException(
    error: Error,
    tags?: Record<string, string>,
    extra?: Record<string, any>
  ): void {
    this.capturedErrors.push({
      error,
      timestamp: new Date(),
      tags,
      extra,
    });
  }

  /**
   * Mock capture message
   */
  captureMessage(
    message: string,
    level: 'info' | 'warning' | 'error' | 'fatal' = 'info',
    tags?: Record<string, string>
  ): void {
    this.capturedMessages.push({
      message,
      level,
      timestamp: new Date(),
      tags,
    });
  }

  /**
   * Check if Sentry was initialized
   */
  isInitialized(): boolean {
    return this.initialized;
  }

  /**
   * Get all captured errors
   */
  getCapturedErrors(): CapturedError[] {
    return this.capturedErrors;
  }

  /**
   * Get all captured messages
   */
  getCapturedMessages(): CapturedMessage[] {
    return this.capturedMessages;
  }

  /**
   * Get last captured error
   */
  getLastError(): CapturedError | undefined {
    return this.capturedErrors[this.capturedErrors.length - 1];
  }

  /**
   * Get last captured message
   */
  getLastMessage(): CapturedMessage | undefined {
    return this.capturedMessages[this.capturedMessages.length - 1];
  }

  /**
   * Check if specific error was captured
   */
  wasErrorCaptured(errorMessage: string): boolean {
    return this.capturedErrors.some(e => e.error.message.includes(errorMessage));
  }

  /**
   * Clear all captured data
   */
  clear(): void {
    this.capturedErrors = [];
    this.capturedMessages = [];
  }

  /**
   * Reset mock (clear and uninitialize)
   */
  reset(): void {
    this.clear();
    this.initialized = false;
  }
}

export const sentryMock = new SentryMock();

export default sentryMock;
