import React from 'react';
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

vi.mock('../CanvasRoute', () => ({
  CanvasRoute: () => <div data-testid="canvas-route-stub" />,
}));

import CanvasScene from '../CanvasScene';

describe('CanvasScene shim', () => {
  it('renders CanvasRoute compatibility surface', () => {
    render(<CanvasScene />);

    expect(screen.getByTestId('canvas-route-stub')).toBeInTheDocument();
  });
});
