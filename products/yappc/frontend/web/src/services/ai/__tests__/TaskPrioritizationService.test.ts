/**
 * TaskPrioritizationService Tests
 */

import { describe, it, expect } from 'vitest';
import {
  prioritizeTasks,
  type Task,
  type PrioritizationRequest,
  type PrioritizationResponse,
} from '../TaskPrioritizationService';

describe('TaskPrioritizationService', () => {
  describe('prioritizeTasks', () => {
    it('should prioritize tasks by score', async () => {
      const request: PrioritizationRequest = {
        tasks: [
          {
            id: '1',
            title: 'Low priority task',
            type: 'Code',
            priority: 'Low',
            status: 'pending',
            project: 'test',
            projectId: 'proj1',
          },
          {
            id: '2',
            title: 'High priority task',
            type: 'Code',
            priority: 'High',
            status: 'pending',
            project: 'test',
            projectId: 'proj1',
          },
        ],
      };

      const response = await prioritizeTasks(request);

      expect(response).toBeDefined();
      expect(response.prioritizedTasks).toBeInstanceOf(Array);
      expect(response.prioritizedTasks.length).toBe(2);
      expect(response.prioritizedTasks[0].id).toBe('2');
    });

    it('should include reasoning for each task', async () => {
      const request: PrioritizationRequest = {
        tasks: [
          {
            id: '1',
            title: 'Test task',
            type: 'Code',
            priority: 'High',
            status: 'pending',
            project: 'test',
            projectId: 'proj1',
          },
        ],
      };

      const response = await prioritizeTasks(request);

      expect(response.prioritizedTasks[0]).toHaveProperty('reasoning');
      expect(response.prioritizedTasks[0].reasoning).toBeDefined();
    });

    it('should include score and factors for each task', async () => {
      const request: PrioritizationRequest = {
        tasks: [
          {
            id: '1',
            title: 'Test task',
            type: 'Code',
            priority: 'High',
            status: 'pending',
            project: 'test',
            projectId: 'proj1',
          },
        ],
      };

      const response = await prioritizeTasks(request);

      expect(response.prioritizedTasks[0]).toHaveProperty('score');
      expect(response.prioritizedTasks[0]).toHaveProperty('factors');
      expect(response.prioritizedTasks[0].factors).toHaveProperty('deadlineUrgency');
    });

    it('should handle empty task list', async () => {
      const request: PrioritizationRequest = {
        tasks: [],
      };

      const response = await prioritizeTasks(request);

      expect(response.prioritizedTasks).toEqual([]);
    });

    it('should consider deadline urgency', async () => {
      const request: PrioritizationRequest = {
        tasks: [
          {
            id: '1',
            title: 'Urgent task',
            type: 'Code',
            priority: 'Medium',
            status: 'pending',
            dueDate: new Date(Date.now() + 3600000).toISOString(),
            project: 'test',
            projectId: 'proj1',
          },
          {
            id: '2',
            title: 'Non-urgent task',
            type: 'Code',
            priority: 'Medium',
            status: 'pending',
            dueDate: new Date(Date.now() + 604800000).toISOString(),
            project: 'test',
            projectId: 'proj1',
          },
        ],
      };

      const response = await prioritizeTasks(request);

      expect(response.prioritizedTasks[0].id).toBe('1');
    });
  });
});
