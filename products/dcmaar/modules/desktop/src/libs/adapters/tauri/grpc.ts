/**
 * Tauri gRPC bridge for adapter operations.
 * Provides request/response abstractions with optional mTLS support.
 */

export interface GrpcRequest {
  method: string;
  service: string;
  payload: Record<string, unknown>;
  metadata?: Record<string, string>;
  timeoutMs?: number;
}

export interface GrpcResponse<T = unknown> {
  status: number;
  payload: T;
  metadata?: Record<string, string>;
}

export interface GrpcStreamMessage<T = unknown> {
  type: 'data' | 'end' | 'error';
  payload?: T;
  error?: string;
}

export interface GrpcConfig {
  endpoint: string;
  clientCert?: string;
  clientKey?: string;
  caCert?: string;
  certPin?: string;
}

export interface GrpcClient {
  unary<T = unknown>(request: GrpcRequest): Promise<GrpcResponse<T>>;
  stream<T = unknown>(request: GrpcRequest, onMessage: (message: GrpcStreamMessage<T>) => void): Promise<void>;
  config: GrpcConfig;
}

class TauriGrpcClient implements GrpcClient {
  config: GrpcConfig;

  constructor(config: GrpcConfig) {
    this.config = config;
  }

  async unary<T>(request: GrpcRequest): Promise<GrpcResponse<T>> {
    const payload = {
      endpoint: this.config.endpoint,
      request,
      mtls: this.getTlsConfig(),
    };

    const result = await this.invoke<GrpcResponse<T>>('plugin:grpc|unary', payload);
    return result;
  }

  async stream<T>(request: GrpcRequest, onMessage: (message: GrpcStreamMessage<T>) => void): Promise<void> {
    const payload = {
      endpoint: this.config.endpoint,
      request,
      mtls: this.getTlsConfig(),
    };

    await this.invoke('plugin:grpc|stream', {
      ...payload,
      callback: this.registerStreamCallback(onMessage),
    });
  }

  private getTlsConfig() {
    const { clientCert, clientKey, caCert, certPin } = this.config;
    if (!clientCert && !clientKey && !caCert && !certPin) {
      return undefined;
    }
    return { clientCert, clientKey, caCert, certPin };
  }

  private async invoke<T>(command: string, args?: Record<string, unknown>): Promise<T> {
    if (typeof window === 'undefined' || !('__TAURI__' in window)) {
      throw new Error('Tauri runtime not available');
    }
    const tauri = (window as any).__TAURI__;
    return tauri.invoke(command, args);
  }

  private registerStreamCallback<T>(handler: (message: GrpcStreamMessage<T>) => void) {
    if (typeof window === 'undefined') {
      throw new Error('Stream callbacks require window context');
    }

    const callbackId = `grpc_stream_${Date.now()}_${Math.random().toString(16).slice(2)}`;
    (window as any)[callbackId] = handler;
    return callbackId;
  }
}

class MockGrpcClient implements GrpcClient {
  config: GrpcConfig;

  constructor(config: GrpcConfig) {
    this.config = config;
  }

  async unary<T>(request: GrpcRequest): Promise<GrpcResponse<T>> {
    return {
      status: 200,
      payload: {
        mock: true,
        method: request.method,
        service: request.service,
        payload: request.payload,
      } as unknown as T,
      metadata: { 'x-mock': 'grpc' },
    };
  }

  async stream<T>(request: GrpcRequest, onMessage: (message: GrpcStreamMessage<T>) => void): Promise<void> {
    onMessage({ type: 'data', payload: { mock: true, request } as unknown as T });
    onMessage({ type: 'end' });
  }
}

export const createGrpcClient = (config: GrpcConfig): GrpcClient => {
  if (typeof window !== 'undefined' && '__TAURI__' in window) {
    return new TauriGrpcClient(config);
  }
  return new MockGrpcClient(config);
};
