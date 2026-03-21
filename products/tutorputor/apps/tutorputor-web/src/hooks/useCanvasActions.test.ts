import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { Provider } from 'jotai';
import { useCanvasActions } from './useCanvasActions';
import type { Node, Edge } from '@xyflow/react';
import React from 'react';

const wrapper = ({ children }: { children: React.ReactNode }) => (
  React.createElement(Provider, {}, children)
);

describe('useCanvasActions', () => {
  it('should select a node', () => {
    const { result } = renderHook(() => useCanvasActions(), { wrapper });

    act(() => {
      result.current.selectNode('node-1');
    });

    expect(result.current.selectedNodes).toContain('node-1');
  });

  it('should deselect a node', () => {
    const { result } = renderHook(() => useCanvasActions(), { wrapper });

    act(() => {
      result.current.selectNode('node-1');
      result.current.deselectNode('node-1');
    });

    expect(result.current.selectedNodes).not.toContain('node-1');
  });

  it('should clear selection', () => {
    const { result } = renderHook(() => useCanvasActions(), { wrapper });

    act(() => {
      result.current.selectNode('node-1');
      result.current.selectNode('node-2');
      result.current.clearSelection();
    });

    expect(result.current.selectedNodes).toHaveLength(0);
  });

  it('should add a node', () => {
    const { result } = renderHook(() => useCanvasActions(), { wrapper });

    const newNode: Node = {
      id: 'node-1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: { label: 'Test Node' },
    };

    act(() => {
      result.current.addNode(newNode);
    });

    expect(result.current.nodes).toHaveLength(1);
    expect(result.current.nodes[0].id).toBe('node-1');
  });

  it('should remove a node', () => {
    const { result } = renderHook(() => useCanvasActions(), { wrapper });

    const node: Node = {
      id: 'node-1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: { label: 'Test Node' },
    };

    act(() => {
      result.current.addNode(node);
      result.current.removeNode('node-1');
    });

    expect(result.current.nodes).toHaveLength(0);
  });

  it('should update a node', () => {
    const { result } = renderHook(() => useCanvasActions(), { wrapper });

    const node: Node = {
      id: 'node-1',
      type: 'default',
      position: { x: 100, y: 100 },
      data: { label: 'Test Node' },
    };

    act(() => {
      result.current.addNode(node);
      result.current.updateNode('node-1', { data: { label: 'Updated Node' } });
    });

    expect(result.current.nodes[0].data.label).toBe('Updated Node');
  });
});
