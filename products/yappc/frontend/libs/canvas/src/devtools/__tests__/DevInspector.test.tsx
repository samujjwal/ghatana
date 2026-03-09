/**
 * @vitest-environment jsdom
 */

import { render, screen } from '@testing-library/react';
import { userEvent } from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import { DevInspector } from '../DevInspector';

import type { CanvasDocument, CanvasSelection, CanvasViewport, CanvasHistoryEntry, CanvasUIState, CanvasPerformanceMetrics, CanvasNode } from '../../types/canvas-document';

// Mock Jotai atoms and state
vi.mock('jotai', async (importOriginal) => {
  const actual = await importOriginal<typeof import('jotai')>();
  return {
    ...actual,
    useAtomValue: vi.fn(),
  };
});

// Import atoms so we can reference them
import {
  canvasDocumentAtom,
  canvasSelectionAtom,
  canvasViewportAtom,
  canvasHistoryAtom,
  canvasUIStateAtom,
  canvasPerformanceAtom,
} from '../../state';

import { useAtomValue } from 'jotai';
const mockUseAtomValue = useAtomValue as ReturnType<typeof vi.fn>;

describe('DevInspector', () => {
  const mockNode: CanvasNode = {
    id: 'node-1',
    type: 'node',
    nodeType: 'basic',
    transform: { position: { x: 100, y: 100 }, scale: 1, rotation: 0 },
    bounds: { x: 100, y: 100, width: 100, height: 50 },
    visible: true,
    locked: false,
    selected: false,
    zIndex: 1,
    metadata: {},
    version: '1.0.0',
    createdAt: new Date('2024-01-01'),
    updatedAt: new Date('2024-01-02'),
    data: { label: 'Test Node' },
    inputs: [],
    outputs: [],
    style: {},
  };

  const mockDocument: CanvasDocument = {
    version: '1.0.0',
    id: 'doc-1',
    title: 'Test Document',
    description: 'A test canvas document',
    viewport: {
      center: { x: 0, y: 0 },
      zoom: 1,
    },
    elements: {
      'node-1': mockNode,
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
      allowedElementTypes: ['node', 'edge', 'group'],
    },
    createdAt: new Date('2024-01-01'),
    updatedAt: new Date('2024-01-02'),
  };

  const mockSelection: CanvasSelection = {
    selectedIds: ['node-1'],
    focusedId: 'node-1',
    hoveredId: undefined,
  };

  const mockViewport: CanvasViewport = {
    center: { x: 0, y: 0 },
    zoom: 1,
    bounds: { x: 0, y: 0, width: 1920, height: 1080 },
  };

  const mockHistory: { entries: CanvasHistoryEntry[]; currentIndex: number } = {
    entries: [
      {
        id: 'history-1',
        timestamp: new Date('2024-01-01T10:00:00'),
        action: 'element:created',
        elementIds: ['node-1'],
        beforeState: {},
        afterState: {},
      },
    ],
    currentIndex: 0,
  };

  const mockUIState: CanvasUIState = {
    isDragging: false,
    isSelecting: false,
    isPanning: false,
    isLoading: false,
    mode: 'select',
  };

  const mockPerformance: CanvasPerformanceMetrics = {
    renderTime: 16.5,
    fps: 60,
    lastUpdate: new Date('2024-01-01T10:00:00'),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAtomValue.mockImplementation((atom: unknown) => {
      // Match atoms by reference
      if (atom === canvasDocumentAtom) return mockDocument;
      if (atom === canvasSelectionAtom) return mockSelection;
      if (atom === canvasViewportAtom) return mockViewport;
      if (atom === canvasHistoryAtom) return mockHistory;
      if (atom === canvasUIStateAtom) return mockUIState;
      if (atom === canvasPerformanceAtom) return mockPerformance;
      return null;
    });
  });

  describe('Visibility', () => {
    it('should not render when visible is false', () => {
      const { container } = render(<DevInspector visible={false} />);
      expect(container).toBeEmptyDOMElement();
    });

    it('should render when visible is true', () => {
      render(<DevInspector visible={true} />);
      expect(screen.getByText('🔍 Canvas DevTools')).toBeInTheDocument();
    });

    it('should call onVisibilityChange when close button clicked', async () => {
      const user = userEvent.setup();
      const onVisibilityChange = vi.fn();
      render(<DevInspector visible={true} onVisibilityChange={onVisibilityChange} />);

      const closeButton = screen.getByTitle('Close');
      await user.click(closeButton);

      expect(onVisibilityChange).toHaveBeenCalledWith(false);
    });
  });

  describe('Positioning', () => {
    it('should position at bottom-right by default', () => {
      const { container } = render(<DevInspector visible={true} />);
      const panel = container.firstChild as HTMLElement;
      expect(panel.style.bottom).toBe('20px');
      expect(panel.style.right).toBe('20px');
    });

    it('should position at top-left when specified', () => {
      const { container } = render(<DevInspector visible={true} position="top-left" />);
      const panel = container.firstChild as HTMLElement;
      expect(panel.style.top).toBe('20px');
      expect(panel.style.left).toBe('20px');
    });

    it('should position at top-right when specified', () => {
      const { container } = render(<DevInspector visible={true} position="top-right" />);
      const panel = container.firstChild as HTMLElement;
      expect(panel.style.top).toBe('20px');
      expect(panel.style.right).toBe('20px');
    });

    it('should position at bottom-left when specified', () => {
      const { container } = render(<DevInspector visible={true} position="bottom-left" />);
      const panel = container.firstChild as HTMLElement;
      expect(panel.style.bottom).toBe('20px');
      expect(panel.style.left).toBe('20px');
    });
  });

  describe('Collapse/Expand', () => {
    it('should collapse when collapse button clicked', async () => {
      const user = userEvent.setup();
      const { container } = render(<DevInspector visible={true} width={400} />);

      const collapseButton = screen.getByTitle('Collapse');
      await user.click(collapseButton);

      const panel = container.firstChild as HTMLElement;
      expect(panel.style.width).toBe('200px');
      expect(screen.getByText('Click ⬆ to expand')).toBeInTheDocument();
    });

    it('should expand when already collapsed', async () => {
      const user = userEvent.setup();
      const { container } = render(<DevInspector visible={true} width={400} />);

      // Collapse first
      const collapseButton = screen.getByTitle('Collapse');
      await user.click(collapseButton);

      // Then expand
      const expandButton = screen.getByTitle('Expand');
      await user.click(expandButton);

      const panel = container.firstChild as HTMLElement;
      expect(panel.style.width).toBe('400px');
      expect(screen.queryByText('Click ⬆ to expand')).not.toBeInTheDocument();
    });
  });

  describe('Tabs', () => {
    it('should render all tabs', () => {
      render(<DevInspector visible={true} />);
      // Use getAllByRole to find buttons, then check their text
      const buttons = screen.getAllByRole('button');
      const tabButtons = buttons.filter(btn => 
        btn.textContent?.match(/Document|Selection|Viewport|History|UI|Performance/)
      );
      
      expect(tabButtons.some(btn => btn.textContent?.includes('Document'))).toBe(true);
      expect(tabButtons.some(btn => btn.textContent?.includes('Selection'))).toBe(true);
      expect(tabButtons.some(btn => btn.textContent?.includes('Viewport'))).toBe(true);
      expect(tabButtons.some(btn => btn.textContent?.includes('History'))).toBe(true);
      expect(tabButtons.some(btn => btn.textContent?.includes('UI'))).toBe(true);
      expect(tabButtons.some(btn => btn.textContent?.includes('Performance'))).toBe(true);
    });

    it('should show element counts in tab labels', () => {
      render(<DevInspector visible={true} />);
      const buttons = screen.getAllByRole('button');
      const docButton = buttons.find(btn => btn.textContent?.includes('Document') && btn.textContent?.includes('('));
      const selButton = buttons.find(btn => btn.textContent?.includes('Selection') && btn.textContent?.includes('('));
      const histButton = buttons.find(btn => btn.textContent?.includes('History') && btn.textContent?.includes('('));
      
      expect(docButton?.textContent).toContain('(1)');
      expect(selButton?.textContent).toContain('(1)');
      expect(histButton?.textContent).toContain('(1)');
    });

    it('should switch to Selection tab when clicked', async () => {
      const user = userEvent.setup();
      render(<DevInspector visible={true} />);

      const buttons = screen.getAllByRole('button');
      // Find tab buttons by their content - they have element counts
      const selectionTab = buttons.find(btn => {
        const text = btn.textContent || '';
        return text.includes('Selection') && text.includes('(');
      });
      
      expect(selectionTab).toBeDefined();
      await user.click(selectionTab!);

      // After clicking, the Selection panel should show - check for unique Selection panel text
      // The Selection panel shows "Focused:" label
      const allText = document.body.textContent || '';
      expect(allText).toContain('Focused');
    });

    it('should switch to Viewport tab when clicked', async () => {
      const user = userEvent.setup();
      render(<DevInspector visible={true} />);

      const viewportTab = screen.getByText(/Viewport/);
      await user.click(viewportTab);

      expect(screen.getByText('Center')).toBeInTheDocument();
    });
  });

  describe('Document Panel', () => {
    it('should display document overview', () => {
      render(<DevInspector visible={true} />);
      expect(screen.getByText('doc-1')).toBeInTheDocument();
      expect(screen.getByText('Test Document')).toBeInTheDocument();
      expect(screen.getByText('1.0.0')).toBeInTheDocument();
    });

    it('should display element counts', () => {
      render(<DevInspector visible={true} />);
      expect(screen.getByText('1 (1N, 0E, 0G)')).toBeInTheDocument();
    });

    it('should display element list', () => {
      render(<DevInspector visible={true} />);
      expect(screen.getByText('NODE')).toBeInTheDocument();
      expect(screen.getByText('node-1')).toBeInTheDocument();
    });

    it('should handle empty document', () => {
      mockUseAtomValue.mockImplementation(() => null);
      render(<DevInspector visible={true} />);
      expect(screen.getByText('No document loaded')).toBeInTheDocument();
    });
  });

  describe('Selection Panel', () => {
    it('should display selection info', async () => {
      const user = userEvent.setup();
      render(<DevInspector visible={true} />);

      const selectionTab = screen.getByText(/Selection/);
      await user.click(selectionTab);

      expect(screen.getAllByText('node-1')[0]).toBeInTheDocument();
    });

    it('should handle empty selection', async () => {
      const user = userEvent.setup();
      mockUseAtomValue.mockImplementation((atom: unknown) => {
        if (atom === canvasSelectionAtom) return { selectedIds: [], focusedId: undefined, hoveredId: undefined };
        if (atom === canvasDocumentAtom) return mockDocument;
        return null;
      });

      render(<DevInspector visible={true} />);

      const selectionTab = screen.getByText(/Selection/);
      await user.click(selectionTab);

      expect(screen.getByText('No selection')).toBeInTheDocument();
    });
  });

  describe('Viewport Panel', () => {
    it('should display viewport info', async () => {
      const user = userEvent.setup();
      render(<DevInspector visible={true} />);

      const viewportTab = screen.getByText(/Viewport/);
      await user.click(viewportTab);

      expect(screen.getByText('100%')).toBeInTheDocument();
      expect(screen.getByText('1920')).toBeInTheDocument();
      expect(screen.getByText('1080')).toBeInTheDocument();
    });

    it('should handle null viewport', async () => {
      const user = userEvent.setup();
      mockUseAtomValue.mockImplementation((atom: unknown) => {
        if (atom === canvasViewportAtom) return null;
        if (atom === canvasDocumentAtom) return mockDocument;
        return null;
      });

      render(<DevInspector visible={true} />);

      const viewportTab = screen.getByText(/Viewport/);
      await user.click(viewportTab);

      expect(screen.getByText('No viewport state')).toBeInTheDocument();
    });
  });

  describe('History Panel', () => {
    it('should display history info', async () => {
      const user = userEvent.setup();
      render(<DevInspector visible={true} />);

      const historyTab = screen.getByText(/History/);
      await user.click(historyTab);

      expect(screen.getByText('element:created')).toBeInTheDocument();
    });

    it('should indicate current history entry', async () => {
      const user = userEvent.setup();
      render(<DevInspector visible={true} />);

      const historyTab = screen.getByText(/History/);
      await user.click(historyTab);

      // Current entry should have special styling (we check for the action text)
      expect(screen.getByText('element:created')).toBeInTheDocument();
    });

    it('should handle empty history', async () => {
      const user = userEvent.setup();
      mockUseAtomValue.mockImplementation((atom: unknown) => {
        if (atom === canvasHistoryAtom) return { entries: [], currentIndex: 0 };
        if (atom === canvasDocumentAtom) return mockDocument;
        return null;
      });

      render(<DevInspector visible={true} />);

      const historyTab = screen.getByText(/History/);
      await user.click(historyTab);

      expect(screen.getByText('No history')).toBeInTheDocument();
    });
  });

  describe('UI Panel', () => {
    it('should display UI state', async () => {
      const user = userEvent.setup();
      render(<DevInspector visible={true} />);

      const uiTab = screen.getByText(/UI/);
      await user.click(uiTab);

      expect(screen.getByText('select')).toBeInTheDocument();
    });

    it('should display error when present', async () => {
      const user = userEvent.setup();
      mockUseAtomValue.mockImplementation((atom: unknown) => {
        if (atom === canvasUIStateAtom) {
          return { ...mockUIState, error: 'Test error message' };
        }
        if (atom === canvasDocumentAtom) return mockDocument;
        return null;
      });

      render(<DevInspector visible={true} />);

      const uiTab = screen.getByText(/UI/);
      await user.click(uiTab);

      expect(screen.getByText('Test error message')).toBeInTheDocument();
    });

    it('should handle null UI state', async () => {
      const user = userEvent.setup();
      mockUseAtomValue.mockImplementation((atom: unknown) => {
        if (atom === canvasUIStateAtom) return null;
        if (atom === canvasDocumentAtom) return mockDocument;
        return null;
      });

      render(<DevInspector visible={true} />);

      const uiTab = screen.getByText(/UI/);
      await user.click(uiTab);

      expect(screen.getByText('No UI state')).toBeInTheDocument();
    });
  });

  describe('Performance Panel', () => {
    it('should display performance metrics', async () => {
      const user = userEvent.setup();
      render(<DevInspector visible={true} />);

      const perfTab = screen.getByText(/Performance/);
      await user.click(perfTab);

      expect(screen.getByText('16.50ms')).toBeInTheDocument();
      expect(screen.getByText('60')).toBeInTheDocument();
    });

    it('should handle null performance data', async () => {
      const user = userEvent.setup();
      mockUseAtomValue.mockImplementation((atom: unknown) => {
        if (atom === canvasPerformanceAtom) return null;
        if (atom === canvasDocumentAtom) return mockDocument;
        return null;
      });

      render(<DevInspector visible={true} />);

      const perfTab = screen.getByText(/Performance/);
      await user.click(perfTab);

      expect(screen.getByText('No performance data')).toBeInTheDocument();
    });
  });

  describe('Sizing', () => {
    it('should respect custom width', () => {
      const { container } = render(<DevInspector visible={true} width={500} />);
      const panel = container.firstChild as HTMLElement;
      expect(panel.style.width).toBe('500px');
    });

    it('should respect custom height', () => {
      const { container } = render(<DevInspector visible={true} height={700} />);
      const panel = container.firstChild as HTMLElement;
      expect(panel.style.height).toBe('700px');
    });
  });
});
