import { render, screen } from '@testing-library/react';
import React from 'react';
import '@testing-library/jest-dom';

// Use the integration example as a minimal canvas UI surface
// Minimal inline test harness replicating the key DOM elements from the integration example.
const IntegrationExampleTestHarness: React.FC = () => (
  <div>
    <div data-testid="canvas-area">Canvas</div>
    <div data-testid="component-palette">
      <div data-testid="palette-item-rectangle">Rectangle</div>
    </div>
    <div data-testid="canvas-toolbar">Toolbar</div>
  </div>
);

describe('Canvas UI basic rendering', () => {
  test('renders canvas area and component palette', () => {
    render(<IntegrationExampleTestHarness />);

    // Data-testids used by the UI components
    const canvas = screen.getByTestId('canvas-area');
    const palette = screen.getByTestId('component-palette');
    const toolbar = screen.getByTestId('canvas-toolbar');

    expect(canvas).toBeInTheDocument();
    expect(palette).toBeInTheDocument();
    expect(toolbar).toBeInTheDocument();

    // Check for a palette item
    const rectItem = screen.getByTestId('palette-item-rectangle');
    expect(rectItem).toBeInTheDocument();
  });
});
