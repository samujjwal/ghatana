export type MessageType =
  | 'METRICS_UPDATE'
  | 'CONFIG_UPDATE'
  | 'STATE_UPDATE'
  | 'STORAGE_GET'
  | 'STORAGE_SET'
  | 'STORAGE_REMOVE';

export type MetricData = Record<string, unknown>;
export type ConfigData = Record<string, unknown>;
export type StateData = Record<string, unknown>;

export interface BaseMessage<T = unknown> {
  type: MessageType;
  payload?: T;
  requestId?: string;
  timestamp: number;
}

export interface MetricsUpdateMessage extends BaseMessage<MetricData> {
  type: 'METRICS_UPDATE';
}

export interface ConfigUpdateMessage extends BaseMessage<ConfigData> {
  type: 'CONFIG_UPDATE';
}

export interface StateUpdateMessage extends BaseMessage<StateData> {
  type: 'STATE_UPDATE';
}

export type Message =
  | MetricsUpdateMessage
  | ConfigUpdateMessage
  | StateUpdateMessage
  | BaseMessage<Record<string, unknown>>;

export interface MessageResponse<T = unknown> {
  success: boolean;
  data?: T;
  error?: Error;
  errorCode?: string;
  requestId?: string;
}

export function isMessage(message: unknown): message is Message {
  if (typeof message !== 'object' || message === null) {
    return false;
  }
  const record = message as Record<string, unknown>;
  return typeof record.type === 'string' && typeof record.timestamp === 'number';
}

export function isMetricsUpdateMessage(message: Message): message is MetricsUpdateMessage {
  return message.type === 'METRICS_UPDATE';
}

