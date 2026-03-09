/**
 * Core interfaces for the application
 */

/** Base interface for all events */
export interface IEvent {
  /** Unique event ID */
  id: string;
  /** Event type */
  type: string;
  /** Event timestamp in ISO format */
  timestamp: string;
  /** Source of the event */
  source: string;
  /** Optional metadata */
  metadata?: Record<string, unknown>;
}

/** Envelope for wrapping messages/events */
export interface Envelope<T = unknown> {
  /** Unique envelope ID */
  id: string;
  /** Envelope type */
  type: string;
  /** Creation timestamp */
  timestamp: string;
  /** Source application/component */
  source: string;
  /** Target application/component */
  target?: string;
  /** Message payload */
  payload: T;
  /** Security context */
  security?: {
    /** Authentication token */
    token?: string;
    /** Signature of the payload */
    signature?: string;
    /** Encryption details */
    encryption?: {
      algorithm: string;
      keyId?: string;
      iv?: string;
    };
  };
  /** Additional metadata */
  metadata?: Record<string, unknown>;
}

/** Logger interface */
export interface ILogger {
  debug: (message: string, ...args: unknown[]) => void;
  info: (message: string, ...args: unknown[]) => void;
  warn: (message: string, ...args: unknown[]) => void;
  error: (message: string, error?: Error, ...args: unknown[]) => void;
  /** Log a metric */
  metric: (name: string, value: number, tags?: Record<string, string>) => void;
  /** Create a child logger with additional context */
  child: (context: Record<string, unknown>) => ILogger;
}

/** Configuration interface */
export interface IConfig {
  /** Get a configuration value */
  get: <T>(key: string, defaultValue?: T) => T;
  /** Set a configuration value */
  set: <T>(key: string, value: T) => void;
  /** Check if a configuration key exists */
  has: (key: string) => boolean;
  /** Subscribe to configuration changes */
  subscribe: (key: string, callback: (value: unknown) => void) => () => void;
  /** Load configuration from a source */
  load: (source: Record<string, unknown>) => void;
}

/** Storage interface */
export interface IStorage {
  /** Get a value from storage */
  get: <T>(key: string) => Promise<T | null>;
  /** Set a value in storage */
  set: <T>(key: string, value: T) => Promise<void>;
  /** Remove a value from storage */
  remove: (key: string) => Promise<void>;
  /** Clear all values from storage */
  clear: () => Promise<void>;
  /** Get all keys in storage */
  keys: () => Promise<string[]>;
  /** Subscribe to storage changes */
  subscribe: (key: string, callback: (newValue: unknown, oldValue: unknown) => void) => () => void;
}

/** File system interface */
export interface IFileSystem {
  /** Read a file as text */
  readFile: (path: string) => Promise<string>;
  /** Write text to a file */
  writeFile: (path: string, content: string) => Promise<void>;
  /** Check if a path exists */
  exists: (path: string) => Promise<boolean>;
  /** Create a directory */
  mkdir: (path: string, options?: { recursive?: boolean }) => Promise<void>;
  /** Read a directory */
  readdir: (path: string) => Promise<string[]>;
  /** Get file/directory stats */
  stat: (path: string) => Promise<{ isFile: boolean; isDirectory: boolean; size: number; mtime: Date }>;
  /** Remove a file or directory */
  remove: (path: string, options?: { recursive?: boolean }) => Promise<void>;
  /** Watch for file changes */
  watch: (path: string, callback: (event: 'change' | 'rename') => void) => () => void;
}

/** Cryptographic operations interface */
export interface ICrypto {
  /** Encrypt data */
  encrypt: (data: string, key?: string) => Promise<string>;
  /** Decrypt data */
  decrypt: (encrypted: string, key?: string) => Promise<string>;
  /** Generate a hash of the data */
  hash: (data: string, algorithm?: string) => Promise<string>;
  /** Generate a cryptographic key */
  generateKey: (options?: { length?: number; type?: string }) => Promise<string>;
  /** Sign data */
  sign: (data: string, privateKey: string) => Promise<string>;
  /** Verify a signature */
  verify: (data: string, signature: string, publicKey: string) => Promise<boolean>;
  /** Generate a key pair */
  generateKeyPair: (options?: { type?: string; modulusLength?: number }) => Promise<{
    publicKey: string;
    privateKey: string;
  }>;
}

/** Message interface for communication between components */
export interface IMessage<T = unknown> {
  /** Message ID */
  id: string;
  /** Message type */
  type: string;
  /** Sender ID */
  sender: string;
  /** Recipient ID (optional for broadcast) */
  recipient?: string;
  /** Message payload */
  payload: T;
  /** Timestamp */
  timestamp: number;
  /** Message metadata */
  metadata?: Record<string, unknown>;
  /** Error information (for error responses) */
  error?: {
    code: string;
    message: string;
    details?: unknown;
  };
}

/** Event bus interface for pub/sub */
export interface IEventBus {
  /** Subscribe to an event */
  on: <T = unknown>(event: string, handler: (data: T) => void) => () => void;
  /** Emit an event */
  emit: <T = unknown>(event: string, data?: T) => void;
  /** Remove all listeners for an event */
  off: (event: string) => void;
  /** Remove a specific listener */
  removeListener: (event: string, handler: (...args: any[]) => void) => void;
}
