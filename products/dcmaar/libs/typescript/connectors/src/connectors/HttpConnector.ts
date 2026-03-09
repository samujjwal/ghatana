/**
 * @fileoverview HTTP connector implementation supporting request/response and polling modes.
 *
 * Provides configurable retry behavior, request authentication helpers, structured response
 * handling, and integration points for telemetry and resilience primitives.
 * Use alongside `RetryPolicy` for backoff logic and `Telemetry` to trace outbound requests.
 *
 * @see {@link HttpConnector}
 * @see {@link ../BaseConnector.BaseConnector | BaseConnector}
 * @see {@link ../types.ConnectionOptions | ConnectionOptions}
 */
// Using global fetch (Node 20+)
import { v4 as uuidv4 } from 'uuid';
import { BaseConnector } from '../BaseConnector';
import { ConnectionOptions } from '../types';

export interface HttpConnectorConfig extends ConnectionOptions {
  /**
   * Base URL for HTTP requests
   */
  url: string;
  
  /**
   * HTTP method to use
   * @default 'GET'
   */
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  
  /**
   * Request timeout in milliseconds
   * @default 30000
   */
  timeout?: number;
  
  /**
   * Enable/disable SSL certificate verification
   * @default true
   */
  rejectUnauthorized?: boolean;
  
  /**
   * Additional fetch options
   */
  fetchOptions?: Omit<RequestInit, 'method' | 'headers' | 'body'>;
  
  /**
   * Polling interval in milliseconds (for polling mode)
   * If not set, the connector will work in request/response mode
   */
  pollInterval?: number;
  
  /**
   * Enable/disable automatic retry on failure
   * @default true
   */
  autoRetry?: boolean;
  
  /**
   * Maximum number of retry attempts
   * @default 3
   */
  maxRetryAttempts?: number;
  
  /**
   * Initial retry delay in milliseconds
   * @default 1000
   */
  retryDelay?: number;
  
  /**
   * Maximum retry delay in milliseconds
   * @default 30000
   */
  maxRetryDelay?: number;
  
  /**
   * Retry multiplier for exponential backoff
   * @default 2
   */
  retryMultiplier?: number;
}

/**
 * HTTP connector capable of operating in both on-demand and polling modes.
 *
 * Handles request construction, authentication, retries, timeout management, content negotiation,
 * and emits structured events for downstream consumers.
 *
 * **Example (polling endpoint):**
 * ```ts
 * const connector = new HttpConnector({ url: 'https://api.example.com/data', pollInterval: 5000 });
 * connector.onEvent('data', event => telemetry.trace('http.receive', async () => event.payload));
 * await connector.connect();
 * ```
 *
 * **Example (ingest endpoint with retry):**
 * ```ts
 * const retry = new RetryPolicy({ maxAttempts: 5, jitter: true });
 * const connector = new HttpConnector({ url: 'https://api.example.com/ingest', method: 'POST' });
 * await connector.connect();
 * await retry.execute(() => connector.send({ message: 'hello' }));
 * ```
 */
export class HttpConnector extends BaseConnector<HttpConnectorConfig> {
  /** Timer handle for scheduled polling requests. */
  private _pollingInterval: NodeJS.Timeout | null = null;
  /** Abort controller used to cancel in-flight requests. */
  private _abortController: AbortController | null = null;
  /** Indicates whether polling loop is active. */
  private _isPolling: boolean = false;
  /** Last successful HTTP response captured by the connector. */
  private _lastResponse: Response | null = null;
  
  /**
   * Creates an HTTP connector with defaulted options.
   *
   * @param {HttpConnectorConfig} config - Connector configuration
   */
  constructor(config: HttpConnectorConfig) {
    super({
      ...config,
      type: 'http',
    });
  }
  
  /**
   * Returns the most recent HTTP `Response` instance, if any.
   *
   * Useful for diagnostics and custom parsing routines where callers need access to headers or
   * status codes beyond emitted events.
   */
  public get lastResponse(): Response | null {
    return this._lastResponse;
  }
  
  /** @inheritdoc */
  protected async _connect(): Promise<void> {
    if (this._config.pollInterval) {
      this._startPolling();
    }
    
    // For HTTP connectors, we consider the connection successful if we can make a request
    try {
      await this._makeRequest();
      this.status = 'connected';
    } catch (error) {
      this.status = 'error';
      throw error;
    }
  }
  
