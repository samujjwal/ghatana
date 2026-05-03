import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

const {
  validateDocumentMock,
  insertNodeMock,
  moveNodeMock,
} = vi.hoisted(() => ({
  validateDocumentMock: vi.fn(),
  insertNodeMock: vi.fn(),
  moveNodeMock: vi.fn(),
}));

vi.mock('@ghatana/design-system', () => ({
  Box: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', props, children),
  Stack: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', props, children),
  Typography: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('p', props, children),
  IconButton: ({ children, onClick, title, 'data-testid': testId }: React.PropsWithChildren<{ onClick?: () => void; title?: string; 'data-testid'?: string }>) =>
    React.createElement('button', { onClick, title, 'data-testid': testId }, children),
  Button: ({ children, onClick, title, 'data-testid': testId }: React.PropsWithChildren<{ onClick?: () => void; title?: string; 'data-testid'?: string }>) =>
    React.createElement('button', { onClick, title, 'data-testid': testId }, children),
  Surface: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', props, children),
  Drawer: ({ open, children }: { open: boolean; children: React.ReactNode }) =>
    open ? React.createElement('div', { 'data-testid': 'property-drawer' }, children) : null,
  Card: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', props, children),
  CardHeader: ({ title, subheader }: { title?: string; subheader?: string }) =>
    React.createElement('div', null, title, subheader),
  CardContent: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', props, children),
}));

vi.mock('../PropertyForm', () => ({
  PropertyForm: ({ onUpdate, onCancel }: {
    onUpdate: (payload: { props: Record<string, unknown>; name?: string }) => void;
    onCancel: () => void;
  }) =>
    React.createElement('div', { 'data-testid': 'property-form' },
      React.createElement('button', {
        'data-testid': 'save-property',
        onClick: () => onUpdate({ props: { children: 'Updated Button' }, name: 'Updated' }),
      }, 'Save'),
      React.createElement('button', { 'data-testid': 'cancel-property', onClick: onCancel }, 'Cancel'),
    ),
}));

vi.mock('../registry', () => ({
  getBuilderPalette: () => [
    {
      id: 'button',
      name: 'Button',
      displayName: 'Button',
      tooltip: 'Button',
      group: 'Inputs',
      subGroup: undefined,
      rank: 1,
      icon: undefined,
      defaultProps: {},
      featured: false,
      searchKeywords: [],
      version: '1.0.0',
      status: 'stable',
    },
    {
      id: 'card',
      name: 'Card',
      displayName: 'Card',
      tooltip: 'Card',
      group: 'Layout',
      subGroup: undefined,
      rank: 2,
      icon: undefined,
      defaultProps: {},
      featured: false,
      searchKeywords: [],
      version: '1.0.0',
      status: 'stable',
    },
  ],
  getContractMap: () =>
    new Map([
      ['Button', { name: 'Button', props: [], slots: [], layout: { isContainer: false } }],
      ['Card', { name: 'Card', props: [], slots: [{ name: 'default', isDefault: true }], layout: { isContainer: true } }],
    ]),
  getDefaultSlotName: (contractName: string) => (contractName === 'Card' ? 'default' : undefined),
  isContainerContract: (contractName: string) => contractName === 'Card',
  normalizeContractName: (type: string) => {
    if (type === 'textfield') return 'TextField';
    if (type === 'card') return 'Card';
    if (type === 'button') return 'Button';
    if (type === 'box') return 'Box';
    if (type === 'typography') return 'Typography';
    return type;
  },
  getContractByName: (contractName: string) => ({
    name: contractName,
    props: [],
    slots: contractName === 'Card' ? [{ name: 'default', isDefault: true }] : [],
    layout: { isContainer: contractName === 'Card' },
  }),
  toLegacyComponentType: (contractName: string) => contractName.toLowerCase(),
}));

vi.mock('../schemas', () => ({
  getDefaultComponentData: (type: string) => ({
    id: `${type}-1`,
    type,
    text: 'Button',
    variant: 'contained',
    color: 'primary',
    size: 'medium',
    disabled: false,
    fullWidth: false,
  }),
}));

