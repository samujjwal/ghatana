/**
 * Task API Client
 *
 * HTTP client for interacting with the Java backend task endpoints.
 * Provides typed methods for managing dashboard priority tasks.
 *
 * @doc.type class
 * @doc.purpose HTTP client for task API
 * @doc.layer product
 * @doc.pattern Service
 */

import { BaseDashboardApiClient, ClientMode } from './BaseDashboardApiClient';
import type { DashboardApiConfig, ApiResponse, PaginationParams, PaginatedResponse } from './types';

// ============================================================================
// Types
// ============================================================================

/**
 * Task status
 */
export type TaskStatus = 'pending' | 'in-progress' | 'blocked' | 'completed' | 'skipped';

/**
 * Task priority
 */
export type TaskPriority = 'low' | 'medium' | 'high' | 'urgent' | 'critical';

/**
 * Task type
 */
export type TaskType = 'Design' | 'Code' | 'Deploy' | 'Testing' | 'Documentation' | 'Security' | 'Operations';

/**
 * Priority task interface (matches dashboard component)
 */
export interface PriorityTask {
  id: string;
  title: string;
  project: string;
  projectId: string;
  type: TaskType;
  priority: TaskPriority;
  persona: string;
  dueDate?: string;
  isBlocked?: boolean;
  status: TaskStatus;
  createdAt: string;
  updatedAt: string;
  assignee?: string;
  tags?: string[];
}

/**
 * List priority tasks request
 */
export interface ListPriorityTasksRequest extends PaginationParams {
  projectId?: string;
  status?: TaskStatus;
  priority?: TaskPriority;
  type?: TaskType;
  assignee?: string;
}

/**
 * Update task status request
 */
export interface UpdateTaskStatusRequest {
  status: TaskStatus;
  reason?: string;
}

/**
 * Bulk task action request
 */
export interface BulkTaskActionRequest {
  taskIds: string[];
  action: 'approve' | 'reject' | 'complete' | 'skip';
  reason?: string;
}

/**
 * Bulk task action response
 */
export interface BulkTaskActionResult {
  succeeded: string[];
  failed: Array<{ taskId: string; error: string }>;
}

// ============================================================================
// Client Implementation
// ============================================================================

/**
 * Task API Client
 *
 * Provides methods for:
 * - Listing priority tasks with filtering
 * - Updating individual task status
 * - Bulk task operations
 * - Task assignment and reassignment
 */
