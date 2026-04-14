/**
 * PageDesigner component tests
 *
 * Covers core CRUD interactions: add, select, delete, edit,
 * and BuilderDocument sync via onDocumentChange.
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';

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
  componentDataToBuilderDocument: vi.fn((components: unknown[]) => ({
    id: 'doc-id',
    version: '1',
    name: 'Test Page',
    designSystem: { id: 'ds', name: 'DS', version: '1', tokenSetIds: [], componentContracts: [], themeId: 'default' },
    rootNodes: components.map((c: { id: string }) => c.id),
    nodes: new Map(components.map((c: { id: string; type: string }) => [c.id, { id: c.id, contractName: c.type, props: c, slots: {}, bindings: [], metadata: {} }])),
    metadata: { createdAt: '', updatedAt: '' },
  })),
  builderDocumentToComponentData: vi.fn((doc: { nodes: Map<string, { id: string; contractName: string }> }) =>
    Array.from(doc.nodes.values()).map((n) => ({ id: n.id, type: n.contractName })),
  ),
  isBuilderDocument: vi.fn((v: unknown) => Boolean(v && typeof v === 'object' && 'nodes' in (v as object))),
}));

// ---- Subject under test -----------------------------------------------------

import { PageDesigner } from '../PageDesigner';

// ---------------------------------------------------------------------------

describe('PageDesigner', () => {
  const onComponentsChange = vi.fn();
  const onDocumentChange = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
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

  it('initialises from a ComponentData array', () => {
    const initial = [
      { id: 'btn-1', type: 'button', text: 'Go', variant: 'contained', color: 'primary', size: 'medium', disabled: false, fullWidth: false },
    ];
    render(<PageDesigner initialComponents={initial} />);
    expect(screen.getByTestId('component-btn-1')).toBeDefined();
  });
});
