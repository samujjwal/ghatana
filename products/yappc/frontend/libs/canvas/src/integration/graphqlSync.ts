/**
 * GraphQL Sync Adapter
 * 
 * Synchronizes Canvas documents using GraphQL queries/mutations/subscriptions.
 */

import type {
  SyncAdapter,
  SyncResult,
  CanvasChange,
  GraphQLResponse,
  SyncConfig,
} from './types';

/**
 *
 */
export interface GraphQLSyncConfig extends Pick<SyncConfig, 'endpoint' | 'authToken' | 'retry'> {
  /** Enable GraphQL subscriptions for real-time updates */
  enableSubscriptions?: boolean;
}

/**
 *
 */
export class GraphQLSyncAdapter implements SyncAdapter {
  readonly type = 'graphql' as const;
  
  private config: Required<GraphQLSyncConfig>;
  private connected = false;
  private subscriptionClients = new Map<string, unknown>();
  
  /**
   *
   */
  constructor(config: GraphQLSyncConfig) {
    this.config = {
      endpoint: config.endpoint,
      authToken: config.authToken || '',
      enableSubscriptions: config.enableSubscriptions ?? true,
      retry: config.retry || {
        maxRetries: 3,
        backoffMultiplier: 2,
        initialDelay: 1000,
      },
    };
  }
  
  /**
   *
   */
  async connect(): Promise<void> {
    if (this.connected) return;
    
    try {
      // Test connection with introspection query
      const response = await this.query(`{ __schema { queryType { name } } }`);
      
      if (response.errors) {
        throw new Error(`GraphQL connection failed: ${response.errors[0].message}`);
      }
      
      this.connected = true;
    } catch (error) {
      throw new Error(`GraphQL connection failed: ${error instanceof Error ? error.message : 'Unknown error'}`);
    }
  }
  
  /**
   *
   */
  async disconnect(): Promise<void> {
    this.subscriptionClients.forEach((client) => client?.close?.());
    this.subscriptionClients.clear();
    this.connected = false;
  }
  
  /**
   *
   */
  isConnected(): boolean {
    return this.connected;
  }
  
  /**
   *
   */
  async pull(documentId: string): Promise<SyncResult> {
    if (!this.connected) {
      throw new Error('Not connected');
    }
    
    const query = `
      query GetDocument($id: ID!) {
        document(id: $id) {
          id
          version
          changes {
            id
            operation
            timestamp
            userId
            data
            version
          }
        }
      }
    `;
    
    try {
      const response = await this.query<{ document: { version: number; changes: CanvasChange[] } }>(
        query,
        { id: documentId }
      );
      
      if (response.errors) {
        return {
          success: false,
          version: 0,
          changes: [],
          error: {
            code: 'GRAPHQL_ERROR',
            message: response.errors[0].message,
            details: response.errors,
          },
        };
      }
      
      return {
        success: true,
        version: response.data!.document.version,
        changes: response.data!.document.changes,
      };
    } catch (error) {
      return {
        success: false,
        version: 0,
        changes: [],
        error: {
          code: 'PULL_ERROR',
          message: error instanceof Error ? error.message : 'Unknown error',
          details: error,
        },
      };
    }
  }
  
  /**
   *
   */
  async push(documentId: string, changes: CanvasChange[]): Promise<SyncResult> {
    if (!this.connected) {
      throw new Error('Not connected');
    }
    
    const mutation = `
      mutation PushChanges($documentId: ID!, $changes: [ChangeInput!]!) {
        pushChanges(documentId: $documentId, changes: $changes) {
          success
          version
          conflicts {
            changeId
            localChange {
              id
              operation
              timestamp
              userId
              data
              version
            }
            serverChange {
              id
              operation
              timestamp
              userId
              data
              version
            }
          }
        }
      }
    `;
    
    try {
      const response = await this.mutate<{
        pushChanges: {
          success: boolean;
          version: number;
          conflicts?: Array<{
            changeId: string;
            localChange: CanvasChange;
            serverChange: CanvasChange;
          }>;
        };
      }>(mutation, { documentId, changes });
      
      if (response.errors) {
        return {
          success: false,
          version: 0,
          changes: [],
          error: {
            code: 'GRAPHQL_ERROR',
            message: response.errors[0].message,
            details: response.errors,
          },
        };
      }
      
      const data = response.data!.pushChanges;
      const conflicts = data.conflicts?.map((c) => ({
        changeId: c.changeId,
        documentId,
        localChange: c.localChange,
        serverChange: c.serverChange,
        strategy: 'last-write-wins' as const,
        resolved: false,
      }));
      
      return {
        success: data.success,
        version: data.version,
        changes: [],
        conflicts,
      };
    } catch (error) {
      return {
        success: false,
        version: 0,
        changes: [],
        error: {
          code: 'PUSH_ERROR',
          message: error instanceof Error ? error.message : 'Unknown error',
          details: error,
        },
      };
    }
  }
  
  /**
   *
   */
  subscribe(documentId: string, callback: (change: CanvasChange) => void): () => void {
    if (!this.config.enableSubscriptions) {
      console.warn('GraphQL subscriptions not enabled');
      return () => {};
    }
    
    // In a real implementation, this would use graphql-ws or similar
    const subscription = `
      subscription OnDocumentChange($documentId: ID!) {
        documentChanged(documentId: $documentId) {
          id
          operation
          timestamp
          userId
          data
          version
        }
      }
    `;
    
    // Store subscription client for cleanup
    const key = `sub-${documentId}-${Date.now()}`;
    // Mock subscription client - replace with real graphql-ws implementation
    const client = { close: () => {} };
    this.subscriptionClients.set(key, client);
    
    return () => {
      const client = this.subscriptionClients.get(key);
      client?.close?.();
      this.subscriptionClients.delete(key);
    };
  }
  
  /**
   *
   */
  private async query<T>(query: string, variables?: Record<string, unknown>): Promise<GraphQLResponse<T>> {
    const response = await fetch(this.config.endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(this.config.authToken && { Authorization: `Bearer ${this.config.authToken}` }),
      },
      body: JSON.stringify({ query, variables }),
    });
    
    return await response.json();
  }
  
  /**
   *
   */
  private async mutate<T>(mutation: string, variables?: Record<string, unknown>): Promise<GraphQLResponse<T>> {
    return this.query<T>(mutation, variables);
  }
}

/**
 *
 */
export function createGraphQLSyncAdapter(config: GraphQLSyncConfig): GraphQLSyncAdapter {
  return new GraphQLSyncAdapter(config);
}
