import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';

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
  IconButton: ({ children, onClick, title, disabled, 'data-testid': testId }: React.PropsWithChildren<{ onClick?: () => void; title?: string; disabled?: boolean; 'data-testid'?: string }>) =>
    React.createElement('button', { onClick, title, disabled, 'data-testid': testId }, children),
  Button: ({ children, onClick, title, disabled, draggable, onDragStart, 'data-testid': testId }: React.PropsWithChildren<{ onClick?: () => void; title?: string; disabled?: boolean; draggable?: boolean; onDragStart?: (event: React.DragEvent<HTMLButtonElement>) => void; 'data-testid'?: string }>) =>
    React.createElement('button', { onClick, title, disabled, draggable, onDragStart, 'data-testid': testId }, children),
  TextArea: React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
    (props, ref) => React.createElement('textarea', { ...props, ref }),
  ),
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
  PropertyForm: ({ onUpdate, onCancel, readOnly }: {
    onUpdate: (payload: {
      props: Record<string, unknown>;
      name?: string;
      responsiveVariant?: { breakpoint: string; props?: Record<string, unknown> };
      stateVariant?: { state: 'hover'; props?: Record<string, unknown> };
      dataBinding?: { id: string; type: 'data'; source: string; target: string };
      actionBinding?: { id: string; type: 'event'; source: string; target: string };
    }) => void;
    onCancel: () => void;
    readOnly?: boolean;
  }) =>
    React.createElement('div', { 'data-testid': 'property-form' },
      React.createElement('button', {
        'data-testid': 'save-property',
        disabled: readOnly,
        onClick: () => onUpdate({
          props: { children: 'Updated Button' },
          name: 'Updated',
          responsiveVariant: { breakpoint: 'md', props: { size: 'lg' } },
          stateVariant: { state: 'hover', props: { variant: 'outline' } },
          dataBinding: { id: 'binding-data', type: 'data', source: 'dataSource.orders', target: 'children' },
          actionBinding: { id: 'binding-action', type: 'event', source: 'onClick', target: 'navigate:/orders' },
          privacyClassification: 'SENSITIVE',
        }),
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
      id: 'textfield',
      name: 'TextField',
      displayName: 'Text field',
      tooltip: 'Capture customer email',
      group: 'Inputs',
      subGroup: 'Text',
      rank: 3,
      icon: undefined,
      defaultProps: {},
      featured: true,
      searchKeywords: ['email', 'form'],
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
  getBuilderPaletteCategories: (entries: Array<{ group: string }>) =>
    Array.from(new Set(entries.map((entry) => entry.group))).sort(),
  getFilteredBuilderPalette: (
    filters: { query?: string; category?: string },
    entries: Array<{
      name: string;
      displayName: string;
      tooltip: string;
      group: string;
      subGroup?: string;
      searchKeywords: readonly string[];
      featured: boolean;
      rank: number;
    }>
  ) => {
    const query = filters.query?.toLowerCase() ?? '';
    return entries
      .filter((entry) => !filters.category || entry.group === filters.category)
      .filter((entry) =>
        [entry.name, entry.displayName, entry.tooltip, entry.group, entry.subGroup, ...entry.searchKeywords]
          .filter(Boolean)
          .join(' ')
          .toLowerCase()
          .includes(query)
      )
      .sort((a, b) => Number(b.featured) - Number(a.featured) || a.rank - b.rank);
  },
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
  migrateRegistryContractInstance: (input: { contractName: string; props: Record<string, unknown>; version?: string }) => ({
    contractName:
      input.contractName === 'textfield'
        ? 'TextField'
        : input.contractName === 'card'
          ? 'Card'
          : input.contractName === 'button'
            ? 'Button'
            : input.contractName,
    version: input.version ?? '1.0.0',
    props: input.props,
    migrationsApplied: [],
    compatibility: {
      compatible: true,
      requiresMigration: false,
      warnings: [],
      errors: [],
    },
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
  attachBuilderDocumentCompatibility: (document: {
    nodes: Map<string, unknown> | Record<string, unknown>;
    layout?: { rootId: string; nodes: Record<string, { children?: string[] }> };
    [key: string]: unknown;
  }) => {
    if (!(document.nodes instanceof Map)) {
      const nodeRecord = document.nodes;
      Object.defineProperty(nodeRecord, 'get', { value: (id: string) => nodeRecord[id], enumerable: false });
      Object.defineProperty(nodeRecord, 'has', { value: (id: string) => Object.prototype.hasOwnProperty.call(nodeRecord, id), enumerable: false });
      Object.defineProperty(nodeRecord, 'size', { get: () => Object.values(nodeRecord).filter((node) => (node as { contractName?: string }).contractName !== 'RootContainer').length, enumerable: false });
    }
    Object.defineProperty(document, 'rootNodes', {
      get: () => document.layout?.nodes[document.layout.rootId]?.children ?? [],
      configurable: true,
      enumerable: false,
    });
    return document;
  },
  normalizeBuilderDocument: (document: unknown) => {
    const doc = document as {
      documentId?: string;
      id?: string;
      owner?: string;
      root?: string;
      rootNodes?: string[];
      nodes?: Map<string, unknown> | Record<string, unknown>;
      layout?: { rootId: string; nodes: Record<string, { id?: string; type?: string; children?: string[] }> };
      metadata?: Record<string, unknown>;
    } | undefined;
    if (!doc) {
      return {
        documentId: 'doc-1',
        owner: 'test',
        root: 'root',
        rootNodes: [],
        nodes: new Map(),
        layout: { rootId: 'root', nodes: { root: { id: 'root', type: 'root', children: [] } } },
        metadata: {},
      };
    }
    const rootId = doc.root ?? doc.layout?.rootId ?? 'root';
    const rootNodes = doc.rootNodes ?? doc.layout?.nodes[rootId]?.children ?? [];
    const nodeRecord = doc.nodes instanceof Map
      ? doc.nodes
      : { ...(doc.nodes ?? {}) };
    if (nodeRecord instanceof Map) {
      for (const [nodeId, node] of nodeRecord.entries()) {
        Object.defineProperty(nodeRecord, nodeId, {
          value: node,
          enumerable: true,
          configurable: true,
          writable: true,
        });
      }
    } else {
      Object.defineProperty(nodeRecord, 'get', { value: (id: string) => nodeRecord[id], enumerable: false, configurable: true });
      Object.defineProperty(nodeRecord, 'has', { value: (id: string) => Object.prototype.hasOwnProperty.call(nodeRecord, id), enumerable: false, configurable: true });
      Object.defineProperty(nodeRecord, 'set', { value: (id: string, value: unknown) => { nodeRecord[id] = value; return nodeRecord; }, enumerable: false, configurable: true });
      Object.defineProperty(nodeRecord, 'delete', { value: (id: string) => delete nodeRecord[id], enumerable: false, configurable: true });
      Object.defineProperty(nodeRecord, 'size', { get: () => Object.keys(nodeRecord).length, enumerable: false, configurable: true });
    }
    return {
      ...doc,
      documentId: doc.documentId ?? doc.id ?? 'doc-1',
      owner: doc.owner ?? 'test',
      root: rootId,
      rootNodes,
      nodes: nodeRecord,
      layout: {
        type: 'flex',
        ...(doc.layout ?? {}),
        rootId,
        nodes: {
          ...(doc.layout?.nodes ?? {}),
          [rootId]: {
            id: rootId,
            type: 'root',
            ...(doc.layout?.nodes[rootId] ?? {}),
            children: rootNodes,
          },
        },
      },
      metadata: doc.metadata ?? {},
    };
  },
  createBuilderDocument: (owner: string, options?: { documentId?: string }) => ({
    id: options?.documentId ?? 'doc-1',
    documentId: options?.documentId ?? 'doc-1',
    version: '1',
    schemaVersion: '1.0.0',
    name: 'Test Document',
    owner,
    root: 'root',
    rootNodes: [],
    nodes: new Map(),
    bindings: [],
    layout: {
      type: 'flex',
      rootId: 'root',
      nodes: {
        root: {
          id: 'root',
          type: 'root',
          children: [],
        },
      },
    },
    designSystem: {
      id: 'ghatana-ds-v1',
      name: 'Ghatana Design System',
      version: '1.0.0',
      tokenSetIds: [],
      componentContracts: [],
      themeId: 'default',
    },
    metadata: {
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      author: owner,
      dataClassification: 'INTERNAL',
      trustLevel: 'GENERATED_TRUSTED',
    },
  }),
  insertNode: insertNodeMock.mockImplementation((document: {
    nodes: Map<string, { id: string; contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }>;
    rootNodes: string[];
  }, instance: { contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }, parentId?: string, slotName?: string, bus?: { onNodeInserted?: (payload: { nodeId: string }) => void }) => {
    const nextNodes = new Map(document.nodes instanceof Map ? document.nodes : Object.entries(document.nodes));
    const nodeId = `node-${nextNodes.size + 1}`;
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
    const nextNodes = new Map(document.nodes instanceof Map ? document.nodes : Object.entries(document.nodes));
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
    const nextNodes = new Map(document.nodes instanceof Map ? document.nodes : Object.entries(document.nodes));

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
    const nextNodes = new Map(document.nodes instanceof Map ? document.nodes : Object.entries(document.nodes));
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
  addBinding: vi.fn((document: {
    nodes: Map<string, { id: string; contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }>;
  }, nodeId: string, binding: unknown) => {
    const nextNodes = new Map(document.nodes instanceof Map ? document.nodes : Object.entries(document.nodes));
    const current = nextNodes.get(nodeId);
    if (current) {
      nextNodes.set(nodeId, {
        ...current,
        bindings: [...current.bindings, binding],
      });
    }
    return {
      ...document,
      nodes: nextNodes,
    };
  }),
  removeBinding: vi.fn((document: {
    nodes: Map<string, { id: string; contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: Array<{ id?: string }>; metadata: Record<string, unknown> }>;
  }, nodeId: string, bindingId: string) => {
    const nextNodes = new Map(document.nodes instanceof Map ? document.nodes : Object.entries(document.nodes));
    const current = nextNodes.get(nodeId);
    if (current) {
      nextNodes.set(nodeId, {
        ...current,
        bindings: current.bindings.filter((binding) => binding.id !== bindingId),
      });
    }
    return {
      ...document,
      nodes: nextNodes,
    };
  }),
  setResponsiveVariant: vi.fn((document: {
    nodes: Map<string, { id: string; contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }>;
  }, nodeId: string, variant: unknown) => {
    const nextNodes = new Map(document.nodes instanceof Map ? document.nodes : Object.entries(document.nodes));
    const current = nextNodes.get(nodeId);
    if (current) {
      nextNodes.set(nodeId, {
        ...current,
        metadata: {
          ...current.metadata,
          responsiveVariants: [variant],
        },
      });
    }
    return {
      ...document,
      nodes: nextNodes,
    };
  }),
  validateDocument: (...args: unknown[]) => validateDocumentMock(...args),
  serializeDocument: vi.fn((document: unknown) => document),
  deserializeDocument: vi.fn((doc: unknown) => {
    if (
      doc &&
      typeof doc === 'object' &&
      'nodes' in doc &&
      !((doc as { nodes: unknown }).nodes instanceof Map)
    ) {
      const serialized = doc as {
        id?: string;
        version?: string | number;
        name?: string;
        rootNodes?: string[];
        nodes?: Record<string, {
          id: string;
          contractName: string;
          props?: Record<string, unknown>;
          slots?: Record<string, string[]>;
          bindings?: unknown[];
          metadata?: Record<string, unknown>;
        }>;
      };

      return {
        id: serialized.id ?? 'doc-import',
        version: String(serialized.version ?? '1'),
        name: serialized.name ?? 'Imported Page',
        designSystem: {
          id: 'ghatana-ds-v1',
          name: 'Ghatana Design System',
          version: '1.0.0',
          tokenSetIds: [],
          componentContracts: [],
          themeId: 'default',
        },
        rootNodes: serialized.rootNodes ?? [],
        nodes: new Map(
          Object.entries(serialized.nodes ?? {}).map(([nodeId, node]) => [
            nodeId,
            {
              id: node.id,
              contractName: node.contractName,
              props: node.props ?? {},
              slots: node.slots ?? {},
              bindings: node.bindings ?? [],
              metadata: node.metadata ?? {},
            },
          ]),
        ),
        metadata: {
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          author: 'import',
          dataClassification: 'INTERNAL',
          trustLevel: 'GENERATED_TRUSTED',
        },
      };
    }

    return doc;
  }),
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

function makeSingleNodeDocument(nodeId = 'node-preview-hover') {
  return {
    id: 'doc-1',
    version: '1',
    name: 'Preview Sync Doc',
    designSystem: {
      id: 'ds-1',
      name: 'Design System',
      version: '1.0.0',
      tokenSetIds: [],
      componentContracts: [],
      themeId: 'default',
    },
    rootNodes: [nodeId],
    nodes: new Map([
      [nodeId, {
        id: nodeId,
        contractName: 'Button',
        props: {},
        slots: {},
        bindings: [],
        metadata: { name: 'Button' },
      }],
    ]),
    metadata: {
      createdAt: '2026-05-06T00:00:00.000Z',
      updatedAt: '2026-05-06T00:00:00.000Z',
      trustLevel: 'GENERATED_TRUSTED',
    },
  };
}

const readOnlyPhaseConfig: import('../../../../services/canvas/phase-config/PhaseCanvasConfig').PhaseCanvasConfig = {
  mode: 'validate',
  visibleTools: ['select', 'inspect', 'preview'],
  defaultTool: 'inspect',
  allowEditing: false,
  allowAddComponent: false,
  allowDelete: false,
  allowEdgeManipulation: false,
  enableValidation: true,
  enablePreview: true,
};

describe('PageDesigner', () => {
  const onComponentsChange = vi.fn();
  const onDocumentChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.unstubAllGlobals();
    insertNodeMock.mockClear();
    moveNodeMock.mockClear();
    validateDocumentMock.mockReturnValue({ valid: true, errors: [], warnings: [] });
  });

  it('renders the registry palette', () => {
    render(<PageDesigner />);
    expect(screen.getByText('Registry Components')).toBeInTheDocument();
    expect(screen.getByText('Button')).toBeInTheDocument();
    expect(screen.getByText('Card')).toBeInTheDocument();
    expect(screen.getByText('Recommended: Text field')).toBeInTheDocument();
    expect(screen.getByTestId('page-component-palette-summary')).toHaveTextContent(
      '3 of 3 registry components shown'
    );
  });

  it('filters the registry palette by search and category', () => {
    render(<PageDesigner />);

    fireEvent.change(screen.getByTestId('page-component-search'), {
      target: { value: 'email' },
    });

    expect(screen.getByText('Recommended: Text field')).toBeInTheDocument();
    expect(screen.queryByText('Button')).not.toBeInTheDocument();
    expect(screen.getByTestId('page-component-palette-summary')).toHaveTextContent(
      '1 of 3 registry components shown'
    );

    fireEvent.change(screen.getByTestId('page-component-search'), {
      target: { value: '' },
    });
    fireEvent.change(screen.getByTestId('page-component-category'), {
      target: { value: 'Layout' },
    });

    expect(screen.getByText('Card')).toBeInTheDocument();
    expect(screen.queryByText('Button')).not.toBeInTheDocument();
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

  it('blocks palette inserts and imports when the builder is read-only', () => {
    render(
      <PageDesigner
        onComponentsChange={onComponentsChange}
        phaseConfig={readOnlyPhaseConfig}
        readOnlyReason="Included projects are view-only."
      />,
    );

    onComponentsChange.mockClear();
    fireEvent.click(screen.getByText('Button'));
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));

    expect(screen.getByTestId('page-component-button')).toBeDisabled();
    expect(screen.getByTestId('page-designer-import-btn')).toBeDisabled();
    expect(onComponentsChange).not.toHaveBeenCalled();
    expect(insertNodeMock).not.toHaveBeenCalled();
    expect(screen.queryByTestId('page-designer-import-panel')).not.toBeInTheDocument();
  });

  it('persists scoped command audit records when audit context is available', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            id: 'audit-1',
            timestamp: '2026-05-06T12:00:00.000Z',
            type: 'PAGE_BUILDER_INSERT_COMPONENT',
            userId: 'user-1',
            projectId: 'proj-1',
            artifactId: 'artifact-1',
            flowStage: 'BUILD',
            phase: 'SHAPE',
            description: 'Page builder command insert-component completed.',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );

    render(
      <PageDesigner
        auditContext={{
          userId: 'user-1',
          tenantId: 'tenant-1',
          workspaceId: 'workspace-1',
          projectId: 'proj-1',
          artifactId: 'artifact-1',
          phase: 'SHAPE',
        }}
      />,
    );
    fireEvent.click(screen.getByText('Button'));

    await waitFor(() => {
      expect(vi.mocked(fetch).mock.calls.filter(([input]) => String(input).includes('/api/audit/events'))).toHaveLength(2);
    });
    const request = vi.mocked(fetch).mock.calls[0]?.[1];
    const body = JSON.parse(String(request?.body)) as {
      type: string;
      userId: string;
      projectId: string;
      artifactId: string;
      metadata: Record<string, unknown>;
    };
    expect(body.type).toBe('PAGE_BUILDER_INSERT_COMPONENT');
    expect(body.userId).toBe('user-1');
    expect(body.projectId).toBe('proj-1');
    expect(body.artifactId).toBe('artifact-1');
    expect(body.metadata).toMatchObject({
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      commandType: 'insert-component',
      correlationId: expect.any(String),
      success: true,
    });
    const telemetryRequest = vi.mocked(fetch).mock.calls[1]?.[1];
    const telemetryBody = JSON.parse(String(telemetryRequest?.body)) as {
      type: string;
      flowStage: string;
      metadata: Record<string, unknown>;
    };
    expect(telemetryBody.type).toBe('PAGE_BUILDER_TELEMETRY_PAGE_BUILDER_COMMAND_EXECUTED');
    expect(telemetryBody.flowStage).toBe('BUILD_TELEMETRY');
    expect(telemetryBody.metadata).toMatchObject({
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      event: 'page_builder_command_executed',
      commandType: 'insert-component',
      validationErrorCount: 0,
      correlationId: body.metadata.correlationId,
    });
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

  it('applies inspector variants and bindings through builder commands', () => {
    const onDocumentChangeForInspector = vi.fn();

    render(
      <PageDesigner
        initialComponents={makeSingleNodeDocument('node-1')}
        onDocumentChange={onDocumentChangeForInspector}
      />,
    );

    fireEvent.click(screen.getByTestId('page-button'));
    fireEvent.click(screen.getByTitle('Edit Properties'));
    fireEvent.click(screen.getByTestId('save-property'));

    expect(onDocumentChangeForInspector).toHaveBeenLastCalledWith(
      expect.objectContaining({
        nodes: expect.any(Map),
      }),
      expect.objectContaining({ valid: true }),
      expect.objectContaining({
        operation: 'document-update',
        commandType: 'add-action-binding',
      }),
    );
    const updatedDocument = onDocumentChangeForInspector.mock.calls.at(-1)?.[0];
    const updatedNode = updatedDocument.nodes.get('node-1');
    expect(updatedNode.props.children).toBe('Updated Button');
    expect(updatedNode.metadata.name).toBe('Updated');
    expect(updatedNode.metadata.responsiveVariants).toEqual([
      { breakpoint: 'md', props: { size: 'lg' } },
    ]);
    expect(updatedNode.metadata.stateVariants).toEqual([
      { state: 'hover', props: { variant: 'outline' } },
    ]);
    expect(updatedNode.metadata.dataClassification).toBe('SENSITIVE');
    expect(updatedNode.bindings).toEqual([
      { id: 'binding-data', type: 'data', source: 'dataSource.orders', target: 'children' },
      { id: 'binding-action', type: 'event', source: 'onClick', target: 'navigate:/orders' },
    ]);
  });

  it('blocks property updates when the builder is read-only', () => {
    render(
      <PageDesigner
        initialComponents={makeSingleNodeDocument('node-1')}
        onDocumentChange={onDocumentChange}
        phaseConfig={readOnlyPhaseConfig}
        readOnlyReason="Canvas edits are paused during validation review."
      />,
    );

    fireEvent.click(screen.getByTestId('page-button'));

    expect(screen.getByTitle('Edit Properties')).toBeDisabled();
    expect(screen.queryByTestId('property-form')).not.toBeInTheDocument();
  });

  it('emits document changes with validation payloads', () => {
    render(<PageDesigner onDocumentChange={onDocumentChange} />);
    fireEvent.click(screen.getByText('Button'));
    expect(onDocumentChange).toHaveBeenCalledWith(
      expect.objectContaining({ id: expect.any(String) }),
      expect.objectContaining({ valid: true }),
      expect.objectContaining({
        operation: 'document-update',
        commandType: 'insert-component',
      }),
    );
  });

  it('emits semantic undo and redo document change contexts', () => {
    render(<PageDesigner onDocumentChange={onDocumentChange} />);

    expect(screen.getByTestId('page-designer-undo-command')).toBeDisabled();
    expect(screen.getByTestId('page-designer-redo-command')).toBeDisabled();

    fireEvent.click(screen.getByText('Button'));
    expect(screen.getByTestId('page-designer-undo-command')).not.toBeDisabled();

    fireEvent.click(screen.getByTestId('page-designer-undo-command'));
    expect(onDocumentChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ rootNodes: [] }),
      expect.objectContaining({ valid: true }),
      expect.objectContaining({
        operation: 'undo-command',
        commandType: 'insert-component',
        changedNodeIds: expect.any(Array),
      }),
    );
    expect(screen.getByTestId('page-designer-redo-command')).not.toBeDisabled();

    fireEvent.click(screen.getByTestId('page-designer-redo-command'));
    expect(onDocumentChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ rootNodes: expect.arrayContaining([expect.any(String)]) }),
      expect.objectContaining({ valid: true }),
      expect.objectContaining({
        operation: 'redo-command',
        commandType: 'insert-component',
        changedNodeIds: expect.any(Array),
      }),
    );
  });

  it('highlights externally hovered preview nodes without changing selection', () => {
    const onSelectionChange = vi.fn();

    render(
      <PageDesigner
        initialComponents={makeSingleNodeDocument()}
        externalHoveredNodeId="node-preview-hover"
        onSelectionChange={onSelectionChange}
      />,
    );

    expect(screen.getByTestId('page-button')).toHaveAttribute('data-preview-hovered', 'true');
    expect(onSelectionChange).toHaveBeenLastCalledWith(null);
  });

  it('emits non-destructive hover changes for preview sync', () => {
    const onHoverChange = vi.fn();
    render(
      <PageDesigner
        initialComponents={makeSingleNodeDocument()}
        onHoverChange={onHoverChange}
      />,
    );

    fireEvent.mouseEnter(screen.getByTestId('page-button'));
    fireEvent.mouseLeave(screen.getByTestId('page-button'));

    expect(onHoverChange).toHaveBeenCalledWith('node-preview-hover');
    expect(onHoverChange).toHaveBeenCalledWith(null);
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

  it('shows explicit feedback for invalid self drops', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Button'));

    const beforeMarker = screen.getByTestId('page-button-drop-before');
    const dataTransfer = {
      getData: (type: string) => (type === 'application/x-page-node' ? 'node-1' : ''),
      setData: vi.fn(),
      effectAllowed: 'move',
    };

    fireEvent.drop(beforeMarker, { dataTransfer });

    expect(screen.getByTestId('page-drop-feedback')).toHaveTextContent('Cannot drop a component onto itself.');
  });

  it('supports keyboard reordering with Alt+ArrowUp', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Button'));
    fireEvent.click(screen.getByText('Card'));

    fireEvent.keyDown(screen.getByTestId('page-card'), {
      key: 'ArrowUp',
      altKey: true,
    });

    expect(moveNodeMock).toHaveBeenCalled();
  });

  it('supports keyboard nesting into the previous container with Alt+ArrowRight', () => {
    render(
      <PageDesigner
        initialComponents={{
          id: 'doc-keyboard',
          version: '1',
          name: 'Keyboard Fixture',
          designSystem: {
            id: 'ds',
            name: 'DS',
            version: '1',
            tokenSetIds: [],
            componentContracts: [],
            themeId: 'default',
          },
          rootNodes: ['node-1', 'node-2'],
          nodes: new Map([
            [
              'node-1',
              {
                id: 'node-1',
                contractName: 'Card',
                props: {},
                slots: { default: [] },
                bindings: [],
                metadata: {},
              },
            ],
            [
              'node-2',
              {
                id: 'node-2',
                contractName: 'Button',
                props: {},
                slots: {},
                bindings: [],
                metadata: {},
              },
            ],
          ]),
          metadata: {
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            trustLevel: 'GENERATED_TRUSTED',
            dataClassification: 'INTERNAL',
          },
        }}
      />,
    );

    fireEvent.keyDown(screen.getByTestId('page-button'), {
      key: 'ArrowRight',
      altKey: true,
    });

    expect(moveNodeMock).toHaveBeenCalledWith(expect.anything(), 'node-2', 'node-1', 'default');
  });

  it('announces when keyboard nesting has no previous container target', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Button'));

    fireEvent.keyDown(screen.getByTestId('page-button'), {
      key: 'ArrowRight',
      altKey: true,
    });

    expect(screen.getByTestId('page-drop-feedback')).toHaveTextContent(
      'Cannot move into a container because there is no previous component.',
    );
  });

  it('exposes keyboard movement instructions and live invalid-move feedback to assistive tech', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Button'));

    const buttonNode = screen.getByRole('treeitem', {
      name: /Button component\. Use Alt\+ArrowUp or Alt\+ArrowDown to reorder/i,
    });
    expect(buttonNode).toHaveAttribute('tabindex', '0');
    expect(buttonNode).toHaveAccessibleName(
      'Button component. Use Alt+ArrowUp or Alt+ArrowDown to reorder, Alt+ArrowRight to move into the previous container, Alt+ArrowLeft to move out of a container.',
    );

    fireEvent.keyDown(buttonNode, {
      key: 'ArrowLeft',
      altKey: true,
    });

    const feedback = screen.getByRole('alert');
    expect(feedback).toHaveAttribute('aria-live', 'polite');
    expect(feedback).toHaveTextContent('Component is already at the top level.');
  });
});

