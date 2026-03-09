/**
 * Core Event type definition
 * Represents an event in the DCMAAR system
 */

export interface Event {
  id: string;
  type: string;
  timestamp: Date;
  source: string;
  payload: Record<string, unknown>;
  metadata?: Record<string, unknown>;
}

export interface EventListener {
  onEvent(event: Event): Promise<void> | void;
}

export interface EventEmitter {
  emit(event: Event): Promise<void>;
  on(type: string, listener: EventListener): void;
  off(type: string, listener: EventListener): void;
}
