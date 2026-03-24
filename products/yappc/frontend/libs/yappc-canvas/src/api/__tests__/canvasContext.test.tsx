/**
 * Canvas Context API Tests
 *
 * Tests for CanvasProvider, useCanvas hook, and CanvasFlow component.
 * Covers controlled/uncontrolled modes, event callbacks, and API methods.
 */

import { render, screen, waitFor, renderHook, act } from '@testing-library/react';
import React, { useState } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

import {
  CanvasProvider,
  useCanvas,
  CanvasFlow,
  type CanvasChangeEvent,
  type CanvasSelectionEvent,
  type CanvasViewportEvent,
} from '../canvasContext';

import type { CanvasDocument, CanvasElement } from '../../types/canvas-document';

describe('CanvasProvider', () => {
  const mockDocument: CanvasDocument = {
    id: 'test-doc',
    version: '1.0.0',
    title: 'Test Document',
    viewport: {
      center: { x: 0, y: 0 },
      zoom: 1.0,
    },
    elements: {
      'node-1': {
        id: 'node-1',
        type: 'node',
        transform: { position: { x: 100, y: 100 }, scale: 1, rotation: 0 },
        bounds: { x: 100, y: 100, width: 100, height: 80 },
        visible: true,
        locked: false,
        selected: false,
        zIndex: 1,
        metadata: {},
        version: '1.0.0',
        createdAt: new Date(),
        updatedAt: new Date(),
      } as CanvasElement,
    },
    elementOrder: ['node-1'],
    metadata: {},
    capabilities: {
      canEdit: true,
      canZoom: true,
      canPan: true,
      canSelect: true,
      canUndo: true,
      canRedo: true,
      canExport: true,
      canImport: true,
      canCollaborate: false,
      canPersist: true,
      allowedElementTypes: ['node', 'edge'],
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  describe('Uncontrolled Mode', () => {
    it('should provide context value', () => {
      const TestComponent = () => {
        const { document } = useCanvas();
        return <div data-testid="doc-title">{document.title}</div>;
      };

      render(
        <CanvasProvider initialDocument={mockDocument}>
          <TestComponent />
        </CanvasProvider>
      );

      expect(screen.getByTestId('doc-title')).toHaveTextContent('Test Document');
    });

    it('should initialize with initial document', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      expect(result.current.document.id).toBe('test-doc');
      expect(result.current.document.title).toBe('Test Document');
      expect(result.current.isControlled).toBe(false);
    });

    it('should manage internal state', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      act(() => {
        result.current.api.selectElement('node-1');
      });

      expect(result.current.selection.selectedIds).toEqual(['node-1']);
    });

    it('should add element to internal state', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      let elementId: string = '';

      act(() => {
        elementId = result.current.api.addElement({
          type: 'node',
          transform: { position: { x: 200, y: 200 }, scale: 1, rotation: 0 },
          bounds: { x: 200, y: 200, width: 100, height: 80 },
        } as Partial<CanvasElement>);
      });

      expect(elementId).toBeTruthy();
      // Note: In uncontrolled mode, atom state is managed internally
    });

    it('should clear selection', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      act(() => {
        result.current.api.selectElement('node-1');
      });

      expect(result.current.selection.selectedIds).toEqual(['node-1']);

      act(() => {
        result.current.api.clearSelection();
      });

      expect(result.current.selection.selectedIds).toEqual([]);
    });
  });

  describe('Controlled Mode', () => {
    it('should use external document', () => {
      const TestWrapper = () => {
        const [doc, setDoc] = useState(mockDocument);

        return (
          <CanvasProvider document={doc} onDocumentChange={(e) => setDoc(e.document)}>
            <TestComponent />
          </CanvasProvider>
        );
      };

      const TestComponent = () => {
        const { document, isControlled } = useCanvas();
        return (
          <>
            <div data-testid="doc-title">{document.title}</div>
            <div data-testid="is-controlled">{String(isControlled)}</div>
          </>
        );
      };

      render(<TestWrapper />);

      expect(screen.getByTestId('doc-title')).toHaveTextContent('Test Document');
      expect(screen.getByTestId('is-controlled')).toHaveTextContent('true');
    });

    it('should call onDocumentChange when document changes', async () => {
      const onDocumentChange = vi.fn();
      
      const TestWrapper = () => {
        const [doc, setDoc] = useState(mockDocument);

        const handleChange = (e: CanvasChangeEvent) => {
          setDoc(e.document);
          onDocumentChange(e);
        };

        return (
          <CanvasProvider document={doc} onDocumentChange={handleChange}>
            <TestComponent />
          </CanvasProvider>
        );
      };

      const TestComponent = () => {
        const { api } = useCanvas();
        return (
          <button onClick={() => api.addElement({ type: 'node' } as Partial<CanvasElement>)}>
            Add
          </button>
        );
      };

      render(<TestWrapper />);

      const button = screen.getByRole('button');
      act(() => {
        button.click();
      });

      await waitFor(() => {
        expect(onDocumentChange).toHaveBeenCalled();
      });
    });

    it('should use external selected IDs', () => {
      const TestWrapper = () => {
        const [selectedIds, setSelectedIds] = useState<readonly string[]>(['node-1']);

        return (
          <CanvasProvider
            document={mockDocument}
            selectedIds={selectedIds}
            onSelectionChange={(e) => setSelectedIds(e.selectedIds)}
          >
            <TestComponent />
          </CanvasProvider>
        );
      };

      const TestComponent = () => {
        const { selection } = useCanvas();
        return <div data-testid="selection">{selection.selectedIds.join(',')}</div>;
      };

      render(<TestWrapper />);

      expect(screen.getByTestId('selection')).toHaveTextContent('node-1');
    });

    it('should call onSelectionChange when selection changes', async () => {
      const onSelectionChange = vi.fn();

      const TestWrapper = () => {
        const [selectedIds, setSelectedIds] = useState<readonly string[]>([]);

        const handleSelectionChange = (e: CanvasSelectionEvent) => {
          setSelectedIds(e.selectedIds);
          onSelectionChange(e);
        };

        return (
          <CanvasProvider
            document={mockDocument}
            selectedIds={selectedIds}
            onSelectionChange={handleSelectionChange}
          >
            <TestComponent />
          </CanvasProvider>
        );
      };

      const TestComponent = () => {
        const { api } = useCanvas();
        return (
          <button onClick={() => api.selectElement('node-1')}>Select</button>
        );
      };

      render(<TestWrapper />);

      const button = screen.getByRole('button');
      act(() => {
        button.click();
      });

      await waitFor(() => {
        expect(onSelectionChange).toHaveBeenCalledWith(
          expect.objectContaining({
            selectedIds: ['node-1'],
            previousIds: [],
          })
        );
      });
    });

    it('should call onViewportChange when viewport changes', async () => {
      const onViewportChange = vi.fn();

      const TestWrapper = () => {
        const [viewport, setViewport] = useState({
          ...mockDocument.viewport,
          bounds: { x: 0, y: 0, width: 1200, height: 800 },
        });

        const handleViewportChange = (e: CanvasViewportEvent) => {
          setViewport(e.viewport);
          onViewportChange(e);
        };

        return (
          <CanvasProvider
            document={mockDocument}
            viewport={viewport}
            onViewportChange={handleViewportChange}
          >
            <TestComponent />
          </CanvasProvider>
        );
      };

      const TestComponent = () => {
        const { api } = useCanvas();
        return (
          <button onClick={() => api.zoomViewport(0.5)}>Zoom</button>
        );
      };

      render(<TestWrapper />);

      const button = screen.getByRole('button');
      act(() => {
        button.click();
      });

      await waitFor(() => {
        expect(onViewportChange).toHaveBeenCalledWith(
          expect.objectContaining({
            viewport: expect.objectContaining({
              zoom: 1.5,
            }),
          })
        );
      });
    });
  });

  describe('API Methods', () => {
    it('should add element and return ID', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      let elementId: string = '';

      act(() => {
        elementId = result.current.api.addElement({
          type: 'node',
          transform: { position: { x: 300, y: 300 }, scale: 1, rotation: 0 },
        } as Partial<CanvasElement>);
      });

      expect(elementId).toMatch(/^element-/);
    });

    it('should get element by ID', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      const element = result.current.api.getElement('node-1');
      expect(element).toBeDefined();
      expect(element?.id).toBe('node-1');
    });

    it('should get all elements', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      const elements = result.current.api.getAllElements();
      expect(elements).toHaveLength(1);
      expect(elements[0].id).toBe('node-1');
    });

    it('should select multiple elements', () => {
      const docWithMultiple: CanvasDocument = {
        ...mockDocument,
        elements: {
          ...mockDocument.elements,
          'node-2': {
            ...mockDocument.elements['node-1'],
            id: 'node-2',
          } as CanvasElement,
        },
        elementOrder: ['node-1', 'node-2'],
      };

      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={docWithMultiple}>{children}</CanvasProvider>
        ),
      });

      act(() => {
        result.current.api.selectElements(['node-1', 'node-2']);
      });

      expect(result.current.selection.selectedIds).toEqual(['node-1', 'node-2']);
    });

    it('should append to selection', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      act(() => {
        result.current.api.selectElement('node-1');
      });

      expect(result.current.selection.selectedIds).toEqual(['node-1']);

      act(() => {
        result.current.api.selectElement('node-2', true);
      });

      expect(result.current.selection.selectedIds).toEqual(['node-1', 'node-2']);
    });

    it('should get current selection', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      act(() => {
        result.current.api.selectElement('node-1');
      });

      const selection = result.current.api.getSelection();
      expect(selection).toEqual(['node-1']);
    });

    it('should pan viewport', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      act(() => {
        result.current.api.panViewport({ x: 50, y: 100 });
      });

      const viewport = result.current.api.getViewport();
      expect(viewport.center.x).toBe(50);
      expect(viewport.center.y).toBe(100);
    });

    it('should zoom viewport', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      act(() => {
        result.current.api.zoomViewport(0.5);
      });

      const viewport = result.current.api.getViewport();
      expect(viewport.zoom).toBe(1.5);
    });

    it('should clamp zoom within limits', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      act(() => {
        result.current.api.zoomViewport(10); // Exceeds max
      });

      const viewport = result.current.api.getViewport();
      expect(viewport.zoom).toBe(5.0); // Clamped to max
    });

    it('should fit elements to screen', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      act(() => {
        result.current.api.fitToScreen();
      });

      const viewport = result.current.api.getViewport();
      expect(viewport.center).toBeDefined();
      expect(viewport.zoom).toBeLessThanOrEqual(1.0);
    });

    it('should convert screen to canvas coordinates', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      const canvasPoint = result.current.api.screenToCanvas({ x: 600, y: 400 });
      expect(canvasPoint).toHaveProperty('x');
      expect(canvasPoint).toHaveProperty('y');
    });

    it('should convert canvas to screen coordinates', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      const screenPoint = result.current.api.canvasToScreen({ x: 100, y: 100 });
      expect(screenPoint).toHaveProperty('x');
      expect(screenPoint).toHaveProperty('y');
    });

    it('should get current document', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      const doc = result.current.api.getDocument();
      expect(doc.id).toBe('test-doc');
    });

    it('should get UI state', () => {
      const { result } = renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument}>{children}</CanvasProvider>
        ),
      });

      const uiState = result.current.api.getUIState();
      expect(uiState).toHaveProperty('mode');
      expect(uiState).toHaveProperty('isDragging');
    });
  });

  describe('Error Handling', () => {
    it('should throw error when useCanvas used outside provider', () => {
      const TestComponent = () => {
        useCanvas(); // Should throw
        return null;
      };

      // Suppress console.error for this test
      const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {});

      expect(() => render(<TestComponent />)).toThrow(
        'useCanvas must be used within a CanvasProvider'
      );

      consoleError.mockRestore();
    });
  });

  describe('Change Tracking', () => {
    it('should track element additions', async () => {
      const onDocumentChange = vi.fn();

      const TestWrapper = () => {
        const [doc, setDoc] = useState(mockDocument);

        const handleChange = (e: CanvasChangeEvent) => {
          setDoc(e.document);
          onDocumentChange(e);
        };

        return (
          <CanvasProvider document={doc} onDocumentChange={handleChange}>
            <TestComponent />
          </CanvasProvider>
        );
      };

      const TestComponent = () => {
        const { api } = useCanvas();
        return (
          <button onClick={() => api.addElement({ type: 'node' } as Partial<CanvasElement>)}>
            Add
          </button>
        );
      };

      render(<TestWrapper />);

      const button = screen.getByRole('button');
      act(() => {
        button.click();
      });

      await waitFor(() => {
        expect(onDocumentChange).toHaveBeenCalledWith(
          expect.objectContaining({
            changes: expect.arrayContaining([
              expect.objectContaining({
                type: 'element-added',
              }),
            ]),
          })
        );
      });
    });

    it('should disable change tracking when configured', async () => {
      const onDocumentChange = vi.fn();

      const TestWrapper = () => {
        const [doc, setDoc] = useState(mockDocument);

        return (
          <CanvasProvider
            document={doc}
            onDocumentChange={onDocumentChange}
            disableChangeTracking
          >
            <TestComponent />
          </CanvasProvider>
        );
      };

      const TestComponent = () => {
        const { api } = useCanvas();
        return (
          <button onClick={() => api.addElement({ type: 'node' } as Partial<CanvasElement>)}>
            Add
          </button>
        );
      };

      render(<TestWrapper />);

      const button = screen.getByRole('button');
      act(() => {
        button.click();
      });

      // Should not be called because change tracking is disabled
      expect(onDocumentChange).not.toHaveBeenCalled();
    });
  });

  describe('Debug Mode', () => {
    it('should log debug messages when enabled', () => {
      const consoleSpy = vi.spyOn(console, 'log').mockImplementation(() => {});

      renderHook(() => useCanvas(), {
        wrapper: ({ children }) => (
          <CanvasProvider initialDocument={mockDocument} debug>
            {children}
          </CanvasProvider>
        ),
      });

      expect(consoleSpy).toHaveBeenCalledWith(
        expect.stringContaining('[CanvasProvider] Initialized'),
        expect.anything()
      );

      consoleSpy.mockRestore();
    });
  });
});