// ---------------------------------------------------------------------------
// Import panel tests
// ---------------------------------------------------------------------------

const {
  importPageArtifactsFromCodeMock,
  importSourceToPageArtifactsMock,
  checkArtifactCompilerRuntimeHealthMock,
} = vi.hoisted(() => ({
  importPageArtifactsFromCodeMock: vi.fn(),
  importSourceToPageArtifactsMock: vi.fn(),
  checkArtifactCompilerRuntimeHealthMock: vi.fn(),
}));

vi.mock('../artifactCompilerBridge', () => ({
  importPageArtifactsFromCode: (...args: unknown[]) => importPageArtifactsFromCodeMock(...args),
}));

vi.mock('../../../../services/compiler/ImportSourceWorkflow', () => ({
  importSourceToPageArtifacts: (...args: unknown[]) => importSourceToPageArtifactsMock(...args),
}));

vi.mock('../../../../services/compiler/ArtifactCompilerRuntimeHealth', () => ({
  checkArtifactCompilerRuntimeHealth: (...args: unknown[]) => checkArtifactCompilerRuntimeHealthMock(...args),
}));

describe('PageDesigner — import panel', () => {
  const emptySerializedDocument = { id: 'doc-import', version: 1, nodes: {}, rootNodes: [] };

  beforeEach(() => {
    vi.clearAllMocks();
    checkArtifactCompilerRuntimeHealthMock.mockResolvedValue({
      status: 'available',
      checkedAt: '2026-05-07T16:00:00.000Z',
      requirements: [],
      unavailableRequirements: [],
      message: 'Artifact compiler runtime is available.',
    });
    importPageArtifactsFromCodeMock.mockReturnValue([
      {
        artifactId: 'art-1',
        documentId: 'doc-import',
        serializedBuilderDocument: emptySerializedDocument,
        source: 'imported',
        roundTripFidelity: {
          canRoundTrip: false,
          confidence: 0.72,
          lossPoints: [
            {
              type: 'unsupported-pattern',
              location: 'section.hero',
              description: 'Hero animation could not be represented as a canonical component.',
            },
          ],
        },
        syncStatus: 'dirty',
        trustLevel: 'low',
        dataClassification: 'public',
        createdBy: 'import',
        updatedBy: 'import',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        residualIslandIds: ['island-a'],
        artifactGraph: {
          graphId: 'art-1:graph',
          projectId: 'proj-1',
          sourceType: 'semantic-model',
          source: 'semantic-model',
          importedAt: '2026-05-07T00:00:00.000Z',
          nodes: [
            { id: 'art-1:page', kind: 'page', label: 'Imported Page' },
            { id: 'art-1:residual:island-a', kind: 'residual', label: 'island-a' },
          ],
          edges: [
            {
              id: 'art-1:page-residual-island-a',
              from: 'art-1:residual:island-a',
              to: 'art-1:page',
              kind: 'residual-of',
            },
          ],
          provenance: {
            createdBy: 'import',
            compiler: 'yappc-artifact-compiler',
            confidence: 0.72,
            residualIslandIds: ['island-a'],
          },
        },
      },
    ]);
    importSourceToPageArtifactsMock.mockResolvedValue({
      importResult: {
        success: true,
        componentId: 'imported-project/ImportedRemotePage',
        files: [],
        warnings: [],
        errors: [],
        metadata: {
          sourceType: 'tsx',
          source: 'https://example.com/Page.tsx',
          importedAt: new Date().toISOString(),
          componentName: 'ImportedRemotePage',
          dependencies: [],
          fileCount: 0,
          totalSize: 0,
        },
      },
      pageArtifacts: [
        {
          artifactId: 'imported-project-imported-remote-page',
          documentId: 'doc-source-import',
          serializedBuilderDocument: emptySerializedDocument,
          source: 'imported',
          syncStatus: 'dirty',
          trustLevel: 'low',
          dataClassification: 'public',
          createdBy: 'import',
          updatedBy: 'import',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          residualIslandIds: [],
          artifactGraph: {
            graphId: 'imported-project-imported-remote-page:graph',
            projectId: 'proj-1',
            sourceType: 'tsx',
            source: 'https://example.com/Page.tsx',
            importedAt: '2026-05-07T00:00:00.000Z',
            nodes: [{ id: 'imported-project-imported-remote-page:page', kind: 'page', label: 'ImportedRemotePage' }],
            edges: [],
            provenance: {
              createdBy: 'import',
              compiler: 'yappc-artifact-compiler',
              confidence: 0.8,
              residualIslandIds: [],
            },
          },
        },
      ],
    });
    validateDocumentMock.mockReturnValue({ valid: true, errors: [], warnings: [] });
  });

  it('opens the import panel when the upload button is clicked', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    expect(screen.getByTestId('page-designer-import-textarea')).toBeInTheDocument();
  });

  it('shows import wizard templates for common source paths', () => {
    render(<PageDesigner projectId="proj-1" />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));

    expect(screen.getByTestId('page-import-template-paste-code')).toHaveTextContent('Paste code');
    expect(screen.getByTestId('page-import-template-upload-zip')).toHaveTextContent('Upload zip');
    expect(screen.getByTestId('page-import-template-connect-repo')).toHaveTextContent('Connect repo');
    expect(screen.getByTestId('page-import-template-import-storybook')).toHaveTextContent('Import Storybook');
    expect(screen.getByTestId('page-import-template-import-route')).toHaveTextContent('Import route');
  });

  it('preconfigures governed source fields from import wizard templates', () => {
    render(<PageDesigner projectId="proj-1" />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));

    fireEvent.click(screen.getByTestId('page-import-template-upload-zip'));
    expect(screen.getByTestId('page-designer-source-type')).toHaveValue('zip');
    expect(screen.getByTestId('page-designer-source-locator')).toHaveAttribute(
      'placeholder',
      'https://example.com/artifacts/app-pages.zip',
    );

    fireEvent.click(screen.getByTestId('page-import-template-import-storybook'));
    expect(screen.getByTestId('page-designer-source-type')).toHaveValue('storybook');
    expect(screen.getByTestId('page-designer-source-locator')).toHaveAttribute(
      'placeholder',
      'https://example.com/Button.stories.tsx#Primary',
    );

    fireEvent.click(screen.getByTestId('page-import-template-paste-code'));
    expect(screen.getByTestId('page-designer-import-textarea')).toHaveAttribute(
      'placeholder',
      '{"pages": [{"name": "Home", "confidence": 0.92}]}',
    );
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
    render(<PageDesigner projectId="proj-1" onImportArtifacts={onImportArtifacts} />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '{ "pages": [] }' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    expect(onImportArtifacts).toHaveBeenCalledWith(
      expect.arrayContaining([expect.objectContaining({ artifactId: 'art-1' })]),
    );
    const residualsPanel = screen.getByTestId('page-designer-residuals');
    expect(residualsPanel).toBeInTheDocument();
    expect(within(residualsPanel).getByText(/island-a/i)).toBeInTheDocument();
    expect(screen.getByTestId('residual-island-explanation')).toHaveTextContent(
      /could not be mapped to a reviewed registry contract/i,
    );
    expect(screen.getByTestId('page-designer-graph-merge-review')).toHaveTextContent(
      'Artifact graph merge review: required',
    );
  });

  it('runs graph-wide merge review for imported artifact graphs and exposes retryable failures', async () => {
    let graphMergeCalls = 0;
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const url = String(input);
      if (!url.includes('/api/v1/yappc/artifact/graph/merge')) {
        return Promise.resolve(
          new Response(JSON.stringify({ id: 'audit-1' }), {
            status: 202,
            headers: { 'Content-Type': 'application/json' },
          }),
        );
      }

      graphMergeCalls += 1;
      if (graphMergeCalls === 1) {
        return Promise.resolve(
          new Response('graph merge unavailable', {
            status: 503,
            headers: { 'Content-Type': 'text/plain' },
          }),
        );
      }

      return Promise.resolve(
        new Response(
          JSON.stringify({
            success: true,
            operation: 'merge',
            result: {
              mergedModel: { nodeIds: ['art-1:page', 'art-1:residual:island-a'] },
              conflicts: [],
              fieldProvenance: { nodeIds: 'right' },
              conflictCount: 0,
            },
            message: 'Three-way merge completed with 0 conflicts',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    render(
      <PageDesigner
        projectId="proj-1"
        auditContext={{
          tenantId: 'tenant-1',
          workspaceId: 'workspace-1',
          projectId: 'proj-1',
          userId: 'reviewer-1',
        }}
      />,
    );
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '{ "pages": [] }' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    fireEvent.click(screen.getByTestId('page-designer-graph-merge-review-run'));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/yappc/artifact/graph/merge',
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          body: expect.stringContaining('"tenantId":"tenant-1"'),
        }),
      );
    });
    expect(await screen.findByTestId('page-designer-graph-merge-review-error')).toHaveTextContent(
      'graph merge unavailable',
    );
    expect(screen.getByTestId('page-designer-graph-merge-review-run')).toHaveTextContent(
      'Retry graph merge review',
    );

    fireEvent.click(screen.getByTestId('page-designer-graph-merge-review-run'));

    expect(await screen.findByTestId('page-designer-graph-merge-review-result')).toHaveTextContent(
      'Merge result: 0 conflicts',
    );
    expect(screen.getByTestId('page-designer-graph-merge-review')).toHaveTextContent(
      'Artifact graph merge review: passed',
    );
  });

  it('persists import review queue decisions for loss points before marking them decided', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          artifactId: 'art-1',
          reviewItemId: 'loss-0-unsupported-pattern-section.hero',
          kind: 'loss-point',
          decision: 'skipped',
          auditRecordId: 'audit-import-review-1',
          auditRecorded: true,
          reviewedAt: '2026-05-07T00:00:00.000Z',
        }),
        { status: 201, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<PageDesigner projectId="proj-1" />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '{ "pages": [] }' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    expect(screen.getByTestId('page-designer-import-review-queue')).toHaveTextContent(
      'Import review queue: 0/2 decided',
    );
    expect(screen.getByTestId('import-review-gate-explanation')).toHaveTextContent(
      /need an explicit apply or skip decision/i,
    );
    expect(
      screen.getByTestId('page-designer-import-review-diff-loss-0-unsupported-pattern-section.hero'),
    ).toHaveTextContent('Source path: section.hero');
    expect(
      screen.getByTestId('page-designer-import-review-diff-loss-0-unsupported-pattern-section.hero'),
    ).toHaveTextContent('Governed builder impact');
    expect(screen.getAllByText(/Hero animation could not be represented/).length).toBeGreaterThan(0);

    fireEvent.click(
      screen.getByTestId(
        'page-designer-import-review-skip-loss-0-unsupported-pattern-section.hero',
      ),
    );

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/yappc/artifacts/art-1/import-review-decisions',
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          body: JSON.stringify({
            reviewItemId: 'loss-0-unsupported-pattern-section.hero',
            kind: 'loss-point',
            decision: 'skipped',
            label: 'unsupported-pattern at section.hero',
            details: 'Hero animation could not be represented as a canonical component.',
            notes: 'Import review queue decision recorded from PageDesigner: skipped.',
          }),
        }),
      );
    });

    expect(
      await screen.findByTestId('page-designer-import-review-decision-loss-0-unsupported-pattern-section.hero'),
    ).toHaveTextContent('Decision: skipped');
    expect(screen.getByTestId('page-designer-import-review-queue')).toHaveTextContent(
      'Import review queue: 1/2 decided',
    );
  });

  it('promotes decompiled residual islands to registry candidates', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          candidateId: 'candidate-island-a',
          artifactId: 'art-1',
          residualIslandId: 'island-a',
          proposedContractName: 'IslandACandidate',
          status: 'NEEDS_REVIEW',
          auditRecordId: 'audit-candidate-1',
          createdAt: '2026-05-07T00:00:00.000Z',
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<PageDesigner projectId="proj-1" />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '{ "pages": [] }' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    fireEvent.click(screen.getByTestId('page-designer-residual-promote-island-a'));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/yappc/artifacts/art-1/residual-islands/island-a/registry-candidates',
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          body: JSON.stringify({
            proposedContractName: 'IslandACandidate',
            source: 'decompiled-import',
            notes: 'Promote decompiled residual island to reviewed registry candidate.',
          }),
        }),
      );
    });
    await waitFor(() => {
      expect(screen.queryByTestId('page-designer-residuals')).not.toBeInTheDocument();
    });
    expect(screen.getByTestId('page-designer-registry-candidates')).toHaveTextContent(
      'Registry candidates: 1 awaiting review',
    );
    expect(screen.getByTestId('page-designer-registry-candidate-island-a')).toHaveTextContent(
      'IslandACandidate',
    );
    expect(screen.getByTestId('page-designer-import-review-decision-residual-island-a')).toHaveTextContent(
      'Decision: promoted',
    );
  });

  it('persists residual island review decisions before clearing pending islands', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          artifactId: 'art-1',
          residualIslandId: 'island-a',
          decision: 'ACCEPTED',
          auditRecordId: 'audit-residual-1',
          reviewedAt: '2026-05-07T00:00:00.000Z',
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    render(<PageDesigner projectId="proj-1" />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '{ "pages": [] }' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    fireEvent.click(screen.getByTestId('page-designer-residual-accept-island-a'));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/v1/yappc/artifacts/art-1/residual-islands/island-a/review',
        expect.objectContaining({
          method: 'POST',
          credentials: 'include',
          body: JSON.stringify({
            decision: 'ACCEPTED',
            notes: 'Residual island accepted during PageDesigner import review.',
          }),
        }),
      );
    });
    await waitFor(() => {
      expect(screen.queryByTestId('page-designer-residuals')).not.toBeInTheDocument();
    });
  });

  it('notifies the parent document observer after a successful import', () => {
    const onDocumentChange = vi.fn();
    render(<PageDesigner projectId="proj-1" onDocumentChange={onDocumentChange} />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: '{ "pages": [] }' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    expect(onDocumentChange).toHaveBeenCalledWith(
      expect.objectContaining({ id: 'doc-import' }),
      expect.objectContaining({ valid: true }),
      expect.objectContaining({
        operation: 'document-update',
        commandType: 'import-document',
      }),
    );
  });

  it('rejects legacy source command syntax in the semantic import textarea', async () => {
    render(<PageDesigner projectId="proj-1" />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    const textarea = screen.getByTestId('page-designer-import-textarea') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: 'source:tsx:https://example.com/Page.tsx' } });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    expect(await screen.findByText(/Source imports must use the governed source workflow/i)).toBeInTheDocument();
    expect(importSourceToPageArtifactsMock).not.toHaveBeenCalled();
  });

  it('imports from guided source fields without requiring command syntax', async () => {
    const onImportArtifacts = vi.fn();
    render(
      <PageDesigner
        projectId="proj-1"
        auditContext={{
          userId: 'user-1',
          tenantId: 'tenant-1',
          workspaceId: 'workspace-1',
          projectId: 'proj-1',
          artifactId: 'artifact-1',
          phase: 'SHAPE',
        }}
        onImportArtifacts={onImportArtifacts}
      />,
    );
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    expect(screen.getByText(/Step 1: choose an import path/i)).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('page-import-mode-source'));
    expect(screen.getByTestId('import-trust-explanation')).toHaveTextContent(
      /preview trust level determines/i,
    );
    fireEvent.change(screen.getByTestId('page-designer-source-type'), {
      target: { value: 'route' },
    });
    fireEvent.change(screen.getByTestId('page-designer-source-locator'), {
      target: { value: 'https://example.com/routes/Home.tsx' },
    });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    await waitFor(() => {
      expect(importSourceToPageArtifactsMock).toHaveBeenCalledWith(
        expect.objectContaining({
          sourceType: 'route',
          source: 'https://example.com/routes/Home.tsx',
          projectId: 'proj-1',
        }),
        'import',
      );
    });
    expect(onImportArtifacts).toHaveBeenCalled();
  });

  it('blocks governed source import with an actionable runtime health message when compiler runtime is unavailable', async () => {
    checkArtifactCompilerRuntimeHealthMock.mockResolvedValue({
      status: 'unavailable',
      checkedAt: '2026-05-07T16:00:00.000Z',
      requirements: [
        {
          id: 'artifact-analyzer-http',
          label: 'YAPPC artifact analyzer HTTP runtime',
          required: true,
          endpoint: 'http://localhost:8080/api/v1/yappc/artifact',
          healthUrl: 'http://localhost:8080/health',
          description: 'Required for governed source import.',
        },
      ],
      unavailableRequirements: [
        {
          id: 'artifact-analyzer-http',
          label: 'YAPPC artifact analyzer HTTP runtime',
          required: true,
          endpoint: 'http://localhost:8080/api/v1/yappc/artifact',
          healthUrl: 'http://localhost:8080/health',
          description: 'Required for governed source import.',
        },
      ],
      message: 'Artifact compiler runtime unavailable. Start or configure: YAPPC artifact analyzer HTTP runtime (http://localhost:8080/health).',
    });

    render(
      <PageDesigner
        projectId="proj-1"
        auditContext={{
          userId: 'user-1',
          tenantId: 'tenant-1',
          workspaceId: 'workspace-1',
          projectId: 'proj-1',
          artifactId: 'artifact-1',
          phase: 'SHAPE',
        }}
      />,
    );
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    fireEvent.click(screen.getByTestId('page-import-mode-source'));
    fireEvent.change(screen.getByTestId('page-designer-source-locator'), {
      target: { value: 'https://example.com/routes/Home.tsx' },
    });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    expect(await screen.findAllByText(/Artifact compiler runtime unavailable/i)).toHaveLength(2);
    expect(screen.getByTestId('page-designer-artifact-runtime-status')).toHaveTextContent('http://localhost:8080/health');
    expect(importSourceToPageArtifactsMock).not.toHaveBeenCalled();
  });

  it('requires authenticated tenant and workspace context for guided source imports', async () => {
    render(<PageDesigner projectId="proj-1" />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    fireEvent.click(screen.getByTestId('page-import-mode-source'));
    fireEvent.change(screen.getByTestId('page-designer-source-locator'), {
      target: { value: 'https://example.com/Page.tsx' },
    });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    expect(await screen.findByText(/authenticated tenant and workspace context/i)).toBeInTheDocument();
    expect(importSourceToPageArtifactsMock).not.toHaveBeenCalled();
  });

  it('requires active project context for guided source imports', async () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByTestId('page-designer-import-btn'));
    fireEvent.click(screen.getByTestId('page-import-mode-source'));
    fireEvent.change(screen.getByTestId('page-designer-source-locator'), {
      target: { value: 'https://example.com/Page.tsx' },
    });
    fireEvent.click(screen.getByTestId('page-designer-import-confirm'));

    expect(await screen.findByText(/active project context/i)).toBeInTheDocument();
    expect(importSourceToPageArtifactsMock).not.toHaveBeenCalled();
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
        rollbackMetadata: expect.objectContaining({
          strategy: 'restore-builder-document',
          reason: expect.stringContaining('Restore'),
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
