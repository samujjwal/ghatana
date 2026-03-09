// Core Types
type MetricData = Record<string, unknown>;
type ConfigData = Record<string, unknown>;
type StateData = Record<string, unknown>;

declare global {
  // Extended Window interface
  interface Window {
    // DCMAAR specific properties
    __dcmaarContentScriptLoaded?: boolean;
    __dcmaarTestAPI?: unknown;
    __dcmaarExtensionLoaded?: boolean;
    __dcmaar__bridgeInstalled?: boolean;
    __dcmaarEvents?: Array<Record<string, unknown>>;
    __DCMAAR_TEST_HELPERS?: Record<string, unknown>;
    
    // Extension specific properties
    [key: string]: any;
  }

  // Message types
  interface BaseMessage<T = unknown> {
    type: string;
    data: T;
    timestamp?: number;
    id?: string;
  }

  // Specific message types
  interface MetricsUpdateMessage extends BaseMessage<MetricData> {}
  interface ConfigUpdateMessage extends BaseMessage<ConfigData> {}
  interface StateUpdateMessage extends BaseMessage<StateData> {}

  // DOM extensions
  interface HTMLElement {
    dataset: DOMStringMap & {
      section?: string;
      testId?: string;
      [key: string]: string | undefined;
    };
  }
}

// Module declarations
declare module '*.json' {
  const value: any;
  export default value;
}

declare module '*.css' {
  const content: { [className: string]: string };
  export default content;
}

declare module '*.scss' {
  const content: { [className: string]: string };
  export default content;
}

// Custom module declarations
declare module '@/config/constants' {
  export const WINDOW_PROPERTIES: {
    EVENTS_ARRAY: string;
    CONTENT_SCRIPT_LOADED: string;
    [key: string]: string;
  };
  
  export const MESSAGE_CONSTANTS: {
    MESSAGE_MARKER: string;
    REPLY_GET_TEST_SINK: string;
    [key: string]: string;
  };
  
  export const TIMEOUTS: {
    [key: string]: number;
  };
  
  export const DOM_CONSTANTS: {
    [key: string]: string;
  };
}

declare module '@/platform/messageBridge' {
  export function sendIngestEvent(event: unknown): Promise<unknown>;
  export function getTestSink(): Promise<unknown>;
  export function sendRuntimeMessage(message: unknown): Promise<unknown>;
}

declare module '@/core/utils/logger' {
  export const contentLogger: {
    debug: (...args: unknown[]) => void;
    info: (...args: unknown[]) => void;
    warn: (...args: unknown[]) => void;
    error: (...args: unknown[]) => void;
  };
}

// Add type for webextension-polyfill
declare module 'webextension-polyfill' {
  export default browser;
}

export {};
