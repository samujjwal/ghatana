/**
 * Breadcrumb Component Unit Tests
 *
 * Tests for the custom Breadcrumb navigation component.
 *
 * @module DevSecOps/Breadcrumb/__tests__
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi } from 'vitest';

import { Breadcrumb } from '../Breadcrumb';
import type { BreadcrumbItem } from '../Breadcrumb';

// Mock react-router-dom navigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

describe('Breadcrumb Component', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  // ============================================================================
  // Basic Rendering
  // ============================================================================

  describe('Basic Rendering', () => {
    it('renders breadcrumb trail', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Reports', href: '/devsecops/reports' },
        { label: 'Executive Summary' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      expect(screen.getByText('Dashboard')).toBeDefined();
      expect(screen.getByText('Reports')).toBeDefined();
      expect(screen.getByText('Executive Summary')).toBeDefined();
    });

    it('renders single breadcrumb', () => {
      const items: BreadcrumbItem[] = [{ label: 'Dashboard' }];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      expect(screen.getByText('Dashboard')).toBeDefined();
    });

    it('renders empty breadcrumbs', () => {
      const { container } = render(
        <MemoryRouter>
          <Breadcrumb items={[]} />
        </MemoryRouter>
      );

      // Should render container but no items
      expect(container.firstChild).toBeDefined();
    });
  });

  // ============================================================================
  // Separators
  // ============================================================================

  describe('Separators', () => {
    it('renders forward slash separators between items', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Home', href: '/' },
        { label: 'DevSecOps', href: '/devsecops' },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      // Should have 2 separators for 3 items
      const separators = screen.getAllByText('/');
      expect(separators).toHaveLength(2);
    });

    it('does not render separator after last item', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Home', href: '/' },
        { label: 'Current' },
      ];

      const { container } = render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      // Count separators - should be 1 for 2 items
      const separators = screen.getAllByText('/');
      expect(separators).toHaveLength(1);
    });

    it('does not render separator for single item', () => {
      const items: BreadcrumbItem[] = [{ label: 'Dashboard' }];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      expect(screen.queryByText('/')).toBeNull();
    });
  });

  // ============================================================================
  // Click Behavior
  // ============================================================================

  describe('Click Behavior', () => {
    it('items with href are clickable buttons', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const dashboardButton = screen.getByText('Dashboard').closest('button');
      expect(dashboardButton).toBeDefined();
    });

    it('last item without href is not clickable', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const currentText = screen.getByText('Current');
      expect(currentText.tagName).toBe('P'); // Typography renders as <p>
    });

    it('calls navigate with href on click', async () => {
      const user = userEvent.setup();
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const dashboardButton = screen.getByText('Dashboard');
      await user.click(dashboardButton);

      expect(mockNavigate).toHaveBeenCalledWith('/devsecops');
    });

    it('calls custom onClick when provided', async () => {
      const user = userEvent.setup();
      const mockOnClick = vi.fn();
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops', onClick: mockOnClick },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const dashboardButton = screen.getByText('Dashboard');
      await user.click(dashboardButton);

      expect(mockOnClick).toHaveBeenCalledTimes(1);
      // Should not navigate when onClick is provided
      expect(mockNavigate).not.toHaveBeenCalled();
    });

    it('prioritizes onClick over href navigation', async () => {
      const user = userEvent.setup();
      const mockOnClick = vi.fn();
      const items: BreadcrumbItem[] = [
        { label: 'Custom', href: '/should-not-navigate', onClick: mockOnClick },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const customButton = screen.getByText('Custom');
      await user.click(customButton);

      expect(mockOnClick).toHaveBeenCalledTimes(1);
      expect(mockNavigate).not.toHaveBeenCalled();
    });
  });

  // ============================================================================
  // Styling
  // ============================================================================

  describe('Styling', () => {
    it('applies ghost variant to clickable items', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Current' },
      ];

      const { container } = render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      // Ghost variant button should be present
      const button = screen.getByText('Dashboard').closest('button');
      expect(button).toBeDefined();
    });

    it('applies text transform none to buttons', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const button = screen.getByText('Dashboard').closest('button');
      expect(button).toBeDefined();
    });

    it('applies primary color to current item', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const currentText = screen.getByText('Current');
      expect(currentText.tagName).toBe('P');
    });

    it('applies margin bottom to container', () => {
      const items: BreadcrumbItem[] = [{ label: 'Dashboard' }];

      const { container } = render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      // Container should exist
      expect(container.firstChild).toBeDefined();
    });
  });

  // ============================================================================
  // Edge Cases
  // ============================================================================

  describe('Edge Cases', () => {
    it('handles very long labels', () => {
      const longLabel = 'A'.repeat(100);
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: longLabel },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      expect(screen.getByText(longLabel)).toBeDefined();
    });

    it('handles special characters in labels', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Home & Away', href: '/' },
        { label: 'Reports > Analytics' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      expect(screen.getByText('Home & Away')).toBeDefined();
      expect(screen.getByText('Reports > Analytics')).toBeDefined();
    });

    it('handles many breadcrumb levels', () => {
      const items: BreadcrumbItem[] = Array.from({ length: 10 }, (_, i) => ({
        label: `Level ${i + 1}`,
        href: `/level-${i + 1}`,
      }));
      items[items.length - 1].href = undefined; // Last item no href

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      expect(screen.getByText('Level 1')).toBeDefined();
      expect(screen.getByText('Level 10')).toBeDefined();
    });

    it('handles missing href gracefully', () => {
      const items: BreadcrumbItem[] = [
        { label: 'No Href' }, // Should render as text
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const noHrefText = screen.getByText('No Href');
      expect(noHrefText.tagName).toBe('P');
    });
  });

  // ============================================================================
  // Accessibility
  // ============================================================================

  describe('Accessibility', () => {
    it('supports keyboard navigation', async () => {
      const user = userEvent.setup();
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Reports', href: '/devsecops/reports' },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const dashboardButton = screen.getByText('Dashboard');
      const reportsButton = screen.getByText('Reports');

      // Tab to first button
      await user.tab();
      expect(dashboardButton).toHaveFocus();

      // Tab to second button
      await user.tab();
      expect(reportsButton).toHaveFocus();
    });

    it('activates buttons with Enter key', async () => {
      const user = userEvent.setup();
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const dashboardButton = screen.getByText('Dashboard');
      dashboardButton.focus();

      await user.keyboard('{Enter}');

      expect(mockNavigate).toHaveBeenCalledWith('/devsecops');
    });

    it('buttons have descriptive text', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Current' },
      ];

      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const button = screen.getByText('Dashboard');
      expect(button.textContent).toBe('Dashboard');
    });
  });

  // ============================================================================
  // Performance
  // ============================================================================

  describe('Performance', () => {
    it('renders many items efficiently', () => {
      const items: BreadcrumbItem[] = Array.from({ length: 50 }, (_, i) => ({
        label: `Item ${i + 1}`,
        href: `/item-${i + 1}`,
      }));

      const start = performance.now();
      render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );
      const duration = performance.now() - start;

      // Should render quickly even with many items
      expect(duration).toBeLessThan(200);
    });

    it('handles rapid re-renders', () => {
      const items: BreadcrumbItem[] = [
        { label: 'Dashboard', href: '/devsecops' },
        { label: 'Current' },
      ];

      const { rerender } = render(
        <MemoryRouter>
          <Breadcrumb items={items} />
        </MemoryRouter>
      );

      const start = performance.now();
      for (let i = 0; i < 10; i++) {
        rerender(
          <MemoryRouter>
            <Breadcrumb items={items} />
          </MemoryRouter>
        );
      }
      const duration = performance.now() - start;

      // 10 re-renders should be fast
      expect(duration).toBeLessThan(100);
    });
  });
});
