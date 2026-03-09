/**
 * @fileoverview Network Request Interceptor
 *
 * Intercepts fetch() and XMLHttpRequest calls in the page context to capture
 * network requests. This is the Manifest V3 compatible way to monitor network
 * activity since webRequest API is not available in service workers.
 *
 * @module interactions/content/networkInterceptor
 */

/**
 * Network request data
 */
export interface NetworkRequestData {
  requestId: string;
  url: string;
  method: string;
  timestamp: number;
  type: 'fetch' | 'xhr';
  headers?: Record<string, string>;
  body?: string;
}

/**
 * Network response data
 */
export interface NetworkResponseData {
  requestId: string;
  url: string;
  method: string;
  status: number;
  statusText: string;
  timestamp: number;
  duration: number;
  type: 'fetch' | 'xhr';
  headers?: Record<string, string>;
  responseSize?: number;
}

export interface NetworkErrorData {
  requestId: string;
  url: string;
  method: string;
  timestamp: number;
  duration: number;
  type: 'fetch' | 'xhr';
  error: string;
}

/**
 * Initialize network request interception
 *
 * Patches fetch() and XMLHttpRequest to capture all network requests
 * made by the page and sends them to the background script.
 */
export function initializeNetworkInterception(): void {
  // Check if already initialized
  if ((window as any).__dcmaar_network_interceptor_initialized) {
    return;
  }
  (window as any).__dcmaar_network_interceptor_initialized = true;

  let requestCounter = 0;
  const generateRequestId = () => `net-${Date.now()}-${requestCounter++}`;

  // Track active requests for duration calculation
  const activeRequests = new Map<string, number>();

  /**
   * Send network event to background script
   */
  function sendNetworkEvent(event: any) {
    try {
      // Send to background via runtime messaging
      if (typeof chrome !== 'undefined' && chrome.runtime) {
        const maybePromise = chrome.runtime.sendMessage({
          type: 'network-event',
          payload: event,
        }) as unknown;
        if (maybePromise && typeof (maybePromise as Promise<unknown>).catch === 'function') {
          (maybePromise as Promise<unknown>).catch((error) => {
            if (!(error instanceof Error) || !error.message.includes('Extension context invalidated')) {
              console.debug('[NetworkInterceptor] Failed to send event:', error);
            }
          });
        }
      }
    } catch (error) {
      // Ignore errors in sending (extension might be reloading)
    }
  }

  /**
   * Intercept fetch() calls
   */
  const originalFetch = window.fetch;
  window.fetch = function (input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    const requestId = generateRequestId();
    const startTime = Date.now();
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const method = init?.method || 'GET';

    // Capture request
    const requestData: NetworkRequestData = {
      requestId,
      url,
      method,
      timestamp: startTime,
      type: 'fetch',
      headers: init?.headers ? Object.fromEntries(new Headers(init.headers).entries()) : undefined,
    };

    activeRequests.set(requestId, startTime);
    sendNetworkEvent({
      eventType: 'network-request',
      ...requestData,
    });

    // Call original fetch
    return originalFetch.apply(this, [input, init] as any).then(
      (response) => {
        // Capture response
        const endTime = Date.now();
        const duration = endTime - startTime;

        const responseData: NetworkResponseData = {
          requestId,
          url,
          method,
          status: response.status,
          statusText: response.statusText,
          timestamp: endTime,
          duration,
          type: 'fetch',
          headers: Object.fromEntries(response.headers.entries()),
        };

        activeRequests.delete(requestId);
        sendNetworkEvent({
          eventType: 'network-response',
          ...responseData,
        });

        return response;
      },
      (error) => {
        // Capture error
        const endTime = Date.now();
        const duration = endTime - startTime;

        activeRequests.delete(requestId);
        const errorEvent: NetworkErrorData = {
          requestId,
          url,
          method,
          timestamp: endTime,
          duration,
          type: 'fetch',
          error: error instanceof Error ? error.message : String(error),
        };
        sendNetworkEvent({
          eventType: 'network-error',
          ...errorEvent,
        });

        throw error;
      }
    );
  };

  /**
   * Intercept XMLHttpRequest
   */
  const OriginalXHR = window.XMLHttpRequest;

  window.XMLHttpRequest = function () {
    const xhr = new OriginalXHR();
    let requestId: string;
    let startTime: number;
    let requestUrl: string;
    let requestMethod: string;

    // Intercept open()
    const originalOpen = xhr.open;
    xhr.open = function (method: string, url: string | URL, async = true, username?: string | null, password?: string | null) {
      requestId = generateRequestId();
      startTime = Date.now();
      requestUrl = typeof url === 'string' ? url : url.href;
      requestMethod = method;

      return originalOpen.apply(this, [method, url, async, username, password] as any);
    };

    // Intercept send()
    const originalSend = xhr.send;
    xhr.send = function (body?: Document | XMLHttpRequestBodyInit | null) {
      // Capture request
      const requestData: NetworkRequestData = {
        requestId,
        url: requestUrl,
        method: requestMethod,
        timestamp: startTime,
        type: 'xhr',
        body: body ? String(body) : undefined,
      };

      activeRequests.set(requestId, startTime);
      sendNetworkEvent({
        eventType: 'network-request',
        ...requestData,
      });

      // Capture response
      const handleResponse = () => {
        if (xhr.readyState === 4) {
          const endTime = Date.now();
          const duration = endTime - startTime;

          if (xhr.status > 0) {
            // Success or HTTP error
            const responseData: NetworkResponseData = {
              requestId,
              url: requestUrl,
              method: requestMethod,
              status: xhr.status,
              statusText: xhr.statusText,
              timestamp: endTime,
              duration,
              type: 'xhr',
              responseSize: xhr.responseText?.length || 0,
            };

            activeRequests.delete(requestId);
            sendNetworkEvent({
              eventType: 'network-response',
              ...responseData,
            });
          } else {
            // Network error
            activeRequests.delete(requestId);
            const errorEvent: NetworkErrorData = {
              requestId,
              url: requestUrl,
              method: requestMethod,
              timestamp: endTime,
              duration,
              type: 'xhr',
              error: 'Network request failed',
            };
            sendNetworkEvent({
              eventType: 'network-error',
              ...errorEvent,
            });
          }
        }
      };

      xhr.addEventListener('readystatechange', handleResponse);
      xhr.addEventListener('error', () => {
        const endTime = Date.now();
        const duration = endTime - startTime;

      activeRequests.delete(requestId);
      const errorEvent: NetworkErrorData = {
        requestId,
        url: requestUrl,
        method: requestMethod,
        timestamp: endTime,
        duration,
        type: 'xhr',
        error: 'XHR error event',
      };
      sendNetworkEvent({
        eventType: 'network-error',
        ...errorEvent,
      });
      });

      return originalSend.apply(this, [body] as any);
    };

    return xhr;
  } as any;

  // Copy static properties from original XHR
  Object.setPrototypeOf(window.XMLHttpRequest, OriginalXHR);
  Object.setPrototypeOf(window.XMLHttpRequest.prototype, OriginalXHR.prototype);

  console.debug('[NetworkInterceptor] Network interception initialized');
}
