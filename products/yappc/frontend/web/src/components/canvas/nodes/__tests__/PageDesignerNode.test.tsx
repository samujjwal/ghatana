/**
 * PageDesignerNode Unit Tests
 *
 * @doc.type test
 * @doc.purpose Verify PageDesignerNode collapsed/expanded render and interaction
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

// --------------------------------------------------------------------------
// Mocks
// --------------------------------------------------------------------------

// @xyflow/react: mock the hooks + components used by PageDesignerNode
const mockUpdateNodeData = vi.fn();
vi.mock('@xyflow/react', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@xyflow/react')>();
  return {
    ...actual,
    useReactFlow: () => ({ updateNodeData: mockUpdateNodeData }),
    Handle: ({ id, position }: { id: string; position: string }) => (
      <div data-testid={`handle-${id}`} data-position={position} />
    ),
    NodeResizer: ({ isVisible }: { isVisible: boolean }) =>
      isVisible ? <div data-testid="node-resizer" /> : null,
  };
});

// PageDesigner: minimal stub — we test the node wrapper, not PageDesigner itself
vi.mock('@/components/canvas/page/PageDesigner', () => ({
  PageDesigner: ({ onDocumentChange }: { onDocumentChange?: (doc: unknown) => void }) => (
    <div
      data-testid="page-designer-full"
      onClick={() => onDocumentChange?.({ nodes: new Map(), rootId: 'root' })}
    />
  ),
}));

// @ghatana/design-system: pass-through for layout primitives
vi.mock('@ghatana/design-system', async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
    Box: ({ children, ...rest }: React.PropsWithChildren<React.HTMLAttributes<HTMLDivElement>>) => (
      <div {...rest}>{children}</div>
    ),
    Button: ({ children, onClick, ...rest }: React.PropsWithChildren<React.ButtonHTMLAttributes<HTMLButtonElement>>) => (
      <button onClick={onClick} {...rest}>{children}</button>
    ),
    IconButton: ({ children, onClick, 'aria-label': ariaLabel }: { children: React.ReactNode; onClick?: (e: React.MouseEvent) => void; 'aria-label'?: string }) => (
      <button onClick={onClick} aria-label={ariaLabel}>{children}</button>
    ),
    Typography: ({ children }: React.PropsWithChildren) => <span>{children}</span>,
  };
});

// --------------------------------------------------------------------------
// Subject under test
// --------------------------------------------------------------------------

import { PageDesignerNode } from '../PageDesignerNode';
import type { Node } from '@xyflow/react';
import type { PageDesignerNodeData } from '../PageDesignerNode';

// --------------------------------------------------------------------------
// Helpers
// --------------------------------------------------------------------------

function makeNodeProps(
  overrides: Partial<PageDesignerNodeData> = {},
): Parameters<typeof PageDesignerNode>[0] {
  const nodeData: PageDesignerNodeData = { label: 'Home Page', ...overrides };
  const node: Node<PageDesignerNodeData, 'page-designer'> = {
    id: 'node-1',
    type: 'page-designer',
    position: { x: 0, y: 0 },
    data: nodeData,
  };
  return {
    id: 'node-1',
    type: 'page-designer',
    selected: false,
    dragging: false,
    data: nodeData,
    zIndex: 1,
    isConnectable: true,
    positionAbsoluteX: 0,
    positionAbsoluteY: 0,
    width: 220,
    height: 90,
    // @ts-expect-error — not all ReactFlow node props needed in tests
    node,
  };
}

// --------------------------------------------------------------------------
// Tests
// --------------------------------------------------------------------------

describe('PageDesignerNode', () => {
  beforeEach(() => {
    mockUpdateNodeData.mockClear();
  });

  describe('collapsed state (default)', () => {
    it('renders the page label', () => {
      render(<PageDesignerNode {...makeNodeProps()} />);
      expect(screen.getByText('Home Page')).toBeTruthy();
    });

    it('does not render the full PageDesigner when collapsed', () => {
      render(<PageDesignerNode {...makeNodeProps()} />);
      expect(screen.queryByTestId('page-designer-full')).toBeNull();
    });

    it('shows empty-page hint when document is absent', () => {
      render(<PageDesignerNode {...makeNodeProps({ document: undefined })} />);
      expect(screen.getByText(/double-click to start designing/i)).toBeTruthy();
    });

    it('shows component count when document nodes exist', () => {
      const fakeDoc = {
        nodes: new Map([['c1', {}], ['c2', {}]]),
        rootId: 'root',
      };
      render(<PageDesignerNode {...makeNodeProps({ document: fakeDoc as never })} />);
      // Component count appears in both the header badge and the body prompt
      const matches = screen.getAllByText(/2 component/i);
      expect(matches.length).toBeGreaterThan(0);
    });

    it('renders connection handles for all four positions', () => {
      render(<PageDesignerNode {...makeNodeProps()} />);
      expect(screen.getByTestId('handle-top')).toBeTruthy();
      expect(screen.getByTestId('handle-right')).toBeTruthy();
      expect(screen.getByTestId('handle-bottom')).toBeTruthy();
      expect(screen.getByTestId('handle-left')).toBeTruthy();
    });
  });

  describe('expand / collapse', () => {
    it('expands and shows the full PageDesigner when the expand button is clicked', () => {
      render(<PageDesignerNode {...makeNodeProps()} />);
      const expandBtn = screen.getByRole('button', { name: /expand page designer/i });
      fireEvent.click(expandBtn);
      expect(screen.getByTestId('page-designer-full')).toBeTruthy();
    });

    it('calls updateNodeData with expanded: true when expanding', () => {
      render(<PageDesignerNode {...makeNodeProps()} />);
      const expandBtn = screen.getByRole('button', { name: /expand page designer/i });
      fireEvent.click(expandBtn);
      expect(mockUpdateNodeData).toHaveBeenCalledWith(
        'node-1',
        expect.objectContaining({ expanded: true }),
      );
    });

    it('collapses back when the collapse button is clicked', () => {
      render(<PageDesignerNode {...makeNodeProps({ expanded: true })} />);
      const collapseBtn = screen.getByRole('button', { name: /collapse page designer/i });
      fireEvent.click(collapseBtn);
      expect(screen.queryByTestId('page-designer-full')).toBeNull();
      expect(mockUpdateNodeData).toHaveBeenCalledWith(
        'node-1',
        expect.objectContaining({ expanded: false }),
      );
    });
  });

  describe('document persistence', () => {
    it('calls updateNodeData with the new document when PageDesigner emits onDocumentChange', () => {
      render(<PageDesignerNode {...makeNodeProps({ expanded: true })} />);
      const designer = screen.getByTestId('page-designer-full');
      // The stub calls onDocumentChange on click
      fireEvent.click(designer);
      expect(mockUpdateNodeData).toHaveBeenCalledWith(
        'node-1',
        expect.objectContaining({ document: expect.objectContaining({ rootId: 'root' }) }),
      );
    });
  });

  describe('starts expanded when data.expanded is true', () => {
    it('renders full PageDesigner immediately if node data has expanded = true', () => {
      render(<PageDesignerNode {...makeNodeProps({ expanded: true })} />);
      expect(screen.getByTestId('page-designer-full')).toBeTruthy();
    });
  });
});
