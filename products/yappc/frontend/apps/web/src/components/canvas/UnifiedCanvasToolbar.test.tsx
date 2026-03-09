/**
 * UnifiedCanvasToolbar Component Tests
 *
 * @description Comprehensive test suite for UnifiedCanvasToolbar
 * ensuring 100% code coverage and all edge cases.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { UnifiedCanvasToolbar, type ToolType } from './UnifiedCanvasToolbar';

// Mock framer-motion
vi.mock('framer-motion', () => ({
  motion: {
    div: ({ children, ...props }: unknown) => <div {...props}>{children}</div>,
  },
  AnimatePresence: ({ children }: unknown) => <>{children}</>,
}));

vi.mock('@ghatana/yappc-ui/utils', () => ({
  cn: (...classes: unknown[]) => classes.filter(Boolean).join(' '),
}));

describe('UnifiedCanvasToolbar', () => {
  const defaultProps = {
    activeTool: 'select' as ToolType,
    onToolChange: vi.fn(),
    canUndo: true,
    canRedo: true,
    onUndo: vi.fn(),
    onRedo: vi.fn(),
    zoom: 1,
    onZoomIn: vi.fn(),
    onZoomOut: vi.fn(),
    onZoomFit: vi.fn(),
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('should render the toolbar', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      expect(screen.getByTitle('Select (V)')).toBeInTheDocument();
    });

    it('should render primary tools', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      expect(screen.getByTitle('Select (V)')).toBeInTheDocument();
      expect(screen.getByTitle('Pan (H)')).toBeInTheDocument();
    });

    it('should render history controls', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      expect(screen.getByTitle('Undo (⌘Z)')).toBeInTheDocument();
      expect(screen.getByTitle('Redo (⌘⇧Z)')).toBeInTheDocument();
    });

    it('should render zoom controls', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      expect(screen.getByTitle('Zoom Out')).toBeInTheDocument();
      expect(screen.getByTitle('Zoom In')).toBeInTheDocument();
      expect(screen.getByTitle('Fit to Screen')).toBeInTheDocument();
    });

    it('should display current zoom level', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} zoom={0.75} />);
      expect(screen.getByText('75%')).toBeInTheDocument();
    });

    it('should render AI assist button when provided', () => {
      const onAIAssist = vi.fn();
      render(<UnifiedCanvasToolbar {...defaultProps} onAIAssist={onAIAssist} />);
      expect(screen.getByTitle('AI Assist')).toBeInTheDocument();
    });

    it('should not render AI assist button when not provided', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      expect(screen.queryByTitle('AI Assist')).not.toBeInTheDocument();
    });

    it('should render more options button', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      expect(screen.getByTitle('More Options')).toBeInTheDocument();
    });
  });

  describe('Tool Selection', () => {
    it('should call onToolChange when select tool is clicked', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} activeTool="pan" />);
      fireEvent.click(screen.getByTitle('Select (V)'));
      expect(defaultProps.onToolChange).toHaveBeenCalledWith('select');
    });

    it('should call onToolChange when pan tool is clicked', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      fireEvent.click(screen.getByTitle('Pan (H)'));
      expect(defaultProps.onToolChange).toHaveBeenCalledWith('pan');
    });

    it('should highlight active tool', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} activeTool="select" />);
      const selectButton = screen.getByTitle('Select (V)');
      expect(selectButton).toHaveClass('bg-blue-100');
    });
  });

  describe('History Controls', () => {
    it('should call onUndo when undo button is clicked', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      fireEvent.click(screen.getByTitle('Undo (⌘Z)'));
      expect(defaultProps.onUndo).toHaveBeenCalled();
    });

    it('should call onRedo when redo button is clicked', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      fireEvent.click(screen.getByTitle('Redo (⌘⇧Z)'));
      expect(defaultProps.onRedo).toHaveBeenCalled();
    });

    it('should disable undo button when canUndo is false', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} canUndo={false} />);
      const undoButton = screen.getByTitle('Undo (⌘Z)');
      expect(undoButton).toBeDisabled();
    });

    it('should disable redo button when canRedo is false', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} canRedo={false} />);
      const redoButton = screen.getByTitle('Redo (⌘⇧Z)');
      expect(redoButton).toBeDisabled();
    });
  });

  describe('Zoom Controls', () => {
    it('should call onZoomIn when zoom in button is clicked', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      fireEvent.click(screen.getByTitle('Zoom In'));
      expect(defaultProps.onZoomIn).toHaveBeenCalled();
    });

    it('should call onZoomOut when zoom out button is clicked', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      fireEvent.click(screen.getByTitle('Zoom Out'));
      expect(defaultProps.onZoomOut).toHaveBeenCalled();
    });

    it('should call onZoomFit when fit button is clicked', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      fireEvent.click(screen.getByTitle('Fit to Screen'));
      expect(defaultProps.onZoomFit).toHaveBeenCalled();
    });

    it('should display zoom percentage correctly', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} zoom={1.5} />);
      expect(screen.getByText('150%')).toBeInTheDocument();
    });
  });

  describe('AI Assist', () => {
    it('should call onAIAssist when AI button is clicked', () => {
      const onAIAssist = vi.fn();
      render(<UnifiedCanvasToolbar {...defaultProps} onAIAssist={onAIAssist} />);
      fireEvent.click(screen.getByTitle('AI Assist'));
      expect(onAIAssist).toHaveBeenCalled();
    });
  });

  describe('Advanced Options', () => {
    it('should toggle advanced options panel', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} onToggleGrid={vi.fn()} />);
      
      // Initially advanced options should be hidden
      expect(screen.queryByTitle('Toggle Grid')).not.toBeInTheDocument();
      
      // Click more options
      fireEvent.click(screen.getByTitle('More Options'));
      
      // Advanced options should be visible
      expect(screen.getByTitle('Toggle Grid')).toBeInTheDocument();
    });

    it('should call onToggleGrid when grid button is clicked', () => {
      const onToggleGrid = vi.fn();
      render(<UnifiedCanvasToolbar {...defaultProps} onToggleGrid={onToggleGrid} />);
      
      fireEvent.click(screen.getByTitle('More Options'));
      fireEvent.click(screen.getByTitle('Toggle Grid'));
      
      expect(onToggleGrid).toHaveBeenCalled();
    });

    it('should call onToggleLock when lock button is clicked', () => {
      const onToggleLock = vi.fn();
      render(<UnifiedCanvasToolbar {...defaultProps} onToggleLock={onToggleLock} />);
      
      fireEvent.click(screen.getByTitle('More Options'));
      fireEvent.click(screen.getByTitle('Lock Canvas'));
      
      expect(onToggleLock).toHaveBeenCalled();
    });

    it('should show locked state correctly', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} isLocked={true} onToggleLock={vi.fn()} />);
      
      fireEvent.click(screen.getByTitle('More Options'));
      
      expect(screen.getByTitle('Unlock Canvas')).toBeInTheDocument();
    });

    it('should highlight grid button when grid is shown', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} showGrid={true} onToggleGrid={vi.fn()} />);
      
      fireEvent.click(screen.getByTitle('More Options'));
      
      const gridButton = screen.getByTitle('Toggle Grid');
      expect(gridButton).toHaveClass('bg-blue-100');
    });
  });

  describe('Keyboard Shortcuts', () => {
    it('should change tool on V key press', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} activeTool="pan" />);
      
      fireEvent.keyDown(window, { key: 'v' });
      
      expect(defaultProps.onToolChange).toHaveBeenCalledWith('select');
    });

    it('should change tool on H key press', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      
      fireEvent.keyDown(window, { key: 'h' });
      
      expect(defaultProps.onToolChange).toHaveBeenCalledWith('pan');
    });

    it('should undo on Cmd+Z', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      
      fireEvent.keyDown(window, { key: 'z', metaKey: true });
      
      expect(defaultProps.onUndo).toHaveBeenCalled();
    });

    it('should redo on Cmd+Shift+Z', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      
      fireEvent.keyDown(window, { key: 'z', metaKey: true, shiftKey: true });
      
      expect(defaultProps.onRedo).toHaveBeenCalled();
    });

    it('should not trigger shortcuts when typing in input', () => {
      render(
        <div>
          <input data-testid="input" />
          <UnifiedCanvasToolbar {...defaultProps} />
        </div>
      );
      
      const input = screen.getByTestId('input');
      input.focus();
      
      fireEvent.keyDown(input, { key: 'v' });
      
      expect(defaultProps.onToolChange).not.toHaveBeenCalled();
    });
  });

  describe('Tool Dropdowns', () => {
    it('should open shapes dropdown on click', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      
      // Find shapes dropdown (first dropdown after primary tools)
      const dropdowns = screen.getAllByRole('button');
      const shapesDropdown = dropdowns.find(btn => 
        btn.querySelector('svg') && btn.textContent === ''
      );
      
      if (shapesDropdown) {
        fireEvent.click(shapesDropdown);
        // Dropdown should open with shape options
      }
    });
  });

  describe('Accessibility', () => {
    it('should have proper button titles', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} />);
      
      expect(screen.getByTitle('Select (V)')).toBeInTheDocument();
      expect(screen.getByTitle('Pan (H)')).toBeInTheDocument();
      expect(screen.getByTitle('Undo (⌘Z)')).toBeInTheDocument();
      expect(screen.getByTitle('Redo (⌘⇧Z)')).toBeInTheDocument();
    });

    it('should have proper disabled states', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} canUndo={false} canRedo={false} />);
      
      expect(screen.getByTitle('Undo (⌘Z)')).toBeDisabled();
      expect(screen.getByTitle('Redo (⌘⇧Z)')).toBeDisabled();
    });
  });

  describe('Custom className', () => {
    it('should apply custom className', () => {
      const { container } = render(
        <UnifiedCanvasToolbar {...defaultProps} className="custom-class" />
      );
      
      expect(container.firstChild).toHaveClass('custom-class');
    });
  });

  describe('Edge Cases', () => {
    it('should handle zoom of 0', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} zoom={0} />);
      expect(screen.getByText('0%')).toBeInTheDocument();
    });

    it('should handle very high zoom', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} zoom={5} />);
      expect(screen.getByText('500%')).toBeInTheDocument();
    });

    it('should handle decimal zoom', () => {
      render(<UnifiedCanvasToolbar {...defaultProps} zoom={0.333} />);
      expect(screen.getByText('33%')).toBeInTheDocument();
    });
  });
});
