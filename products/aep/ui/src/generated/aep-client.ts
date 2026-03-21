/**
 * Generated AEP API Client
 * 
 * This file contains types generated from OpenAPI specification.
 * To regenerate: pnpm generate-client
 * 
 * Source: ../../contracts/openapi.yaml
 */

// Use with openapi-fetch for type-safe API calls
// Example:
// import createClient from 'openapi-fetch';
// const client = createClient<paths>({ baseUrl: '/api' });

export type paths = {
  "/health": {
    get: {
      responses: {
        200: {
          content: {
            "application/json": {
              status: string;
              version?: string;
            };
          };
        };
      };
    };
  };
  "/ready": {
    get: {
      responses: {
        200: {
          content: {
            "application/json": {
              ready: boolean;
              checks?: Record<string, boolean>;
            };
          };
        };
        503: {
          content: {
            "application/json": {
              ready: false;
              reason: string;
            };
          };
        };
      };
    };
  };
  "/api/v1/pipelines": {
    get: {
      parameters: {
        query?: {
          tenantId?: string;
          limit?: number;
          offset?: number;
        };
        header?: {
          "X-Tenant-Id"?: string;
        };
      };
      responses: {
        200: {
          content: {
            "application/json": {
              pipelines: Pipeline[];
              total: number;
            };
          };
        };
      };
    };
    post: {
      requestBody: {
        content: {
          "application/json": PipelineCreateRequest;
        };
      };
      responses: {
        201: {
          content: {
            "application/json": Pipeline;
          };
        };
      };
    };
  };
  "/api/v1/pipelines/{id}": {
    get: {
      parameters: {
        path: {
          id: string;
        };
      };
      responses: {
        200: {
          content: {
            "application/json": Pipeline;
          };
        };
        404: {
          content: {
            "application/json": {
              error: string;
            };
          };
        };
      };
    };
    put: {
      parameters: {
        path: {
          id: string;
        };
      };
      requestBody: {
        content: {
          "application/json": PipelineUpdateRequest;
        };
      };
      responses: {
        200: {
          content: {
            "application/json": Pipeline;
          };
        };
      };
    };
    delete: {
      parameters: {
        path: {
          id: string;
        };
      };
      responses: {
        204: never;
        404: {
          content: {
            "application/json": {
              error: string;
            };
          };
        };
      };
    };
  };
  "/events/stream": {
    get: {
      responses: {
        200: {
          content: {
            "text/event-stream": EventStreamMessage;
          };
        };
      };
    };
  };
};

// Component Types
export interface Pipeline {
  id: string;
  name: string;
  description?: string;
  stages: PipelineStage[];
  status: "draft" | "active" | "paused" | "archived";
  createdAt: string;
  updatedAt: string;
  tenantId?: string;
}

export interface PipelineStage {
  id: string;
  name: string;
  type: string;
  config: Record<string, unknown>;
  connections: string[];
}

export interface PipelineCreateRequest {
  name: string;
  description?: string;
  stages: PipelineStage[];
  tenantId?: string;
}

export interface PipelineUpdateRequest {
  name?: string;
  description?: string;
  stages?: PipelineStage[];
  status?: "draft" | "active" | "paused" | "archived";
}

export interface EventStreamMessage {
  id: string;
  event: string;
  data: string;
}

// Helper type for API responses
type SuccessResponse<T extends keyof paths> = paths[T] extends {
  get: { responses: { 200: { content: { "application/json": infer R } } } }
} ? R : never;

export type { SuccessResponse };