  /** @inheritdoc */
  protected async _disconnect(): Promise<void> {
    this._stopPolling();
    
    // Abort any in-flight requests
    if (this._abortController) {
      this._abortController.abort();
      this._abortController = null;
    }
    
    this.status = 'disconnected';
  }
  
  /**
   * Sends data to the configured HTTP endpoint (POST by default).
   *
   * Serializes objects to JSON, issues a request with connector defaults, and emits a `response`
   * event containing status codes and headers. Use `RetryPolicy.execute()` externally for backoff.
   *
   * @param {*} data - Payload to serialize and send
   * @param {Record<string, any>} [options] - Additional fetch options overrides
   * @throws {Error} When request fails after retries
   */
  public async send(data: unknown, options: Record<string, any> = {}): Promise<void> {
    try {
      const response = await this._makeRequest({
        method: 'POST',
        body: JSON.stringify(data),
        ...options,
      });
      
      const responseData = await this._parseResponse(response);
      
      this._emitEvent({
        id: uuidv4(),
        type: 'response',
        timestamp: Date.now(),
        payload: responseData,
        metadata: {
          status: response.status,
          statusText: response.statusText,
          headers: this._formatHeaders(response.headers),
        },
      });
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err));
      this._handleError('send', error);
      throw error;
    }
  }
  
  /**
   * Executes a fetch request with connector defaults and timeout handling.
   *
   * Adds authentication headers, enforces per-request timeout via `AbortController`, and ensures
   * structured error propagation so higher-level policies can react.
   *
   * @param {RequestInit} [overrides] - Per-request overrides
   * @returns {Promise<Response>} Resolved fetch response
   * @throws {Error} When request fails or times out
   */
  private async _makeRequest(overrides: RequestInit & { timeout?: number } = {}): Promise<Response> {
    if (!this._config.url) {
      throw new Error('URL is required for HTTP connector');
    }
    
    // Create a new AbortController for this request
    const abortController = new AbortController();
    this._abortController = abortController;
    
    const timeout = (overrides as any).timeout ?? this._config.timeout ?? 30000;
    
    // Set up timeout
    const timeoutId = setTimeout(() => {
      abortController.abort();
    }, timeout);
    
    try {
      const restOverrides: RequestInit & { timeout?: number } = { ...(overrides as any) };
      delete (restOverrides as any).timeout;
      const requestOptions: RequestInit = {
        method: this._config.method || 'GET',
        headers: {
          'Content-Type': 'application/json',
          ...this._config.headers,
          ...(restOverrides.headers || {}),
        },
        signal: abortController.signal,
        ...this._config.fetchOptions,
        ...restOverrides,
      };
      
      // Add authentication if configured
      this._applyAuth(requestOptions);
      
      this.emit('request', {
        url: this._config.url,
        method: requestOptions.method,
        headers: requestOptions.headers,
        body: requestOptions.body,
      });
      
      const response = await fetch(this._config.url, requestOptions);
      this._lastResponse = response;
      
      if (!response.ok) {
        const errorText = await response.text().catch(() => 'Unknown error');
        throw new HttpError(
          `HTTP error ${response.status}: ${response.statusText}`,
          response.status,
          response.statusText,
          errorText
        );
      }
      
      return response;
    } catch (err) {
      const error: unknown = err;
      if (error?.name === 'AbortError') {
        throw new Error(`Request timed out after ${timeout}ms`);
      }
      throw error as Error;
    } finally {
      clearTimeout(timeoutId);
      if (this._abortController === abortController) {
        this._abortController = null;
      }
    }
  }
  
  /**
   * Parses response based on `Content-Type` header.
   *
   * Supports JSON, text, multipart, and binary formats. Extend this for protocol-specific
   * decoding (e.g., protobuf, Avro) before emitting downstream events.
   *
   * @param {Response} response - Fetch response
   * @returns {Promise<any>} Parsed payload
   */
  private async _parseResponse(response: Response): Promise<unknown> {
    const contentType = response.headers.get('content-type') || '';
    
    if (contentType.includes('application/json')) {
      return response.json();
    } else if (contentType.includes('text/')) {
      return response.text();
    } else if (contentType.startsWith('multipart/')) {
      return response.formData();
    } else {
      return response.arrayBuffer();
    }
  }
  
  /**
   * Converts fetch headers into a plain object.
   *
   * Facilitates downstream serialization in events, metrics tags, or structured logs.
   */
  private _formatHeaders(headers: Headers): Record<string, string> {
    const result: Record<string, string> = {};
    headers.forEach((value: string, key: string) => {
      result[key] = value;
    });
    return result;
  }
  
  /**
   * Applies configured authentication scheme to request headers.
   *
   * Supports basic, bearer, API key, and placeholder OAuth2 flows. Customize this method to plug in
   * token refreshers or signed request strategies.
   */
  private _applyAuth(options: RequestInit): void {
    if (!this._config.auth) return;
    
    const { auth } = this._config;
    
    switch (auth.type) {
      case 'basic':
        const credentials = Buffer.from(`${auth.username}:${auth.password}`).toString('base64');
        options.headers = {
          ...options.headers,
          'Authorization': `Basic ${credentials}`,
        };
        break;
        
      case 'bearer':
        options.headers = {
          ...options.headers,
          'Authorization': `Bearer ${auth.token}`,
        };
        break;
        
      case 'api_key':
        const headerName = auth.headerName || 'X-API-Key';
        options.headers = {
          ...options.headers,
          [headerName]: auth.apiKey,
        };
        break;
        
      case 'oauth2':
        // OAuth2 implementation would go here
        // This is a simplified example
        if (auth.accessToken) {
          options.headers = {
            ...options.headers,
            'Authorization': `Bearer ${auth.accessToken}`,
          };
        }
        break;
    }
  }
  
  /**
   * Starts asynchronous polling loop using `_makeRequest()`.
   *
   * Emits `data` events on each successful fetch. Backoff can be layered via external schedulers or
   * by dynamically adjusting `pollInterval`.
   */
  private _startPolling(): void {
    if (this._isPolling || !this._config.pollInterval) return;
    
    this._isPolling = true;
    
    const poll = async () => {
      if (!this._isPolling) return;
      
      try {
        const response = await this._makeRequest();
        const data = await this._parseResponse(response);
        
        this._emitEvent({
          id: uuidv4(),
          type: 'data',
          timestamp: Date.now(),
          payload: data,
          metadata: {
            status: response.status,
            statusText: response.statusText,
            headers: this._formatHeaders(response.headers),
          },
        });
      } catch (err) {
        const error = err instanceof Error ? err : new Error(String(err));
        this._handleError('polling', error);
      }
      
      if (this._isPolling) {
        this._pollingInterval = setTimeout(poll, this._config.pollInterval);
      }
    };
    
    // Initial poll
    poll();
  }
  
  /**
   * Stops polling loop and clears timers.
   */
  private _stopPolling(): void {
    this._isPolling = false;
    
    if (this._pollingInterval) {
      clearTimeout(this._pollingInterval);
      this._pollingInterval = null;
    }
  }
  
  /**
   * Emits structured error events and forwards errors to listeners.
   *
   * Error payloads include context (e.g., `send`, `polling`) to simplify alert routing and root
   * cause analysis.
   */
  private _handleError(context: string, error: Error): void {
    const errorEvent = {
      id: uuidv4(),
      type: 'error',
      timestamp: Date.now(),
      payload: {
        context,
        message: error.message,
        stack: error.stack,
        ...(error instanceof HttpError ? {
          status: error.status,
          statusText: error.statusText,
          response: error.response,
        } : {}),
      },
    };
    
    this._emitEvent(errorEvent);
    this.emit('error', error);
  }
  
  /**
   * @inheritdoc
   */
  public override async destroy(): Promise<void> {
    this._stopPolling();
    
    if (this._abortController) {
      this._abortController.abort();
      this._abortController = null;
    }
    
    await super.destroy();
  }
}

/**
 * Error type representing failed HTTP responses.
 */
export class HttpError extends Error {
  /**
   * @param {string} message - Error message
   * @param {number} status - HTTP status code
   * @param {string} statusText - HTTP status text
   * @param {string} response - Response body text
   */
  constructor(
    message: string,
    public readonly status: number,
    public readonly statusText: string,
    public readonly response: string,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}
