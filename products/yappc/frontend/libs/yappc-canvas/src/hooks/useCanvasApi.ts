import { useState, useCallback, useRef, useEffect } from 'react';

import type { Node, Edge } from '@xyflow/react';

// API configuration interface
/**
 *
 */
export interface ApiConfig {
  baseUrl: string;
  timeout: number;
  retryAttempts: number;
  retryDelay: number;
  authentication: {
    type: 'bearer' | 'apikey' | 'basic' | 'oauth';
    token?: string;
    apiKey?: string;
    clientId?: string;
    clientSecret?: string;
  };
  endpoints: {
    saveCanvas: string;
    loadCanvas: string;
    shareCanvas: string;
    exportCanvas: string;
    templates: string;
    collaboration: string;
    validation: string;
  };
  headers?: Record<string, string>;
}

// API request/response types
/**
 *
 */
export interface ApiRequest<T = unknown> {
  method: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  endpoint: string;
  data?: T;
  params?: Record<string, string>;
  headers?: Record<string, string>;
  timeout?: number;
  retries?: number;
}

/**
 *
 */
export interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
    details?: unknown;
  };
  metadata?: {
    timestamp: string;
    requestId: string;
    version: string;
  };
}

// Canvas API operations
/**
 *
 */
export interface CanvasApiOperations {
  // Canvas management
  saveCanvas: (
    canvasData: CanvasData
  ) => Promise<{ id: string; version: number } | null>;
  loadCanvas: (canvasId: string) => Promise<CanvasData | null>;
  deleteCanvas: (canvasId: string) => Promise<void | null>;
  duplicateCanvas: (
    canvasId: string,
    name: string
  ) => Promise<CanvasData | null>;

  // Sharing and collaboration
  shareCanvas: (
    canvasId: string,
    permissions: SharePermissions
  ) => Promise<{ shareUrl: string } | null>;
  getSharedCanvas: (shareToken: string) => Promise<CanvasData | null>;
  updatePermissions: (
    canvasId: string,
    permissions: SharePermissions
  ) => Promise<void | null>;

  // Export functionality
  exportCanvas: (
    canvasId: string,
    format: ExportFormat
  ) => Promise<ExportResult | null>;
  exportToImage: (
    canvasId: string,
    options: ImageExportOptions
  ) => Promise<{ url: string } | null>;
  exportToPdf: (
    canvasId: string,
    options: PdfExportOptions
  ) => Promise<{ url: string } | null>;

  // Templates
  getTemplates: (category?: string) => Promise<Template[] | null>;
  createTemplate: (
    canvasData: CanvasData,
    metadata: TemplateMetadata
  ) => Promise<Template | null>;
  useTemplate: (templateId: string) => Promise<CanvasData | null>;

  // Validation
  validateCanvas: (canvasData: CanvasData) => Promise<ValidationResult | null>;
  validateNode: (node: Node) => Promise<NodeValidationResult | null>;

  // Analytics
  getAnalytics: (
    canvasId: string,
    timeRange?: TimeRange
  ) => Promise<AnalyticsData | null>;
  trackUsage: (event: UsageEvent) => Promise<void | null>;
}

// Data interfaces
/**
 *
 */
export interface CanvasData {
  id: string;
  name: string;
  description?: string;
  nodes: Node[];
  edges: Edge[];
  metadata: {
    created: string;
    modified: string;
    author: string;
    version: number;
    tags: string[];
  };
  settings: {
    theme: string;
    layout: string;
    permissions: SharePermissions;
  };
}

/**
 *
 */
export interface SharePermissions {
  public: boolean;
  allowEdit: boolean;
  allowComment: boolean;
  allowExport: boolean;
  users: Array<{
    email: string;
    role: 'viewer' | 'editor' | 'admin';
  }>;
  expiration?: string;
}

/**
 *
 */
export interface ExportFormat {
  type: 'json' | 'xml' | 'csv' | 'yaml' | 'image' | 'pdf';
  options?: {
    includeMetadata?: boolean;
    compression?: boolean;
    imageFormat?: 'png' | 'jpg' | 'svg';
    quality?: number;
  };
}

/**
 *
 */
export interface ExportResult {
  url: string;
  format: string;
  size: number;
  expiration: string;
}

/**
 *
 */
