/**
 * Canvas Integration Node Types
 * 
 * Type definitions for integration nodes (Service, API, Database).
 * Extracted to prevent circular dependencies between integration and components.
 * 
 * @doc.type types
 * @doc.purpose Integration node type definitions
 * @doc.layer canvas/integration
 */

/**
 * Base persona node data (extracted from PersonaNodes to avoid circular deps)
 */
export type PersonaNodeType =
    | 'aiPrompt'
    | 'userStory'
    | 'requirement'
    | 'apiEndpoint'
    | 'service'
    | 'database'
    | 'algorithm'
    | 'dataStructure'
    | 'codeBlock'
    | 'testSuite'
    | 'uiScreen'
    | 'wireframe';

export interface PersonaNodeData {
    label: string;
    type: PersonaNodeType;
    persona: 'pm' | 'architect' | 'developer' | 'qa' | 'ux';
    description?: string;
    status?: 'draft' | 'ready' | 'in-progress' | 'completed' | 'error';
    metadata?: Record<string, unknown>;
    aiGenerated?: boolean;
    lastUpdated?: string;
}

/**
 * Database node data - for architect persona
 */
export interface DatabaseNodeData extends PersonaNodeData {
  type: 'database';
  persona: 'architect';
  databaseType: 'postgres' | 'mysql' | 'mongodb' | 'redis' | 'other';
  schema?: SchemaField[];
  connectionString?: string;
}

/**
 * Service node data - for architect persona
 */
export interface ServiceNodeData extends PersonaNodeData {
  type: 'service';
  persona: 'architect';
  serviceType: 'rest' | 'graphql' | 'grpc' | 'websocket' | 'queue' | 'other';
  port?: number;
  dependencies?: string[];
  deployment?: DeploymentConfig;
}

/**
 * API endpoint node data - for architect persona
 */
export interface APIEndpointNodeData extends PersonaNodeData {
  type: 'apiEndpoint';
  persona: 'architect';
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
  path: string;
  requestBody?: string;
  responseBody?: string;
  config?: APIEndpointConfig;
}

/**
 * Schema field definition (re-exported from PropertyPanels)
 */
export interface SchemaField {
  name: string;
  type: string;
  nullable?: boolean;
  unique?: boolean;
  primaryKey?: boolean;
  foreignKey?: {
    table: string;
    column: string;
  };
}

/**
 * Deployment configuration (re-exported from PropertyPanels)
 */
export interface DeploymentConfig {
  replicas?: number;
  resources?: {
    cpu: string;
    memory: string;
  };
  environment?: Record<string, string>;
}

/**
 * API endpoint configuration (re-exported from PropertyPanels)
 */
export interface APIEndpointConfig {
  authentication?: 'none' | 'bearer' | 'apikey' | 'oauth';
  rateLimit?: number;
  timeout?: number;
  cache?: boolean;
}
