/**
 * DraggableComponent tests — CC-AL
 *
 * Covers:
 *   - DraggableItem: renders children, sets draggable, fires onDragStart callback
 *   - ComponentLibrary: renders component labels, calls onComponentSelect on click
 *   - DraggableCanvas (default export): empty state, adds component from library
 */

import React, { useState } from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import DraggableCanvas, { DraggableItem, ComponentLibrary } from '../DraggableComponent';
import type { ComponentType } from '../DraggableComponent';

// ---------------------------------------------------------------------------
// DraggableItem
// ---------------------------------------------------------------------------

describe('DraggableItem', () => {
  it('renders its children', () => {
    render(
      <DraggableItem id="item-1" type="text">
        <span>My child content</span>
      </DraggableItem>
    );
    expect(screen.getByText('My child content')).toBeDefined();
  });

  it('renders a button when type="button"', () => {
    render(<DraggableItem id="item-btn" type="button" />);
    expect(screen.getByRole('button')).toBeDefined();
  });

  it('renders text content when type="text" and no children', () => {
    render(<DraggableItem id="item-text" type="text" />);
    expect(screen.getByText('Text content goes here')).toBeDefined();
  });

  it('renders fallback message for unknown type', () => {
    render(<DraggableItem id="item-x" type={'custom' as ComponentType} />);
    // 'custom' hits the default case in ComponentRenderer
    expect(screen.getByText(/Component custom not found/)).toBeDefined();
  });

  it('calls onDragStart with item id when drag starts', () => {
    const onDragStart = vi.fn();
    render(
      <DraggableItem id="drag-id-42" type="text" onDragStart={onDragStart} />
    );
    // The outer Box is draggable
    const draggable = document.querySelector('[draggable]') as HTMLElement;
    expect(draggable).toBeDefined();
    fireEvent.dragStart(draggable, { dataTransfer: { setData: vi.fn() } });
    expect(onDragStart).toHaveBeenCalledWith('drag-id-42');
  });
});

// ---------------------------------------------------------------------------
// ComponentLibrary
// ---------------------------------------------------------------------------

describe('ComponentLibrary', () => {
  const sampleComponents = [
    { type: 'button' as ComponentType, label: 'Button' },
    { type: 'input' as ComponentType, label: 'Input' },
    { type: 'card' as ComponentType, label: 'Card' },
  ];

  it('renders all component labels', () => {
    render(
      <ComponentLibrary
        components={sampleComponents}
        onComponentSelect={vi.fn()}
      />
    );
    expect(screen.getByText('Button')).toBeDefined();
    expect(screen.getByText('Input')).toBeDefined();
    expect(screen.getByText('Card')).toBeDefined();
  });

  it('calls onComponentSelect with type when an item is clicked', () => {
    const onComponentSelect = vi.fn();
    render(
      <ComponentLibrary
        components={sampleComponents}
        onComponentSelect={onComponentSelect}
      />
    );
    fireEvent.click(screen.getByText('Input'));
    expect(onComponentSelect).toHaveBeenCalledWith('input');
  });

  it('renders "Component Library" heading', () => {
    render(
      <ComponentLibrary
        components={sampleComponents}
        onComponentSelect={vi.fn()}
      />
    );
    expect(screen.getByText('Component Library')).toBeDefined();
  });
});

// ---------------------------------------------------------------------------
// DraggableCanvas (controlled wrapper for state)
// ---------------------------------------------------------------------------

function ControlledCanvas() {
  const [items, setItems] = useState<Array<{ id: string; type: ComponentType; props?: Record<string, unknown> }>>([]);
  return <DraggableCanvas items={items} onItemsChange={setItems} />;
}

describe('DraggableCanvas', () => {
  it('renders empty canvas message when items is empty', () => {
    render(<ControlledCanvas />);
    expect(screen.getByText(/click on components in the library/i)).toBeDefined();
  });

  it('renders Canvas heading', () => {
    render(<ControlledCanvas />);
    expect(screen.getByText('Canvas')).toBeDefined();
  });

  it('renders the component library sidebar', () => {
    render(<ControlledCanvas />);
    expect(screen.getByText('Component Library')).toBeDefined();
  });

  it('adds a component to the canvas when clicking a library item', () => {
    render(<ControlledCanvas />);
    // Click "Button" in the library
    fireEvent.click(screen.getByText('Button'));
    // Empty state message should be gone now
    expect(screen.queryByText(/click on components in the library/i)).toBeNull();
    // A button should appear on canvas
    expect(screen.getAllByRole('button').length).toBeGreaterThanOrEqual(1);
  });

  it('renders passed-in items', () => {
    const items = [
      { id: 'i1', type: 'text' as ComponentType },
      { id: 'i2', type: 'text' as ComponentType },
    ];
    render(<DraggableCanvas items={items} onItemsChange={vi.fn()} />);
    // Two text components = two "Text content goes here" blocks
    const texts = screen.getAllByText('Text content goes here');
    expect(texts.length).toBe(2);
  });
});
