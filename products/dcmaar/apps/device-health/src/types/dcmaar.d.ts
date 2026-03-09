import type { TransportEventMap, TransportType } from '../connectors/schemas/transport';

declare global {
  interface Window {
    dcmaar?: DCMAARInterface;
  }
}

type NamespacedTransportEvents = {
  'source:message': TransportEventMap['message'];
  'source:statusChange': TransportEventMap['statusChange'];
  'source:error': TransportEventMap['error'];
  'sink:message': TransportEventMap['message'];
  'sink:statusChange': TransportEventMap['statusChange'];
  'sink:error': TransportEventMap['error'];
  status: TransportEventMap['statusChange'];
};

type DCMAARTransportEvents = TransportEventMap & NamespacedTransportEvents;
type DCMAARTransportEvent = keyof DCMAARTransportEvents;

export interface DCMAARInterface {
  logEvent(event: Record<string, unknown>): Promise<void>;
  initTransports(): Promise<void>;
  getTransportType(): { source: TransportType; sink: TransportType };
  on<T extends DCMAARTransportEvent>(
    event: T,
    listener: (payload: DCMAARTransportEvents[T]) => void
  ): void;
  off<T extends DCMAARTransportEvent>(
    event: T,
    listener: (payload: DCMAARTransportEvents[T]) => void
  ): void;
}
