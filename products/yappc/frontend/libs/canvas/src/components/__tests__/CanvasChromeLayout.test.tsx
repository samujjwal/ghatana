/**
 * Canvas Chrome Layout Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'jotai';
import { CanvasChromeLayout } from '../CanvasChromeLayout';

describe('CanvasChromeLayout', () => {
  const renderWithProvider = (ui: React.ReactElement) => {
    return render(<Provider>{ui}</Provider>);
  };

  it('should render children', () => {
    renderWithProvider(
      <CanvasChromeLayout>
        <div data-testid="canvas-content">Canvas Content</div>
      </CanvasChromeLayout>
    );

    expect(screen.getByTestId('canvas-content')).toBeInTheDocument();
    expect(screen.getByText('Canvas Content')).toBeInTheDocument();
  });

  it('should render left rail when provided', () => {
    renderWithProvider(
      <CanvasChromeLayout
        leftRail={<div data-testid="left-rail">Left Rail</div>}
      >
        <div>Canvas</div>
      </CanvasChromeLayout>
    );

    expect(screen.getByTestId('left-rail')).toBeInTheDocument();
  });

  it('should render outline panel when provided', () => {
    renderWithProvider(
      <CanvasChromeLayout outline={<div data-testid="outline">Outline</div>}>
        <div>Canvas</div>
      </CanvasChromeLayout>
    );

    expect(screen.getByTestId('outline')).toBeInTheDocument();
  });

  it('should render inspector when provided', () => {
    renderWithProvider(
      <CanvasChromeLayout
        inspector={<div data-testid="inspector">Inspector</div>}
      >
        <div>Canvas</div>
      </CanvasChromeLayout>
    );

    expect(screen.getByTestId('inspector')).toBeInTheDocument();
  });

  it('should toggle calm mode with Ctrl+Shift+C', () => {
    renderWithProvider(
      <CanvasChromeLayout defaultCalmMode={false}>
        <div>Canvas</div>
      </CanvasChromeLayout>
    );

    // Initially no calm indicator
    expect(screen.queryByText('🌙 Calm Mode')).not.toBeInTheDocument();

    // Press Ctrl+Shift+C
    fireEvent.keyDown(window, {
      key: 'C',
      ctrlKey: true,
      shiftKey: true,
    });

    // Should show calm mode indicator
    expect(screen.getByText('🌙 Calm Mode')).toBeInTheDocument();
  });

  it('should have correct z-index hierarchy', () => {
    const { container } = renderWithProvider(
      <CanvasChromeLayout
        leftRail={<div data-testid="left-rail">Left</div>}
        outline={<div data-testid="outline">Outline</div>}
        inspector={<div data-testid="inspector">Inspector</div>}
        minimap={<div data-testid="minimap">Minimap</div>}
        contextBar={<div data-testid="context-bar">Context</div>}
      >
        <div data-testid="canvas">Canvas</div>
      </CanvasChromeLayout>
    );

    const layers = {
      canvas: container.querySelector('.canvas-content-layer'),
      leftRail: container.querySelector('.canvas-left-rail-layer'),
      outline: container.querySelector('.canvas-outline-layer'),
      inspector: container.querySelector('.canvas-inspector-layer'),
      minimap: container.querySelector('.canvas-minimap-layer'),
    };

    // Canvas should be lowest
    expect(layers.canvas).toHaveStyle({ zIndex: 10 });

    // Left rail
    expect(layers.leftRail).toHaveStyle({ zIndex: 50 });

    // Outline
    expect(layers.outline).toHaveStyle({ zIndex: 60 });

    // Inspector
    expect(layers.inspector).toHaveStyle({ zIndex: 80 });

    // Minimap
    expect(layers.minimap).toHaveStyle({ zIndex: 70 });
  });
});
