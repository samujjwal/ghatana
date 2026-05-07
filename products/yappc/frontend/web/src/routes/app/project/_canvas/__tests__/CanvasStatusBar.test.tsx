import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import { CanvasStatusBar } from '../CanvasStatusBar';

describe('CanvasStatusBar', () => {
  it('renders the shared remote-saved sync label', () => {
    render(
      <CanvasStatusBar
        activeTool="select"
        calmMode={false}
        currentPhase="Shape"
        drawingColor="#000000"
        drawingTool="pen"
        hasSelection={false}
        selectedCount={0}
        syncStatus="remote-saved"
        zoom={1}
      />,
    );

    expect(screen.getByTestId('canvas-sync-status')).toHaveTextContent('Remote draft saved');
  });

  it('renders the shared conflict sync label', () => {
    render(
      <CanvasStatusBar
        activeTool="select"
        calmMode={false}
        currentPhase="Shape"
        drawingColor="#000000"
        drawingTool="pen"
        hasSelection={false}
        selectedCount={0}
        syncStatus="conflict"
        zoom={1}
      />,
    );

    expect(screen.getByTestId('canvas-sync-status')).toHaveTextContent('Sync conflict detected');
  });
});
