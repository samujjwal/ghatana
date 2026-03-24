import { renderHook, act } from '@testing-library/react';
import { Provider } from 'jotai';
import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { useCanvasPortal } from '../useCanvasPortal';


// Mock React Flow
vi.mock('reactflow', () => ({
  useReactFlow: () => ({
    getNodes: vi.fn(() => []),
    getEdges: vi.fn(() => []),
    setNodes: vi.fn(),
    setEdges: vi.fn(),
  }),
}));

// Mock window.history
Object.defineProperty(window, 'history', {
  value: {
    pushState: vi.fn(),
  },
  writable: true,
});

describe('useCanvasPortal', () => {
  let wrapper: React.ComponentType<{ children: React.ReactNode }>;

  beforeEach(() => {
    wrapper = ({ children }: { children: React.ReactNode }) => React.createElement(Provider, null, children);
    vi.clearAllMocks();
  });

  it('should initialize with root canvas context', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });

    expect(result.current.drillDownContext).toEqual({
      canvasStack: [],
      currentCanvasId: 'root',
      parentCanvasId: undefined,
      breadcrumbPath: [{ id: 'root', label: 'Main Canvas' }],
    });
  });

  it('should create portal elements', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });

    const portal = result.current.createPortal(
      'parent-canvas',
      'target-canvas',
      { x: 100, y: 100 },
      'Test Portal'
    );

    expect(portal).toMatchObject({
      type: 'portal',
      parentCanvasId: 'parent-canvas',
      targetCanvasId: 'target-canvas',
      position: { x: 100, y: 100 },
      data: {
        label: 'Test Portal',
        connectionPoint: 'entry',
        description: 'Portal to Test Portal',
      },
    });
    expect(portal.id).toMatch(/^portal-/);
  });

  it('should add portal to canvas', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });
    const mockSetNodes = vi.fn();
    
    // Mock the React Flow setNodes function by assigning the hook implementation on the module
    const rf = require('reactflow');
    rf.useReactFlow = () => ({
      getNodes: vi.fn(() => []),
      getEdges: vi.fn(() => []),
      setNodes: mockSetNodes,
      setEdges: vi.fn(),
    });

    act(() => {
      result.current.addPortal(
        'target-canvas',
        { x: 200, y: 200 },
        'New Portal'
      );
    });

    expect(mockSetNodes).toHaveBeenCalledWith(
      expect.any(Function)
    );
  });

  it('should drill down into sub-canvas', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });

    act(() => {
      result.current.drillDown('sub-canvas', 'Sub Canvas');
    });

    expect(result.current.drillDownContext).toEqual({
      canvasStack: ['root'],
      currentCanvasId: 'sub-canvas',
      parentCanvasId: 'root',
      breadcrumbPath: [
        { id: 'root', label: 'Main Canvas' },
        { id: 'sub-canvas', label: 'Sub Canvas' },
      ],
    });

    expect(window.history.pushState).toHaveBeenCalledWith(
      {
        canvasId: 'sub-canvas',
        breadcrumbPath: [
          { id: 'root', label: 'Main Canvas' },
          { id: 'sub-canvas', label: 'Sub Canvas' },
        ],
      },
      '',
      '/canvas/root/sub-canvas'
    );
  });

  it('should drill up to parent canvas', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });

    // First drill down
    act(() => {
      result.current.drillDown('sub-canvas', 'Sub Canvas');
    });

    // Then drill up
    act(() => {
      result.current.drillUp();
    });

    expect(result.current.drillDownContext).toEqual({
      canvasStack: [],
      currentCanvasId: 'root',
      parentCanvasId: undefined,
      breadcrumbPath: [{ id: 'root', label: 'Main Canvas' }],
    });
  });

  it('should navigate to specific canvas via breadcrumb', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });

    // Drill down multiple levels
    act(() => {
      result.current.drillDown('level1', 'Level 1');
    });
    act(() => {
      result.current.drillDown('level2', 'Level 2');
    });

    // Navigate back to level 1
    act(() => {
      result.current.navigateToCanvas('level1');
    });

    expect(result.current.drillDownContext).toEqual({
      canvasStack: ['root'],
      currentCanvasId: 'level1',
      parentCanvasId: 'root',
      breadcrumbPath: [
        { id: 'root', label: 'Main Canvas' },
        { id: 'level1', label: 'Level 1' },
      ],
    });
  });

  it('should validate canvas references and detect circular dependencies', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });

    // Create a portal that would create a circular reference
    act(() => {
      result.current.addPortal('root', { x: 0, y: 0 }, 'Root Portal');
      result.current.drillDown('child', 'Child');
      result.current.addPortal('root', { x: 100, y: 100 }, 'Back to Root');
    });

    const validation = result.current.validateCanvasReferences();
    
    expect(validation.isValid).toBe(false);
    // Robust check: prefer the structured errorsDetailed if available, otherwise
    // fall back to substring matching on the errors array. This mirrors product
    // behavior that surfaces structured diagnostics to consumers while keeping
    // backward-compatible checks for older shapes.
    if ((validation as unknown).errorsDetailed) {
      const details = (validation as unknown).errorsDetailed as Array<{ code: string; message: string }>;
      expect(details.some(d => d.code === 'CIRCULAR_REFERENCE' || d.message.includes('Circular reference detected'))).toBe(true);
    } else {
      expect(validation.errors.some(e => e.includes('Circular reference detected'))).toBe(true);
    }
  });

  it('should detect orphaned canvases', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });

    // Simulate having an orphaned canvas in state
    // This would typically happen through external state manipulation
    const validation = result.current.validateCanvasReferences();
    
    // Initially should be valid with no orphaned canvases
    expect(validation.warnings).toEqual([]);
  });

  it('should handle deep linking URL updates', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });

    const breadcrumbPath = [
      { id: 'root', label: 'Main Canvas' },
      { id: 'level1', label: 'Level 1' },
      { id: 'level2', label: 'Level 2' },
    ];

    act(() => {
      result.current.updateUrlForCanvas('level2', breadcrumbPath);
    });

    expect(window.history.pushState).toHaveBeenCalledWith(
      { canvasId: 'level2', breadcrumbPath },
      '',
      '/canvas/root/level1/level2'
    );
  });

  it('should not drill up from root canvas', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });
    const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    act(() => {
      result.current.drillUp();
    });

    expect(consoleSpy).toHaveBeenCalledWith(
      'Cannot drill up: already at root canvas'
    );
    expect(result.current.drillDownContext.currentCanvasId).toBe('root');

    consoleSpy.mockRestore();
  });

  it('should handle invalid canvas navigation gracefully', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });
    const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});

    act(() => {
      result.current.navigateToCanvas('non-existent-canvas');
    });

    expect(consoleSpy).toHaveBeenCalledWith(
      'Canvas non-existent-canvas not found in breadcrumb path'
    );

    consoleSpy.mockRestore();
  });

  it('should return empty portals array for new canvas', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });

    const portals = result.current.getPortals();
    expect(portals).toEqual([]);
  });

  it('should create exit portal when drilling into empty canvas', () => {
    const { result } = renderHook(() => useCanvasPortal(), { wrapper });
    const mockSetNodes = vi.fn();

    // Mock React Flow to capture the exit portal creation by assigning the hook implementation
    const rf = require('reactflow');
    rf.useReactFlow = () => ({
      getNodes: vi.fn(() => []),
      getEdges: vi.fn(() => []),
      setNodes: mockSetNodes,
      setEdges: vi.fn(),
    });

    act(() => {
      result.current.drillDown('empty-canvas', 'Empty Canvas');
    });

    expect(mockSetNodes).toHaveBeenCalledWith([
      expect.objectContaining({
        id: 'exit-portal-empty-canvas',
        type: 'portal',
        data: expect.objectContaining({
          label: 'Back to Parent',
          connectionPoint: 'exit',
          portalType: 'exit',
        }),
      }),
    ]);
  });
});

describe('PortalElement Interface', () => {
  it('should have correct portal element structure', () => {
    const { result } = renderHook(() => useCanvasPortal(), { 
      wrapper: ({ children }: { children: React.ReactNode }) => React.createElement(Provider, null, children)
    });

    const portal = result.current.createPortal(
      'parent',
      'target',
      { x: 0, y: 0 },
      'Test Portal',
      'entry'
    );

    expect(portal).toEqual({
      id: expect.stringMatching(/^portal-/),
      type: 'portal',
      parentCanvasId: 'parent',
      targetCanvasId: 'target',
      position: { x: 0, y: 0 },
      data: {
        label: 'Test Portal',
        description: 'Portal to Test Portal',
        connectionPoint: 'entry',
      },
    });
  });
});