/**
 * Message type definitions for extension communication
 * Provides type-safe interfaces for all message types used across the extension
 */

/**
 * Base message interface for all extension messages
 */
export interface BaseMessage {
  /** Unique identifier for the message */
  id?: string;
  /** Timestamp when the message was created */
  timestamp: number;
}

/**
 * Message types for runtime communication
 */
export enum MessageType {
  PING = 'PING',
  INGEST = 'INGEST',
  TEST_ENQUEUE = 'TEST_ENQUEUE',
  SET_ENDPOINT = 'SET_ENDPOINT',
  GET_ENDPOINT = 'GET_ENDPOINT',
  DCMAAR_LIST_ADDONS = 'DCMAAR_LIST_ADDONS',
  DCMAAR_INSTALL_ADDON = 'DCMAAR_INSTALL_ADDON',
  DCMAAR_GET_ENABLED_ADDONS = 'DCMAAR_GET_ENABLED_ADDONS',
  DCMAAR_ENABLE_ADDON = 'DCMAAR_ENABLE_ADDON',
  DCMAAR_DISABLE_ADDON = 'DCMAAR_DISABLE_ADDON',
  DCMAAR_LIST_AUDIT = 'DCMAAR_LIST_AUDIT',
  DCMAAR_GET_RECEIVE_CONFIG = 'DCMAAR_GET_RECEIVE_CONFIG',
  DCMAAR_SET_RECEIVE_CONFIG = 'DCMAAR_SET_RECEIVE_CONFIG',
  DCMAAR_ADMIN_UNLOCK = 'DCMAAR_ADMIN_UNLOCK',
}

/**
 * Ping message for health checks
 */
export interface PingMessage extends BaseMessage {
  type: MessageType.PING;
}

/**
 * Ping response
 */
export interface PingResponse {
  success: boolean;
  timestamp: number;
}

/**
 * Ingest event message
 */
export interface IngestMessage extends BaseMessage {
  type: MessageType.INGEST;
  payload: IngestPayload;
}

/**
 * Ingest payload structure
 */
export interface IngestPayload {
  eventType: string;
  data: Record<string, unknown>;
  metadata?: {
    url?: string;
    userAgent?: string;
    sessionId?: string;
    [key: string]: unknown;
  };
}

/**
 * Ingest response
 */
export interface IngestResponse {
  ok: boolean;
  queued?: boolean;
  error?: string;
}

/**
 * Test enqueue message (only available when test hooks are enabled)
 */
export interface TestEnqueueMessage extends BaseMessage {
  type: MessageType.TEST_ENQUEUE;
  payload: IngestPayload;
}

/**
 * Set endpoint message
 */
export interface SetEndpointMessage extends BaseMessage {
  type: MessageType.SET_ENDPOINT;
  payload: {
    endpoint: string;
  };
}

/**
 * Get endpoint message
 */
export interface GetEndpointMessage extends BaseMessage {
  type: MessageType.GET_ENDPOINT;
}

/**
 * Endpoint response
 */
export interface EndpointResponse {
  endpoint: string;
}

/**
 * Add-on manifest structure
 */
export interface AddonManifest {
  id: string;
  name: string;
  version: string;
  entry: string;
  capabilities: string[];
}

/**
 * Install add-on message
 */
export interface InstallAddonMessage extends BaseMessage {
  type: MessageType.DCMAAR_INSTALL_ADDON;
  manifest: AddonManifest;
  files: Record<string, string>;
  signature?: string;
  keyId?: string;
}

/**
 * Add-on operation response
 */
export interface AddonOperationResponse {
  ok: boolean;
  error?: string;
}

/**
 * Enable/Disable add-on message
 */
export interface AddonToggleMessage extends BaseMessage {
  type: MessageType.DCMAAR_ENABLE_ADDON | MessageType.DCMAAR_DISABLE_ADDON;
  id: string;
}

/**
 * List add-ons message
 */
export interface ListAddonsMessage extends BaseMessage {
  type: MessageType.DCMAAR_LIST_ADDONS;
}

/**
 * Add-on info structure
 */
export interface AddonInfo {
  id: string;
  name: string;
  version: string;
  enabled: boolean;
  capabilities: string[];
}

/**
 * Get enabled add-ons message
 */
export interface GetEnabledAddonsMessage extends BaseMessage {
  type: MessageType.DCMAAR_GET_ENABLED_ADDONS;
}

/**
 * List audit entries message
 */
export interface ListAuditMessage extends BaseMessage {
  type: MessageType.DCMAAR_LIST_AUDIT;
}

/**
 * Audit entry structure
 */
export interface AuditEntry {
  id: string;
  timestamp: number;
  action: string;
  actor: string;
  result: 'success' | 'denied' | 'error';
  details?: Record<string, unknown>;
}

/**
 * Receive configuration structure
 */
export interface ReceiveConfig {
  enabled: boolean;
  port?: number;
  allowedOrigins?: string[];
}

/**
 * Get receive config message
 */
export interface GetReceiveConfigMessage extends BaseMessage {
  type: MessageType.DCMAAR_GET_RECEIVE_CONFIG;
}

/**
 * Set receive config message
 */
export interface SetReceiveConfigMessage extends BaseMessage {
  type: MessageType.DCMAAR_SET_RECEIVE_CONFIG;
  config: ReceiveConfig;
}

/**
 * Admin unlock message
 */
export interface AdminUnlockMessage extends BaseMessage {
  type: MessageType.DCMAAR_ADMIN_UNLOCK;
  password: string;
}

/**
 * Admin unlock response
 */
export interface AdminUnlockResponse {
  ok: boolean;
  error?: string;
}

/**
 * Union type of all possible messages
 */
export type ExtensionMessage =
  | PingMessage
  | IngestMessage
  | TestEnqueueMessage
  | SetEndpointMessage
  | GetEndpointMessage
  | InstallAddonMessage
  | AddonToggleMessage
  | ListAddonsMessage
  | GetEnabledAddonsMessage
  | ListAuditMessage
  | GetReceiveConfigMessage
  | SetReceiveConfigMessage
  | AdminUnlockMessage;

/**
 * Union type of all possible responses
 */
export type ExtensionResponse =
  | PingResponse
  | IngestResponse
  | EndpointResponse
  | AddonOperationResponse
  | AddonInfo[]
  | AuditEntry[]
  | ReceiveConfig
  | AdminUnlockResponse;

/**
 * Type guard to check if a message is a specific type
 */
export function isMessageType<T extends ExtensionMessage>(
  message: unknown,
  type: MessageType
): message is T {
  return (
    typeof message === 'object' &&
    message !== null &&
    'type' in message &&
    message.type === type
  );
}

/**
 * Type-safe message creator
 */
export function createMessage<T extends ExtensionMessage>(
  type: MessageType,
  payload?: Partial<T>
): T {
  return {
    type,
    timestamp: Date.now(),
    ...payload,
  } as T;
}
