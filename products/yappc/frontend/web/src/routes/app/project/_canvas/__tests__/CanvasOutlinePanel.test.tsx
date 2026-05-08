import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { CanvasOutlinePanel } from '../CanvasOutlinePanel';

describe('CanvasOutlinePanel', () => {
  it('renders an accessible outline minimap and selects nodes from markers', () => {
    const selectNodes = vi.fn();

    render(
      <CanvasOutlinePanel
        nodes={[
          {
            id: 'frame-1',
            type: 'frame',
            position: { x: 0, y: 0 },
            size: { width: 320, height: 200 },
            data: { title: 'Landing frame' },
          },
          {
            id: 'button-1',
            type: 'button',
            position: { x: 96, y: 80 },
            size: { width: 120, height: 44 },
            data: { label: 'Start trial' },
          },
        ]}
        selectedNodeIds={['button-1']}
        selectNodes={selectNodes}
        addNodeAtPosition={vi.fn()}
        getViewport={() => ({ x: 0, y: 0, zoom: 1 })}
      />,
    );

    expect(screen.getByTestId('canvas-outline-minimap')).toBeInTheDocument();
    expect(screen.getByLabelText(/Canvas outline map with 2 nodes, 1 selected/i)).toBeInTheDocument();
    expect(screen.getByTestId('canvas-outline-minimap-marker-button-1')).toHaveAttribute('data-selected', 'true');

    fireEvent.keyDown(screen.getByTestId('canvas-outline-minimap-marker-frame-1'), { key: 'Enter' });

    expect(selectNodes).toHaveBeenCalledWith(['frame-1']);
  });
});