vi.mock('@ghatana/ui-builder', () => ({
  createDocumentId: () => 'doc-1',
  insertNode: insertNodeMock.mockImplementation((document: {
    nodes: Map<string, { id: string; contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }>;
    rootNodes: string[];
  }, instance: { contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }, parentId?: string, slotName?: string, bus?: { onNodeInserted?: (payload: { nodeId: string }) => void }) => {
    const nextNodes = new Map(document.nodes);
    const nodeId = `node-${document.nodes.size + 1}`;
    nextNodes.set(nodeId, {
      id: nodeId,
      contractName: instance.contractName,
      props: instance.props,
      slots: instance.slots,
      bindings: instance.bindings,
      metadata: instance.metadata,
    });

    if (parentId && slotName) {
      const parent = nextNodes.get(parentId);
      if (parent) {
        const nextSlotChildren = [...(parent.slots[slotName] ?? []), nodeId];
        nextNodes.set(parentId, {
          ...parent,
          slots: {
            ...parent.slots,
            [slotName]: nextSlotChildren,
          },
        });
      }
    }

    const nextRootNodes = parentId && slotName ? document.rootNodes : [...document.rootNodes, nodeId];
    bus?.onNodeInserted?.({ nodeId });

    return {
      ...document,
      nodes: nextNodes,
      rootNodes: nextRootNodes,
    };
  }),
  deleteNode: vi.fn((document: { nodes: Map<string, unknown>; rootNodes: string[] }, nodeId: string) => {
    const nextNodes = new Map(document.nodes);
    nextNodes.delete(nodeId);
    return {
      ...document,
      nodes: nextNodes,
      rootNodes: document.rootNodes.filter((id) => id !== nodeId),
    };
  }),
  moveNode: moveNodeMock.mockImplementation((document: {
    nodes: Map<string, { id: string; contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }>;
    rootNodes: string[];
  }, nodeId: string, newParentId?: string | null, newSlotName?: string) => {
    const nextNodes = new Map(document.nodes);

    const nextRootNodes = document.rootNodes.filter((id) => id !== nodeId);
    for (const [id, node] of nextNodes.entries()) {
      const nextSlots: Record<string, string[]> = {};
      let changed = false;

      Object.entries(node.slots).forEach(([slotName, children]) => {
        const filtered = children.filter((childId) => childId !== nodeId);
        nextSlots[slotName] = filtered;
        if (filtered.length !== children.length) {
          changed = true;
        }
      });

      if (changed) {
        nextNodes.set(id, {
          ...node,
          slots: nextSlots,
        });
      }
    }

    if (newParentId && newSlotName) {
      const parent = nextNodes.get(newParentId);
      if (parent) {
        nextNodes.set(newParentId, {
          ...parent,
          slots: {
            ...parent.slots,
            [newSlotName]: [...(parent.slots[newSlotName] ?? []), nodeId],
          },
        });
      }
    } else {
      nextRootNodes.push(nodeId);
    }

    return {
      ...document,
      nodes: nextNodes,
      rootNodes: nextRootNodes,
    };
  }),
  updateNodeProps: vi.fn((document: {
    nodes: Map<string, { id: string; contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }>;
  }, nodeId: string, props: Record<string, unknown>) => {
    const nextNodes = new Map(document.nodes);
    const current = nextNodes.get(nodeId);
    if (current) {
      nextNodes.set(nodeId, {
        ...current,
        props: { ...current.props, ...props },
      });
    }
    return {
      ...document,
      nodes: nextNodes,
    };
  }),
  validateDocument: (...args: unknown[]) => validateDocumentMock(...args),
  deserializeDocument: vi.fn((doc: unknown) => doc),
  createLineageEntry: vi.fn((hookKind: string, reason: string, confidence: number, affectedNodeIds: readonly string[]) => ({
    actionId: `ai-test-${Math.random().toString(36).slice(2, 9)}`,
    hookKind,
    reason,
    confidence,
    reversible: true,
    reviewState: 'pending',
    affectedNodeIds,
    appliedAt: new Date().toISOString(),
    evidence: [],
  })),
  AIActionLineageTracker: class MockAIActionLineageTracker {
    private entries: unknown[] = [];
    record(entry: unknown) { this.entries.push(entry); }
    setReviewState(_actionId: string, _state: string) { return true; }
    getAll() { return [...this.entries]; }
    getPending() { return []; }
    getByNode(_nodeId: string) { return []; }
    getReversible() { return []; }
    clear() { this.entries = []; }
  },
}));

