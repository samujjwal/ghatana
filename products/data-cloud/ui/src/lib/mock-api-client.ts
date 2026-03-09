/**
 * Mock API client for Collection Entity System
 *
 * Provides mock endpoints that return demo data for UI testing and E2E scenarios.
 * In production, these would call real API endpoints.
 *
 * @doc.type service
 * @doc.purpose Mock API client for UI demonstration
 * @doc.layer frontend
 */

import {
  getMockCollections,
  getMockCollectionById,
  createMockCollection,
  updateMockCollection,
  deleteMockCollection,
  getMockEntitiesForCollection,
  getMockWorkflows,
  getMockWorkflowById,
  getMockExecutionsForWorkflow,
  getMockExecutionById,
  createMockEntity,
  createMockWorkflow,
  MockCollection,
  MockEntity,
  MockWorkflow,
  MockExecution,
} from './mock-data';

/**
 * API Response wrapper
 */
export interface ApiResponse<T> {
  data: T;
  status: number;
  message?: string;
}

/**
 * Pagination response
 */
export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  skip: number;
  limit: number;
}

/**
 * Mock API client
 */
export class MockApiClient {
  public simulateDelay = true;
  public delayMs = 500;

  /**
   * Simulate network delay for realistic behavior
   */
  private async delay(): Promise<void> {
    if (this.simulateDelay) {
      return new Promise((resolve) => setTimeout(resolve, this.delayMs));
    }
  }

  /**
   * Collections endpoints
   */

  async getCollections(): Promise<ApiResponse<MockCollection[]>> {
    await this.delay();
    return {
      data: getMockCollections(),
      status: 200,
      message: 'Collections retrieved successfully',
    };
  }

  async getCollectionById(id: string): Promise<ApiResponse<MockCollection>> {
    await this.delay();
    const collection = getMockCollectionById(id);

    if (!collection) {
      throw new Error(`Collection ${id} not found`);
    }

    return {
      data: collection,
      status: 200,
    };
  }

  async createCollection(
    data: Omit<MockCollection, 'id' | 'createdAt' | 'updatedAt' | 'entityCount'>
  ): Promise<ApiResponse<MockCollection>> {
    await this.delay();
    
    const newCollection = createMockCollection(
      data.name,
      data.description,
      data.schema,
      data.isActive
    );

    return {
      data: newCollection,
      status: 201,
      message: 'Collection created successfully',
    };
  }

  async updateCollection(
    id: string,
    updates: Partial<Omit<MockCollection, 'id' | 'createdAt' | 'updatedAt' | 'entityCount'>>
  ): Promise<ApiResponse<MockCollection>> {
    await this.delay();
    
    const updatedCollection = updateMockCollection(id, updates);
    
    if (!updatedCollection) {
      throw new Error(`Collection ${id} not found`);
    }

    return {
      data: updatedCollection,
      status: 200,
      message: 'Collection updated successfully',
    };
  }

  async deleteCollection(id: string): Promise<ApiResponse<{ id: string }>> {
    await this.delay();
    
    const success = deleteMockCollection(id);
    
    if (!success) {
      throw new Error(`Collection ${id} not found`);
    }

    return {
      data: { id },
      status: 200,
      message: 'Collection deleted successfully',
    };
  }

  /**
   * Entity endpoints
   */

  async getCollectionEntities(
    collectionId: string,
    skip = 0,
    limit = 10
  ): Promise<ApiResponse<PaginatedResponse<MockEntity>>> {
    await this.delay();
    const collection = getMockCollectionById(collectionId);

    if (!collection) {
      throw new Error(`Collection ${collectionId} not found`);
    }

    const entities = getMockEntitiesForCollection(collectionId, skip, limit);

    return {
      data: {
        items: entities,
        total: collection.entityCount,
        skip,
        limit,
      },
      status: 200,
    };
  }

  async createEntity(
    collectionId: string,
    data: Record<string, any>
  ): Promise<ApiResponse<MockEntity>> {
    await this.delay();
    const collection = getMockCollectionById(collectionId);

    if (!collection) {
      throw new Error(`Collection ${collectionId} not found`);
    }

    const newEntity = createMockEntity(collectionId, data);

    return {
      data: newEntity,
      status: 201,
      message: 'Entity created successfully',
    };
  }

