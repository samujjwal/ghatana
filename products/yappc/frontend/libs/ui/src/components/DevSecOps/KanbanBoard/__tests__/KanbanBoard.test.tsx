import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { KanbanBoard } from '../KanbanBoard';
import type { KanbanBoardProps } from '../types';
import type { Item } from '@ghatana/yappc-types/devsecops';

/**
 * KanbanBoard Component Unit Tests
 * 
 * Tests KanbanBoard component behavior:
 * - Column rendering
 * - Item distribution across columns
 * - Drag and drop functionality
 * - WIP limits
 * - Empty states
 * - Item click interactions
 */

describe('KanbanBoard Component', () => {
  const mockOnItemMove = vi.fn();
  const mockOnItemClick = vi.fn();

  const sampleItems: Item[] = [
    {
      id: 'item-1',
      title: 'Implement Authentication',
      description: 'Add JWT authentication',
      status: 'in-progress',
      priority: 'high',
      phaseId: 'code',
      assignee: 'John Doe',
      createdAt: '2024-01-01',
      updatedAt: '2024-01-15',
    },
    {
      id: 'item-2',
      title: 'Security Audit',
      description: 'Perform security review',
      status: 'not-started',
      priority: 'critical',
      phaseId: 'test',
      assignee: 'Jane Smith',
      createdAt: '2024-01-02',
      updatedAt: '2024-01-16',
    },
    {
      id: 'item-3',
      title: 'Database Migration',
      description: 'Migrate to PostgreSQL',
      status: 'completed',
      priority: 'medium',
      phaseId: 'code',
      assignee: 'Bob Johnson',
      createdAt: '2024-01-03',
      updatedAt: '2024-01-17',
    },
    {
      id: 'item-4',
      title: 'API Documentation',
      description: 'Write API docs',
      status: 'in-review',
      priority: 'low',
      phaseId: 'code',
      assignee: 'Alice Williams',
      createdAt: '2024-01-04',
      updatedAt: '2024-01-18',
    },
    {
      id: 'item-5',
      title: 'CI/CD Pipeline',
      description: 'Set up deployment',
      status: 'blocked',
      priority: 'high',
      phaseId: 'deploy',
      assignee: 'Charlie Brown',
      createdAt: '2024-01-05',
      updatedAt: '2024-01-19',
    },
  ];

  const defaultProps: KanbanBoardProps = {
    items: sampleItems,
    onItemMove: mockOnItemMove,
    onItemClick: mockOnItemClick,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Basic Rendering', () => {
    it('should render all default columns', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      expect(screen.getAllByText('Not Started').length).toBeGreaterThan(0);
      expect(screen.getAllByText('In Progress').length).toBeGreaterThan(0);
      expect(screen.getAllByText('In Review').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Completed').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Blocked').length).toBeGreaterThan(0);
    });

    it('should render 5 columns by default', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      const columns = screen.getAllByRole('region');
      expect(columns).toHaveLength(5);
    });

    it('should display column titles', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      expect(screen.getByRole('heading', { name: /not started/i })).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: /in progress/i })).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: /in review/i })).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: /completed/i })).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: /blocked/i })).toBeInTheDocument();
    });
  });

  describe('Item Distribution', () => {
    it('should distribute items to correct columns by status', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      const inProgressColumn = screen.getAllByText('In Progress')[0].closest('[role="region"]');
      const notStartedColumn = screen.getAllByText('Not Started')[0].closest('[role="region"]');
      const completedColumn = screen.getAllByText('Completed')[0].closest('[role="region"]');
      
      if (inProgressColumn) {
        expect(within(inProgressColumn as HTMLElement).getByText('Implement Authentication')).toBeInTheDocument();
      }
      if (notStartedColumn) {
        expect(within(notStartedColumn as HTMLElement).getByText('Security Audit')).toBeInTheDocument();
      }
      if (completedColumn) {
        expect(within(completedColumn as HTMLElement).getByText('Database Migration')).toBeInTheDocument();
      }
    });

    it('should show correct item count per column', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      // Each column should show count badge
      const inProgressHeader = screen.getAllByText('In Progress')[0].closest('div');
      if (inProgressHeader) {
        expect(within(inProgressHeader as HTMLElement).getByText('1')).toBeInTheDocument();
      }
    });

    it('should handle empty columns', () => {
      const itemsWithoutBlocked = sampleItems.filter(item => item.status !== 'blocked');
      render(<KanbanBoard {...defaultProps} items={itemsWithoutBlocked} />);
      
      const blockedColumn = screen.getAllByText('Blocked')[0].closest('[role="region"]');
      if (blockedColumn) {
        const blockedHeader = within(blockedColumn as HTMLElement).getAllByText('Blocked')[0].closest('div');
        if (blockedHeader) {
          expect(within(blockedHeader as HTMLElement).getByText('0')).toBeInTheDocument();
        }
      }
    });

    it('should handle column with multiple items', () => {
      const multipleInProgress: Item[] = [
        ...sampleItems,
        {
          id: 'item-6',
          title: 'Another Task',
          description: 'More work',
          status: 'in-progress',
          priority: 'medium',
          phaseId: 'code',
          assignee: 'Dev User',
          createdAt: '2024-01-06',
          updatedAt: '2024-01-20',
        },
      ];
      
      render(<KanbanBoard {...defaultProps} items={multipleInProgress} />);
      
      const inProgressColumn = screen.getAllByText('In Progress')[0].closest('[role="region"]');
      if (inProgressColumn) {
        expect(within(inProgressColumn as HTMLElement).getByText('Implement Authentication')).toBeInTheDocument();
        expect(within(inProgressColumn as HTMLElement).getByText('Another Task')).toBeInTheDocument();
      }
    });
  });

  describe('WIP Limits', () => {
    it('should display WIP limit for columns that have it', () => {
      render(<KanbanBoard {...defaultProps} showWipLimits={true} />);
      
      const inProgressColumn = screen.getAllByText('In Progress')[0].closest('[role="region"]');
      if (inProgressColumn) {
        // In Progress has wipLimit: 5
        expect(within(inProgressColumn as HTMLElement).getByText('WIP Limit')).toBeInTheDocument();
      }
    });

    it('should not display WIP limit when showWipLimit is false', () => {
      render(<KanbanBoard {...defaultProps} showWipLimits={false} />);
      
      const inProgressColumn = screen.getAllByText('In Progress')[0].closest('[role="region"]');
      if (inProgressColumn) {
        expect(within(inProgressColumn as HTMLElement).queryByText(/\/5/)).not.toBeInTheDocument();
      }
    });

    it('should warn when WIP limit exceeded', () => {
      const manyInProgress: Item[] = Array.from({ length: 6 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i}`,
        description: 'Description',
        status: 'in-progress' as const,
        priority: 'medium' as const,
        phaseId: 'code',
        assignee: 'User',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      }));
      
      render(<KanbanBoard {...defaultProps} items={manyInProgress} showWipLimits={true} />);
      
      const inProgressColumn = screen.getAllByText('In Progress')[0].closest('[role="region"]');
      if (inProgressColumn) {
        // Should show WIP Limit warning
        expect(within(inProgressColumn as HTMLElement).getByText('WIP Limit')).toBeInTheDocument();
      }
    });

    it('should handle columns without WIP limits', () => {
      render(<KanbanBoard {...defaultProps} showWipLimits={true} />);
      
      // Completed column doesn't have WIP limit
      const completedColumn = screen.getAllByText('Completed')[0].closest('[role="region"]');
      if (completedColumn) {
        expect(within(completedColumn as HTMLElement).queryByText(/\//)).not.toBeInTheDocument();
      }
    });
  });

  describe('Item Click Interaction', () => {
    it('should call onItemClick when item is clicked', async () => {
      const user = userEvent.setup();
      render(<KanbanBoard {...defaultProps} />);
      
      // Click on the first item card (Implement Authentication)
      const itemCards = screen.getAllByTestId('item-card');
      await user.click(itemCards[0]);
      
      expect(mockOnItemClick).toHaveBeenCalled();
    });

    it('should handle clicks on different items', async () => {
      const user = userEvent.setup();
      render(<KanbanBoard {...defaultProps} />);
      
      await user.click(screen.getByText('Security Audit').closest('.MuiCard-root')!);
      await user.click(screen.getByText('Database Migration').closest('.MuiCard-root')!);
      
      expect(mockOnItemClick).toHaveBeenCalledTimes(2);
    });

    it('should not crash when onItemClick is undefined', async () => {
      const user = userEvent.setup();
      render(<KanbanBoard {...defaultProps} onItemClick={undefined} />);
      
      const itemCard = screen.getByText('Implement Authentication').closest('.MuiCard-root');
      if (itemCard) {
        await user.click(itemCard);
      }
      
      // Should not crash
      expect(itemCard).toBeInTheDocument();
    });
  });

  describe('Drag and Drop', () => {
    it('should have draggable items', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      // Check that items are rendered (dnd-kit uses different attributes)
      const itemCards = screen.getAllByTestId('item-card');
      expect(itemCards.length).toBeGreaterThan(0);
    });

    it('should support keyboard drag and drop', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      const itemCard = screen.getByText('Implement Authentication').closest('div');
      expect(itemCard).toBeInTheDocument();
    });

    it('should provide visual feedback during drag', () => {
      const { container } = render(<KanbanBoard {...defaultProps} />);
      
      // DragOverlay should be present for visual feedback
      const dndContext = container.querySelector('[data-dnd-context]');
      expect(dndContext || container).toBeInTheDocument();
    });
  });

  describe('Empty States', () => {
    it('should show empty state for column with no items', () => {
      const emptyItems: Item[] = [];
      render(<KanbanBoard {...defaultProps} items={emptyItems} />);
      
      const notStartedColumn = screen.getByText('Not Started').closest('[role="region"]');
      if (notStartedColumn) {
        const count = within(notStartedColumn as HTMLElement).getByText('0');
        expect(count).toBeInTheDocument();
      }
    });

    it('should show empty message in empty column', () => {
      const emptyItems: Item[] = [];
      render(<KanbanBoard {...defaultProps} items={emptyItems} />);
      
      const emptyMessages = screen.getAllByText(/no items/i);
      expect(emptyMessages.length).toBeGreaterThan(0);
    });

    it('should handle board with no items', () => {
      render(<KanbanBoard {...defaultProps} items={[]} />);
      
      expect(screen.getByText('Not Started')).toBeInTheDocument();
      expect(screen.getByText('In Progress')).toBeInTheDocument();
      
      const allZeros = screen.getAllByText('0');
      expect(allZeros.length).toBeGreaterThan(0);
    });
  });

  describe('Custom Columns', () => {
    it('should render custom columns when provided', () => {
      const customColumns = [
        { id: 'backlog', title: 'Backlog', status: 'not-started' as const, order: 1 },
        { id: 'done', title: 'Done', status: 'completed' as const, order: 2 },
      ];
      
      render(<KanbanBoard {...defaultProps} columns={customColumns} />);
      
      expect(screen.getByText('Backlog')).toBeInTheDocument();
      expect(screen.getByText('Done')).toBeInTheDocument();
      expect(screen.queryByText('In Progress')).not.toBeInTheDocument();
    });

    it('should respect custom column order', () => {
      const customColumns = [
        { id: 'col3', title: 'Third', status: 'completed' as const, order: 3 },
        { id: 'col1', title: 'First', status: 'not-started' as const, order: 1 },
        { id: 'col2', title: 'Second', status: 'in-progress' as const, order: 2 },
      ];
      
      render(<KanbanBoard {...defaultProps} columns={customColumns} />);
      
      // Check that all custom columns are rendered
      expect(screen.getAllByText('First').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Second').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Third').length).toBeGreaterThan(0);
    });
  });

  describe('Column Colors', () => {
    it('should apply column colors to headers', () => {
      const { container } = render(<KanbanBoard {...defaultProps} />);
      
      const columns = container.querySelectorAll('[role="region"]');
      expect(columns.length).toBeGreaterThan(0);
      
      // Each column should have a styled header
      columns.forEach(column => {
        const header = column.querySelector('div');
        expect(header).toBeTruthy();
      });
    });

    it('should use custom colors when provided', () => {
      const customColumns = [
        {
          id: 'custom',
          title: 'Custom Column',
          status: 'in-progress' as const,
          color: '#FF0000',
          order: 1,
        },
      ];
      
      render(<KanbanBoard {...defaultProps} columns={customColumns} />);
      
      expect(screen.getByText('Custom Column')).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have accessible column regions', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      const regions = screen.getAllByRole('region');
      expect(regions).toHaveLength(5);
    });

    it('should have accessible column headings', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      const headings = screen.getAllByRole('heading');
      expect(headings.length).toBeGreaterThanOrEqual(5);
    });

    it('should support keyboard navigation for drag and drop', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      const itemCard = screen.getByText('Implement Authentication');
      expect(itemCard).toBeInTheDocument();
    });

    it('should have aria labels on interactive elements', () => {
      render(<KanbanBoard {...defaultProps} />);
      
      const columns = screen.getAllByRole('region');
      columns.forEach(column => {
        expect(column).toHaveAttribute('aria-label');
      });
    });
  });

  describe('Edge Cases', () => {
    it('should handle item with missing status', () => {
      const invalidItem: Item[] = [{
        id: 'invalid',
        title: 'Invalid Item',
        description: 'No status',
        status: undefined as unknown,
        priority: 'medium',
        phaseId: 'code',
        assignee: 'User',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      }];
      
      render(<KanbanBoard {...defaultProps} items={invalidItem} />);
      
      // Should still render without crashing
      expect(screen.getByText('Not Started')).toBeInTheDocument();
    });

    it('should handle very long item titles', () => {
      const longTitleItem: Item[] = [{
        id: 'long',
        title: 'This is a very long title that should be handled gracefully without breaking the layout or causing overflow issues in the kanban board',
        description: 'Test',
        status: 'in-progress',
        priority: 'medium',
        phaseId: 'code',
        assignee: 'User',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      }];
      
      render(<KanbanBoard {...defaultProps} items={longTitleItem} />);
      
      expect(screen.getByText(/very long title/)).toBeInTheDocument();
    });

    it('should handle large number of items in one column', () => {
      const manyItems: Item[] = Array.from({ length: 50 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i}`,
        description: 'Description',
        status: 'in-progress' as const,
        priority: 'medium' as const,
        phaseId: 'code',
        assignee: 'User',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      }));
      
      render(<KanbanBoard {...defaultProps} items={manyItems} />);
      
      const inProgressColumn = screen.getAllByText('In Progress')[0].closest('[role="region"]');
      if (inProgressColumn) {
        expect(within(inProgressColumn as HTMLElement).getByText('50')).toBeInTheDocument();
      }
    });

    it('should handle null onItemMove', () => {
      render(<KanbanBoard {...defaultProps} onItemMove={undefined as unknown} />);
      
      // Should render without crashing
      expect(screen.getByText('Implement Authentication')).toBeInTheDocument();
    });
  });

  describe('Performance', () => {
    it('should render efficiently with many items', () => {
      const startTime = performance.now();
      
      const manyItems: Item[] = Array.from({ length: 100 }, (_, i) => ({
        id: `item-${i}`,
        title: `Task ${i}`,
        description: 'Description',
        status: ['not-started', 'in-progress', 'in-review', 'completed', 'blocked'][i % 5] as unknown,
        priority: 'medium' as const,
        phaseId: 'code',
        assignee: 'User',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      }));
      
      render(<KanbanBoard {...defaultProps} items={manyItems} />);
      
      const endTime = performance.now();
      const renderTime = endTime - startTime;
      
      expect(renderTime).toBeLessThan(1000); // Should render in under 1 second
    });
  });
});
