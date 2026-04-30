import { test, expect, Page } from '@playwright/test';
import axios, { AxiosInstance } from 'axios';

/**
 * Base HTTP API test client for E2E testing.
 * Handles correlation ID propagation, tenant context, authentication.
 * 
 * Pattern: Fixture-based testing for regulated API workflows
 */
export class ApiTestClient {
  private client: AxiosInstance;
  private baseUrl: string;
  private correlationId: string;
  private tenantId: string;
  private authToken?: string;

  constructor(baseUrl: string, tenantId: string = 'test-tenant-001') {
    this.baseUrl = baseUrl;
    this.tenantId = tenantId;
    this.correlationId = this.generateCorrelationId();
    
    this.client = axios.create({
      baseURL: baseUrl,
      headers: this.defaultHeaders(),
      validateStatus: () => true, // Don't throw on any status code
    });
  }

  /**
   * Set authentication token (JWT, Bearer, etc).
   */
  setAuthToken(token: string): this {
    this.authToken = token;
    this.client.defaults.headers.common['Authorization'] = `Bearer ${token}`;
    return this;
  }

  /**
   * Generate correlation ID in format: UUID@tenant@domain
   */
  private generateCorrelationId(): string {
    const uuid = this.generateUUID();
    const domain = this.baseUrl.includes('phr') ? 'phr-api' : 'finance-api';
    return `${uuid}@${this.tenantId}@${domain}`;
  }

  /**
   * Default headers for all requests.
   */
  private defaultHeaders() {
    return {
      'X-Correlation-ID': this.correlationId,
      'X-Tenant-ID': this.tenantId,
      'Content-Type': 'application/json',
      'User-Agent': 'ghatana-e2e-test/1.0',
    };
  }

  /**
   * GET request with error handling and assertions.
   */
  async get<T = any>(path: string): Promise<ApiResponse<T>> {
    const response = await this.client.get<T>(path);
    return new ApiResponse(response.status, response.data, response.headers, this.correlationId);
  }

  /**
   * POST request with payload.
   */
  async post<T = any>(path: string, data: any): Promise<ApiResponse<T>> {
    const response = await this.client.post<T>(path, data);
    return new ApiResponse(response.status, response.data, response.headers, this.correlationId);
  }

  /**
   * PUT request.
   */
  async put<T = any>(path: string, data: any): Promise<ApiResponse<T>> {
    const response = await this.client.put<T>(path, data);
    return new ApiResponse(response.status, response.data, response.headers, this.correlationId);
  }

  /**
   * DELETE request.
   */
  async delete<T = any>(path: string): Promise<ApiResponse<T>> {
    const response = await this.client.delete<T>(path);
    return new ApiResponse(response.status, response.data, response.headers, this.correlationId);
  }

  /**
   * Reset correlation ID (for simulating new requests).
   */
  resetCorrelationId(): void {
    this.correlationId = this.generateCorrelationId();
    this.client.defaults.headers.common['X-Correlation-ID'] = this.correlationId;
  }

  getCorrelationId(): string {
    return this.correlationId;
  }

  getTenantId(): string {
    return this.tenantId;
  }

  private generateUUID(): string {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
      const r = (Math.random() * 16) | 0;
      const v = c === 'x' ? r : (r & 0x3) | 0x8;
      return v.toString(16);
    });
  }
}

/**
 * API response wrapper with assertions.
 */
export class ApiResponse<T = any> {
  status: number;
  data: T;
  headers: Record<string, any>;
  correlationId: string;

  constructor(status: number, data: T, headers: Record<string, any>, correlationId: string) {
    this.status = status;
    this.data = data;
    this.headers = headers;
    this.correlationId = correlationId;
  }

  /**
   * Assert response is successful (2xx).
   */
  assertOk(): this {
    expect(this.status).toBeLessThan(300);
    expect(this.status).toBeGreaterThanOrEqual(200);
    return this;
  }

  /**
   * Assert specific status code.
   */
  assertStatus(expected: number): this {
    expect(this.status).toBe(expected);
    return this;
  }

  /**
   * Assert response is NOT 2xx.
   */
  assertError(): this {
    expect(this.status).toBeGreaterThanOrEqual(400);
    return this;
  }

  /**
   * Assert correlation ID is present in response headers.
   */
  assertCorrelationIdEchoed(): this {
    expect(this.headers['x-correlation-id']).toBe(this.correlationId);
    return this;
  }

  /**
   * Assert data has expected shape.
   */
  assertShape(keys: string[]): this {
    for (const key of keys) {
      expect(this.data).toHaveProperty(key);
    }
    return this;
  }

  /**
   * Assert tenant ID is enforced.
   */
  assertTenantIsolation(expectedTenantId: string): this {
    if (this.data && typeof this.data === 'object' && 'tenantId' in this.data) {
      expect((this.data as any).tenantId).toBe(expectedTenantId);
    }
    return this;
  }
}

export default ApiTestClient;
