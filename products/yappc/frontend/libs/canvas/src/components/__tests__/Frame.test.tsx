/**
 * Frame Component Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'jotai';
import { Frame } from '../Frame';

describe('Frame', () => {
  const defaultProps = {
    id: 'frame-1',
    phase: 'discover' as const,
    label: 'Test Frame',
    x: 100,
    y: 100,
    width: 400,
    height: 300,
  };

  const renderWithProvider = (ui: React.ReactElement) => {
    return render(<Provider>{ui}</Provider>);
  };

  it('should render frame with label', () => {
    renderWithProvider(<Frame {...defaultProps} />);
    expect(screen.getByText('Test Frame')).toBeInTheDocument();
  });

  it('should render phase icon', () => {
    renderWithProvider(<Frame {...defaultProps} />);
    // Discover phase icon is 🔍
    expect(screen.getByText('🔍')).toBeInTheDocument();
  });

  it('should render children when not collapsed', () => {
    renderWithProvider(
      <Frame {...defaultProps} collapsed={false}>
        <div data-testid="frame-content">Frame Content</div>
      </Frame>
    );
    expect(screen.getByTestId('frame-content')).toBeInTheDocument();
  });

  it('should hide children when collapsed', () => {
    renderWithProvider(
      <Frame {...defaultProps} collapsed={true}>
        <div data-testid="frame-content">Frame Content</div>
      </Frame>
    );
    expect(screen.queryByTestId('frame-content')).not.toBeInTheDocument();
  });

  it('should call onSelect when clicked', () => {
    const onSelect = vi.fn();
    renderWithProvider(<Frame {...defaultProps} onSelect={onSelect} />);

    const frame = screen.getByText('Test Frame').closest('.canvas-frame');
    fireEvent.click(frame!);

    expect(onSelect).toHaveBeenCalledWith('frame-1');
  });

  it('should call onToggleCollapsed when collapse button clicked', () => {
    const onToggleCollapsed = vi.fn();
    renderWithProvider(
      <Frame
        {...defaultProps}
        collapsed={false}
        onToggleCollapsed={onToggleCollapsed}
      />
    );

    const collapseBtn = screen.getByRole('button', { name: /collapse/i });
    fireEvent.click(collapseBtn);

    expect(onToggleCollapsed).toHaveBeenCalledWith('frame-1', true);
  });

  it('should show correct collapse icon based on state', () => {
    const { rerender } = renderWithProvider(
      <Frame {...defaultProps} collapsed={false} />
    );

    expect(screen.getByText('▲')).toBeInTheDocument();

    rerender(
      <Provider>
        <Frame {...defaultProps} collapsed={true} />
      </Provider>
    );

    expect(screen.getByText('▼')).toBeInTheDocument();
  });

  it('should apply selection styles when selected', () => {
    const { container } = renderWithProvider(
      <Frame {...defaultProps} selected={true} />
    );

    const frame = container.querySelector('[data-selected="true"]');
    expect(frame).toBeInTheDocument();
  });

  it('should position frame correctly', () => {
    const { container } = renderWithProvider(<Frame {...defaultProps} />);

    const frame = container.querySelector('.canvas-frame');
    expect(frame).toHaveStyle({
      left: '100px',
      top: '100px',
      width: '400px',
    });
  });

  it('should show resize handle when hovered', () => {
    const { container } = renderWithProvider(<Frame {...defaultProps} />);

    const frame = container.querySelector('.canvas-frame');
    fireEvent.mouseEnter(frame!);

    const resizeHandle = container.querySelector('.frame-resize-handle');
    expect(resizeHandle).toBeInTheDocument();
  });

  it('should show mini toolbar when hovered and not collapsed', () => {
    const { container } = renderWithProvider(
      <Frame {...defaultProps} collapsed={false} />
    );

    const frame = container.querySelector('.canvas-frame');
    fireEvent.mouseEnter(frame!);

    const toolbar = container.querySelector('.frame-toolbar');
    expect(toolbar).toBeInTheDocument();
  });

  it('should not show mini toolbar when collapsed', () => {
    const { container } = renderWithProvider(
      <Frame {...defaultProps} collapsed={true} />
    );

    const frame = container.querySelector('.canvas-frame');
    fireEvent.mouseEnter(frame!);

    const toolbar = container.querySelector('.frame-toolbar');
    expect(toolbar).not.toBeInTheDocument();
  });
});
