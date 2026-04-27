/**
 * useProjectTasks Hook Tests
 */

import React from 'react';
import { createStore, Provider } from 'jotai';
import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useProjectTasks } from '../useProjectTasks';
import { LifecyclePhase } from '../../types/lifecycle';

function createWrapper(): React.ComponentType<{ children: React.ReactNode }> {
  // Fresh Jotai store per test to isolate state
  const store = createStore();
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <Provider store={store}>{children}</Provider>;
  };
}

describe('useProjectTasks', () => {
  let wrapper: React.ComponentType<{ children: React.ReactNode }>;

  beforeEach(() => {
    localStorage.clear();
    wrapper = createWrapper();
  });

  it('returns empty tasks array initially', () => {
    const { result } = renderHook(() => useProjectTasks('proj-1'), { wrapper });

    expect(result.current.tasks).toEqual([]);
    expect(result.current.isLoading).toBe(false);
  });

  it('exposes required functions', () => {
    const { result } = renderHook(() => useProjectTasks('proj-1'), { wrapper });

    expect(typeof result.current.createTask).toBe('function');
    expect(typeof result.current.updateTask).toBe('function');
    expect(typeof result.current.deleteTask).toBe('function');
    expect(typeof result.current.getTasksByPhase).toBe('function');
    expect(typeof result.current.getTasksByStatus).toBe('function');
    expect(typeof result.current.getTask).toBe('function');
  });

  it('createTask adds a task to the list', () => {
    const { result } = renderHook(() => useProjectTasks('proj-1'), { wrapper });

    act(() => {
      result.current.createTask({
        name: 'Test Task',
        phase: LifecyclePhase.INTENT,
        priority: 'medium',
      });
    });

    expect(result.current.tasks.length).toBe(1);
    expect(result.current.tasks[0].name).toBe('Test Task');
    expect(result.current.tasks[0].phase).toBe(LifecyclePhase.INTENT);
  });

  it('getTasksByPhase returns only tasks for that phase', () => {
    const { result } = renderHook(() => useProjectTasks('proj-1'), { wrapper });

    act(() => {
      result.current.createTask({
        name: 'Intent Task',
        phase: LifecyclePhase.INTENT,
      });
      result.current.createTask({
        name: 'Context Task',
        phase: LifecyclePhase.CONTEXT,
      });
    });

    const intentTasks = result.current.getTasksByPhase(LifecyclePhase.INTENT);
    expect(intentTasks.length).toBe(1);
    expect(intentTasks[0].name).toBe('Intent Task');
  });

  it('updateTask updates task properties by id', () => {
    const { result } = renderHook(() => useProjectTasks('proj-1'), { wrapper });

    act(() => {
      result.current.createTask({ name: 'Initial Name', phase: LifecyclePhase.INTENT });
    });

    const taskId = result.current.tasks[0].id;

    act(() => {
      result.current.updateTask(taskId, { name: 'Updated Name' });
    });

    expect(result.current.tasks[0].name).toBe('Updated Name');
  });

  it('deleteTask removes a task by id', () => {
    const { result } = renderHook(() => useProjectTasks('proj-1'), { wrapper });

    act(() => {
      result.current.createTask({ name: 'To Delete', phase: LifecyclePhase.INTENT });
    });

    const taskId = result.current.tasks[0].id;

    act(() => {
      result.current.deleteTask(taskId);
    });

    expect(result.current.tasks).toEqual([]);
  });

  it('setTaskStatus changes a task status', () => {
    const { result } = renderHook(() => useProjectTasks('proj-1'), { wrapper });

    act(() => {
      result.current.createTask({ name: 'Task', phase: LifecyclePhase.INTENT });
    });

    const taskId = result.current.tasks[0].id;

    act(() => {
      result.current.setTaskStatus(taskId, 'completed');
    });

    expect(result.current.tasks[0].status).toBe('completed');
  });

  it('tasks are isolated per projectId', () => {
    const { result: r1 } = renderHook(() => useProjectTasks('proj-A'), { wrapper });
    const { result: r2 } = renderHook(() => useProjectTasks('proj-B'), { wrapper });

    act(() => {
      r1.current.createTask({ name: 'Alpha Task', phase: LifecyclePhase.INTENT });
    });

    expect(r1.current.tasks.length).toBe(1);
    expect(r2.current.tasks.length).toBe(0);
  });

  it('getTasksByStatus filters correctly', () => {
    const { result } = renderHook(() => useProjectTasks('proj-1'), { wrapper });

    act(() => {
      result.current.createTask({ name: 'T1', phase: LifecyclePhase.INTENT });
      result.current.createTask({ name: 'T2', phase: LifecyclePhase.INTENT });
    });

    const t1Id = result.current.tasks[0].id;

    act(() => {
      result.current.setTaskStatus(t1Id, 'completed');
    });

    const completed = result.current.getTasksByStatus('completed');
    const pending = result.current.getTasksByStatus('pending');
    expect(completed.length).toBe(1);
    expect(pending.length).toBe(1);
  });
});
