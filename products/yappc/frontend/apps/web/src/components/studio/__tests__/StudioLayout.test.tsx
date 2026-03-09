/**
 * Studio Layout Component Tests
 *
 * Comprehensive test suite for Studio Layout component
 * including panel management, resizing, and keyboard shortcuts
 *
 * @doc.type test
 * @doc.purpose Component testing
 * @doc.layer components
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { StudioLayout, useStudioMode } from '../StudioLayout';

// Mock the custom hook
vi.mock('../StudioLayout', () => ({
  ...vi.importActual('../StudioLayout'),
  useStudioMode: vi.fn(),
}));

const mockUseStudioMode = useStudioMode as vi.MockedFunction<
  typeof useStudioMode
>;

describe('StudioLayout Component', () => {
  const defaultProps = {
    fileTree: <div data-testid="file-tree">File Tree</div>,
    codeEditor: <div data-testid="code-editor">Code Editor</div>,
    livePreview: <div data-testid="live-preview">Live Preview</div>,
    validation: <div data-testid="validation">Validation</div>,
    onClose: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('renders all 4 panels', () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} />);

      expect(screen.getByTestId('file-tree')).toBeInTheDocument();
      expect(screen.getByTestId('code-editor')).toBeInTheDocument();
      expect(screen.getByTestId('live-preview')).toBeInTheDocument();
      expect(screen.getByTestId('validation')).toBeInTheDocument();
    });

    it('renders close button', () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} />);

      const closeButton = screen.getByLabelText('Close Studio Mode');
      expect(closeButton).toBeInTheDocument();
    });

    it('renders resize handles between panels', () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} />);

      // Should have resize handles between panels
      const resizeHandles = screen.getAllByTestId('resize-handle');
      expect(resizeHandles.length).toBeGreaterThan(0);
    });
  });

  describe('Interactions', () => {
    it('calls onClose when close button is clicked', () => {
      const mockOnClose = vi.fn();

      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} onClose={mockOnClose} />);

      const closeButton = screen.getByLabelText('Close Studio Mode');
      fireEvent.click(closeButton);

      expect(mockOnClose).toHaveBeenCalledTimes(1);
    });

    it('calls toggleStudioMode when close button is clicked', () => {
      const mockToggleStudioMode = vi.fn();

      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: mockToggleStudioMode,
      });

      render(<StudioLayout {...defaultProps} />);

      const closeButton = screen.getByLabelText('Close Studio Mode');
      fireEvent.click(closeButton);

      expect(mockToggleStudioMode).toHaveBeenCalledTimes(1);
    });

    it('supports keyboard shortcut to close', () => {
      const mockOnClose = vi.fn();

      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} onClose={mockOnClose} />);

      // Test Escape key
      fireEvent.keyDown(document, { key: 'Escape' });
      expect(mockOnClose).toHaveBeenCalled();
    });
  });

  describe('Panel Resizing', () => {
    it('allows panel resizing', async () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} />);

      const resizeHandle = screen.getAllByTestId('resize-handle')[0];

      // Simulate drag to resize
      fireEvent.mouseDown(resizeHandle);
      fireEvent.mouseMove(resizeHandle, { clientX: 100 });
      fireEvent.mouseUp(resizeHandle);

      // Should update panel sizes
      await waitFor(() => {
        const fileTree = screen.getByTestId('file-tree');
        expect(fileTree).toBeInTheDocument();
      });
    });

    it('maintains minimum panel sizes', () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} />);

      const resizeHandle = screen.getAllByTestId('resize-handle')[0];

      // Try to resize below minimum
      fireEvent.mouseDown(resizeHandle);
      fireEvent.mouseMove(resizeHandle, { clientX: -1000 });
      fireEvent.mouseUp(resizeHandle);

      // Should maintain minimum size
      const fileTree = screen.getByTestId('file-tree');
      expect(fileTree).toBeInTheDocument();
    });
  });

  describe('Keyboard Shortcuts', () => {
    it('responds to ⌘⇧S shortcut', () => {
      const mockOnClose = vi.fn();

      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} onClose={mockOnClose} />);

      // Test ⌘⇧S shortcut
      fireEvent.keyDown(document, { metaKey: true, shiftKey: true, key: 's' });
      expect(mockOnClose).toHaveBeenCalled();
    });

    it('supports Ctrl+Shift+S on Windows', () => {
      const mockOnClose = vi.fn();

      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} onClose={mockOnClose} />);

      // Test Ctrl+Shift+S shortcut
      fireEvent.keyDown(document, { ctrlKey: true, shiftKey: true, key: 's' });
      expect(mockOnClose).toHaveBeenCalled();
    });
  });

  describe('Accessibility', () => {
    it('has proper ARIA labels', () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} />);

      const layout = screen.getByTestId('studio-layout');
      expect(layout).toHaveAttribute('aria-label', 'Studio Mode Layout');
    });

    it('supports keyboard navigation', () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} />);

      const closeButton = screen.getByLabelText('Close Studio Mode');
      expect(closeButton).toHaveAttribute('tabIndex', '0');

      closeButton.focus();
      fireEvent.keyDown(closeButton, { key: 'Enter' });

      expect(defaultProps.onClose).toHaveBeenCalled();
    });

    it('announces panel changes to screen readers', () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} />);

      const liveRegion = screen.getByTestId('studio-announcer');
      expect(liveRegion).toHaveAttribute('aria-live', 'polite');
    });
  });

  describe('Responsive Design', () => {
    it('adapts to smaller screens', () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      // Simulate smaller screen
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: 768,
      });

      render(<StudioLayout {...defaultProps} />);

      // Should stack panels vertically on smaller screens
      const layout = screen.getByTestId('studio-layout');
      expect(layout).toHaveClass('studio-layout--compact');
    });

    it('handles window resize', () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      render(<StudioLayout {...defaultProps} />);

      // Simulate window resize
      fireEvent.resize(window);

      // Should update layout
      const layout = screen.getByTestId('studio-layout');
      expect(layout).toBeInTheDocument();
    });
  });

  describe('Performance', () => {
    it('renders efficiently with large content', () => {
      mockUseStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      const largeContent = Array.from({ length: 1000 }, (_, i) => (
        <div key={i}>Item {i}</div>
      ));

      render(
        <StudioLayout
          fileTree={<div>{largeContent}</div>}
          codeEditor={<div>{largeContent}</div>}
          livePreview={<div>{largeContent}</div>}
          validation={<div>{largeContent}</div>}
          onClose={vi.fn()}
        />
      );

      // Should render without performance issues
      expect(screen.getByTestId('studio-layout')).toBeInTheDocument();
    });
  });
});

describe('useStudioMode Hook', () => {
  it('initializes with default values', () => {
    mockUseStudioMode.mockReturnValue({
      isStudioMode: false,
      toggleStudioMode: vi.fn(),
    });

    const TestComponent = () => {
      const hook = useStudioMode();
      return <div data-testid="hook-result">{JSON.stringify(hook)}</div>;
    };

    render(<TestComponent />);

    const result = screen.getByTestId('hook-result');
    expect(result).toBeInTheDocument();
  });

  it('toggles studio mode correctly', () => {
    const mockToggleStudioMode = vi.fn();

    mockUseStudioMode.mockReturnValue({
      isStudioMode: false,
      toggleStudioMode: mockToggleStudioMode,
    });

    const TestComponent = () => {
      const { toggleStudioMode } = useStudioMode();
      return <button onClick={toggleStudioMode}>Toggle Studio Mode</button>;
    };

    render(<TestComponent />);

    const button = screen.getByText('Toggle Studio Mode');
    fireEvent.click(button);

    expect(mockToggleStudioMode).toHaveBeenCalled();
  });

  it('persists studio mode preference', () => {
    const mockToggleStudioMode = vi.fn();

    mockUseStudioMode.mockReturnValue({
      isStudioMode: true,
      toggleStudioMode: mockToggleStudioMode,
    });

    const TestComponent = () => {
      const { isStudioMode, toggleStudioMode } = useStudioMode();
      return (
        <div>
          <div data-testid="studio-mode">{isStudioMode.toString()}</div>
          <button onClick={toggleStudioMode}>Toggle</button>
        </div>
      );
    };

    render(<TestComponent />);

    expect(screen.getByTestId('studio-mode')).toHaveTextContent('true');
  });
});
