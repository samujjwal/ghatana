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

  interface WorkflowNode {
    id: string;
    type: string;
    position: { x: number; y: number };
    data: {
      label: string;
      [key: string]: unknown;
    };
  }

  interface WorkflowEdge {
    id: string;
    source: string;
    target: string;
    sourceHandle?: string;
    targetHandle?: string;
    label?: string;
  }

  interface WorkflowDefinition {
    id: string;
    name: string;
    description?: string;
    version: number;
    nodes: WorkflowNode[];
    edges: WorkflowEdge[];
    createdAt: string;
    updatedAt: string;
    createdBy: string;
  }

  // Add other global types as needed
}

// Make this file a module
export {};
