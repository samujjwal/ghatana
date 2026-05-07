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
const mockPersistenceSave = vi.fn().mockResolvedValue(undefined);
const mockPersistenceLoad = vi.fn().mockResolvedValue(null);
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
    onImportArtifacts,
    onAIChangeRecord,
    onAIReviewDecision,
  }: {
    onDocumentChange?: (doc: unknown, validation: { valid: boolean; errors: unknown[]; warnings: unknown[] }) => void;
    onImportArtifacts?: (artifacts: readonly unknown[]) => void;
    onAIChangeRecord?: (record: unknown) => void;
    onAIReviewDecision?: (actionId: string, decision: 'accepted' | 'rejected') => void;
  }) => (
    <div>
      <button
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
      <button
        data-testid="trigger-import-artifacts"
        onClick={() =>
          onImportArtifacts?.([
            createPageArtifactDocument({ artifactId: 'import-1', name: 'Imported A', createdBy: 'import' }),
            createPageArtifactDocument({ artifactId: 'import-2', name: 'Imported B', createdBy: 'import' }),
          ])
        }
      />
      <button
        data-testid="trigger-ai-record"
        onClick={() =>
          onAIChangeRecord?.({
            artifactId: 'artifact-1',
            documentId: 'doc-1',
            lineage: {
              actionId: 'ai-1',
              hookKind: 'property-completion',
              reason: 'Synthetic AI change for node test',
              confidence: 0.9,
              reversible: true,
              reviewState: 'pending',
              affectedNodeIds: [],
              appliedAt: new Date().toISOString(),
              evidence: [],
            },
          })
        }
      />
      <button
        data-testid="trigger-ai-review-accept"
        onClick={() => onAIReviewDecision?.('ai-1', 'accepted')}
      />
    </div>
  ),
}));

vi.mock('../../page/pageArtifactPersistence', () => ({
  HttpPageArtifactPersistenceAdapter: class {
    async save(document: unknown) {
      await mockPersistenceSave(document);
    }
    async load(artifactId: string) {
      return mockPersistenceLoad(artifactId);
    }
  },
  LocalStoragePageArtifactPersistenceAdapter: class {
    async save(document: unknown) {
      await mockPersistenceSave(document);
    }
    async load(artifactId: string) {
      return mockPersistenceLoad(artifactId);
    }
  },
  ResilientPageArtifactPersistenceAdapter: class {
    async save(document: unknown) {
      await mockPersistenceSave(document);
    }
    async load(artifactId: string) {
      return mockPersistenceLoad(artifactId);
    }
  },
  isConflictError: (err: unknown) => err instanceof Error && err.name === 'PageArtifactConflictError',
}));

vi.mock('@/components/studio/LivePreviewPanel', () => ({
  LivePreviewPanel: () => <div data-testid="live-preview-panel" />,
}));