describe('CanvasFlow', () => {
  const mockDocument: CanvasDocument = {
    id: 'test-doc',
    version: '1.0.0',
    title: 'Test Document',
    viewport: {
      center: { x: 0, y: 0 },
      zoom: 1.0,
    },
    elements: {},
    elementOrder: [],
    metadata: {},
    capabilities: {
      canEdit: true,
      canZoom: true,
      canPan: true,
      canSelect: true,
      canUndo: true,
      canRedo: true,
      canExport: true,
      canImport: true,
      canCollaborate: false,
      canPersist: true,
      allowedElementTypes: ['node'],
    },
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  it('should render with initial document', () => {
    render(<CanvasFlow initialDocument={mockDocument} />);

    expect(screen.getByText(/Canvas Document:/)).toBeInTheDocument();
    expect(screen.getByText(/Test Document/)).toBeInTheDocument();
  });

  it('should apply custom dimensions', () => {
    render(<CanvasFlow initialDocument={mockDocument} width={800} height={600} />);

    const container = screen.getByRole('application');
    expect(container).toHaveStyle({ width: '800px', height: '600px' });
  });

  it('should apply custom class name', () => {
    render(<CanvasFlow initialDocument={mockDocument} className="custom-canvas" />);

    // The className is applied directly to the application role element (CanvasFlowInner wrapper)
    const container = screen.getByRole('application');
    expect(container).toHaveClass('custom-canvas');
  });

  it('should use custom ARIA label', () => {
    render(<CanvasFlow initialDocument={mockDocument} ariaLabel="My Custom Canvas" />);

    expect(screen.getByLabelText('My Custom Canvas')).toBeInTheDocument();
  });

  it('should handle controlled mode', () => {
    const onDocumentChange = vi.fn();

    render(
      <CanvasFlow
        document={mockDocument}
        onDocumentChange={onDocumentChange}
      />
    );

    expect(screen.getByText(/Test Document/)).toBeInTheDocument();
  });
});
