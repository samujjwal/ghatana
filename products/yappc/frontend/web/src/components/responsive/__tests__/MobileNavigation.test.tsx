/**
 * MobileNavigation Component Tests
 */

import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MobileNavigation } from '../MobileNavigation';
import type { NavItem } from '../MobileNavigation';

describe('MobileNavigation', () => {
  it('should render navigation items', () => {
    const items: NavItem[] = [
      {
        id: '1',
        label: 'Home',
        icon: <span>Home</span>,
        onClick: jest.fn(),
      },
      {
        id: '2',
        label: 'Projects',
        icon: <span>Projects</span>,
        onClick: jest.fn(),
      },
    ];

    render(<MobileNavigation items={items} />);

    expect(screen.getByText('Home')).toBeDefined();
    expect(screen.getByText('Projects')).toBeDefined();
  });

  it('should render badge count', () => {
    const items: NavItem[] = [
      {
        id: '1',
        label: 'Projects',
        icon: <span>Projects</span>,
        onClick: jest.fn(),
        badge: 5,
      },
    ];

    render(<MobileNavigation items={items} />);

    expect(screen.getByText('5')).toBeDefined();
  });

  it('should handle item click', () => {
    const onClick = jest.fn();
    const items: NavItem[] = [
      {
        id: '1',
        label: 'Home',
        icon: <span>Home</span>,
        onClick,
      },
    ];

    render(<MobileNavigation items={items} />);

    const homeButton = screen.getByText('Home');
    homeButton.click();

    expect(onClick).toHaveBeenCalled();
  });

  it('should show more button when items exceed limit', () => {
    const items: NavItem[] = Array.from({ length: 6 }, (_, i) => ({
      id: String(i),
      label: `Item ${i}`,
      icon: <span>Icon {i}</span>,
      onClick: jest.fn(),
    }));

    render(<MobileNavigation items={items} />);

    expect(screen.getByText('More')).toBeDefined();
  });
});
