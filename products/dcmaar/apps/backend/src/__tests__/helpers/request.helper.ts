/**
 * Request Helper for Fastify Testing
 * 
 * Provides a supertest-like API for Fastify app.inject()
 */

import { FastifyInstance } from 'fastify';

export interface RequestHelper {
  get(url: string): RequestBuilder;
  post(url: string): RequestBuilder;
  put(url: string): RequestBuilder;
  patch(url: string): RequestBuilder;
  delete(url: string): RequestBuilder;
}

interface RequestBuilder {
  send(body: any): RequestBuilder;
  set(header: string, value: string): RequestBuilder;
  set(headers: Record<string, string>): RequestBuilder;
  query(params: Record<string, any>): RequestBuilder;
  expect(status: number): Promise<ResponseHelper>;
  expect(status: number, callback: (res: ResponseHelper) => void): Promise<void>;
}

interface ResponseHelper {
  status: number;
  statusCode: number;
  body: any;
  headers: Record<string, any>;
  payload: string;
  text: string;
  get(header: string): string | undefined;
}

class FastifyRequestBuilder implements RequestBuilder {
  private method: string;
  private url: string;
  private app: FastifyInstance;
  private payload?: any;
  private requestHeaders: Record<string, string> = {};
  private queryParams: Record<string, any> = {};

  constructor(method: string, url: string, app: FastifyInstance) {
    this.method = method;
    this.url = url;
    this.app = app;
  }

  send(body: any): RequestBuilder {
    this.payload = body;
    return this;
  }

  query(params: Record<string, any>): RequestBuilder {
    this.queryParams = { ...this.queryParams, ...params };
    return this;
  }

  set(headerOrHeaders: string | Record<string, string>, value?: string): RequestBuilder {
    if (typeof headerOrHeaders === 'string' && value !== undefined) {
      this.requestHeaders[headerOrHeaders.toLowerCase()] = value;
    } else if (typeof headerOrHeaders === 'object') {
      Object.entries(headerOrHeaders).forEach(([key, val]) => {
        this.requestHeaders[key.toLowerCase()] = val;
      });
    }
    return this;
  }

  async expect(status: number, callback?: (res: ResponseHelper) => void): Promise<any> {
    // Build URL with query params if any
    let finalUrl = this.url;
    if (Object.keys(this.queryParams).length > 0) {
      const queryString = new URLSearchParams(
        Object.entries(this.queryParams).reduce((acc, [key, value]) => {
          acc[key] = String(value);
          return acc;
        }, {} as Record<string, string>)
      ).toString();
      finalUrl = `${this.url}?${queryString}`;
    }

    const response = await this.app.inject({
      method: this.method as any,
      url: finalUrl,
      payload: this.payload,
      headers: this.requestHeaders,
    });

    // Try to parse JSON, but fallback to payload if it's not JSON
    let parsedBody: any;
    try {
      parsedBody = response.json();
    } catch {
      parsedBody = response.payload;
    }

    const helper: ResponseHelper = {
      status: response.statusCode,
      statusCode: response.statusCode,
      body: parsedBody,
      headers: response.headers,
      payload: response.payload,
      text: response.payload,
      get(header: string) {
        const value = response.headers[header.toLowerCase()];
        return typeof value === 'string' ? value : value?.toString();
      },
    };

    if (response.statusCode !== status) {
      throw new Error(
        `Expected status ${status} but got ${response.statusCode}. Body: ${response.body}`
      );
    }

    if (callback) {
      callback(helper);
      return;
    }

    return helper;
  }
}

export function request(app: FastifyInstance): RequestHelper {
  return {
    get(url: string) {
      return new FastifyRequestBuilder('GET', url, app);
    },
    post(url: string) {
      return new FastifyRequestBuilder('POST', url, app);
    },
    put(url: string) {
      return new FastifyRequestBuilder('PUT', url, app);
    },
    patch(url: string) {
      return new FastifyRequestBuilder('PATCH', url, app);
    },
    delete(url: string) {
      return new FastifyRequestBuilder('DELETE', url, app);
    },
  };
}