vi.mock('@/context', () => ({
  usePhaseContext: () => ({ currentPhase: null }),
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
    Typography: ({ children, ...rest }: React.PropsWithChildren<Record<string, unknown>>) => <span {...rest}>{children}</span>,
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
    mockPersistenceSave.mockClear();
    mockPersistenceLoad.mockClear();
    mockPersistenceLoad.mockResolvedValue(null);
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

    it('shows governance trace count badge when page document has governance records', () => {
      const pageDocument = {
        ...createPageArtifactDocument({ artifactId: 'artifact-1', name: 'Home Page', createdBy: 'tester' }),
        aiChangeRecords: [
          {
            artifactId: 'artifact-1',
            documentId: 'doc-1',
            lineage: {
              actionId: 'ai-1',
              hookKind: 'property-completion',
              reason: 'Imported from decompile',
              confidence: 0.9,
              reversible: true,
              reviewState: 'pending',
              affectedNodeIds: [],
              appliedAt: new Date().toISOString(),
              evidence: [],
            },
          },
        ],
      };

      render(<PageDesignerNode {...makeNodeProps({ pageDocument })} />);
      expect(screen.getByTestId('page-node-governance-trace-count')).toHaveTextContent('Governance 1');
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

    it('creates additional page-designer nodes for imported artifacts beyond the first one', () => {
      const pageDocument = createPageArtifactDocument({
        artifactId: 'artifact-main',
        name: 'Home Page',
        createdBy: 'tester',
      });

      render(<PageDesignerNode {...makeNodeProps({ expanded: true, pageDocument })} />);

      fireEvent.click(screen.getByTestId('trigger-import-artifacts'));
      expect(mockExecuteCommand).toHaveBeenCalledTimes(1);
    });

    it('records AI lineage updates in node data when PageDesigner emits an AI change', () => {
      const pageDocument = createPageArtifactDocument({
        artifactId: 'artifact-main',
        name: 'Home Page',
        createdBy: 'tester',
      });

      render(<PageDesignerNode {...makeNodeProps({ expanded: true, pageDocument })} />);
      fireEvent.click(screen.getByTestId('trigger-ai-record'));
      expect(mockExecuteCommand).toHaveBeenCalledTimes(1);
    });

    it('persists AI review decisions into the page artifact governance records', () => {
      const pageDocument = {
        ...createPageArtifactDocument({
          artifactId: 'artifact-main',
          name: 'Home Page',
          createdBy: 'tester',
        }),
        aiChangeRecords: [
          {
            artifactId: 'artifact-main',
            documentId: 'doc-1',
            lineage: {
              actionId: 'ai-1',
              hookKind: 'property-completion' as const,
              reason: 'Synthetic AI change for node test',
              confidence: 0.9,
              reversible: true,
              reviewState: 'pending' as const,
              affectedNodeIds: [],
              appliedAt: new Date().toISOString(),
              evidence: [],
            },
          },
        ],
      };

      render(<PageDesignerNode {...makeNodeProps({ expanded: true, pageDocument })} />);
      fireEvent.click(screen.getByTestId('trigger-ai-review-accept'));

      expect(mockExecuteCommand).toHaveBeenCalledTimes(1);
      const command = mockExecuteCommand.mock.calls[0]?.[0] as {
        readonly to?: { readonly pageDocument?: typeof pageDocument };
      };
      const persistedDocument = command.to?.pageDocument;
      expect(persistedDocument?.syncStatus).toBe('dirty');
      expect(persistedDocument?.aiChangeRecords?.[0]?.lineage.reviewState).toBe('accepted');
      expect(persistedDocument?.operationLog?.at(-1)).toMatchObject({
        operation: 'governance-record',
        status: 'succeeded',
        metadata: {
          actionId: 'ai-1',
          decision: 'accepted',
        },
      });
    });
  });

  describe('conflict resolution', () => {
    it('shows conflict controls and reloads from server when Reload is clicked', async () => {
      const conflicted = {
        ...createPageArtifactDocument({ artifactId: 'artifact-main', name: 'Home Page', createdBy: 'tester' }),
        syncStatus: 'error' as const,
      };

      mockPersistenceLoad.mockResolvedValue({ ...conflicted, syncStatus: 'synced' });

      render(<PageDesignerNode {...makeNodeProps({ expanded: true, pageDocument: conflicted })} />);

      fireEvent.click(screen.getByRole('button', { name: 'Reload remote' }));
      expect(mockPersistenceLoad).toHaveBeenCalledWith('artifact-main');
    });

    it('requires an audit reason before overwriting the remote conflict', () => {
      const conflicted = {
        ...createPageArtifactDocument({ artifactId: 'artifact-main', name: 'Home Page', createdBy: 'tester' }),
        syncStatus: 'error' as const,
      };

      render(<PageDesignerNode {...makeNodeProps({ expanded: true, pageDocument: conflicted })} />);

      const overwriteButton = screen.getByRole('button', { name: 'Overwrite with reason' });
      expect(overwriteButton).toBeDisabled();

      fireEvent.change(screen.getByTestId('page-conflict-overwrite-reason'), {
        target: { value: 'Local version has reviewed changes' },
      });
      fireEvent.click(overwriteButton);
      expect(mockPersistenceSave).toHaveBeenCalled();
    });
  });

  describe('starts expanded when data.expanded is true', () => {
    it('renders full PageDesigner immediately if node data has expanded = true', () => {
      render(<PageDesignerNode {...makeNodeProps({ expanded: true })} />);
      expect(screen.getByTestId('page-designer-full')).toBeTruthy();
    });
  });
});
