import { describe, it, expect } from 'vitest';

import {
  createWorkflowState,
  createTask,
  getTask,
  getAllTasks,
  updateTask,
  completeTask,
  deleteTask,
  reorderTasks,
  changeStage,
  canTransitionToStage,
  isReadyForDev,
  addValidationRule,
  removeValidationRule,
  validateWorkflow,
  markNotificationRead,
  getUnreadNotifications,
  clearNotifications,
  lockWorkflow,
  unlockWorkflow,
  getWorkflowStatistics,
  exportSpecBundle,
  exportSpecBundleJSON,
  importSpecBundle,
  searchTasks,
  filterTasksByStatus,
  filterTasksByPriority,
  getBlockedTasks,
  getReadyTasks,
  CHECKLIST_TEMPLATES,
  type WorkflowState,
  type ChecklistTask,
} from '../checklistStore';

describe('checklistStore', () => {
  describe('Workflow Creation', () => {
    it('should create default workflow state', () => {
      const state = createWorkflowState({ title: 'Test Workflow' });
      
      expect(state.tasks.size).toBe(0);
      expect(state.taskOrder).toEqual([]);
      expect(state.stage).toBe('draft');
      expect(state.metadata.title).toBe('Test Workflow');
      expect(state.locked).toBe(false);
    });

    it('should create workflow with template', () => {
      const state = createWorkflowState({
        title: 'Design Project',
        template: CHECKLIST_TEMPLATES['basic-design'],
      });
      
      expect(state.tasks.size).toBe(5);
      expect(getAllTasks(state)).toHaveLength(5);
      expect(getAllTasks(state)[0].title).toBe('Define user stories');
    });

    it('should set creator metadata', () => {
      const state = createWorkflowState({
        title: 'Test',
        createdBy: 'user123',
      });
      
      expect(state.metadata.createdBy).toBe('user123');
    });
  });

  describe('Task CRUD', () => {
    it('should create a task', () => {
      const state = createWorkflowState({ title: 'Test' });
      const newState = createTask(state, {
        title: 'Task 1',
        priority: 'high',
        required: true,
      });
      
      expect(newState.tasks.size).toBe(1);
      expect(getAllTasks(newState)[0].title).toBe('Task 1');
      expect(getAllTasks(newState)[0].status).toBe('pending');
    });

    it('should create multiple tasks with correct order', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      state = createTask(state, { title: 'Task 2', priority: 'medium', required: false });
      state = createTask(state, { title: 'Task 3', priority: 'low', required: false });
      
      const tasks = getAllTasks(state);
      expect(tasks).toHaveLength(3);
      expect(tasks[0].order).toBe(0);
      expect(tasks[1].order).toBe(1);
      expect(tasks[2].order).toBe(2);
    });

    it('should get task by ID', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Find Me', priority: 'high', required: true });
      
      const taskId = state.taskOrder[0];
      const task = getTask(state, taskId);
      
      expect(task).toBeDefined();
      expect(task?.title).toBe('Find Me');
    });

    it('should return undefined for non-existent task', () => {
      const state = createWorkflowState({ title: 'Test' });
      const task = getTask(state, 'nonexistent');
      
      expect(task).toBeUndefined();
    });

    it('should update task properties', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Original', priority: 'low', required: false });
      
      const taskId = state.taskOrder[0];
      const newState = updateTask(state, taskId, {
        title: 'Updated',
        priority: 'critical',
        assignee: 'user123',
      });
      
      const task = getTask(newState, taskId);
      expect(task?.title).toBe('Updated');
      expect(task?.priority).toBe('critical');
      expect(task?.assignee).toBe('user123');
    });

    it('should not modify state when updating non-existent task', () => {
      const state = createWorkflowState({ title: 'Test' });
      const newState = updateTask(state, 'nonexistent', { title: 'Updated' });
      
      expect(newState).toBe(state);
    });

    it('should delete task', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      state = createTask(state, { title: 'Task 2', priority: 'medium', required: false });
      state = createTask(state, { title: 'Task 3', priority: 'low', required: false });
      
      const taskIdToDelete = state.taskOrder[1];
      const newState = deleteTask(state, taskIdToDelete);
      
      expect(newState.tasks.size).toBe(2);
      expect(getAllTasks(newState)).toHaveLength(2);
      expect(getAllTasks(newState)[0].order).toBe(0);
      expect(getAllTasks(newState)[1].order).toBe(1);
    });

    it('should remove task from dependencies when deleted', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      const task1Id = state.taskOrder[0];
      
      state = createTask(state, {
        title: 'Task 2',
        priority: 'medium',
        required: false,
        dependsOn: [task1Id],
      });
      const task2Id = state.taskOrder[1];
      
      const newState = deleteTask(state, task1Id);
      const task2 = getTask(newState, task2Id);
      
      expect(task2?.dependsOn).toEqual([]);
    });

    it('should not create task when locked', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = lockWorkflow(state);
      
      const newState = createTask(state, { title: 'Task', priority: 'high', required: true });
      
      expect(newState.tasks.size).toBe(0);
    });
  });

  describe('Task Reordering', () => {
    it('should reorder tasks', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'A', priority: 'high', required: true });
      state = createTask(state, { title: 'B', priority: 'medium', required: false });
      state = createTask(state, { title: 'C', priority: 'low', required: false });
      
      const [id1, id2, id3] = state.taskOrder;
      const newState = reorderTasks(state, [id3, id1, id2]);
      
      const tasks = getAllTasks(newState);
      expect(tasks[0].title).toBe('C');
      expect(tasks[1].title).toBe('A');
      expect(tasks[2].title).toBe('B');
      expect(tasks[0].order).toBe(0);
      expect(tasks[1].order).toBe(1);
      expect(tasks[2].order).toBe(2);
    });

    it('should not reorder with invalid task IDs', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'A', priority: 'high', required: true });
      
      const newState = reorderTasks(state, ['nonexistent']);
      
      expect(newState).toBe(state);
    });
  });

  describe('Task Completion', () => {
    it('should complete task', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      
      const taskId = state.taskOrder[0];
      const newState = completeTask(state, taskId);
      
      const task = getTask(newState, taskId);
      expect(task?.status).toBe('completed');
      expect(task?.completedAt).toBeDefined();
    });

    it('should create notification when task completed', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      state = createTask(state, { title: 'Task 2', priority: 'high', required: true });
      
      const taskId = state.taskOrder[0];
      const newState = completeTask(state, taskId);
      
      expect(newState.notifications).toHaveLength(1);
      expect(newState.notifications[0].type).toBe('task-completed');
    });

    it('should not complete task with unmet dependencies', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      const task1Id = state.taskOrder[0];
      
      state = createTask(state, {
        title: 'Task 2',
        priority: 'medium',
        required: false,
        dependsOn: [task1Id],
      });
      const task2Id = state.taskOrder[1];
      
      const newState = completeTask(state, task2Id);
      const task2 = getTask(newState, task2Id);
      
      expect(task2?.status).toBe('pending');
    });

    it('should complete task after dependencies met', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      const task1Id = state.taskOrder[0];
      
      state = createTask(state, {
        title: 'Task 2',
        priority: 'medium',
        required: false,
        dependsOn: [task1Id],
      });
      const task2Id = state.taskOrder[1];
      
      state = completeTask(state, task1Id);
      state = completeTask(state, task2Id);
      
      const task2 = getTask(state, task2Id);
      expect(task2?.status).toBe('completed');
    });

    it('should create ready-for-dev notification when all required tasks completed', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Required 1', priority: 'high', required: true });
      state = createTask(state, { title: 'Required 2', priority: 'high', required: true });
      
      const [task1Id, task2Id] = state.taskOrder;
      
      state = completeTask(state, task1Id);
      state = completeTask(state, task2Id);
      
      const readyNotif = state.notifications.find((n) => n.type === 'ready-for-dev');
      expect(readyNotif).toBeDefined();
    });
  });

  describe('Workflow Stages', () => {
    it('should change workflow stage', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Required', priority: 'high', required: true });
      
      const newState = changeStage(state, 'review');
      
      expect(newState.stage).toBe('review');
    });

    it('should create notification when stage changes', () => {
      const state = createWorkflowState({ title: 'Test' });
      const newState = changeStage(state, 'review');
      
      expect(newState.notifications).toHaveLength(1);
      expect(newState.notifications[0].type).toBe('stage-changed');
    });

    it('should not allow skipping stages', () => {
      const state = createWorkflowState({ title: 'Test' });
      const canTransition = canTransitionToStage(state, 'ready-for-dev');
      
      expect(canTransition).toBe(false);
    });

    it('should not transition to ready-for-dev without completed required tasks', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Required', priority: 'high', required: true });
      state = changeStage(state, 'review');
      state = changeStage(state, 'approved');
      
      const canTransition = canTransitionToStage(state, 'ready-for-dev');
      
      expect(canTransition).toBe(false);
    });

    it('should transition to ready-for-dev with all required tasks completed', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Required', priority: 'high', required: true });
      const taskId = state.taskOrder[0];
      
      state = completeTask(state, taskId);
      state = changeStage(state, 'review');
      state = changeStage(state, 'approved');
      
      const canTransition = canTransitionToStage(state, 'ready-for-dev');
      
      expect(canTransition).toBe(true);
    });

    it('should check isReadyForDev correctly', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Required 1', priority: 'high', required: true });
      state = createTask(state, { title: 'Optional', priority: 'low', required: false });
      
      expect(isReadyForDev(state)).toBe(false);
      
      const taskId = state.taskOrder[0];
      state = completeTask(state, taskId);
      
      expect(isReadyForDev(state)).toBe(true);
    });

    it('should return false for isReadyForDev with no required tasks', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Optional', priority: 'low', required: false });
      
      expect(isReadyForDev(state)).toBe(false);
    });
  });

  describe('Validation Rules', () => {
    it('should add validation rule', () => {
      const state = createWorkflowState({ title: 'Test' });
      const newState = addValidationRule(state, {
        id: 'rule1',
        description: 'Test rule',
        validate: () => true,
        errorMessage: 'Validation failed',
      });
      
      expect(newState.validationRules).toHaveLength(1);
    });

    it('should remove validation rule', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = addValidationRule(state, {
        id: 'rule1',
        description: 'Test rule',
        validate: () => true,
        errorMessage: 'Validation failed',
      });
      
      const newState = removeValidationRule(state, 'rule1');
      
      expect(newState.validationRules).toHaveLength(0);
    });

    it('should validate workflow against rules', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = addValidationRule(state, {
        id: 'rule1',
        description: 'Must have at least one task',
        validate: (s) => getAllTasks(s).length > 0,
        errorMessage: 'No tasks defined',
      });
      
      let result = validateWorkflow(state);
      expect(result.valid).toBe(false);
      expect(result.errors).toHaveLength(1);
      
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      result = validateWorkflow(state);
      expect(result.valid).toBe(true);
    });

    it('should prevent stage transition when validation fails', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = addValidationRule(state, {
        id: 'rule1',
        description: 'Test rule',
        validate: () => false,
        errorMessage: 'Always fails',
      });
      
      const canTransition = canTransitionToStage(state, 'review');
      
      expect(canTransition).toBe(false);
    });
  });

  describe('Notifications', () => {
    it('should mark notification as read', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task', priority: 'high', required: true });
      const taskId = state.taskOrder[0];
      state = completeTask(state, taskId);
      
      const notifId = state.notifications[0].id;
      const newState = markNotificationRead(state, notifId);
      
      expect(newState.notifications[0].read).toBe(true);
    });

    it('should get unread notifications', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      state = createTask(state, { title: 'Task 2', priority: 'high', required: true });
      
      const [task1Id, task2Id] = state.taskOrder;
      state = completeTask(state, task1Id);
      state = completeTask(state, task2Id);
      
      const unread = getUnreadNotifications(state);
      expect(unread.length).toBeGreaterThan(0);
      expect(unread.every((n) => !n.read)).toBe(true);
    });

    it('should clear all notifications', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task', priority: 'high', required: true });
      const taskId = state.taskOrder[0];
      state = completeTask(state, taskId);
      
      const newState = clearNotifications(state);
      
      expect(newState.notifications).toHaveLength(0);
    });
  });

  describe('Workflow Locking', () => {
    it('should lock workflow', () => {
      const state = createWorkflowState({ title: 'Test' });
      const newState = lockWorkflow(state);
      
      expect(newState.locked).toBe(true);
    });

    it('should unlock workflow', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = lockWorkflow(state);
      
      const newState = unlockWorkflow(state);
      
      expect(newState.locked).toBe(false);
    });

    it('should prevent mutations when locked', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task', priority: 'high', required: true });
      const taskId = state.taskOrder[0];
      
      state = lockWorkflow(state);
      
      const afterCreate = createTask(state, { title: 'New', priority: 'high', required: true });
      expect(afterCreate.tasks.size).toBe(1);
      
      const afterUpdate = updateTask(state, taskId, { title: 'Updated' });
      expect(getTask(afterUpdate, taskId)?.title).toBe('Task');
      
      const afterDelete = deleteTask(state, taskId);
      expect(afterDelete.tasks.size).toBe(1);
      
      const afterReorder = reorderTasks(state, [taskId]);
      expect(afterReorder).toBe(state);
      
      const afterStage = changeStage(state, 'review');
      expect(afterStage.stage).toBe('draft');
    });
  });

  describe('Statistics', () => {
    it('should calculate workflow statistics', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'critical', required: true });
      state = createTask(state, { title: 'Task 2', priority: 'high', required: true });
      state = createTask(state, { title: 'Task 3', priority: 'low', required: false });
      
      const task1Id = state.taskOrder[0];
      const task2Id = state.taskOrder[1];
      const task3Id = state.taskOrder[2];
      state = completeTask(state, task1Id);
      state = updateTask(state, task2Id, { status: 'in-progress' });
      state = updateTask(state, task3Id, { status: 'blocked' });
      
      const stats = getWorkflowStatistics(state);
      
      expect(stats.totalTasks).toBe(3);
      expect(stats.completedTasks).toBe(1);
      expect(stats.requiredTasks).toBe(2);
      expect(stats.completedRequiredTasks).toBe(1);
      expect(stats.completionPercentage).toBeCloseTo(33.33, 1);
      expect(stats.inProgressTasks).toBe(1);
      expect(stats.blockedTasks).toBe(1);
      expect(stats.criticalTasks).toBe(1);
    });

    it('should return 0 percentage for no tasks', () => {
      const state = createWorkflowState({ title: 'Test' });
      const stats = getWorkflowStatistics(state);
      
      expect(stats.completionPercentage).toBe(0);
    });
  });

  describe('Export/Import', () => {
    it('should export spec bundle', () => {
      let state = createWorkflowState({ title: 'Test Project' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      state = createTask(state, { title: 'Task 2', priority: 'medium', required: false });
      
      const bundle = exportSpecBundle(state, {
        canvasReference: 'canvas-123',
        notes: 'Test notes',
        exportedBy: 'user123',
      });
      
      expect(bundle.version).toBe('1.0.0');
      expect(bundle.metadata.title).toBe('Test Project');
      expect(bundle.metadata.exportedBy).toBe('user123');
      expect(bundle.tasks).toHaveLength(2);
      expect(bundle.canvasReference).toBe('canvas-123');
      expect(bundle.notes).toBe('Test notes');
    });

    it('should export spec bundle as JSON', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task', priority: 'high', required: true });
      
      const json = exportSpecBundleJSON(state);
      const parsed = JSON.parse(json);
      
      expect(parsed.metadata.title).toBe('Test');
      expect(parsed.tasks).toHaveLength(1);
    });

    it('should import spec bundle', () => {
      const bundle = exportSpecBundle(
        (() => {
          let state = createWorkflowState({ title: 'Original' });
          state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
          state = createTask(state, { title: 'Task 2', priority: 'medium', required: false });
          state = changeStage(state, 'review');
          return state;
        })()
      );
      
      const imported = importSpecBundle(bundle);
      
      expect(imported.metadata.title).toBe('Original');
      expect(imported.stage).toBe('review');
      expect(getAllTasks(imported)).toHaveLength(2);
    });
  });

  describe('Task Search and Filtering', () => {
    it('should search tasks by title', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Design mockups', priority: 'high', required: true });
      state = createTask(state, { title: 'Implement feature', priority: 'medium', required: false });
      state = createTask(state, { title: 'Review design', priority: 'low', required: false });
      
      const results = searchTasks(state, 'design');
      
      expect(results).toHaveLength(2);
      expect(results.some((t) => t.title === 'Design mockups')).toBe(true);
      expect(results.some((t) => t.title === 'Review design')).toBe(true);
    });

    it('should search tasks by description', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, {
        title: 'Task 1',
        description: 'Important task with special requirements',
        priority: 'high',
        required: true,
      });
      state = createTask(state, {
        title: 'Task 2',
        description: 'Regular task',
        priority: 'medium',
        required: false,
      });
      
      const results = searchTasks(state, 'special');
      
      expect(results).toHaveLength(1);
      expect(results[0].title).toBe('Task 1');
    });

    it('should filter tasks by status', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      state = createTask(state, { title: 'Task 2', priority: 'medium', required: false });
      
      const taskId = state.taskOrder[0];
      state = completeTask(state, taskId);
      
      const completed = filterTasksByStatus(state, 'completed');
      const pending = filterTasksByStatus(state, 'pending');
      
      expect(completed).toHaveLength(1);
      expect(pending).toHaveLength(1);
    });

    it('should filter tasks by priority', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Critical', priority: 'critical', required: true });
      state = createTask(state, { title: 'High', priority: 'high', required: true });
      state = createTask(state, { title: 'Low', priority: 'low', required: false });
      
      const critical = filterTasksByPriority(state, 'critical');
      const low = filterTasksByPriority(state, 'low');
      
      expect(critical).toHaveLength(1);
      expect(critical[0].title).toBe('Critical');
      expect(low).toHaveLength(1);
    });

    it('should get blocked tasks', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      const task1Id = state.taskOrder[0];
      
      state = createTask(state, {
        title: 'Task 2',
        priority: 'medium',
        required: false,
        dependsOn: [task1Id],
      });
      
      const blocked = getBlockedTasks(state);
      
      expect(blocked).toHaveLength(1);
      expect(blocked[0].title).toBe('Task 2');
    });

    it('should get ready tasks', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      const task1Id = state.taskOrder[0];
      
      state = createTask(state, {
        title: 'Task 2',
        priority: 'medium',
        required: false,
        dependsOn: [task1Id],
      });
      state = createTask(state, { title: 'Task 3', priority: 'low', required: false });
      
      let ready = getReadyTasks(state);
      expect(ready).toHaveLength(2); // Task 1 and Task 3
      
      state = completeTask(state, task1Id);
      ready = getReadyTasks(state);
      expect(ready).toHaveLength(2); // Task 2 and Task 3
    });
  });

  describe('Templates', () => {
    it('should provide basic-design template', () => {
      const template = CHECKLIST_TEMPLATES['basic-design'];
      
      expect(template).toBeDefined();
      expect(template.tasks).toHaveLength(5);
      expect(template.tasks.every((t) => t.required)).toBe(true);
    });

    it('should provide feature-spec template', () => {
      const template = CHECKLIST_TEMPLATES['feature-spec'];
      
      expect(template).toBeDefined();
      expect(template.tasks).toHaveLength(6);
      expect(template.tasks.some((t) => t.priority === 'critical')).toBe(true);
    });

    it('should apply template on creation', () => {
      const state = createWorkflowState({
        title: 'Test',
        template: CHECKLIST_TEMPLATES['feature-spec'],
      });
      
      expect(getAllTasks(state)).toHaveLength(6);
      expect(getAllTasks(state)[0].title).toBe('Define feature requirements');
    });
  });

  describe('Dependencies', () => {
    it('should respect task dependencies', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      state = createTask(state, { title: 'Task 2', priority: 'high', required: true });
      const [task1Id, task2Id] = state.taskOrder;
      
      state = createTask(state, {
        title: 'Task 3',
        priority: 'medium',
        required: false,
        dependsOn: [task1Id, task2Id],
      });
      const task3Id = state.taskOrder[2];
      
      // Cannot complete Task 3 yet
      state = completeTask(state, task3Id);
      expect(getTask(state, task3Id)?.status).toBe('pending');
      
      // Complete Task 1
      state = completeTask(state, task1Id);
      state = completeTask(state, task3Id);
      expect(getTask(state, task3Id)?.status).toBe('pending');
      
      // Complete Task 2, now Task 3 can be completed
      state = completeTask(state, task2Id);
      state = completeTask(state, task3Id);
      expect(getTask(state, task3Id)?.status).toBe('completed');
    });

    it('should identify blocked tasks correctly', () => {
      let state = createWorkflowState({ title: 'Test' });
      state = createTask(state, { title: 'Task 1', priority: 'high', required: true });
      state = createTask(state, { title: 'Task 2', priority: 'high', required: true });
      const [task1Id, task2Id] = state.taskOrder;
      
      state = createTask(state, {
        title: 'Dependent',
        priority: 'medium',
        required: false,
        dependsOn: [task1Id],
      });
      state = createTask(state, {
        title: 'Independent',
        priority: 'low',
        required: false,
      });
      
      const blocked = getBlockedTasks(state);
      expect(blocked).toHaveLength(1);
      expect(blocked[0].title).toBe('Dependent');
      
      state = completeTask(state, task1Id);
      const stillBlocked = getBlockedTasks(state);
      expect(stillBlocked).toHaveLength(0);
    });
  });
});
