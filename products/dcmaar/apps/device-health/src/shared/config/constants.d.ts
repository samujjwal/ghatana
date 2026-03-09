// Configuration constants and types
declare module '@config/constants' {
  export const DEFAULT_METRICS_FILE = 'dcmaar_metrics.ndjson';
  export const DEFAULT_ACKS_FILE = 'dcmaar_acks.ndjson';
  export const DEFAULT_DIRECTORY = 'dcmaar-data';
  
  export const RETRY_OPTIONS = {
    maxRetries: 3,
    initialDelay: 1000,
    maxDelay: 5000,
    factor: 2,
  };
  
  export const BATCH_OPTIONS = {
    maxSize: 100,
    maxAge: 5000, // 5 seconds
  };
  
  // Message constants
  export const MESSAGE_CONSTANTS = {
    MESSAGE_MARKER: '__DCMAAR_MESSAGE__',
    CMD_INGEST: 'INGEST',
    CMD_ACK: 'ACK',
    CMD_NACK: 'NACK'
  };

  // Window property names
  export const WINDOW_PROPERTIES = {
    EVENTS_ARRAY: '__DCMAAR_EVENTS__',
    MESSAGE_HANDLER: '__DCMAAR_MESSAGE_HANDLER__'
  };
}
