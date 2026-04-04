declare module "@dcmaar/connectors" {
  export interface ConnectionOptions {
    id?: string;
    type: string;
    auth?: {
      type: "none" | "basic" | "bearer" | "api_key" | "oauth2";
      [key: string]: unknown;
    };
    [key: string]: unknown;
  }

  export interface Event<TPayload = unknown> {
    id: string;
    type: string;
    payload: TPayload;
    metadata?: Record<string, unknown>;
  }

  export interface IConnector<
    TOptions extends ConnectionOptions = ConnectionOptions,
  > {
    connect(): Promise<void>;
    disconnect(): Promise<void>;
    send(
      payload: unknown,
      options?: {
        eventId?: string;
        eventType?: string;
        metadata?: Record<string, unknown>;
      },
    ): Promise<void>;
  }

  export function createConnector<TOptions extends ConnectionOptions>(
    config: TOptions,
  ): IConnector<TOptions>;
}