export class TaskApiClient extends BaseDashboardApiClient {
  constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
    super(config, mode);
  }

  // ========== Task Query Operations ==========

  /**
   * List priority tasks with filtering
   *
   * @param request The list request with filters
   * @returns Promise of paginated tasks
   */
  async listPriorityTasks(
    request: ListPriorityTasksRequest = {}
  ): Promise<ApiResponse<PaginatedResponse<PriorityTask>>> {
    if (this.mode === 'mock') {
      return this.createMockResponse({
        items: this.getMockTasks(request),
        totalItems: 10,
        page: request.page || 1,
        pageSize: request.pageSize || 10,
        totalPages: 1,
        hasNext: false,
        hasPrevious: false,
      });
    }

    return this.get<PaginatedResponse<PriorityTask>>('/tasks/priority', {
      ...request,
    });
  }

  /**
   * Get a single task by ID
   *
   * @param taskId The task ID
   * @returns Promise of task details
   */
  async getTask(taskId: string): Promise<ApiResponse<PriorityTask>> {
    if (this.mode === 'mock') {
      const mockTask = this.getMockTasks({}).find(t => t.id === taskId);
      if (!mockTask) {
        return this.createMockErrorResponse('NOT_FOUND', 'Task not found');
      }
      return this.createMockResponse(mockTask);
    }

    return this.get<PriorityTask>(`/tasks/${taskId}`);
  }

  // ========== Task Status Operations ==========

  /**
   * Update task status
   *
   * @param taskId The task ID
   * @param request The status update request
   * @returns Promise of updated task
   */
  async updateTaskStatus(
    taskId: string,
    request: UpdateTaskStatusRequest
  ): Promise<ApiResponse<PriorityTask>> {
    if (this.mode === 'mock') {
      const mockTasks = this.getMockTasks({});
      const taskIndex = mockTasks.findIndex(t => t.id === taskId);
      if (taskIndex === -1) {
        return this.createMockErrorResponse('NOT_FOUND', 'Task not found');
      }
      const updatedTask = {
        ...mockTasks[taskIndex],
        status: request.status,
        updatedAt: new Date().toISOString(),
      };
      return this.createMockResponse(updatedTask);
    }

    return this.put<PriorityTask>(`/tasks/${taskId}/status`, request);
  }

  /**
   * Approve a task
   *
   * @param taskId The task ID
   * @param reason Optional approval reason
   * @returns Promise of updated task
   */
  async approveTask(taskId: string, reason?: string): Promise<ApiResponse<PriorityTask>> {
    return this.updateTaskStatus(taskId, { status: 'completed', reason });
  }

  /**
   * Reject a task
   *
   * @param taskId The task ID
   * @param reason Optional rejection reason
   * @returns Promise of updated task
   */
  async rejectTask(taskId: string, reason?: string): Promise<ApiResponse<PriorityTask>> {
    return this.updateTaskStatus(taskId, { status: 'skipped', reason });
  }

  /**
   * Complete a task
   *
   * @param taskId The task ID
   * @returns Promise of updated task
   */
  async completeTask(taskId: string): Promise<ApiResponse<PriorityTask>> {
    return this.updateTaskStatus(taskId, { status: 'completed' });
  }

  // ========== Bulk Operations ==========

  /**
   * Perform bulk action on multiple tasks
   *
   * @param request The bulk action request
   * @returns Promise of bulk action result
   */
  async bulkTaskAction(
    request: BulkTaskActionRequest
  ): Promise<ApiResponse<BulkTaskActionResult>> {
    if (this.mode === 'mock') {
      return this.createMockResponse({
        succeeded: request.taskIds,
        failed: [],
      });
    }

    return this.post<BulkTaskActionResult>('/tasks/bulk-action', request);
  }

  /**
   * Bulk approve tasks
   *
   * @param taskIds The task IDs to approve
   * @param reason Optional approval reason
   * @returns Promise of bulk action result
   */
  async bulkApprove(taskIds: string[], reason?: string): Promise<ApiResponse<BulkTaskActionResult>> {
    return this.bulkTaskAction({ taskIds, action: 'approve', reason });
  }

  /**
   * Bulk reject tasks
   *
   * @param taskIds The task IDs to reject
   * @param reason Optional rejection reason
   * @returns Promise of bulk action result
   */
  async bulkReject(taskIds: string[], reason?: string): Promise<ApiResponse<BulkTaskActionResult>> {
    return this.bulkTaskAction({ taskIds, action: 'reject', reason });
  }

  // ========== Assignment Operations ==========

  /**
   * Assign task to a user
   *
   * @param taskId The task ID
   * @param assignee The user to assign to
   * @returns Promise of updated task
   */
  async assignTask(taskId: string, assignee: string): Promise<ApiResponse<PriorityTask>> {
    if (this.mode === 'mock') {
      const mockTasks = this.getMockTasks({});
      const taskIndex = mockTasks.findIndex(t => t.id === taskId);
      if (taskIndex === -1) {
        return this.createMockErrorResponse('NOT_FOUND', 'Task not found');
      }
      const updatedTask = {
        ...mockTasks[taskIndex],
        assignee,
        updatedAt: new Date().toISOString(),
      };
      return this.createMockResponse(updatedTask);
    }

    return this.put<PriorityTask>(`/tasks/${taskId}/assign`, { assignee });
  }

  // ========== Helper Methods ==========

  /**
   * Create a mock response with proper ApiResponse structure
   */
  private createMockResponse<T>(data: T): ApiResponse<T> {
    return {
      success: true,
      data,
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * Create a mock error response
   */
  private createMockErrorResponse(code: string, message: string): ApiResponse<never> {
    return {
      success: false,
      error: {
        code,
        message,
      },
      timestamp: new Date().toISOString(),
    };
  }

  /**
   * Get mock tasks for testing
   */
  private getMockTasks(request: ListPriorityTasksRequest): PriorityTask[] {
    const baseTasks: PriorityTask[] = [
      {
        id: 'task-1',
        title: 'Review API endpoint design',
        project: 'E-Commerce Platform',
        projectId: 'proj-1',
        type: 'Design',
        priority: 'urgent',
        persona: 'Tech Lead',
        status: 'pending',
        dueDate: new Date(Date.now() + 86400000).toISOString(),
        isBlocked: false,
        createdAt: new Date(Date.now() - 86400000).toISOString(),
        updatedAt: new Date(Date.now() - 86400000).toISOString(),
        tags: ['api', 'design'],
      },
      {
        id: 'task-2',
        title: 'Implement user authentication',
        project: 'E-Commerce Platform',
        projectId: 'proj-1',
        type: 'Code',
        priority: 'high',
        persona: 'Developer',
        status: 'in-progress',
        dueDate: new Date(Date.now() + 172800000).toISOString(),
        isBlocked: false,
        createdAt: new Date(Date.now() - 172800000).toISOString(),
        updatedAt: new Date(Date.now() - 86400000).toISOString(),
        assignee: 'dev-1',
        tags: ['auth', 'security'],
      },
      {
        id: 'task-3',
        title: 'Deploy to staging environment',
        project: 'E-Commerce Platform',
        projectId: 'proj-1',
        type: 'Deploy',
        priority: 'high',
        persona: 'DevOps',
        status: 'blocked',
        isBlocked: true,
        createdAt: new Date(Date.now() - 259200000).toISOString(),
        updatedAt: new Date(Date.now() - 86400000).toISOString(),
        tags: ['deployment', 'ci-cd'],
      },
    ];

    let filtered = [...baseTasks];

    if (request.projectId) {
      filtered = filtered.filter(t => t.projectId === request.projectId);
    }
    if (request.status) {
      filtered = filtered.filter(t => t.status === request.status);
    }
    if (request.priority) {
      filtered = filtered.filter(t => t.priority === request.priority);
    }
    if (request.type) {
      filtered = filtered.filter(t => t.type === request.type);
    }
    if (request.assignee) {
      filtered = filtered.filter(t => t.assignee === request.assignee);
    }

    return filtered;
  }
}
