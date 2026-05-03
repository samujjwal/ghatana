import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

const validateDocumentMock = vi.fn();

vi.mock('@ghatana/design-system', () => ({
  Box: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', props, children),
  Stack: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', props, children),
  Typography: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('p', props, children),
  IconButton: ({ children, onClick, title }: React.PropsWithChildren<{ onClick?: () => void; title?: string }>) =>
    React.createElement('button', { onClick, title }, children),
  Button: ({ children, onClick, title, 'data-testid': testId }: React.PropsWithChildren<{ onClick?: () => void; title?: string; 'data-testid'?: string }>) =>
    React.createElement('button', { onClick, title, 'data-testid': testId }, children),
  Surface: ({ children, ...props }: React.PropsWithChildren<Record<string, unknown>>) =>
    React.createElement('div', props, children),
  Drawer: ({ open, children }: { open: boolean; children: React.ReactNode }) =>
    open ? React.createElement('div', { 'data-testid': 'property-drawer' }, children) : null,
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
  normalizeContractName: (type: string) => (type === 'textfield' ? 'TextField' : type),
  getContractByName: (contractName: string) => ({ name: contractName, props: [], slots: [], layout: { isContainer: false } }),
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
  insertNode: vi.fn((document: {
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
  moveNode: vi.fn((document: { rootNodes: string[] }, nodeId: string) => ({
    ...document,
    rootNodes: document.rootNodes.filter((id) => id !== nodeId),
  })),
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
}));

import { PageDesigner } from '../PageDesigner';

describe('PageDesigner', () => {
  const onComponentsChange = vi.fn();
  const onDocumentChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
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
});