import { PageDesigner } from '../PageDesigner';

describe('PageDesigner', () => {
  const onComponentsChange = vi.fn();
  const onDocumentChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    insertNodeMock.mockClear();
    moveNodeMock.mockClear();
    validateDocumentMock.mockReturnValue({ valid: true, errors: [], warnings: [] });
  });

  it('renders the registry palette', () => {
    render(<PageDesigner />);
    expect(screen.getByText('Registry Components')).toBeInTheDocument();
    expect(screen.getByText('Button')).toBeInTheDocument();
    expect(screen.getByText('Card')).toBeInTheDocument();
  });

  it('renders the new empty state copy', () => {
    render(<PageDesigner />);
    expect(screen.getByText('Drag components from the registry to start designing')).toBeInTheDocument();
  });

  it('adds a component when a palette button is clicked', () => {
    render(<PageDesigner onComponentsChange={onComponentsChange} />);
    fireEvent.click(screen.getByText('Button'));
    expect(onComponentsChange).toHaveBeenCalled();
  });

  it('surfaces validation issues inline', () => {
    validateDocumentMock.mockReturnValue({
      valid: false,
      errors: [{ message: 'Missing contract' }],
      warnings: [],
    });

    render(<PageDesigner />);
    expect(screen.getByText(/1 error\(s\), 0 warning\(s\)/i)).toBeInTheDocument();
    expect(screen.getByText('Missing contract')).toBeInTheDocument();
  });

  it('opens the registry property drawer for the selected node', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Button'));
    fireEvent.click(screen.getByTitle('Edit Properties'));
    expect(screen.getByTestId('property-drawer')).toBeInTheDocument();
    expect(screen.getByTestId('property-form')).toBeInTheDocument();
  });

  it('emits document changes with validation payloads', () => {
    render(<PageDesigner onDocumentChange={onDocumentChange} />);
    fireEvent.click(screen.getByText('Button'));
    expect(onDocumentChange).toHaveBeenCalledWith(
      expect.objectContaining({ id: expect.any(String) }),
      expect.objectContaining({ valid: true }),
    );
  });

  it('moves an existing node to root when dropped on design area', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Card'));

    const cardNode = screen.getByTestId('page-card');
    const designArea = screen.getByTestId('page-design-area');

    const dataTransfer = {
      store: new Map<string, string>(),
      setData(type: string, value: string) {
        this.store.set(type, value);
      },
      getData(type: string) {
        return this.store.get(type) ?? '';
      },
      effectAllowed: 'all',
    };

    fireEvent.dragStart(cardNode, { dataTransfer });
    fireEvent.drop(designArea, { dataTransfer });

    expect(moveNodeMock).toHaveBeenCalled();
  });

  it('supports palette drop on sibling insertion marker', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Button'));

    const beforeMarker = screen.getByTestId('page-button-drop-before');
    const dataTransfer = {
      getData: (type: string) => (type === 'application/x-page-component' ? 'Button' : ''),
      setData: vi.fn(),
      effectAllowed: 'copy',
    };

    fireEvent.dragOver(beforeMarker, { dataTransfer });
    fireEvent.drop(beforeMarker, { dataTransfer });

    expect(insertNodeMock).toHaveBeenCalled();
  });

  it('supports slot-aware palette drop into container slots', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Card'));

    const slotTarget = screen.getByTestId('slot-drop-default');
    const dataTransfer = {
      getData: (type: string) => (type === 'application/x-page-component' ? 'Button' : ''),
      setData: vi.fn(),
      effectAllowed: 'copy',
    };

    fireEvent.dragOver(slotTarget, { dataTransfer });
    fireEvent.drop(slotTarget, { dataTransfer });

    expect(insertNodeMock).toHaveBeenCalledWith(
      expect.anything(),
      expect.anything(),
      'node-1',
      'default',
      expect.anything(),
    );
  });
});

