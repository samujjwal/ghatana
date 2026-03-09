/**
 * Extension IPC Channel (Stub)
 */

export interface IpcChannel {
  connect?(): Promise<void>;
  disconnect?(): Promise<void>;
  send?(message: unknown): Promise<void>;
  onMessage?(handler: (msg: unknown) => void): void;
  offMessage?(handler: (msg: unknown) => void): void;
  removeAllListeners?(): void;
  isConnected?(): boolean;
}

export interface IpcMessageHandler<T = unknown> {
  (message: T): void | Promise<void>;
}

export class ExtensionIpcChannel implements IpcChannel {
  connect?(): Promise<void> {
    return Promise.resolve();
  }

  disconnect?(): Promise<void> {
    return Promise.resolve();
  }

  send?(_message: unknown): Promise<void> {
    return Promise.resolve();
  }

  onMessage?(_handler: (msg: unknown) => void): void {
    // Stub
  }

  offMessage?(_handler: (msg: unknown) => void): void {
    // Stub
  }

  removeAllListeners?(): void {
    // Stub
  }

  isConnected?(): boolean {
    return false;
  }
}
