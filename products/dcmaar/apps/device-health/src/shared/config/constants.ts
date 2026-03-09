/**
 * Constants and magic strings used throughout the DCMAAR extension.
 * Centralized here to ensure consistency and ease refactoring.
 */

/**
 * Storage keys for persisted handles and configuration
 */
export const STORAGE_KEYS = {
  // File system handles
  DIR: 'dcmaar:dir',
  CONFIG: 'dcmaar:config', 
  COMMANDS: 'dcmaar:commands',
  METRICS: 'dcmaar:metrics',
  ACKS: 'dcmaar:acks',
  
  // File sink specific keys
  FILE_SINK_METRICS: 'dcmaar:fileSink:metrics',
  FILE_SINK_ACKS: 'dcmaar:fileSink:acks',
  
  // Encryption key for storage
  ENCRYPTION_KEY: 'dcmaar:encryption:key',
} as const;

/**
 * DOM element IDs and attributes used for content script communication
 */
export const DOM_CONSTANTS = {
  // Container for mirroring events to page context
  EVENTS_CONTAINER_ID: '__dcmaar_events_container',
  
  // Data attributes
  EVENTS_CONTAINER_ATTR: 'data-dcmaar',
  EVENT_NODE_ATTR: 'data-dcmaar-event', 
  EXTENSION_LOADED_ATTR: 'data-dcmaar-extension',
  
  // Attribute values
  EVENTS_CONTAINER_VALUE: 'events',
  EVENT_NODE_VALUE: '1',
  EXTENSION_LOADED_VALUE: 'true',
} as const;

/**
 * Message types and postMessage markers
 */
export const MESSAGE_CONSTANTS = {
  // PostMessage marker to identify DCMAAR messages
  MESSAGE_MARKER: '__dcmaar',
  
  // Runtime message types
  INGEST_EVENT: 'INGEST_EVENT',
  SET_INGEST_ENDPOINT: 'SET_INGEST_ENDPOINT', 
  DUMP_STATE: 'DUMP_STATE',
  GET_TEST_SINK: 'GET_TEST_SINK',
  TEST_ENQUEUE: 'TEST_ENQUEUE',
  
  // Content script commands
  CMD_INGEST: 'ingest',
  CMD_SET_ENDPOINT: 'setEndpoint',
  CMD_DUMP_STATE: 'dumpState', 
  CMD_GET_TEST_SINK: 'getTestSink',
  
  // Reply types
  REPLY_INGEST: 'ingest',
  REPLY_SET_ENDPOINT: 'setEndpoint',
  REPLY_DUMP_STATE: 'dumpState',
  REPLY_GET_TEST_SINK: 'getTestSink',
} as const;

/**
 * Global window properties used for extension state
 */
export const WINDOW_PROPERTIES = {
  EVENTS_ARRAY: '__dcmaarEvents',
  CONTENT_SCRIPT_LOADED: '__dcmaarContentScriptLoaded',
} as const;

/**
 * Default file names for filesystem operations
 */
export const DEFAULT_FILE_NAMES = {
  METRICS: 'dcmaar_metrics.ndjson',
  ACKS: 'dcmaar_acks.ndjson',
  CONFIG: 'dcmaar_config.json',
  COMMANDS: 'dcmaar_commands.ndjson',
} as const;

/**
 * Runtime connection names
 */
export const CONNECTION_NAMES = {
  CONTENT_PORT: 'dcmaar-content-port',
} as const;

/**
 * Common timeouts and limits (in milliseconds)
 */
export const TIMEOUTS = {
  PORT_MESSAGE_TIMEOUT: 3000,
  POLL_INTERVAL_DEFAULT: 1000,
  MAX_EVENTS_IN_CONTAINER: 50,
} as const;

/**
 * Crypto and security defaults
 */
export const CRYPTO_DEFAULTS = {
  DEFAULT_PASSPHRASE: 'dcmaar-default',
} as const;