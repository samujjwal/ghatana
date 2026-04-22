/**
 * PageDesigner component tests
 *
 * Covers core CRUD interactions: add, select, delete, edit,
 * and BuilderDocument sync via onDocumentChange.
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

let documentSequence = 0;
let insertedSequence = 0;

// ---- Module mocks -----------------------------------------------------------

vi.mock('@ghatana/design-system', () => ({
  Box: ({ children, ...p }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', p, children),
  Stack: ({ children, ...p }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', p, children),
  Typography: ({ children, ...p }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('p', p, children),
  IconButton: ({ children, onClick, title }: React.PropsWithChildren<{ onClick?: () => void; title?: string }>) =>
    React.createElement('button', { onClick, title }, children),
  Divider: () => React.createElement('hr'),
  Button: ({ children, onClick, 'data-testid': dtid }: React.PropsWithChildren<{ onClick?: () => void; 'data-testid'?: string }>) =>
    React.createElement('button', { onClick, 'data-testid': dtid }, children),
  Surface: ({ children, ...p }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', p, children),
  Drawer: ({ open, children }: { open: boolean; children: React.ReactNode }) =>
    open ? React.createElement('div', { 'data-testid': 'property-drawer' }, children) : null,
}));

vi.mock('../ComponentRenderer', () => ({
  ComponentRenderer: ({ data, isSelected, onClick }: { data: { id: string; type: string }; isSelected: boolean; onClick: () => void }) =>
    React.createElement('div', {
      'data-testid': `component-${data.id}`,
      'data-selected': isSelected,
      onClick,
    }, data.type),
}));

vi.mock('../PropertyForm', () => ({
  PropertyForm: ({ componentData, onUpdate, onCancel }: {
    componentData: { id: string; type: string };
    onUpdate: (d: unknown) => void;
    onCancel: () => void;
  }) =>
    React.createElement('div', { 'data-testid': 'property-form' },
      React.createElement('button', {
        'data-testid': 'save-property',
        onClick: () => onUpdate(componentData),
      }, 'Save'),
      React.createElement('button', { 'data-testid': 'cancel-property', onClick: onCancel }, 'Cancel'),
    ),
}));

vi.mock('../schemas', () => ({
  getDefaultComponentData: (type: string) => ({
    id: `new-${type}-${Date.now()}`,
    type,
    text: '',
    variant: 'contained',
    color: 'primary',
    size: 'medium',
    disabled: false,
    fullWidth: false,
  }),
}));

// builder-document-adapter uses @ghatana/ui-builder — mock it entirely
vi.mock('../builder-document-adapter', () => ({
  componentDataToBuilderDocument: vi.fn((components: unknown[], options?: { existingDocument?: { id: string; metadata: { createdAt: string } } }) => ({
    id: options?.existingDocument?.id ?? `doc-${++documentSequence}`,
    version: '1',
    name: 'Test Page',
    designSystem: { id: 'ds', name: 'DS', version: '1', tokenSetIds: [], componentContracts: [], themeId: 'default' },
    rootNodes: components.map((c: { id: string }) => c.id),
    nodes: new Map(components.map((c: { id: string; type: string }) => [c.id, { id: c.id, contractName: c.type, props: c, slots: {}, bindings: [], metadata: {} }])),
    metadata: { createdAt: options?.existingDocument?.metadata.createdAt ?? 'created', updatedAt: 'updated' },
  })),
  componentDataToInsertableInstance: vi.fn((component: { type: string; [key: string]: unknown }) => ({
    contractName: component.type,
    props: { ...component, type: undefined, id: undefined },
    slots: {},
    bindings: [],
    metadata: {},
  })),
  componentDataToBuilderProps: vi.fn((component: { id: string; type: string; [key: string]: unknown }) => {
    const { id: _id, type: _type, ...props } = component;
    return props;
  }),
  builderDocumentToComponentData: vi.fn((doc: { nodes: Map<string, { id: string; contractName: string }> }) =>
    Array.from(doc.nodes.values()).map((n) => ({ id: n.id, type: n.contractName })),
  ),
  isBuilderDocument: vi.fn((v: unknown) => Boolean(v && typeof v === 'object' && 'nodes' in (v as object))),
}));

vi.mock('@ghatana/ui-builder', () => ({
  insertNode: vi.fn((document: {
    id: string;
    rootNodes: string[];
    nodes: Map<string, { id: string; contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }>;
    metadata: { createdAt: string; updatedAt: string };
  }, instance: { contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }, _parentId: unknown, _slotName: unknown, bus?: { onNodeInserted?: (payload: { nodeId: string }) => void }) => {
    const nodeId = `inserted-${++insertedSequence}`;
    const nextDocument = {
      ...document,
      rootNodes: [...document.rootNodes, nodeId],
      nodes: new Map(document.nodes),
      metadata: { ...document.metadata, updatedAt: 'updated' },
    };

    nextDocument.nodes.set(nodeId, {
      id: nodeId,
      contractName: instance.contractName,
      props: instance.props,
      slots: instance.slots,
      bindings: instance.bindings,
      metadata: instance.metadata,
    });

    bus?.onNodeInserted?.({ nodeId });
    return nextDocument;
  }),
  deleteNode: vi.fn((document: {
    rootNodes: string[];
    nodes: Map<string, { id: string; contractName: string }>;
    metadata: { createdAt: string; updatedAt: string };
  }, nodeId: string) => {
    const nextNodes = new Map(document.nodes);
    nextNodes.delete(nodeId);

    return {
      ...document,
      rootNodes: document.rootNodes.filter((id) => id !== nodeId),
      nodes: nextNodes,
      metadata: { ...document.metadata, updatedAt: 'updated' },
    };
  }),
  updateNodeProps: vi.fn((document: {
    nodes: Map<string, { id: string; contractName: string; props: Record<string, unknown>; slots: Record<string, string[]>; bindings: unknown[]; metadata: Record<string, unknown> }>;
    metadata: { createdAt: string; updatedAt: string };
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
      metadata: { ...document.metadata, updatedAt: 'updated' },
    };
  }),
}));

// ---- Subject under test -----------------------------------------------------

import { PageDesigner } from '../PageDesigner';

// ---------------------------------------------------------------------------

describe('PageDesigner', () => {
  const onComponentsChange = vi.fn();
  const onDocumentChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    documentSequence = 0;
    insertedSequence = 0;
  });

  it('renders the component palette with all 5 component types', () => {
    render(<PageDesigner />);
    expect(screen.getByText('Components')).toBeDefined();
    for (const label of ['Button', 'Card', 'Text Field', 'Typography', 'Container']) {
      expect(screen.getByText(label)).toBeDefined();
    }
  });

  it('renders empty state when no components are present', () => {
    render(<PageDesigner />);
    expect(screen.getByText('Add components to get started')).toBeDefined();
  });

  it('adds a component when a palette button is clicked', () => {
    render(<PageDesigner onComponentsChange={onComponentsChange} />);
    fireEvent.click(screen.getByText('Button'));
    expect(onComponentsChange).toHaveBeenCalledWith(
      expect.arrayContaining([expect.objectContaining({ type: 'button' })]),
    );
  });

  it('shows the added component in the design area', () => {
    render(<PageDesigner onComponentsChange={onComponentsChange} />);
    fireEvent.click(screen.getByText('Button'));
    const added = screen.getAllByText('button');
    expect(added.length).toBeGreaterThan(0);
  });

  it('selects a component on click — toolbar appears', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Button'));
    // Toolbar should be visible after palette click (handleAddComponent sets selectedId)
    expect(screen.getByTitle('Edit Properties')).toBeDefined();
    expect(screen.getByTitle('Delete')).toBeDefined();
  });

  it('shows edit/delete toolbar when a component is selected', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Button'));
    const componentEls = screen.getAllByTestId(/component-/);
    fireEvent.click(componentEls[0]);
    expect(screen.getByTitle('Edit Properties')).toBeDefined();
    expect(screen.getByTitle('Delete')).toBeDefined();
  });

  it('deletes the selected component', () => {
    render(<PageDesigner onComponentsChange={onComponentsChange} />);
    fireEvent.click(screen.getByText('Button'));
    // Component is added and selected; toolbar with Delete button is visible
    fireEvent.click(screen.getByTitle('Delete'));
    // After deletion the design area shows the empty-state placeholder again
    expect(screen.getByText('Add components to get started')).toBeDefined();
    // And no component renderer elements remain in the canvas
    expect(screen.queryAllByTestId(/^component-/)).toHaveLength(0);
  });

  it('opens the property drawer on edit', () => {
    render(<PageDesigner />);
    fireEvent.click(screen.getByText('Button'));
    const componentEls = screen.getAllByTestId(/component-/);
    fireEvent.click(componentEls[0]);
    fireEvent.click(screen.getByTitle('Edit Properties'));
    expect(screen.getByTestId('property-drawer')).toBeDefined();
    expect(screen.getByTestId('property-form')).toBeDefined();
  });

  it('emits onDocumentChange when components change', () => {
    render(<PageDesigner onDocumentChange={onDocumentChange} />);
    // onDocumentChange fires on every components change (via useEffect)
    fireEvent.click(screen.getByText('Button'));
    expect(onDocumentChange).toHaveBeenCalled();
  });

  it('preserves BuilderDocument identity across edits', () => {
    render(<PageDesigner onDocumentChange={onDocumentChange} />);

    fireEvent.click(screen.getByText('Button'));
    fireEvent.click(screen.getByText('Card'));

    const documentIds = onDocumentChange.mock.calls.map(([document]) => {
      return (document as { id: string }).id;
    });

    expect(documentIds.length).toBeGreaterThan(1);
    expect(new Set(documentIds)).toEqual(new Set(['doc-1']));
  });

  it('initialises from a ComponentData array', () => {
    const initial = [
      { id: 'btn-1', type: 'button', text: 'Go', variant: 'contained', color: 'primary', size: 'medium', disabled: false, fullWidth: false },
    ];
    render(<PageDesigner initialComponents={initial} />);
    expect(screen.getByTestId('component-btn-1')).toBeDefined();
  });
});