  async updateEntity(
    collectionId: string,
    entityId: string,
    data: Record<string, any>
  ): Promise<ApiResponse<MockEntity>> {
    await this.delay();
    const collection = getMockCollectionById(collectionId);

    if (!collection) {
      throw new Error(`Collection ${collectionId} not found`);
    }

    const updated: MockEntity = {
      id: entityId,
      collectionId,
      data,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    return {
      data: updated,
      status: 200,
      message: 'Entity updated successfully',
    };
  }

  async deleteEntity(_collectionId: string, _entityId: string): Promise<ApiResponse<{ success: boolean }>> {
    await this.delay();
    return {
      data: { success: true },
      status: 200,
      message: 'Entity deleted successfully',
    };
  }

  /**
   * Workflow endpoints
   */

  async getWorkflows(): Promise<ApiResponse<MockWorkflow[]>> {
    await this.delay();
    return {
      data: getMockWorkflows(),
      status: 200,
      message: 'Workflows retrieved successfully',
    };
  }

  async getWorkflowById(id: string): Promise<ApiResponse<MockWorkflow>> {
    await this.delay();
    const workflow = getMockWorkflowById(id);

    if (!workflow) {
      throw new Error(`Workflow ${id} not found`);
    }

    return {
      data: workflow,
      status: 200,
    };
  }

  async createWorkflow(
    data: Omit<MockWorkflow, 'id' | 'createdAt' | 'updatedAt' | 'executionCount'>
  ): Promise<ApiResponse<MockWorkflow>> {
    await this.delay();
    const newWorkflow = createMockWorkflow(data.name, data.description);

    return {
      data: newWorkflow,
      status: 201,
      message: 'Workflow created successfully',
    };
  }

  async updateWorkflow(
    id: string,
    data: Partial<MockWorkflow>
  ): Promise<ApiResponse<MockWorkflow>> {
    await this.delay();
    const workflow = getMockWorkflowById(id);

    if (!workflow) {
      throw new Error(`Workflow ${id} not found`);
    }

    const updated: MockWorkflow = {
      ...workflow,
      ...data,
      updatedAt: new Date().toISOString(),
    };

    return {
      data: updated,
      status: 200,
      message: 'Workflow updated successfully',
    };
  }

  async executeWorkflow(id: string): Promise<ApiResponse<{ executionId: string }>> {
    await this.delay();
    const workflow = getMockWorkflowById(id);

    if (!workflow) {
      throw new Error(`Workflow ${id} not found`);
    }

    return {
      data: {
        executionId: `exec-${Date.now()}`,
      },
      status: 200,
      message: 'Workflow execution started',
    };
  }

  async deleteWorkflow(_id: string): Promise<ApiResponse<{ success: boolean }>> {
    await this.delay();
    return {
      data: { success: true },
      status: 200,
      message: 'Workflow deleted successfully',
    };
  }

  /**
   * Execution endpoints
   */

  async getWorkflowExecutions(
    workflowId: string,
    skip = 0,
    limit = 10
  ): Promise<ApiResponse<PaginatedResponse<MockExecution>>> {
    await this.delay();
    const workflow = getMockWorkflowById(workflowId);

    if (!workflow) {
      throw new Error(`Workflow ${workflowId} not found`);
    }

    const executions = getMockExecutionsForWorkflow(workflowId, skip, limit);

    return {
      data: {
        items: executions,
        total: workflow.executionCount,
        skip,
        limit,
      },
      status: 200,
    };
  }

  async getExecutionById(id: string): Promise<ApiResponse<MockExecution>> {
    await this.delay();
    const execution = getMockExecutionById(id);

    if (!execution) {
      throw new Error(`Execution ${id} not found`);
    }

    return {
      data: execution,
      status: 200,
    };
  }

  /**
   * Search and filter endpoints
   */

  async searchCollections(query: string): Promise<ApiResponse<MockCollection[]>> {
    await this.delay();
    const collections = getMockCollections().filter(
      (c) =>
        c.name.toLowerCase().includes(query.toLowerCase()) ||
        c.description.toLowerCase().includes(query.toLowerCase())
    );

    return {
      data: collections,
      status: 200,
    };
  }

  async searchWorkflows(query: string): Promise<ApiResponse<MockWorkflow[]>> {
    await this.delay();
    const workflows = getMockWorkflows().filter(
      (w) =>
        w.name.toLowerCase().includes(query.toLowerCase()) ||
        w.description.toLowerCase().includes(query.toLowerCase())
    );

    return {
      data: workflows,
      status: 200,
    };
  }

  /**
   * Validation endpoints
   */

  async validateEntity(
    collectionId: string,
    data: Record<string, any>
  ): Promise<ApiResponse<{ valid: boolean; errors: string[] }>> {
    await this.delay();
    const collection = getMockCollectionById(collectionId);

    if (!collection) {
      throw new Error(`Collection ${collectionId} not found`);
    }

    // Mock validation logic
    const errors: string[] = [];

    collection.schema.fields.forEach((field) => {
      if (field.required && !data[field.name]) {
        errors.push(`Field '${field.name}' is required`);
      }

      if (field.type === 'email' && data[field.name]) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(data[field.name])) {
          errors.push(`Field '${field.name}' must be a valid email`);
        }
      }

      if (field.maxLength && data[field.name]?.length > field.maxLength) {
        errors.push(`Field '${field.name}' exceeds max length of ${field.maxLength}`);
      }
    });

    return {
      data: {
        valid: errors.length === 0,
        errors,
      },
      status: 200,
    };
  }

  /**
   * Schema suggestion endpoints
   */

  async suggestSchema(
    data: Record<string, any>[]
  ): Promise<ApiResponse<{ fields: Array<{ name: string; type: string }> }>> {
    await this.delay();

    const fields = Object.keys(data[0] || {}).map((key) => {
      const value = data[0][key];
      let type = 'string';

      if (typeof value === 'number') {
        type = 'number';
      } else if (typeof value === 'boolean') {
        type = 'boolean';
      } else if (value instanceof Date) {
        type = 'date';
      } else if (typeof value === 'string') {
        if (value.includes('@')) {
          type = 'email';
        } else if (value.startsWith('http')) {
          type = 'url';
        }
      }

      return { name: key, type };
    });

    return {
      data: { fields },
      status: 200,
    };
  }
}

/**
 * Singleton instance
 */
export const mockApiClient = new MockApiClient();
