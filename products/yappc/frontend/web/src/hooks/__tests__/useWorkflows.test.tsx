/**
 * useWorkflows Hook Tests
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useWorkflows } from '../useWorkflows';

describe('useWorkflows', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('starts in loading state with empty workflows', () => {
    const { result } = renderHook(() => useWorkflows());

    expect(result.current.isLoading).toBe(true);
    expect(result.current.workflows).toEqual([]);
    expect(result.current.error).toBeNull();
  });

  it('loads workflows after fetch resolves', async () => {
    const { result } = renderHook(() => useWorkflows());

    await act(async () => {
      await vi.runAllTimersAsync();
    });

    expect(result.current.isLoading).toBe(false);
    expect(result.current.workflows.length).toBeGreaterThan(0);
    expect(result.current.error).toBeNull();
  });

  it('loaded workflows have required shape', async () => {
    const { result } = renderHook(() => useWorkflows());

    await act(async () => {
      await vi.runAllTimersAsync();
    });

    const first = result.current.workflows[0];
    expect(first).toHaveProperty('id');
    expect(first).toHaveProperty('name');
    expect(first).toHaveProperty('type');
    expect(first).toHaveProperty('status');
    expect(first).toHaveProperty('taskCount');
  });

  it('exposes createWorkflow, updateWorkflow, deleteWorkflow, refetch functions', async () => {
    const { result } = renderHook(() => useWorkflows());

    await act(async () => {
      await vi.runAllTimersAsync();
    });

    expect(typeof result.current.createWorkflow).toBe('function');
    expect(typeof result.current.updateWorkflow).toBe('function');
    expect(typeof result.current.deleteWorkflow).toBe('function');
    expect(typeof result.current.refetch).toBe('function');
  });

  it('createWorkflow adds a workflow to the list', async () => {
    const { result } = renderHook(() => useWorkflows());

    await act(async () => {
      await vi.runAllTimersAsync();
    });

    const initialCount = result.current.workflows.length;

    await act(async () => {
      await result.current.createWorkflow({
        name: 'New Flow',
        description: 'Test description',
        type: 'custom',
        status: 'draft',
        taskCount: 0,
      });
    });

    expect(result.current.workflows.length).toBe(initialCount + 1);
    expect(result.current.workflows.at(-1)?.name).toBe('New Flow');
  });

  it('updateWorkflow updates an existing workflow by id', async () => {
    const { result } = renderHook(() => useWorkflows());

    await act(async () => {
      await vi.runAllTimersAsync();
    });

    const firstId = result.current.workflows[0].id;

    await act(async () => {
      await result.current.updateWorkflow(firstId, { name: 'Renamed Flow' });
    });

    const updated = result.current.workflows.find((w) => w.id === firstId);
    expect(updated?.name).toBe('Renamed Flow');
  });

  it('deleteWorkflow removes the workflow from the list', async () => {
    const { result } = renderHook(() => useWorkflows());

    await act(async () => {
      await vi.runAllTimersAsync();
    });

    const firstId = result.current.workflows[0].id;
    const countBefore = result.current.workflows.length;

    await act(async () => {
      await result.current.deleteWorkflow(firstId);
    });

    expect(result.current.workflows.length).toBe(countBefore - 1);
    expect(result.current.workflows.find((w) => w.id === firstId)).toBeUndefined();
  });

  it('refetch re-loads workflows', async () => {
    const { result } = renderHook(() => useWorkflows());

    await act(async () => {
      await vi.runAllTimersAsync();
    });

    expect(result.current.isLoading).toBe(false);

    await act(async () => {
      void result.current.refetch();
      await vi.runAllTimersAsync();
    });

    expect(result.current.isLoading).toBe(false);
    expect(result.current.workflows.length).toBeGreaterThan(0);
  });
});
