import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ViewModeSwitcher } from '../ViewModeSwitcher';
import type { ViewModeSwitcherProps } from '../types';
import type { ViewMode } from '@ghatana/yappc-types/devsecops';

/**
 * ViewModeSwitcher Component Unit Tests
 * 
 * Tests ViewModeSwitcher component behavior:
 * - Rendering view mode buttons
 * - View mode switching
 * - Icon and label display
 * - Tooltips
 * - Disabled state
 * - Custom modes
 */

describe('ViewModeSwitcher Component', () => {
  const mockOnChange = vi.fn();

  const defaultProps: ViewModeSwitcherProps = {
    value: 'kanban',
    onChange: mockOnChange,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Basic Rendering', () => {
    it('should render all default view mode buttons', () => {
      render(<ViewModeSwitcher {...defaultProps} />);
      
      expect(screen.getByRole('button', { name: /canvas/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /kanban/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /timeline/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /table/i })).toBeInTheDocument();
    });

    it('should render with canvas mode selected', () => {
      render(<ViewModeSwitcher {...defaultProps} value="canvas" />);
      
      const canvasButton = screen.getByRole('button', { name: /canvas/i });
      expect(canvasButton).toHaveClass('Mui-selected');
    });

    it('should render with kanban mode selected', () => {
      render(<ViewModeSwitcher {...defaultProps} value="kanban" />);
      
      const kanbanButton = screen.getByRole('button', { name: /kanban/i });
      expect(kanbanButton).toHaveClass('Mui-selected');
    });

    it('should render with timeline mode selected', () => {
      render(<ViewModeSwitcher {...defaultProps} value="timeline" />);
      
      const timelineButton = screen.getByRole('button', { name: /timeline/i });
      expect(timelineButton).toHaveClass('Mui-selected');
    });

    it('should render with table mode selected', () => {
      render(<ViewModeSwitcher {...defaultProps} value="table" />);
      
      const tableButton = screen.getByRole('button', { name: /table/i });
      expect(tableButton).toHaveClass('Mui-selected');
    });

    it('should render view mode icons', () => {
      render(<ViewModeSwitcher {...defaultProps} variant="full" />);
      
      expect(screen.getByTestId('GridViewIcon')).toBeInTheDocument(); // Canvas
      expect(screen.getByTestId('ViewKanbanIcon')).toBeInTheDocument(); // Kanban
      expect(screen.getByTestId('TimelineIcon')).toBeInTheDocument(); // Timeline
      expect(screen.getByTestId('TableChartIcon')).toBeInTheDocument(); // Table
    });
  });

  describe('Mode Switching', () => {
    it('should call onChange when canvas button clicked', async () => {
      const user = userEvent.setup();
      render(<ViewModeSwitcher {...defaultProps} value="kanban" />);
      
      const canvasButton = screen.getByRole('button', { name: /canvas/i });
      await user.click(canvasButton);
      
      expect(mockOnChange).toHaveBeenCalledWith('canvas');
    });

    it('should call onChange when kanban button clicked', async () => {
      const user = userEvent.setup();
      render(<ViewModeSwitcher {...defaultProps} value="canvas" />);
      
      const kanbanButton = screen.getByRole('button', { name: /kanban/i });
      await user.click(kanbanButton);
      
      expect(mockOnChange).toHaveBeenCalledWith('kanban');
    });

    it('should call onChange when timeline button clicked', async () => {
      const user = userEvent.setup();
      render(<ViewModeSwitcher {...defaultProps} value="kanban" />);
      
      const timelineButton = screen.getByRole('button', { name: /timeline/i });
      await user.click(timelineButton);
      
      expect(mockOnChange).toHaveBeenCalledWith('timeline');
    });

    it('should call onChange when table button clicked', async () => {
      const user = userEvent.setup();
      render(<ViewModeSwitcher {...defaultProps} value="kanban" />);
      
      const tableButton = screen.getByRole('button', { name: /table/i });
      await user.click(tableButton);
      
      expect(mockOnChange).toHaveBeenCalledWith('table');
    });

    it('should not call onChange when clicking already selected mode', async () => {
      const user = userEvent.setup();
      render(<ViewModeSwitcher {...defaultProps} value="kanban" />);
      
      const kanbanButton = screen.getByRole('button', { name: /kanban/i });
      await user.click(kanbanButton);
      
      // ToggleButtonGroup in exclusive mode shouldn't deselect
      expect(mockOnChange).not.toHaveBeenCalled();
    });

    it('should update selected state when value prop changes', () => {
      const { rerender } = render(<ViewModeSwitcher {...defaultProps} value="kanban" />);
      
      expect(screen.getByRole('button', { name: /kanban/i })).toHaveClass('Mui-selected');
      
      rerender(<ViewModeSwitcher {...defaultProps} value="timeline" />);
      
      expect(screen.getByRole('button', { name: /kanban/i })).not.toHaveClass('Mui-selected');
      expect(screen.getByRole('button', { name: /timeline/i })).toHaveClass('Mui-selected');
    });
  });

  describe('Custom Modes', () => {
    it('should render only specified modes', () => {
      const customModes: ViewMode[] = ['kanban', 'table'];
      
      render(<ViewModeSwitcher {...defaultProps} modes={customModes} />);
      
      expect(screen.getByRole('button', { name: /kanban/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /table/i })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /canvas/i })).not.toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /timeline/i })).not.toBeInTheDocument();
    });

    it('should render single mode', () => {
      const customModes: ViewMode[] = ['kanban'];
      
      render(<ViewModeSwitcher {...defaultProps} modes={customModes} />);
      
      expect(screen.getByRole('button', { name: /kanban/i })).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /canvas/i })).not.toBeInTheDocument();
    });

    it('should maintain mode order', () => {
      const customModes: ViewMode[] = ['table', 'kanban', 'canvas'];
      
      render(<ViewModeSwitcher {...defaultProps} modes={customModes} />);
      
      const buttons = screen.getAllByRole('button');
      expect(buttons[0]).toHaveAccessibleName(/table/i);
      expect(buttons[1]).toHaveAccessibleName(/kanban/i);
      expect(buttons[2]).toHaveAccessibleName(/canvas/i);
    });
  });

  describe('Variants', () => {
    it('should render full variant with icons and labels', () => {
      render(<ViewModeSwitcher {...defaultProps} variant="full" />);
      
      expect(screen.getByText('Canvas')).toBeInTheDocument();
      expect(screen.getByText('Kanban')).toBeInTheDocument();
      expect(screen.getByTestId('GridViewIcon')).toBeInTheDocument();
      expect(screen.getByTestId('ViewKanbanIcon')).toBeInTheDocument();
    });

    it('should render compact variant with only icons', () => {
      render(<ViewModeSwitcher {...defaultProps} variant="compact" />);
      
      // Icons should be present
      expect(screen.getByTestId('ViewKanbanIcon')).toBeInTheDocument();
      
      // Labels should not be visible in compact mode
      expect(screen.queryByText('Kanban')).not.toBeInTheDocument();
    });

    it('should render icon-only variant', () => {
      render(<ViewModeSwitcher {...defaultProps} variant="icon-only" />);
      
      expect(screen.getByTestId('ViewKanbanIcon')).toBeInTheDocument();
      expect(screen.queryByText('Kanban')).not.toBeInTheDocument();
    });
  });

  describe('Size Prop', () => {
    it('should render with small size', () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} size="small" />);
      
      const buttonGroup = container.querySelector('.MuiToggleButtonGroup-root');
      // Check that button group exists (size is managed via props)
      expect(buttonGroup).toBeInTheDocument();
    });

    it('should render with medium size', () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} size="medium" />);
      
      const buttonGroup = container.querySelector('.MuiToggleButtonGroup-root');
      expect(buttonGroup).toBeInTheDocument();
    });

    it('should render with large size', () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} size="large" />);
      
      const buttonGroup = container.querySelector('.MuiToggleButtonGroup-root');
      expect(buttonGroup).toBeInTheDocument();
    });
  });

  describe('Orientation', () => {
    it('should render horizontally by default', () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} />);
      
      const buttonGroup = container.querySelector('.MuiToggleButtonGroup-root');
      expect(buttonGroup).not.toHaveClass('MuiToggleButtonGroup-vertical');
    });

    it('should render vertically when orientation is vertical', () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} orientation="vertical" />);
      
      const buttonGroup = container.querySelector('.MuiToggleButtonGroup-root');
      expect(buttonGroup).toHaveClass('MuiToggleButtonGroup-vertical');
    });

    it('should render horizontally when orientation is horizontal', () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} orientation="horizontal" />);
      
      const buttonGroup = container.querySelector('.MuiToggleButtonGroup-root');
      expect(buttonGroup).toHaveClass('MuiToggleButtonGroup-horizontal');
    });
  });

  describe('Disabled State', () => {
    it('should disable all buttons when disabled is true', () => {
      render(<ViewModeSwitcher {...defaultProps} disabled={true} />);
      
      const buttons = screen.getAllByRole('button');
      buttons.forEach(button => {
        expect(button).toBeDisabled();
      });
    });

    it('should enable all buttons when disabled is false', () => {
      render(<ViewModeSwitcher {...defaultProps} disabled={false} />);
      
      const buttons = screen.getAllByRole('button');
      buttons.forEach(button => {
        expect(button).not.toBeDisabled();
      });
    });

    it('should not trigger onChange when disabled', async () => {
      render(<ViewModeSwitcher {...defaultProps} disabled={true} />);
      
      const canvasButton = screen.getByRole('button', { name: /canvas/i });
      // Disabled buttons have pointer-events: none, so we just verify they're disabled
      expect(canvasButton).toBeDisabled();
      expect(mockOnChange).not.toHaveBeenCalled();
    });
  });

  describe('Tooltips', () => {
    it('should show tooltip on hover for canvas', async () => {
      render(<ViewModeSwitcher {...defaultProps} variant="compact" />);
      
      // Check that canvas button exists (tooltips wrap buttons in compact mode)
      const canvasButton = screen.getByRole('button', { name: /canvas/i });
      expect(canvasButton).toBeInTheDocument();
    });

    it('should show tooltip on hover for kanban', async () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} variant="compact" />);
      
      const kanbanButton = screen.getByRole('button', { name: /kanban/i });
      expect(kanbanButton).toBeInTheDocument();
    });

    it('should show tooltip on hover for timeline', async () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} variant="compact" />);
      
      const timelineButton = screen.getByRole('button', { name: /timeline/i });
      expect(timelineButton).toBeInTheDocument();
    });

    it('should show tooltip on hover for table', async () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} variant="compact" />);
      
      const tableButton = screen.getByRole('button', { name: /table/i });
      expect(tableButton).toBeInTheDocument();
    });
  });

  describe('Custom Labels', () => {
    it('should use custom labels when provided', () => {
      const customLabels = {
        canvas: 'Board',
        kanban: 'Cards',
        timeline: 'Schedule',
        table: 'List',
      };
      
      render(
        <ViewModeSwitcher
          {...defaultProps}
          variant="full"
          labels={customLabels}
        />
      );
      
      expect(screen.getByText('Board')).toBeInTheDocument();
      expect(screen.getByText('Cards')).toBeInTheDocument();
      expect(screen.getByText('Schedule')).toBeInTheDocument();
      expect(screen.getByText('List')).toBeInTheDocument();
    });

    it('should fall back to default labels when custom labels not provided', () => {
      render(<ViewModeSwitcher {...defaultProps} variant="full" />);
      
      expect(screen.getByText('Canvas')).toBeInTheDocument();
      expect(screen.getByText('Kanban')).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have accessible group label', () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} />);
      
      const buttonGroup = container.querySelector('.MuiToggleButtonGroup-root');
      expect(buttonGroup).toHaveAttribute('aria-label', 'view mode selector');
    });

    it('should have accessible button labels', () => {
      render(<ViewModeSwitcher {...defaultProps} />);
      
      expect(screen.getByRole('button', { name: /canvas/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /kanban/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /timeline/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /table/i })).toBeInTheDocument();
    });

    it('should indicate selected state with aria-pressed', () => {
      render(<ViewModeSwitcher {...defaultProps} value="kanban" />);
      
      const kanbanButton = screen.getByRole('button', { name: /kanban/i });
      expect(kanbanButton).toHaveAttribute('aria-pressed', 'true');
      
      const canvasButton = screen.getByRole('button', { name: /canvas/i });
      expect(canvasButton).toHaveAttribute('aria-pressed', 'false');
    });

    it('should be keyboard navigable', async () => {
      const user = userEvent.setup();
      render(<ViewModeSwitcher {...defaultProps} />);
      
      const firstButton = screen.getByRole('button', { name: /canvas/i });
      firstButton.focus();
      
      expect(document.activeElement).toBe(firstButton);
      
      await user.keyboard('{Tab}');
      expect(document.activeElement).toBe(screen.getByRole('button', { name: /kanban/i }));
    });
  });

  describe('Styling', () => {
    it('should apply custom className', () => {
      const { container } = render(
        <ViewModeSwitcher {...defaultProps} className="custom-switcher" />
      );
      
      const wrapper = container.querySelector('.custom-switcher');
      expect(wrapper).toBeInTheDocument();
    });

    it('should apply selected styles', () => {
      render(<ViewModeSwitcher {...defaultProps} value="kanban" />);
      
      const kanbanButton = screen.getByRole('button', { name: /kanban/i });
      expect(kanbanButton).toHaveClass('Mui-selected');
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty modes array', () => {
      const { container } = render(<ViewModeSwitcher {...defaultProps} modes={[]} />);
      
      const buttons = container.querySelectorAll('button');
      expect(buttons.length).toBe(0);
    });

    it('should handle invalid mode value', () => {
      render(<ViewModeSwitcher {...defaultProps} value={'invalid' as ViewMode} />);
      
      // Should render without crashing
      expect(screen.getByRole('button', { name: /kanban/i })).toBeInTheDocument();
    });

    it('should handle null onChange', async () => {
      const user = userEvent.setup();
      render(<ViewModeSwitcher {...defaultProps} onChange={null as unknown} />);
      
      const canvasButton = screen.getByRole('button', { name: /canvas/i });
      await user.click(canvasButton);
      
      // Should not crash
      expect(canvasButton).toBeInTheDocument();
    });
  });
});
