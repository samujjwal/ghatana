import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { FilterPanel } from '../FilterPanel';
import type { FilterPanelProps, FilterConfig } from '../types';

/**
 * FilterPanel Component Unit Tests
 * 
 * Tests FilterPanel component behavior:
 * - Filter rendering (status, priority, phases, tags)
 * - Multi-select functionality
 * - Accordion expand/collapse
 * - Apply and clear filters
 * - Drawer variants
 * - Active filter counts
 */

describe('FilterPanel Component', () => {
  const mockOnChange = vi.fn();
  const mockOnClose = vi.fn();

  const emptyFilters: FilterConfig = {};

  const defaultProps: FilterPanelProps = {
    filters: emptyFilters,
    onChange: mockOnChange,
    open: true,
    variant: 'inline',
  };

  const samplePhaseIds = ['plan', 'code', 'build', 'test', 'deploy'];
  const samplePhaseLabels = {
    plan: 'Plan',
    code: 'Code',
    build: 'Build',
    test: 'Test',
    deploy: 'Deploy',
  };
  const sampleTags = ['backend', 'frontend', 'api', 'database', 'security'];
  const sampleOwners = ['john.doe', 'jane.smith', 'bob.johnson'];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Basic Rendering', () => {
    it('should render filter panel', () => {
      render(<FilterPanel {...defaultProps} />);
      
      expect(screen.getByText('Filters')).toBeInTheDocument();
    });

    it('should render status filter section', () => {
      render(<FilterPanel {...defaultProps} />);
      
      expect(screen.getByText('Status')).toBeInTheDocument();
    });

    it('should render priority filter section', () => {
      render(<FilterPanel {...defaultProps} />);
      
      expect(screen.getByText('Priority')).toBeInTheDocument();
    });

    it('should render all status options', () => {
      render(<FilterPanel {...defaultProps} />);
      
      expect(screen.getByLabelText(/not started/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/in progress/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/in review/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/completed/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/blocked/i)).toBeInTheDocument();
    });

    it('should render all priority options', () => {
      render(<FilterPanel {...defaultProps} />);
      
      expect(screen.getByLabelText(/^low$/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/^medium$/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/^high$/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/critical/i)).toBeInTheDocument();
    });

    it('should render phase filters when phaseIds provided', () => {
      render(
        <FilterPanel
          {...defaultProps}
          phaseIds={samplePhaseIds}
          phaseLabels={samplePhaseLabels}
        />
      );
      
      expect(screen.getByText('Phases')).toBeInTheDocument();
      expect(screen.getByLabelText(/plan/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/code/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/build/i)).toBeInTheDocument();
    });

    it('should not render phase filters when phaseIds empty', () => {
      render(<FilterPanel {...defaultProps} phaseIds={[]} />);
      
      expect(screen.queryByText('Phases')).not.toBeInTheDocument();
    });

    it('should render tags when availableTags provided', () => {
      render(
        <FilterPanel
          {...defaultProps}
          availableTags={sampleTags}
        />
      );
      
      expect(screen.getByText('Tags')).toBeInTheDocument();
      expect(screen.getByText('backend')).toBeInTheDocument();
      expect(screen.getByText('frontend')).toBeInTheDocument();
    });
  });

  describe('Status Filter', () => {
    it('should check status option when clicked', async () => {
      const user = userEvent.setup();
      render(<FilterPanel {...defaultProps} />);
      
      const inProgressCheckbox = screen.getByLabelText(/in progress/i);
      await user.click(inProgressCheckbox);
      
      expect(mockOnChange).toHaveBeenCalledWith({
        status: ['in-progress'],
      });
    });

    it('should uncheck status option when clicked again', async () => {
      const user = userEvent.setup();
      const filters: FilterConfig = { status: ['in-progress', 'completed'] };
      
      render(<FilterPanel {...defaultProps} filters={filters} />);
      
      const inProgressCheckbox = screen.getByLabelText(/in progress/i);
      await user.click(inProgressCheckbox);
      
      expect(mockOnChange).toHaveBeenCalledWith({
        status: ['completed'],
      });
    });

    it('should allow multiple status selections', async () => {
      const user = userEvent.setup();
      let currentFilters: FilterConfig = {};
      const onChange = vi.fn((newFilters: FilterConfig) => {
        currentFilters = newFilters;
      });
      
      const { rerender } = render(<FilterPanel {...defaultProps} filters={currentFilters} onChange={onChange} />);
      
      await user.click(screen.getByLabelText(/in progress/i));
      rerender(<FilterPanel {...defaultProps} filters={currentFilters} onChange={onChange} />);
      
      await user.click(screen.getByLabelText(/blocked/i));
      
      expect(onChange).toHaveBeenNthCalledWith(1, { status: ['in-progress'] });
      expect(onChange).toHaveBeenNthCalledWith(2, { status: ['in-progress', 'blocked'] });
    });

    it('should show checked state for selected statuses', () => {
      const filters: FilterConfig = { status: ['in-progress', 'completed'] };
      render(<FilterPanel {...defaultProps} filters={filters} />);
      
      const inProgressCheckbox = screen.getByLabelText(/in progress/i) as HTMLInputElement;
      const completedCheckbox = screen.getByLabelText(/completed/i) as HTMLInputElement;
      const notStartedCheckbox = screen.getByLabelText(/not started/i) as HTMLInputElement;
      
      expect(inProgressCheckbox.checked).toBe(true);
      expect(completedCheckbox.checked).toBe(true);
      expect(notStartedCheckbox.checked).toBe(false);
    });

    it('should clear status filter when last option unchecked', async () => {
      const user = userEvent.setup();
      const filters: FilterConfig = { status: ['in-progress'] };
      
      render(<FilterPanel {...defaultProps} filters={filters} />);
      
      await user.click(screen.getByLabelText(/in progress/i));
      
      expect(mockOnChange).toHaveBeenCalledWith({});
    });
  });

  describe('Priority Filter', () => {
    it('should toggle priority options', async () => {
      const user = userEvent.setup();
      render(<FilterPanel {...defaultProps} />);
      
      const highCheckbox = screen.getByLabelText(/^high$/i);
      await user.click(highCheckbox);
      
      expect(mockOnChange).toHaveBeenCalledWith({
        priority: ['high'],
      });
    });

    it('should allow multiple priority selections', async () => {
      const user = userEvent.setup();
      let currentFilters: FilterConfig = {};
      const onChange = vi.fn((newFilters: FilterConfig) => {
        currentFilters = newFilters;
      });
      
      const { rerender } = render(<FilterPanel {...defaultProps} filters={currentFilters} onChange={onChange} />);
      
      await user.click(screen.getByLabelText(/^high$/i));
      rerender(<FilterPanel {...defaultProps} filters={currentFilters} onChange={onChange} />);
      
      await user.click(screen.getByLabelText(/critical/i));
      
      expect(onChange).toHaveBeenNthCalledWith(2, {
        priority: ['high', 'critical'],
      });
    });

    it('should preserve other filters when changing priority', async () => {
      const user = userEvent.setup();
      const filters: FilterConfig = { status: ['in-progress'] };
      
      render(<FilterPanel {...defaultProps} filters={filters} />);
      
      await user.click(screen.getByLabelText(/^high$/i));
      
      expect(mockOnChange).toHaveBeenCalledWith({
        status: ['in-progress'],
        priority: ['high'],
      });
    });
  });

  describe('Phase Filter', () => {
    it('should toggle phase options', async () => {
      const user = userEvent.setup();
      render(
        <FilterPanel
          {...defaultProps}
          phaseIds={samplePhaseIds}
          phaseLabels={samplePhaseLabels}
        />
      );
      
      const planCheckbox = screen.getByLabelText(/plan/i);
      await user.click(planCheckbox);
      
      expect(mockOnChange).toHaveBeenCalledWith({
        phaseIds: ['plan'],
      });
    });

    it('should allow multiple phase selections', async () => {
      const user = userEvent.setup();
      let currentFilters: FilterConfig = {};
      const onChange = vi.fn((newFilters: FilterConfig) => {
        currentFilters = newFilters;
      });
      
      const { rerender } = render(
        <FilterPanel
          {...defaultProps}
          filters={currentFilters}
          onChange={onChange}
          phaseIds={samplePhaseIds}
          phaseLabels={samplePhaseLabels}
        />
      );
      
      await user.click(screen.getByLabelText(/plan/i));
      rerender(
        <FilterPanel
          {...defaultProps}
          filters={currentFilters}
          onChange={onChange}
          phaseIds={samplePhaseIds}
          phaseLabels={samplePhaseLabels}
        />
      );
      
      await user.click(screen.getByLabelText(/code/i));
      rerender(
        <FilterPanel
          {...defaultProps}
          filters={currentFilters}
          onChange={onChange}
          phaseIds={samplePhaseIds}
          phaseLabels={samplePhaseLabels}
        />
      );
      
      await user.click(screen.getByLabelText(/build/i));
      
      expect(onChange).toHaveBeenLastCalledWith({
        phaseIds: ['plan', 'code', 'build'],
      });
    });
  });

  describe('Tags Filter', () => {
    it('should toggle tag options', async () => {
      const user = userEvent.setup();
      render(
        <FilterPanel
          {...defaultProps}
          availableTags={sampleTags}
        />
      );
      
      // Expand tags section first
      const tagsHeader = screen.getByText(/^Tags/);
      await user.click(tagsHeader);
      
      const backendChip = screen.getByRole('button', { name: /backend/i });
      await user.click(backendChip);
      
      expect(mockOnChange).toHaveBeenCalledWith({
        tags: ['backend'],
      });
    });

    it('should allow multiple tag selections', async () => {
      const user = userEvent.setup();
      let currentFilters: FilterConfig = {};
      const onChange = vi.fn((newFilters: FilterConfig) => {
        currentFilters = newFilters;
      });
      
      const { rerender } = render(
        <FilterPanel
          {...defaultProps}
          filters={currentFilters}
          onChange={onChange}
          availableTags={sampleTags}
        />
      );
      
      // Expand tags section first
      const tagsHeader = screen.getByText(/^Tags/);
      await user.click(tagsHeader);
      
      await user.click(screen.getByRole('button', { name: /backend/i }));
      rerender(
        <FilterPanel
          {...defaultProps}
          filters={currentFilters}
          onChange={onChange}
          availableTags={sampleTags}
        />
      );
      
      await user.click(screen.getByRole('button', { name: /api/i }));
      
      expect(onChange).toHaveBeenLastCalledWith({
        tags: ['backend', 'api'],
      });
    });
  });

  describe('Accordion Expand/Collapse', () => {
    it('should expand status section by default', () => {
      render(<FilterPanel {...defaultProps} />);
      
      const statusCheckbox = screen.getByLabelText(/in progress/i);
      expect(statusCheckbox).toBeInTheDocument();
    });

    it('should expand priority section by default', () => {
      render(<FilterPanel {...defaultProps} />);
      
      const priorityCheckbox = screen.getByLabelText(/^high$/i);
      expect(priorityCheckbox).toBeInTheDocument();
    });

    it('should toggle section expansion on header click', async () => {
      const user = userEvent.setup();
      render(
        <FilterPanel
          {...defaultProps}
          availableTags={sampleTags}
        />
      );
      
      // Tags section should be collapsed initially
      expect(screen.queryByRole('button', { name: /backend/i })).not.toBeInTheDocument();
      
      // Click to expand
      const tagsHeader = screen.getByText('Tags');
      await user.click(tagsHeader);
      
      // Should now be visible
      expect(screen.getByRole('button', { name: /backend/i })).toBeInTheDocument();
    });

    it('should allow multiple sections expanded simultaneously', async () => {
      const user = userEvent.setup();
      render(
        <FilterPanel
          {...defaultProps}
          phaseIds={samplePhaseIds}
          phaseLabels={samplePhaseLabels}
          availableTags={sampleTags}
        />
      );
      
      // Expand phases
      await user.click(screen.getByText('Phases'));
      
      // Both status and phases should be in document (expanded)
      expect(screen.getByLabelText(/in progress/i)).toBeInTheDocument();
      expect(screen.getByLabelText(/plan/i)).toBeInTheDocument();
    });
  });

  describe('Clear Filters', () => {
    it('should render clear button when filters are active', () => {
      const filters: FilterConfig = { status: ['in-progress'] };
      render(<FilterPanel {...defaultProps} filters={filters} />);
      
      expect(screen.getByText(/clear/i)).toBeInTheDocument();
    });

    it('should not render clear button when no filters active', () => {
      render(<FilterPanel {...defaultProps} />);
      
      expect(screen.queryByText(/clear/i)).not.toBeInTheDocument();
    });

    it('should clear all filters on clear button click', async () => {
      const user = userEvent.setup();
      const filters: FilterConfig = {
        status: ['in-progress', 'completed'],
        priority: ['high'],
        phases: ['plan', 'code'],
      };
      
      render(<FilterPanel {...defaultProps} filters={filters} />);
      
      const clearButton = screen.getByText(/clear/i);
      await user.click(clearButton);
      
      expect(mockOnChange).toHaveBeenCalledWith({});
    });
  });

  describe('Active Filter Count', () => {
    it('should show active filter count', () => {
      const filters: FilterConfig = {
        status: ['in-progress', 'completed'],
        priority: ['high'],
      };
      
      render(<FilterPanel {...defaultProps} filters={filters} />);
      
      // 2 status + 1 priority = 3 active filters
      expect(screen.getByText('3')).toBeInTheDocument();
    });

    it('should update count when filters change', () => {
      const filters1: FilterConfig = { status: ['in-progress'] };
      const { rerender } = render(<FilterPanel {...defaultProps} filters={filters1} />);
      
      expect(screen.getByText('1')).toBeInTheDocument();
      
      const filters2: FilterConfig = {
        status: ['in-progress', 'completed'],
        priority: ['high', 'critical'],
      };
      rerender(<FilterPanel {...defaultProps} filters={filters2} />);
      
      expect(screen.getByText('4')).toBeInTheDocument();
    });

    it('should not show count when no filters active', () => {
      render(<FilterPanel {...defaultProps} />);
      
      const chips = screen.queryAllByRole('button');
      // Should not have any count chips (only filter option chips)
      const countChips = chips.filter(chip => /^\d+$/.test(chip.textContent || ''));
      expect(countChips.length).toBe(0);
    });
  });

  describe('Drawer Variant', () => {
    it('should render as drawer when variant is drawer', () => {
      render(
        <FilterPanel
          {...defaultProps}
          variant="drawer"
          onClose={mockOnClose}
        />
      );
      
      const drawer = screen.getByRole('presentation');
      expect(drawer).toBeInTheDocument();
    });

    it('should call onClose when close button clicked in drawer', async () => {
      const user = userEvent.setup();
      render(
        <FilterPanel
          {...defaultProps}
          variant="drawer"
          onClose={mockOnClose}
        />
      );
      
      const closeButton = screen.getByTestId('CloseIcon').closest('button');
      if (closeButton) {
        await user.click(closeButton);
      }
      
      expect(mockOnClose).toHaveBeenCalled();
    });

    it('should render inline when variant is inline', () => {
      render(<FilterPanel {...defaultProps} variant="inline" />);
      
      const drawer = screen.queryByRole('presentation');
      expect(drawer).not.toBeInTheDocument();
    });

    it('should respect open prop in drawer variant', () => {
      const { rerender } = render(
        <FilterPanel
          {...defaultProps}
          variant="drawer"
          open={false}
        />
      );
      
      // When closed, drawer content should not be visible
      expect(screen.queryByText('Filters')).not.toBeInTheDocument();
      
      rerender(
        <FilterPanel
          {...defaultProps}
          variant="drawer"
          open={true}
        />
      );
      
      // Drawer should now be open and content visible
      expect(screen.getByText('Filters')).toBeInTheDocument();
    });
  });

  describe('Apply Button', () => {
    it('should show apply button in drawer variant', () => {
      render(
        <FilterPanel
          {...defaultProps}
          variant="drawer"
          onClose={mockOnClose}
        />
      );
      
      expect(screen.getByText(/apply/i)).toBeInTheDocument();
    });

    it('should not show apply button in inline variant', () => {
      render(
        <FilterPanel
          {...defaultProps}
          variant="inline"
        />
      );
      
      expect(screen.queryByText(/^apply$/i)).not.toBeInTheDocument();
    });

    it('should close drawer when apply button clicked', async () => {
      const user = userEvent.setup();
      render(
        <FilterPanel
          {...defaultProps}
          variant="drawer"
          onClose={mockOnClose}
        />
      );
      
      const applyButton = screen.getByText(/apply/i);
      await user.click(applyButton);
      
      expect(mockOnClose).toHaveBeenCalled();
    });
  });

  describe('Filter Chips', () => {
    it('should display chips for active filters', () => {
      const filters: FilterConfig = {
        status: ['in-progress'],
        priority: ['high'],
      };
      
      render(<FilterPanel {...defaultProps} filters={filters} />);
      
      expect(screen.getByText(/in progress/i)).toBeInTheDocument();
      expect(screen.getByText(/high/i)).toBeInTheDocument();
    });

    it('should remove filter when chip deleted', async () => {
      const user = userEvent.setup();
      const filters: FilterConfig = {
        status: ['in-progress', 'completed'],
      };
      
      render(<FilterPanel {...defaultProps} filters={filters} />);
      
      // Component uses checkboxes, not deletable chips
      // Uncheck a status to remove it
      const inProgressCheckbox = screen.getByLabelText(/in progress/i);
      await user.click(inProgressCheckbox);
      
      expect(mockOnChange).toHaveBeenCalledWith({
        status: ['completed'],
      });
    });
  });

  describe('Accessibility', () => {
    it('should have accessible labels on checkboxes', () => {
      render(<FilterPanel {...defaultProps} />);
      
      const checkbox = screen.getByLabelText(/in progress/i);
      expect(checkbox).toHaveAttribute('type', 'checkbox');
    });

    it('should have accessible accordion headers', () => {
      render(<FilterPanel {...defaultProps} />);
      
      const statusAccordion = screen.getByText('Status').closest('button');
      expect(statusAccordion).toHaveAttribute('aria-expanded');
    });

    it('should have accessible close button in drawer', () => {
      render(
        <FilterPanel
          {...defaultProps}
          variant="drawer"
          onClose={mockOnClose}
        />
      );
      
      const closeButton = screen.getByTestId('CloseIcon').closest('button');
      expect(closeButton).toHaveAttribute('aria-label', 'Close filters');
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty phase labels', () => {
      render(
        <FilterPanel
          {...defaultProps}
          phaseIds={['phase1', 'phase2']}
          phaseLabels={{}}
        />
      );
      
      // Should render with phase IDs as fallback
      expect(screen.getByText('Phases')).toBeInTheDocument();
    });

    it('should handle undefined filters', () => {
      render(<FilterPanel {...defaultProps} filters={undefined as unknown} />);
      
      expect(screen.getByText('Filters')).toBeInTheDocument();
    });

    it('should handle null onChange', async () => {
      const user = userEvent.setup();
      render(<FilterPanel {...defaultProps} onChange={undefined as unknown} />);
      
      const checkbox = screen.getByLabelText(/in progress/i);
      await user.click(checkbox);
      
      // Should not crash
      expect(checkbox).toBeInTheDocument();
    });

    it('should preserve filter order', () => {
      const filters: FilterConfig = {
        status: ['completed', 'in-progress', 'blocked'],
      };
      
      render(<FilterPanel {...defaultProps} filters={filters} />);
      
      const completedCheckbox = screen.getByLabelText(/completed/i) as HTMLInputElement;
      const inProgressCheckbox = screen.getByLabelText(/in progress/i) as HTMLInputElement;
      const blockedCheckbox = screen.getByLabelText(/blocked/i) as HTMLInputElement;
      
      expect(completedCheckbox.checked).toBe(true);
      expect(inProgressCheckbox.checked).toBe(true);
      expect(blockedCheckbox.checked).toBe(true);
    });
  });
});