export interface ImageExportOptions {
  format: 'png' | 'jpg' | 'svg';
  quality: number;
  width?: number;
  height?: number;
  background?: string;
  includeLabels?: boolean;
}

/**
 *
 */
export interface PdfExportOptions {
  pageSize: 'A4' | 'A3' | 'Letter' | 'Legal';
  orientation: 'portrait' | 'landscape';
  includeMetadata?: boolean;
  watermark?: string;
}

/**
 *
 */
export interface Template {
  id: string;
  name: string;
  description: string;
  category: string;
  preview: string;
  data: CanvasData;
  metadata: TemplateMetadata;
  usage: {
    count: number;
    rating: number;
  };
}

/**
 *
 */
export interface TemplateMetadata {
  author: string;
  tags: string[];
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  estimatedTime: number;
  requirements: string[];
}

/**
 *
 */
export interface ValidationResult {
  valid: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
  suggestions: ValidationSuggestion[];
}

/**
 *
 */
export interface ValidationError {
  id: string;
  type: 'structure' | 'data' | 'logic' | 'performance';
  message: string;
  nodeId?: string;
  edgeId?: string;
  severity: 'high' | 'medium' | 'low';
}

/**
 *
 */
export interface ValidationWarning {
  id: string;
  message: string;
  nodeId?: string;
  recommendation: string;
}

/**
 *
 */
export interface ValidationSuggestion {
  id: string;
  message: string;
  action: string;
  benefit: string;
}

/**
 *
 */
export interface NodeValidationResult {
  valid: boolean;
  errors: ValidationError[];
  suggestions: ValidationSuggestion[];
}

/**
 *
 */
export interface TimeRange {
  start: string;
  end: string;
}

/**
 *
 */
export interface AnalyticsData {
  usage: {
    views: number;
    edits: number;
    exports: number;
    shares: number;
  };
  performance: {
    loadTime: number;
    renderTime: number;
    interactions: number;
  };
  users: {
    unique: number;
    active: number;
    collaborative: number;
  };
  timeline: Array<{
    timestamp: string;
    event: string;
    value: number;
  }>;
}

/**
 *
 */
export interface UsageEvent {
  type: string;
  canvasId: string;
  userId: string;
  timestamp: string;
  data?: unknown;
}

// Default API configuration
const defaultApiConfig: ApiConfig = {
  baseUrl: import.meta.env.DEV
    ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
    : '/api',
  timeout: 30000,
  retryAttempts: 3,
  retryDelay: 1000,
  authentication: {
    type: 'bearer',
  },
  endpoints: {
    saveCanvas: '/canvas',
    loadCanvas: '/canvas/:id',
    shareCanvas: '/canvas/:id/share',
    exportCanvas: '/canvas/:id/export',
    templates: '/templates',
    collaboration: '/collaboration',
    validation: '/validation',
  },
};

// API client class
/**
 *
 */
class CanvasApiClient {
  private config: ApiConfig;
  private cache: Map<string, { data: unknown; timestamp: number; ttl: number }>;
  private requestQueue: Map<string, Promise<unknown>>;

  /**
   *
   */
  constructor(config: ApiConfig = defaultApiConfig) {
    this.config = { ...defaultApiConfig, ...config };
    this.cache = new Map();
    this.requestQueue = new Map();
  }

  // Generic request method
  /**
   *
   */
  async request<T>(request: ApiRequest): Promise<ApiResponse<T>> {
    const { method, endpoint, data, params, headers, timeout, retries } =
      request;

    // Build URL
    let url = `${this.config.baseUrl}${endpoint}`;
    if (params) {
      const searchParams = new URLSearchParams(params);
      url += `?${searchParams.toString()}`;
    }

    // Build headers
    const requestHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      ...this.config.headers,
      ...headers,
    };

    // Add authentication
    if (
      this.config.authentication.type === 'bearer' &&
      this.config.authentication.token
    ) {
      requestHeaders.Authorization = `Bearer ${this.config.authentication.token}`;
    } else if (
      this.config.authentication.type === 'apikey' &&
      this.config.authentication.apiKey
    ) {
      requestHeaders['X-API-Key'] = this.config.authentication.apiKey;
    }

    const requestId = `${method}-${url}-${JSON.stringify(data)}`;

