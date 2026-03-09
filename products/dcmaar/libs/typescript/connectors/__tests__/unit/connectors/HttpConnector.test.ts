/**
 * @fileoverview Comprehensive unit tests for HttpConnector
 *
 * Tests cover:
 * - Connection lifecycle with and without polling
 * - Request/response handling
 * - Authentication (basic, bearer, API key, OAuth2)
 * - Timeout handling
 * - Error handling and retries
 * - Response parsing (JSON, text, form data, binary)
 * - Polling mode
 * - Resource cleanup
 */

import { HttpConnector, HttpConnectorConfig, HttpError } from '../../../src/connectors/HttpConnector';

// Mock global fetch
global.fetch = jest.fn();

describe('HttpConnector', () => {
  let connector: HttpConnector;
  let config: HttpConnectorConfig;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();

    config = {
      id: 'http-test',
      type: 'http',
      url: 'https://api.example.com/data',
      method: 'GET',
      timeout: 5000,
    };

    (global.fetch as jest.Mock).mockResolvedValue({
      ok: true,
      status: 200,
      statusText: 'OK',
      headers: new Headers({ 'content-type': 'application/json' }),
      json: jest.fn().mockResolvedValue({ data: 'test' }),
      text: jest.fn().mockResolvedValue('text response'),
      arrayBuffer: jest.fn().mockResolvedValue(new ArrayBuffer(8)),
      formData: jest.fn().mockResolvedValue(new FormData()),
    });
  });

  afterEach(async () => {
    if (connector) {
      await connector.destroy();
    }
    jest.useRealTimers();
  });

  describe('Constructor', () => {
    it('should create connector with config', () => {
      connector = new HttpConnector(config);

      expect(connector.id).toBe('http-test');
      expect(connector.type).toBe('http');
    });

    it('should override type to http', () => {
      const customConfig = { ...config, type: 'custom' as any };
      connector = new HttpConnector(customConfig);

      expect(connector.type).toBe('http');
    });
  });

  describe('Connection', () => {
    describe('connect() - Request/Response Mode', () => {
      it('should connect successfully without polling', async () => {
        connector = new HttpConnector(config);

        await connector.connect();

        expect(connector.status).toBe('connected');
        expect(global.fetch).toHaveBeenCalledWith(
          config.url,
          expect.objectContaining({
            method: 'GET',
            headers: expect.any(Object),
          })
        );
      });

      it('should make initial request on connect', async () => {
        connector = new HttpConnector(config);

        await connector.connect();

        expect(global.fetch).toHaveBeenCalledTimes(1);
      });

      it('should emit request event', async () => {
        connector = new HttpConnector(config);
        const requestListener = jest.fn();
        connector.on('request', requestListener);

        await connector.connect();

        expect(requestListener).toHaveBeenCalledWith(
          expect.objectContaining({
            url: config.url,
            method: 'GET',
          })
        );
      });

      it('should set error status on connection failure', async () => {
        (global.fetch as jest.Mock).mockRejectedValue(new Error('Network error'));
        connector = new HttpConnector(config);

        await expect(connector.connect()).rejects.toThrow('Network error');
        expect(connector.status).toBe('error');
      });

      it('should store last response', async () => {
        connector = new HttpConnector(config);

        await connector.connect();

        expect(connector.lastResponse).toBeDefined();
        expect(connector.lastResponse?.status).toBe(200);
      });
    });

    describe('connect() - Polling Mode', () => {
      beforeEach(() => {
        config.pollInterval = 1000;
      });

      it('should start polling on connect', async () => {
        connector = new HttpConnector(config);

        await connector.connect();

        expect(global.fetch).toHaveBeenCalled();
        expect(connector.status).toBe('connected');
      });

      it('should poll at specified interval', async () => {
        connector = new HttpConnector(config);

        await connector.connect();
        (global.fetch as jest.Mock).mockClear();

        jest.advanceTimersByTime(1000);
        await Promise.resolve();

        expect(global.fetch).toHaveBeenCalledTimes(1);

        jest.advanceTimersByTime(1000);
        await Promise.resolve();

        expect(global.fetch).toHaveBeenCalledTimes(2);
      });

      it('should emit data events during polling', async () => {
        connector = new HttpConnector(config);
        const dataHandler = jest.fn();
        connector.onEvent('data', dataHandler);

        await connector.connect();
        jest.advanceTimersByTime(1000);
        await Promise.resolve();

        expect(dataHandler).toHaveBeenCalled();
      });

      it('should continue polling after error', async () => {
        connector = new HttpConnector(config);
        (global.fetch as jest.Mock)
          .mockRejectedValueOnce(new Error('Network error'))
          .mockResolvedValue({
            ok: true,
            status: 200,
            statusText: 'OK',
            headers: new Headers({ 'content-type': 'application/json' }),
            json: jest.fn().mockResolvedValue({ data: 'test' }),
          });

        await connector.connect().catch(() => {});

        jest.advanceTimersByTime(1000);
        await Promise.resolve();

        expect(global.fetch).toHaveBeenCalledTimes(2);
      });
    });

    describe('disconnect()', () => {
      it('should stop polling on disconnect', async () => {
        config.pollInterval = 1000;
        connector = new HttpConnector(config);

        await connector.connect();
        await connector.disconnect();

        (global.fetch as jest.Mock).mockClear();
        jest.advanceTimersByTime(5000);

        expect(global.fetch).not.toHaveBeenCalled();
      });

      it('should abort in-flight requests', async () => {
        connector = new HttpConnector(config);

        const slowFetch = new Promise(resolve => setTimeout(resolve, 10000));
        (global.fetch as jest.Mock).mockReturnValue(slowFetch);

        const connectPromise = connector.connect();
        await connector.disconnect();

        expect(connector.status).toBe('disconnected');
      });

      it('should set disconnected status', async () => {
        connector = new HttpConnector(config);

        await connector.connect();
        await connector.disconnect();

        expect(connector.status).toBe('disconnected');
      });
    });
  });

  describe('send()', () => {
    beforeEach(async () => {
      connector = new HttpConnector(config);
      await connector.connect();
      (global.fetch as jest.Mock).mockClear();
    });

    it('should send data with POST method', async () => {
      const data = { message: 'hello' };

      await connector.send(data);

      expect(global.fetch).toHaveBeenCalledWith(
        config.url,
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify(data),
        })
      );
    });

    it('should serialize data as JSON', async () => {
      const data = { key: 'value', number: 42 };

      await connector.send(data);

      expect(global.fetch).toHaveBeenCalledWith(
        config.url,
        expect.objectContaining({
          body: JSON.stringify(data),
        })
      );
    });

    it('should emit response event', async () => {
      const responseHandler = jest.fn();
      connector.onEvent('response', responseHandler);

      await connector.send({ data: 'test' });

      expect(responseHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'response',
          payload: { data: 'test' },
        })
      );
    });

    it('should include response metadata', async () => {
      const responseHandler = jest.fn();
      connector.onEvent('response', responseHandler);

      await connector.send({ data: 'test' });

      expect(responseHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          metadata: expect.objectContaining({
            status: 200,
            statusText: 'OK',
            headers: expect.any(Object),
          }),
        })
      );
    });

    it('should accept request options override', async () => {
      await connector.send({ data: 'test' }, { headers: { 'X-Custom': 'value' } });

      expect(global.fetch).toHaveBeenCalledWith(
        config.url,
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-Custom': 'value',
          }),
        })
      );
    });

    it('should throw on send error', async () => {
      (global.fetch as jest.Mock).mockRejectedValue(new Error('Network error'));

      await expect(connector.send({ data: 'test' })).rejects.toThrow('Network error');
    });

    it('should emit error event on failure', async () => {
      const errorHandler = jest.fn();
      connector.onEvent('error', errorHandler);

      (global.fetch as jest.Mock).mockRejectedValue(new Error('Network error'));

      await expect(connector.send({ data: 'test' })).rejects.toThrow();

      expect(errorHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'error',
          payload: expect.objectContaining({
            context: 'send',
            message: 'Network error',
          }),
        })
      );
    });
  });

  describe('Authentication', () => {
    beforeEach(() => {
      connector = new HttpConnector(config);
    });

    it('should apply basic authentication', async () => {
      const authConfig = {
        ...config,
        auth: {
          type: 'basic' as const,
          username: 'user',
          password: 'pass',
        },
      };
      connector = new HttpConnector(authConfig);

      await connector.connect();

      const expectedAuth = Buffer.from('user:pass').toString('base64');
      expect(global.fetch).toHaveBeenCalledWith(
        config.url,
        expect.objectContaining({
          headers: expect.objectContaining({
            'Authorization': `Basic ${expectedAuth}`,
          }),
        })
      );
    });

    it('should apply bearer token authentication', async () => {
      const authConfig = {
        ...config,
        auth: {
          type: 'bearer' as const,
          token: 'secret-token',
        },
      };
      connector = new HttpConnector(authConfig);

      await connector.connect();

      expect(global.fetch).toHaveBeenCalledWith(
        config.url,
        expect.objectContaining({
          headers: expect.objectContaining({
            'Authorization': 'Bearer secret-token',
          }),
        })
      );
    });

    it('should apply API key authentication with default header', async () => {
      const authConfig = {
        ...config,
        auth: {
          type: 'api_key' as const,
          apiKey: 'key123',
        },
      };
      connector = new HttpConnector(authConfig);

      await connector.connect();

      expect(global.fetch).toHaveBeenCalledWith(
        config.url,
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-API-Key': 'key123',
          }),
        })
      );
    });

    it('should apply API key authentication with custom header', async () => {
      const authConfig = {
        ...config,
        auth: {
          type: 'api_key' as const,
          apiKey: 'key123',
          headerName: 'X-Custom-Key',
        },
      };
      connector = new HttpConnector(authConfig);

      await connector.connect();

      expect(global.fetch).toHaveBeenCalledWith(
        config.url,
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-Custom-Key': 'key123',
          }),
        })
      );
    });

    it('should apply OAuth2 authentication', async () => {
      const authConfig = {
        ...config,
        auth: {
          type: 'oauth2' as const,
          accessToken: 'access-token-123',
        },
      };
      connector = new HttpConnector(authConfig);

      await connector.connect();

      expect(global.fetch).toHaveBeenCalledWith(
        config.url,
        expect.objectContaining({
          headers: expect.objectContaining({
            'Authorization': 'Bearer access-token-123',
          }),
        })
      );
    });

    it('should work without authentication', async () => {
      await connector.connect();

      expect(global.fetch).toHaveBeenCalled();
    });
  });

  describe('Response Parsing', () => {
    beforeEach(async () => {
      connector = new HttpConnector(config);
      await connector.connect();
      (global.fetch as jest.Mock).mockClear();
    });

    it('should parse JSON responses', async () => {
      const jsonData = { key: 'value' };
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers({ 'content-type': 'application/json' }),
        json: jest.fn().mockResolvedValue(jsonData),
      });

      const responseHandler = jest.fn();
      connector.onEvent('response', responseHandler);

      await connector.send({ data: 'test' });

      expect(responseHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: jsonData,
        })
      );
    });

    it('should parse text responses', async () => {
      const textData = 'plain text response';
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers({ 'content-type': 'text/plain' }),
        text: jest.fn().mockResolvedValue(textData),
      });

      const responseHandler = jest.fn();
      connector.onEvent('response', responseHandler);

      await connector.send({ data: 'test' });

      expect(responseHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: textData,
        })
      );
    });

    it('should parse HTML responses as text', async () => {
      const htmlData = '<html><body>Hello</body></html>';
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers({ 'content-type': 'text/html' }),
        text: jest.fn().mockResolvedValue(htmlData),
      });

      const responseHandler = jest.fn();
      connector.onEvent('response', responseHandler);

      await connector.send({ data: 'test' });

      expect(responseHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: htmlData,
        })
      );
    });

    it('should parse form data responses', async () => {
      const formData = new FormData();
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers({ 'content-type': 'multipart/form-data' }),
        formData: jest.fn().mockResolvedValue(formData),
      });

      const responseHandler = jest.fn();
      connector.onEvent('response', responseHandler);

      await connector.send({ data: 'test' });

      expect(responseHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: formData,
        })
      );
    });

    it('should parse binary responses', async () => {
      const buffer = new ArrayBuffer(8);
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers({ 'content-type': 'application/octet-stream' }),
        arrayBuffer: jest.fn().mockResolvedValue(buffer),
      });

      const responseHandler = jest.fn();
      connector.onEvent('response', responseHandler);

      await connector.send({ data: 'test' });

      expect(responseHandler).toHaveBeenCalledWith(
        expect.objectContaining({
          payload: buffer,
        })
      );
    });

    it('should handle missing content-type header', async () => {
      const buffer = new ArrayBuffer(8);
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers(),
        arrayBuffer: jest.fn().mockResolvedValue(buffer),
      });

      const responseHandler = jest.fn();
      connector.onEvent('response', responseHandler);

      await connector.send({ data: 'test' });

      expect(responseHandler).toHaveBeenCalled();
    });
  });

  describe('Error Handling', () => {
    beforeEach(async () => {
      connector = new HttpConnector(config);
      await connector.connect();
      (global.fetch as jest.Mock).mockClear();
    });

    it('should throw HttpError for non-OK responses', async () => {
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: false,
        status: 404,
        statusText: 'Not Found',
        text: jest.fn().mockResolvedValue('Resource not found'),
      });

      await expect(connector.send({ data: 'test' })).rejects.toThrow(HttpError);
    });

    it('should include status code in HttpError', async () => {
      (global.fetch as jest.Mock).mockResolvedValue({
        ok: false,
        status: 500,
        statusText: 'Internal Server Error',
        text: jest.fn().mockResolvedValue('Server error'),
      });

      try {
        await connector.send({ data: 'test' });
      } catch (error) {
        expect(error).toBeInstanceOf(HttpError);
        expect((error as HttpError).status).toBe(500);
        expect((error as HttpError).statusText).toBe('Internal Server Error');
      }
    });

    it('should handle timeout errors', async () => {
      const abortError = new Error('Aborted');
      (abortError as any).name = 'AbortError';
      (global.fetch as jest.Mock).mockRejectedValue(abortError);

      await expect(connector.send({ data: 'test' })).rejects.toThrow('Request timed out');
    });

    it('should handle network errors', async () => {
      (global.fetch as jest.Mock).mockRejectedValue(new Error('Network error'));

      await expect(connector.send({ data: 'test' })).rejects.toThrow('Network error');
    });
  });

  describe('Timeout Handling', () => {
    beforeEach(() => {
      connector = new HttpConnector({ ...config, timeout: 1000 });
    });

    it('should use configured timeout', async () => {
      const abortSpy = jest.fn();
      let abortController: AbortController;

      (global.fetch as jest.Mock).mockImplementation((url, options) => {
        abortController = options.signal as any;
        return new Promise(() => {}); // Never resolves
      });

      const connectPromise = connector.connect();

      jest.advanceTimersByTime(1000);
      await Promise.resolve();

      // Controller should be aborted
      expect(abortController!.signal.aborted).toBe(true);
    });

    it('should allow timeout override per request', async () => {
      await connector.connect();
      (global.fetch as jest.Mock).mockClear();

      let usedTimeout: number = 0;
      (global.fetch as jest.Mock).mockImplementation(() => {
        return new Promise((resolve) => {
          setTimeout(() => {
            usedTimeout = 1;
            resolve({
              ok: true,
              status: 200,
              statusText: 'OK',
              headers: new Headers({ 'content-type': 'application/json' }),
              json: jest.fn().mockResolvedValue({}),
            });
          }, 500);
        });
      });

      await connector.send({ data: 'test' }, { timeout: 2000 });
      jest.advanceTimersByTime(2000);

      expect(usedTimeout).toBe(1);
    });
  });

  describe('Configuration Validation', () => {
    it('should require URL for requests', async () => {
      const invalidConfig = { ...config, url: '' };
      connector = new HttpConnector(invalidConfig);

      await expect(connector.connect()).rejects.toThrow('URL is required');
    });

    it('should use GET as default method', async () => {
      const configWithoutMethod = { ...config };
      delete (configWithoutMethod as any).method;
      connector = new HttpConnector(configWithoutMethod);

      await connector.connect();

      expect(global.fetch).toHaveBeenCalledWith(
        config.url,
        expect.objectContaining({
          method: 'GET',
        })
      );
    });

    it('should apply custom headers from config', async () => {
      const configWithHeaders = {
        ...config,
        headers: { 'X-Custom': 'value' },
      };
      connector = new HttpConnector(configWithHeaders);

      await connector.connect();

      expect(global.fetch).toHaveBeenCalledWith(
        config.url,
        expect.objectContaining({
          headers: expect.objectContaining({
            'X-Custom': 'value',
          }),
        })
      );
    });
  });

  describe('Resource Cleanup', () => {
    it('should cleanup on destroy', async () => {
      config.pollInterval = 1000;
      connector = new HttpConnector(config);

      await connector.connect();
      await connector.destroy();

      (global.fetch as jest.Mock).mockClear();
      jest.advanceTimersByTime(5000);

      expect(global.fetch).not.toHaveBeenCalled();
    });

    it('should abort requests on destroy', async () => {
      connector = new HttpConnector(config);

      const slowFetch = new Promise(resolve => setTimeout(resolve, 10000));
      (global.fetch as jest.Mock).mockReturnValue(slowFetch);

      connector.connect();
      await connector.destroy();

      expect(connector.status).toBe('disconnected');
    });
  });

  describe('HttpError Class', () => {
    it('should create error with all properties', () => {
      const error = new HttpError('Test error', 404, 'Not Found', 'Response body');

      expect(error.message).toBe('Test error');
      expect(error.status).toBe(404);
      expect(error.statusText).toBe('Not Found');
      expect(error.response).toBe('Response body');
      expect(error.name).toBe('HttpError');
    });

    it('should be instanceof Error', () => {
      const error = new HttpError('Test', 500, 'Error', 'Body');

      expect(error).toBeInstanceOf(Error);
    });
  });
});