// ---------------------------------------------------------------------------
// Import panel tests
// ---------------------------------------------------------------------------

const {
  importPageArtifactsFromCodeMock,
} = vi.hoisted(() => ({
  importPageArtifactsFromCodeMock: vi.fn(),
}));

vi.mock('../artifactCompilerBridge', () => ({
  importPageArtifactsFromCode: (...args: unknown[]) => importPageArtifactsFromCodeMock(...args),
}));

describe('PageDesigner — import panel', () => {
  const emptySerializedDocument = { id: 'doc-import', version: 1, nodes: {}, rootNodes: [] };

  beforeEach(() => {
    vi.clearAllMocks();
    importPageArtifactsFromCodeMock.mockReturnValue([
      {
        artifactId: 'art-1',
        documentId: 'doc-import',
        serializedBuilderDocument: emptySerializedDocument,
        source: 'imported',
        roundTripFidelity: {
          canRoundTrip: false,
          confidence: 0.72,
          lossPoints: [{ type: 'unsupported-pattern', location: 'section.hero' }],
        },
        syncStatus: 'dirty',
        trustLevel: 'low',
        dataClassification: 'public',
        createdBy: 'import',
        updatedBy: 'import',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        residualIslandIds: ['island-a'],
      },
    ]);
    validateDocumentMock.mockReturnValue({ valid: true, errors: [], warnings: [] });
  });

  it('opens the import panel when the upload button is clicked', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    expect(screen.getByTestId('page-designer-import-textarea')).toBeInTheDocument();
  });

  it('shows an error when confirm is clicked with empty input', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));
    // The error message is specific: "Paste a JSON semantic model to import."
    // (distinct from the description label in the panel)
    expect(screen.getByText(/to import\./i)).toBeInTheDocument();
  });

  it('shows an error when the bridge throws on invalid JSON', () => {
    importPageArtifactsFromCodeMock.mockImplementation(() => {
      throw new Error('Unexpected token');
    });

    render(<PageDesigner />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: 'bad json' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));
    expect(screen.getByText(/unexpected token/i)).toBeInTheDocument();
  });

  it('calls onImportArtifacts and shows residuals after a successful import', () => {
    const onImportArtifacts = vi.fn();
    render(<PageDesigner onImportArtifacts={onImportArtifacts} />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '{ "pages": [] }' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    expect(onImportArtifacts).toHaveBeenCalledWith(
      expect.arrayContaining([expect.objectContaining({ artifactId: 'art-1' })]),
    );
    expect(screen.getByTestId('page-designer-residuals')).toBeInTheDocument();
    expect(screen.getByText(/island-a/i)).toBeInTheDocument();
  });

  it('closes the import panel after a successful import', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '{ "pages": [] }' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));
    expect(screen.queryByTestId('page-designer-import-textarea')).not.toBeInTheDocument();
  });

  it('shows round-trip fidelity summary after successful import', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '{ "pages": [] }' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    expect(screen.getByTestId('page-designer-roundtrip-fidelity')).toBeInTheDocument();
    expect(screen.getByText(/72%/i)).toBeInTheDocument();
    expect(screen.getByText(/loss points detected/i)).toBeInTheDocument();
  });

  it('emits onAIChangeRecord when an import/decompile action is applied', () => {
    const onAIChangeRecord = vi.fn();
    render(<PageDesigner onAIChangeRecord={onAIChangeRecord} />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '{ "pages": [] }' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    expect(onAIChangeRecord).toHaveBeenCalledWith(
      expect.objectContaining({
        artifactId: 'art-1',
        documentId: 'doc-import',
        lineage: expect.objectContaining({
          hookKind: 'property-completion',
        }),
      }),
    );
  });
});

// ---------------------------------------------------------------------------
// AI governance panel tests
// ---------------------------------------------------------------------------

import { createLineageEntry } from '@ghatana/ui-builder';

describe('PageDesigner — AI governance panel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    validateDocumentMock.mockReturnValue({ valid: true, errors: [], warnings: [] });
  });

  it('does not render the governance panel when there are no pending AI actions', () => {
    render(<PageDesigner />);
    expect(screen.queryByTestId('ai-governance-panel')).not.toBeInTheDocument();
  });
});