    // Check if request is already in progress
    if (this.requestQueue.has(requestId)) {
      return this.requestQueue.get(requestId)!;
    }

    const requestPromise = this.executeRequest<T>({
      url,
      method,
      data,
      headers: requestHeaders,
      timeout: timeout || this.config.timeout,
      retries: retries || this.config.retryAttempts,
    });

    this.requestQueue.set(requestId, requestPromise);

    try {
      const result = await requestPromise;
      return result;
    } finally {
      this.requestQueue.delete(requestId);
    }
  }

  /**
   *
   */
  private async executeRequest<T>(options: {
    url: string;
    method: string;
    data?: unknown;
    headers: Record<string, string>;
    timeout: number;
    retries: number;
  }): Promise<ApiResponse<T>> {
    const { url, method, data, headers, timeout, retries } = options;

    for (let attempt = 0; attempt <= retries; attempt++) {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), timeout);

        const response = await fetch(url, {
          method,
          headers,
          body: data ? JSON.stringify(data) : undefined,
          signal: controller.signal,
        });

        clearTimeout(timeoutId);

        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        const result = await response.json();
        return {
          success: true,
          data: result.data,
          metadata: result.metadata,
        };
      } catch (error) {
        if (attempt === retries) {
          return {
            success: false,
            error: {
              code: 'REQUEST_FAILED',
              message: error instanceof Error ? error.message : 'Unknown error',
              details: { attempt, url, method },
            },
          };
        }

        // Wait before retry
        await new Promise((resolve) =>
          setTimeout(resolve, this.config.retryDelay * (attempt + 1))
        );
      }
    }

    return {
      success: false,
      error: {
        code: 'MAX_RETRIES_EXCEEDED',
        message: 'Maximum retry attempts exceeded',
      },
    };
  }

  // Cache management
  /**
   *
   */
  private getCached<T>(key: string): T | null {
    const cached = this.cache.get(key);
    if (cached && Date.now() - cached.timestamp < cached.ttl) {
      return cached.data;
    }
    this.cache.delete(key);
    return null;
  }

  /**
   *
   */
  private setCache<T>(key: string, data: T, ttl: number = 300000): void {
    this.cache.set(key, {
      data,
      timestamp: Date.now(),
      ttl,
    });
  }

  // API operations implementation
  /**
   *
   */
  async saveCanvas(
    canvasData: CanvasData
  ): Promise<ApiResponse<{ id: string; version: number }>> {
    return this.request({
      method: 'POST',
      endpoint: this.config.endpoints.saveCanvas,
      data: canvasData,
    });
  }

  /**
   *
   */
  async loadCanvas(canvasId: string): Promise<ApiResponse<CanvasData>> {
    const cacheKey = `canvas-${canvasId}`;
    const cached = this.getCached<CanvasData>(cacheKey);

    if (cached) {
      return { success: true, data: cached };
    }

    const response = await this.request<CanvasData>({
      method: 'GET',
      endpoint: this.config.endpoints.loadCanvas.replace(':id', canvasId),
    });

    if (response.success && response.data) {
      this.setCache(cacheKey, response.data);
    }

    return response;
  }

  /**
   *
   */
  async shareCanvas(
    canvasId: string,
    permissions: SharePermissions
  ): Promise<ApiResponse<{ shareUrl: string }>> {
    return this.request({
      method: 'POST',
      endpoint: this.config.endpoints.shareCanvas.replace(':id', canvasId),
      data: permissions,
    });
  }

  /**
   *
   */
  async exportCanvas(
    canvasId: string,
    format: ExportFormat
  ): Promise<ApiResponse<ExportResult>> {
    return this.request({
      method: 'POST',
      endpoint: this.config.endpoints.exportCanvas.replace(':id', canvasId),
      data: format,
    });
  }

  /**
   *
   */
  async getTemplates(category?: string): Promise<ApiResponse<Template[]>> {
    const cacheKey = `templates-${category || 'all'}`;
    const cached = this.getCached<Template[]>(cacheKey);

    if (cached) {
      return { success: true, data: cached };
    }

    const response = await this.request<Template[]>({
      method: 'GET',
      endpoint: this.config.endpoints.templates,
      params: category ? { category } : undefined,
    });

    if (response.success && response.data) {
      this.setCache(cacheKey, response.data, 600000); // 10 minutes
    }

    return response;
  }

  /**
   *
   */
  async validateCanvas(
    canvasData: CanvasData
  ): Promise<ApiResponse<ValidationResult>> {
    return this.request({
      method: 'POST',
      endpoint: this.config.endpoints.validation,
      data: canvasData,
    });
  }

  // Update configuration
  /**
   *
   */
  updateConfig(newConfig: Partial<ApiConfig>): void {
    this.config = { ...this.config, ...newConfig };
  }

  // Clear cache
  /**
   *
   */
  clearCache(): void {
    this.cache.clear();
  }
}

