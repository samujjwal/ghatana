// Global type declarations for the application

// WebSocket types
declare module 'ws' {
  export class WebSocket extends globalThis.WebSocket {
    constructor(url: string, protocols?: string | string[]);
  }
}

// Global window extensions
declare global {
  interface Window {
    // Add any global window properties here if needed
  }

  // WebSocket types for browser
  interface WebSocketMessageEvent extends MessageEvent {
    data: string | ArrayBuffer | Blob | ArrayBufferView;
  }

  // Global types for workflow execution
  interface WorkflowExecution {
    id: string;
    tenantId: string;
    workflowId: string;
    status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
    startTime: string;
    endTime?: string;
    duration?: number;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
    input?: Record<string, unknown>;
    output?: Record<string, unknown>;
    error?: string;
    nodeExecutions: NodeExecution[];
  }

  interface NodeExecution {
    nodeId: string;
    nodeName: string;
    status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'SKIPPED';
    startTime: string;
    endTime?: string;
    duration?: number;
    input?: Record<string, unknown>;
    output?: Record<string, unknown>;
    error?: string;
    retryCount?: number;
  }

  // WorkflowNode, WorkflowEdge, WorkflowDefinition are defined in the module
  // system at @/features/workflow/types/workflow.types (re-exported via @/types/workflow.types).
  // Global declarations must not shadow those module types.
  // Add other global types as needed
}

// Make this file a module
export {};
