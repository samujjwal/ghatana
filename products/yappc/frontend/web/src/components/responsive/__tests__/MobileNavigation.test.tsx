/**
 * MobileNavigation Component Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MobileNavigation } from '../MobileNavigation';
import type { NavItem } from '../MobileNavigation';

// Mock useResponsive so that the mobile navigation actually renders
vi.mock('../../../hooks/useResponsive', () => ({
  useResponsive: () => ({ isMobile: true, isTablet: false, isDesktop: false }),
}));

describe('MobileNavigation', () => {
  it('should render navigation items', () => {
    const items: NavItem[] = [
      {
        id: '1',
        label: 'Home',
        icon: <span>Home</span>,
        onClick: vi.fn(),
      },
      {
        id: '2',
        label: 'Projects',
        icon: <span>Projects</span>,
        onClick: vi.fn(),
      },
    ];

    render(<MobileNavigation items={items} />);

    expect(screen.getAllByText('Home').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Projects').length).toBeGreaterThan(0);
  });

  it('should render badge count', () => {
    const items: NavItem[] = [
      {
        id: '1',
        label: 'Projects',
        icon: <span>Projects</span>,
        onClick: vi.fn(),
        badge: 5,
      },
    ];

    render(<MobileNavigation items={items} />);

    expect(screen.getByText('5')).toBeDefined();
  });

  it('should handle item click', () => {
    const onClick = vi.fn();
    const items: NavItem[] = [
      {
        id: '1',
        label: 'Home',
        icon: <span aria-hidden="true">🏠</span>,
        onClick,
      },
    ];

    render(<MobileNavigation items={items} />);

    const homeButton = screen.getAllByText('Home')[0];
    homeButton.click();

    expect(onClick).toHaveBeenCalled();
  });

  it('should show more button when items exceed limit', () => {
    const items: NavItem[] = Array.from({ length: 6 }, (_, i) => ({
      id: String(i),
      label: `Item ${i}`,
      icon: <span>Icon {i}</span>,
      onClick: vi.fn(),
    }));

    render(<MobileNavigation items={items} />);

    expect(screen.getByText('More')).toBeDefined();
  });
});