// Hook for using the API
export const useCanvasApi = (config?: Partial<ApiConfig>) => {
  const apiClient = useRef<CanvasApiClient>();
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Initialize API client
  useEffect(() => {
    const mergedConfig = config
      ? { ...defaultApiConfig, ...config }
      : defaultApiConfig;
    apiClient.current = new CanvasApiClient(mergedConfig);
  }, [config]);

  // Wrapper for API calls with loading and error handling
  const apiCall = useCallback(
    async <T>(operation: () => Promise<ApiResponse<T>>): Promise<T | null> => {
      setIsLoading(true);
      setError(null);

      try {
        const response = await operation();

        if (response.success) {
          return response.data || null;
        } else {
          setError(response.error?.message || 'Unknown error');
          return null;
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Unknown error');
        return null;
      } finally {
        setIsLoading(false);
      }
    },
    []
  );

  // API operations
  const operations: CanvasApiOperations = {
    saveCanvas: (canvasData) =>
      apiCall(() => apiClient.current!.saveCanvas(canvasData)),
    loadCanvas: (canvasId) =>
      apiCall(() => apiClient.current!.loadCanvas(canvasId)),
    deleteCanvas: (canvasId) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'DELETE',
          endpoint: `/canvas/${canvasId}`,
        })
      ),
    duplicateCanvas: (canvasId, name) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'POST',
          endpoint: `/canvas/${canvasId}/duplicate`,
          data: { name },
        })
      ),
    shareCanvas: (canvasId, permissions) =>
      apiCall(() => apiClient.current!.shareCanvas(canvasId, permissions)),
    getSharedCanvas: (shareToken) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'GET',
          endpoint: `/shared/${shareToken}`,
        })
      ),
    updatePermissions: (canvasId, permissions) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'PUT',
          endpoint: `/canvas/${canvasId}/permissions`,
          data: permissions,
        })
      ),
    exportCanvas: (canvasId, format) =>
      apiCall(() => apiClient.current!.exportCanvas(canvasId, format)),
    exportToImage: (canvasId, options) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'POST',
          endpoint: `/canvas/${canvasId}/export/image`,
          data: options,
        })
      ),
    exportToPdf: (canvasId, options) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'POST',
          endpoint: `/canvas/${canvasId}/export/pdf`,
          data: options,
        })
      ),
    getTemplates: (category) =>
      apiCall(() => apiClient.current!.getTemplates(category)),
    createTemplate: (canvasData, metadata) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'POST',
          endpoint: '/templates',
          data: { canvasData, metadata },
        })
      ),
    useTemplate: (templateId) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'POST',
          endpoint: `/templates/${templateId}/use`,
        })
      ),
    validateCanvas: (canvasData) =>
      apiCall(() => apiClient.current!.validateCanvas(canvasData)),
    validateNode: (node) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'POST',
          endpoint: '/validation/node',
          data: node,
        })
      ),
    getAnalytics: (canvasId, timeRange) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'GET',
          endpoint: `/canvas/${canvasId}/analytics`,
          params: timeRange
            ? { start: timeRange.start, end: timeRange.end }
            : undefined,
        })
      ),
    trackUsage: (event) =>
      apiCall(() =>
        apiClient.current!.request({
          method: 'POST',
          endpoint: '/analytics/track',
          data: event,
        })
      ),
  };

  return {
    ...operations,
    isLoading,
    error,
    clearError: () => setError(null),
    updateConfig: (newConfig: Partial<ApiConfig>) =>
      apiClient.current?.updateConfig(newConfig),
    clearCache: () => apiClient.current?.clearCache(),
  };
};
