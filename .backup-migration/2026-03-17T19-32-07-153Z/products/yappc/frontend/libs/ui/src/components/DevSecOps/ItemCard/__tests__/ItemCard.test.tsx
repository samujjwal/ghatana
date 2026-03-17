import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ItemCard } from '../ItemCard';
import type { ItemCardProps } from '../types';
import type { Item } from '@ghatana/yappc-types/devsecops';

/**
 * ItemCard Component Unit Tests
 * 
 * Tests ItemCard component behavior:
 * - Item rendering
 * - Priority indicators
 * - Progress display
 * - Owner avatars
 * - Selection state
 * - Click interaction
 * - Drag and drop attributes
 */

describe('ItemCard Component', () => {
  const mockOnSelect = vi.fn();

  const baseItem: Item = {
    id: 'item-1',
    title: 'Implement Authentication',
    description: 'Add JWT-based authentication',
    status: 'in-progress',
    priority: 'high',
    phaseId: 'code',
    type: 'feature',
    owners: [
      { id: 'user-1', name: 'John Doe', email: 'john@example.com', role: 'developer' }
    ],
    tags: ['backend', 'security'],
    progress: 60,
    dueDate: '2024-12-31',
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-15T00:00:00Z',
    artifacts: [],
  };

  const defaultProps: ItemCardProps = {
    item: baseItem,
    onSelect: mockOnSelect,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Basic Rendering', () => {
    it('should render item title', () => {
      render(<ItemCard {...defaultProps} />);
      
      expect(screen.getByText('Implement Authentication')).toBeInTheDocument();
    });

    it('should render item description when provided', () => {
      render(<ItemCard {...defaultProps} />);
      
      expect(screen.getByText('Add JWT-based authentication')).toBeInTheDocument();
    });

    it('should not crash when description is missing', () => {
      const itemNoDesc: Item = { ...baseItem, description: undefined };
      render(<ItemCard {...defaultProps} item={itemNoDesc} />);
      
      expect(screen.getByText('Implement Authentication')).toBeInTheDocument();
    });

    it('should render item ID', () => {
      render(<ItemCard {...defaultProps} />);
      
      expect(screen.getByText(/item-1/i)).toBeInTheDocument();
    });

    it('should render as a card', () => {
      const { container } = render(<ItemCard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toBeInTheDocument();
    });
  });

  describe('Priority Display', () => {
    it('should display low priority chip', () => {
      const lowPriorityItem: Item = { ...baseItem, priority: 'low' };
      render(<ItemCard {...defaultProps} item={lowPriorityItem} />);
      
      expect(screen.getByText(/low/i)).toBeInTheDocument();
    });

    it('should display medium priority chip', () => {
      const mediumPriorityItem: Item = { ...baseItem, priority: 'medium' };
      render(<ItemCard {...defaultProps} item={mediumPriorityItem} />);
      
      expect(screen.getByText(/medium/i)).toBeInTheDocument();
    });

    it('should display high priority chip', () => {
      const highPriorityItem: Item = { ...baseItem, priority: 'high' };
      render(<ItemCard {...defaultProps} item={highPriorityItem} />);
      
      expect(screen.getByText(/high/i)).toBeInTheDocument();
    });

    it('should display critical priority chip', () => {
      const criticalItem: Item = { ...baseItem, priority: 'critical' };
      render(<ItemCard {...defaultProps} item={criticalItem} />);
      
      expect(screen.getByText(/critical/i)).toBeInTheDocument();
    });

    it('should apply correct color for low priority', () => {
      const lowPriorityItem: Item = { ...baseItem, priority: 'low' };
      const { container } = render(<ItemCard {...defaultProps} item={lowPriorityItem} />);
      
      const card = container.querySelector('.MuiCard-root');
      // Low priority should have info color border
      expect(card).toHaveStyle({ borderLeftColor: expect.stringContaining('info') });
    });

    it('should apply correct color for high priority', () => {
      const highPriorityItem: Item = { ...baseItem, priority: 'high' };
      const { container } = render(<ItemCard {...defaultProps} item={highPriorityItem} />);
      
      const card = container.querySelector('.MuiCard-root');
      // High priority should have error color border
      expect(card).toHaveStyle({ borderLeftColor: expect.stringContaining('error') });
    });

    it('should default to medium priority when not specified', () => {
      const noPriorityItem: Item = { ...baseItem, priority: undefined };
      render(<ItemCard {...defaultProps} item={noPriorityItem} />);
      
      expect(screen.getByText(/medium/i)).toBeInTheDocument();
    });
  });

  describe('Status Display', () => {
    it('should display not-started status', () => {
      const item: Item = { ...baseItem, status: 'not-started' };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText(/not started/i)).toBeInTheDocument();
    });

    it('should display in-progress status', () => {
      const item: Item = { ...baseItem, status: 'in-progress' };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText(/in progress/i)).toBeInTheDocument();
    });

    it('should display in-review status', () => {
      const item: Item = { ...baseItem, status: 'in-review' };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText(/in review/i)).toBeInTheDocument();
    });

    it('should display completed status', () => {
      const item: Item = { ...baseItem, status: 'completed' };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText(/completed/i)).toBeInTheDocument();
    });

    it('should display blocked status', () => {
      const item: Item = { ...baseItem, status: 'blocked' };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText(/blocked/i)).toBeInTheDocument();
    });
  });

  describe('Progress Display', () => {
    it('should show progress bar when progress is provided', () => {
      render(<ItemCard {...defaultProps} />);
      
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toBeInTheDocument();
    });

    it('should display correct progress value', () => {
      render(<ItemCard {...defaultProps} />);
      
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-valuenow', '60');
    });

    it('should show progress percentage text', () => {
      render(<ItemCard {...defaultProps} />);
      
      expect(screen.getByText(/60%/)).toBeInTheDocument();
    });

    it('should handle 0% progress', () => {
      const item: Item = { ...baseItem, progress: 0 };
      render(<ItemCard {...defaultProps} item={item} />);
      
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-valuenow', '0');
      expect(screen.getByText(/0%/)).toBeInTheDocument();
    });

    it('should handle 100% progress', () => {
      const item: Item = { ...baseItem, progress: 100 };
      render(<ItemCard {...defaultProps} item={item} />);
      
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-valuenow', '100');
      expect(screen.getByText(/100%/)).toBeInTheDocument();
    });

    it('should not crash when progress is undefined', () => {
      const item: Item = { ...baseItem, progress: undefined };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText('Implement Authentication')).toBeInTheDocument();
    });
  });

  describe('Owner/Assignee Display', () => {
    it('should display assignee name', () => {
      render(<ItemCard {...defaultProps} />);
      
      expect(screen.getByText('John Doe')).toBeInTheDocument();
    });

    it('should display assignee avatar', () => {
      render(<ItemCard {...defaultProps} />);
      
      const avatar = screen.getByText('JD'); // Initials
      expect(avatar).toBeInTheDocument();
    });

    it('should handle missing assignee', () => {
      const item: Item = { ...baseItem, owners: [] };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText('Implement Authentication')).toBeInTheDocument();
    });

    it('should show multiple owners if provided', () => {
      const item: Item = { 
        ...baseItem, 
        owners: [
          { id: 'user-1', name: 'John Doe', email: 'john@example.com', role: 'developer' },
          { id: 'user-2', name: 'Jane Smith', email: 'jane@example.com', role: 'developer' }
        ]
      };
      const { container } = render(<ItemCard {...defaultProps} item={item} />);
      
      // Check for AvatarGroup when multiple owners
      const avatarGroup = container.querySelector('.MuiAvatarGroup-root');
      expect(avatarGroup).toBeInTheDocument();
      
      // Check for avatar initials
      expect(screen.getByText('JD')).toBeInTheDocument();
      expect(screen.getByText('JS')).toBeInTheDocument();
    });
  });

  describe('Tags Display', () => {
    it('should display item tags', () => {
      render(<ItemCard {...defaultProps} />);
      
      expect(screen.getByText('backend')).toBeInTheDocument();
      expect(screen.getByText('security')).toBeInTheDocument();
    });

    it('should handle items with no tags', () => {
      const item: Item = { ...baseItem, tags: [] };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText('Implement Authentication')).toBeInTheDocument();
    });

    it('should handle single tag', () => {
      const item: Item = { ...baseItem, tags: ['frontend'] };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText('frontend')).toBeInTheDocument();
    });

    it('should handle many tags', () => {
      const item: Item = { ...baseItem, tags: ['tag1', 'tag2', 'tag3', 'tag4', 'tag5'] };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText('tag1')).toBeInTheDocument();
      expect(screen.getByText('tag2')).toBeInTheDocument();
    });
  });

  describe('Due Date Display', () => {
    it('should display due date', () => {
      render(<ItemCard {...defaultProps} />);
      
      expect(screen.getByText(/2024-12-31/)).toBeInTheDocument();
    });

    it('should handle missing due date', () => {
      const item: Item = { ...baseItem, dueDate: undefined };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText('Implement Authentication')).toBeInTheDocument();
    });

    it('should format due date nicely', () => {
      const item: Item = { ...baseItem, dueDate: '2024-01-15' };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText(/2024-01-15/)).toBeInTheDocument();
    });
  });

  describe('Selection State', () => {
    it('should not show selected state by default', () => {
      const { container } = render(<ItemCard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).not.toHaveStyle({ borderWidth: '2px' });
    });

    it('should show selected state when selected is true', () => {
      const { container } = render(<ItemCard {...defaultProps} selected={true} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toHaveStyle({ borderWidth: '2px' });
    });

    it('should apply selection border color', () => {
      const { container } = render(<ItemCard {...defaultProps} selected={true} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toHaveStyle({ borderColor: expect.stringContaining('primary') });
    });

    it('should update selection state on prop change', () => {
      const { container, rerender } = render(<ItemCard {...defaultProps} selected={false} />);
      
      let card = container.querySelector('.MuiCard-root');
      expect(card).not.toHaveStyle({ borderWidth: '2px' });
      
      rerender(<ItemCard {...defaultProps} selected={true} />);
      
      card = container.querySelector('.MuiCard-root');
      expect(card).toHaveStyle({ borderWidth: '2px' });
    });
  });

  describe('Click Interaction', () => {
    it('should call onSelect when clicked', async () => {
      const user = userEvent.setup();
      render(<ItemCard {...defaultProps} />);
      
      const card = screen.getByText('Implement Authentication').closest('.MuiCard-root');
      if (card) {
        await user.click(card);
      }
      
      expect(mockOnSelect).toHaveBeenCalledWith('item-1');
    });

    it('should not crash when onSelect is not provided', async () => {
      const user = userEvent.setup();
      render(<ItemCard {...defaultProps} onSelect={undefined} />);
      
      const card = screen.getByText('Implement Authentication').closest('.MuiCard-root');
      if (card) {
        await user.click(card);
      }
      
      // Should not crash
      expect(screen.getByText('Implement Authentication')).toBeInTheDocument();
    });

    it('should have pointer cursor', () => {
      const { container } = render(<ItemCard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toHaveStyle({ cursor: 'pointer' });
    });
  });

  describe('Drag and Drop', () => {
    it('should be draggable', () => {
      const { container } = render(<ItemCard {...defaultProps} draggable={true} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toHaveAttribute('draggable', 'true');
    });

    it('should not be draggable by default', () => {
      const { container } = render(<ItemCard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).not.toHaveAttribute('draggable', 'true');
    });

    it('should have data-item-id for drag operations', () => {
      const { container } = render(<ItemCard {...defaultProps} />);
      
      const card = container.querySelector('[data-item-id="item-1"]');
      expect(card).toBeInTheDocument();
    });
  });

  describe('Styling and Layout', () => {
    it('should have minimum width', () => {
      const { container } = render(<ItemCard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toHaveStyle({ minWidth: '240px' });
    });

    it('should have minimum height', () => {
      const { container } = render(<ItemCard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toHaveStyle({ minHeight: '160px' });
    });

    it('should have priority border', () => {
      const { container } = render(<ItemCard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      // Check computed styles - MUI separates border properties
      const styles = card && window.getComputedStyle(card);
      expect(styles?.borderLeftWidth).toBeTruthy();
      expect(styles?.borderLeftStyle).toBeTruthy();
    });

    it('should have hover transition', () => {
      const { container } = render(<ItemCard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      // Check that transition property exists
      const styles = card && window.getComputedStyle(card);
      expect(styles?.transition).toBeTruthy();
    });
  });

  describe('Accessibility', () => {
    it('should have accessible card element', () => {
      const { container } = render(<ItemCard {...defaultProps} />);
      
      const card = container.querySelector('.MuiCard-root');
      expect(card).toBeInTheDocument();
    });

    it('should have accessible progress bar', () => {
      render(<ItemCard {...defaultProps} />);
      
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-valuenow', '60');
      expect(progressBar).toHaveAttribute('aria-valuemin', '0');
      expect(progressBar).toHaveAttribute('aria-valuemax', '100');
    });

    it('should have readable text content', () => {
      render(<ItemCard {...defaultProps} />);
      
      expect(screen.getByText('Implement Authentication')).toBeInTheDocument();
      expect(screen.getByText('Add JWT-based authentication')).toBeInTheDocument();
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty item title', () => {
      const item: Item = { ...baseItem, title: '' };
      render(<ItemCard {...defaultProps} item={item} />);
      
      const card = screen.getByTestId(/item-card/i);
      expect(card).toBeInTheDocument();
    });

    it('should handle very long titles', () => {
      const longTitle = 'This is a very long title that should be handled gracefully by the component without breaking the layout or causing overflow issues';
      const item: Item = { ...baseItem, title: longTitle };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText(longTitle)).toBeInTheDocument();
    });

    it('should handle very long descriptions', () => {
      const longDesc = 'A'.repeat(500);
      const item: Item = { ...baseItem, description: longDesc };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText(longDesc)).toBeInTheDocument();
    });

    it('should handle invalid progress values', () => {
      const item: Item = { ...baseItem, progress: 150 };
      render(<ItemCard {...defaultProps} item={item} />);
      
      // Should clamp to 100
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-valuenow', '100');
    });

    it('should handle negative progress values', () => {
      const item: Item = { ...baseItem, progress: -10 };
      render(<ItemCard {...defaultProps} item={item} />);
      
      // Should clamp to 0
      const progressBar = screen.getByRole('progressbar');
      expect(progressBar).toHaveAttribute('aria-valuenow', '0');
    });

    it('should handle missing item ID', () => {
      const item: Item = { ...baseItem, id: '' };
      render(<ItemCard {...defaultProps} item={item} />);
      
      expect(screen.getByText('Implement Authentication')).toBeInTheDocument();
    });

    it('should handle minimal item data', () => {
      const minimalItem: Item = {
        id: 'min-1',
        title: 'Minimal Item',
        status: 'not-started',
        phaseId: 'plan',
        createdAt: '2024-01-01',
        updatedAt: '2024-01-01',
      };
      
      render(<ItemCard {...defaultProps} item={minimalItem} />);
      
      expect(screen.getByText('Minimal Item')).toBeInTheDocument();
    });
  });
});
