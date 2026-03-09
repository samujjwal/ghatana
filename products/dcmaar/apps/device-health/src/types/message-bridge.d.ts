// Type definitions for message bridge

declare module '../../src/platform/messageBridge' {
  export function sendRuntimeMessage(
    message: unknown,
    options?: { tabId?: number }
  ): Promise<unknown>;
}
