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
import { createPageArtifactDocument } from '../../page/pageArtifactDocument';

// --------------------------------------------------------------------------
// Mocks
// --------------------------------------------------------------------------

const mockExecuteCommand = vi.fn();
vi.mock('@xyflow/react', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@xyflow/react')>();
  return {
    ...actual,
    Handle: ({ id, position }: { id: string; position: string }) => (
      <div data-testid={`handle-${id}`} data-position={position} />
    ),
    NodeResizer: ({ isVisible }: { isVisible: boolean }) =>
      isVisible ? <div data-testid="node-resizer" /> : null,
  };
});

// PageDesigner: minimal stub — we test the node wrapper, not PageDesigner itself
vi.mock('@/components/canvas/page/PageDesigner', () => ({
  PageDesigner: ({
    onDocumentChange,
  }: {
    onDocumentChange?: (doc: unknown, validation: { valid: boolean; errors: unknown[]; warnings: unknown[] }) => void;
  }) => (
    <div
      data-testid="page-designer-full"
      onClick={() =>
        onDocumentChange?.(
          {
            id: 'doc-1',
            version: '1',
            name: 'Home Page',
            designSystem: {
              id: 'ds',
              name: 'DS',
              version: '1',
              tokenSetIds: [],
              componentContracts: [],
              themeId: 'default',
            },
            rootNodes: [],
            nodes: new Map(),
            metadata: {
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              trustLevel: 'GENERATED_TRUSTED',
              dataClassification: 'INTERNAL',
            },
          },
          { valid: true, errors: [], warnings: [] },
        )
      }
    />
  ),
}));

vi.mock('@/components/studio/LivePreviewPanel', () => ({
  LivePreviewPanel: () => <div data-testid="live-preview-panel" />,
}));

vi.mock('jotai', async (importOriginal) => {
  const actual = await importOriginal<typeof import('jotai')>();
  return {
    ...actual,
    useSetAtom: () => mockExecuteCommand,
  };
});

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
    mockExecuteCommand.mockClear();
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
      render(<PageDesignerNode {...makeNodeProps({ pageDocument: undefined })} />);
      expect(screen.getByText(/double-click to start designing/i)).toBeTruthy();
    });

    it('shows component count when document nodes exist', () => {
      const fakeDoc = createPageArtifactDocument({
        artifactId: 'artifact-1',
        name: 'Home Page',
        createdBy: 'tester',
        document: {
          id: 'doc-1' as never,
          version: '1',
          name: 'Home Page',
          designSystem: {
            id: 'ds',
            name: 'DS',
            version: '1',
            tokenSetIds: [],
            componentContracts: [],
            themeId: 'default',
          },
          rootNodes: ['c1' as never, 'c2' as never],
          nodes: new Map([
            ['c1' as never, { id: 'c1', contractName: 'Button', props: {}, slots: {}, bindings: [], metadata: {} }],
            ['c2' as never, { id: 'c2', contractName: 'Card', props: {}, slots: {}, bindings: [], metadata: {} }],
          ]),
          metadata: {
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            trustLevel: 'GENERATED_TRUSTED',
            dataClassification: 'INTERNAL',
          },
        },
      });
      render(<PageDesignerNode {...makeNodeProps({ pageDocument: fakeDoc })} />);
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
      expect(mockExecuteCommand).toHaveBeenCalledTimes(1);
    });

    it('collapses back when the collapse button is clicked', () => {
      render(<PageDesignerNode {...makeNodeProps({ expanded: true })} />);
      const collapseBtn = screen.getByRole('button', { name: /collapse page designer/i });
      fireEvent.click(collapseBtn);
      expect(screen.queryByTestId('page-designer-full')).toBeNull();
      expect(mockExecuteCommand).toHaveBeenCalledTimes(1);
    });
  });

  describe('document persistence', () => {
    it('dispatches a command when PageDesigner emits onDocumentChange', () => {
      const pageDocument = createPageArtifactDocument({
        artifactId: 'artifact-1',
        name: 'Home Page',
        createdBy: 'tester',
      });
      render(<PageDesignerNode {...makeNodeProps({ expanded: true, pageDocument })} />);
      const designer = screen.getByTestId('page-designer-full');
      // The stub calls onDocumentChange on click
      fireEvent.click(designer);
      expect(mockExecuteCommand).toHaveBeenCalledTimes(1);
    });
  });

  describe('starts expanded when data.expanded is true', () => {
    it('renders full PageDesigner immediately if node data has expanded = true', () => {
      render(<PageDesignerNode {...makeNodeProps({ expanded: true })} />);
      expect(screen.getByTestId('page-designer-full')).toBeTruthy();
    });
  });
});
