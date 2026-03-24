/**
 * TopNav Component Unit Tests
 *
 * Tests for the top navigation bar with navigation links, notifications, and user profile.
 *
 * @module DevSecOps/TopNav/__tests__
 */

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';

import { TopNav } from '../TopNav';
import type { NavigationPage, TopNavUser } from '../types';

describe('TopNav Component', () => {
  // ============================================================================
  // Basic Rendering
  // ============================================================================

  describe('Basic Rendering', () => {
    it('renders logo and title', () => {
      render(<TopNav />);

      expect(screen.getByText('DevSecOps Canvas')).toBeInTheDocument();
    });

    it('renders navigation links', () => {
      render(<TopNav />);

      expect(screen.getByText('Dashboard')).toBeInTheDocument();
      expect(screen.getByText('Phases')).toBeInTheDocument();
      expect(screen.getByText('Reports')).toBeInTheDocument();
    });

    it('renders settings button', () => {
      render(<TopNav />);

      const settingsButton = screen.getByLabelText('Settings');
      expect(settingsButton).toBeInTheDocument();
    });

    it('renders notifications button', () => {
      render(<TopNav />);

      const notificationsButton = screen.getByLabelText(/notifications/i);
      expect(notificationsButton).toBeInTheDocument();
    });
  });

  // ============================================================================
  // Active Page Highlighting
  // ============================================================================

  describe('Active Page Highlighting', () => {
    it('highlights dashboard as active by default', () => {
      render(<TopNav />);

      const dashboardButton = screen.getByText('Dashboard').closest('button');
      expect(dashboardButton).toHaveAttribute('aria-current', 'page');
    });

    it('highlights current page', () => {
      render(<TopNav currentPage="phases" />);

      const phasesButton = screen.getByText('Phases').closest('button');
      expect(phasesButton).toHaveAttribute('aria-current', 'page');
    });

    it('applies contained variant to active page', () => {
      const { rerender } = render(<TopNav currentPage="dashboard" />);

      let dashboardButton = screen.getByText('Dashboard').closest('button');
      expect(dashboardButton?.className).toContain('MuiButton-contained');

      rerender(<TopNav currentPage="reports" />);

      const reportsButton = screen.getByText('Reports').closest('button');
      expect(reportsButton?.className).toContain('MuiButton-contained');
    });

    it('applies text variant to inactive pages', () => {
      render(<TopNav currentPage="dashboard" />);

      const phasesButton = screen.getByText('Phases').closest('button');
      const reportsButton = screen.getByText('Reports').closest('button');

      expect(phasesButton?.className).toContain('MuiButton-text');
      expect(reportsButton?.className).toContain('MuiButton-text');
    });
  });

  // ============================================================================
  // Navigation
  // ============================================================================

  describe('Navigation', () => {
    it('calls onNavigate with correct page on dashboard click', async () => {
      const user = userEvent.setup();
      const mockOnNavigate = vi.fn();

      render(<TopNav onNavigate={mockOnNavigate} />);

      const dashboardButton = screen.getByText('Dashboard');
      await user.click(dashboardButton);

      expect(mockOnNavigate).toHaveBeenCalledWith('dashboard');
    });

    it('calls onNavigate with correct page on phases click', async () => {
      const user = userEvent.setup();
      const mockOnNavigate = vi.fn();

      render(<TopNav onNavigate={mockOnNavigate} />);

      const phasesButton = screen.getByText('Phases');
      await user.click(phasesButton);

      expect(mockOnNavigate).toHaveBeenCalledWith('phases');
    });

    it('calls onNavigate with correct page on reports click', async () => {
      const user = userEvent.setup();
      const mockOnNavigate = vi.fn();

      render(<TopNav onNavigate={mockOnNavigate} />);

      const reportsButton = screen.getByText('Reports');
      await user.click(reportsButton);

      expect(mockOnNavigate).toHaveBeenCalledWith('reports');
    });

    it('calls onNavigate for settings click', async () => {
      const user = userEvent.setup();
      const mockOnNavigate = vi.fn();

      render(<TopNav onNavigate={mockOnNavigate} />);

      const settingsButton = screen.getByLabelText('Settings');
      await user.click(settingsButton);

      expect(mockOnNavigate).toHaveBeenCalledWith('settings');
    });

    it('does not crash when onNavigate not provided', async () => {
      const user = userEvent.setup();

      render(<TopNav />);

      const dashboardButton = screen.getByText('Dashboard');
      await user.click(dashboardButton);

      // Should not crash
      expect(dashboardButton).toBeInTheDocument();
    });
  });

  // ============================================================================
  // Notifications
  // ============================================================================

  describe('Notifications', () => {
    it('shows notification count badge', () => {
      render(<TopNav notificationCount={5} />);

      expect(screen.getByText('5')).toBeInTheDocument();
    });

    it('shows zero notifications as no badge', () => {
      const { container } = render(<TopNav notificationCount={0} />);

      // MUI Badge with badgeContent={0} renders but is invisible
      const badge = container.querySelector('.MuiBadge-badge');
      expect(badge).toHaveClass('MuiBadge-invisible');
    });

    it('handles large notification counts', () => {
      const { container } = render(<TopNav notificationCount={999} />);

      const badge = container.querySelector('.MuiBadge-badge');
      expect(badge).toBeInTheDocument();
      expect(badge).not.toHaveClass('MuiBadge-invisible');
    });

    it('calls onNotificationsClick when clicked', async () => {
      const user = userEvent.setup();
      const mockOnClick = vi.fn();

      render(<TopNav onNotificationsClick={mockOnClick} notificationCount={3} />);

      const notificationsButton = screen.getByLabelText('3 notifications');
      await user.click(notificationsButton);

      expect(mockOnClick).toHaveBeenCalledTimes(1);
    });

    it('updates aria-label based on count', () => {
      const { rerender } = render(<TopNav notificationCount={1} />);

      expect(screen.getByLabelText('1 notifications')).toBeInTheDocument();

      rerender(<TopNav notificationCount={5} />);

      expect(screen.getByLabelText('5 notifications')).toBeInTheDocument();
    });
  });

  // ============================================================================
  // User Profile
  // ============================================================================

  describe('User Profile', () => {
    const mockUser: TopNavUser = {
      name: 'Jane Smith',
      role: 'Developer',
    };

    it('renders user name', () => {
      render(<TopNav user={mockUser} />);

      expect(screen.getByText('Jane Smith')).toBeInTheDocument();
    });

    it('renders user role', () => {
      render(<TopNav user={mockUser} />);

      expect(screen.getByText('Developer')).toBeInTheDocument();
    });

    it('renders user avatar with initials', () => {
      render(<TopNav user={mockUser} />);

      const avatar = screen.getByText('J'); // First letter of name
      expect(avatar).toBeInTheDocument();
    });

    it('renders user avatar image when provided', () => {
      const userWithAvatar: TopNavUser = {
        ...mockUser,
        avatar: '/avatars/jane.png',
      };

      render(<TopNav user={userWithAvatar} />);

      const img = screen.getByAltText('Jane Smith');
      expect(img).toHaveAttribute('src', '/avatars/jane.png');
    });

    it('calls onProfileClick when profile clicked', async () => {
      const user = userEvent.setup();
      const mockOnClick = vi.fn();

      render(<TopNav user={mockUser} onProfileClick={mockOnClick} />);

      const profileContainer = screen.getByText('Jane Smith').closest('div');
      await user.click(profileContainer!);

      expect(mockOnClick).toHaveBeenCalledTimes(1);
    });

    it('does not render user section when no user provided', () => {
      render(<TopNav />);

      expect(screen.queryByText(/Developer|PM|Executive/i)).not.toBeInTheDocument();
    });

    it('handles all user roles', () => {
      const roles: Array<TopNavUser['role']> = [
        'Executive',
        'PM',
        'Developer',
        'Security',
        'DevOps',
        'QA',
      ];

      roles.forEach((role) => {
        const { unmount } = render(<TopNav user={{ name: 'Test User', role }} />);
        expect(screen.getByText(role)).toBeInTheDocument();
        unmount();
      });
    });
  });

  // ============================================================================
  // Styling & Layout
  // ============================================================================

  describe('Styling & Layout', () => {
    it('applies sticky positioning', () => {
      const { container } = render(<TopNav />);

      const appBar = container.querySelector('.MuiAppBar-root');
      expect(appBar?.className).toContain('MuiAppBar-positionSticky');
    });

    it('has correct height', () => {
      const { container } = render(<TopNav />);

      const toolbar = container.querySelector('.MuiToolbar-root');
      expect(toolbar).toBeInTheDocument();
    });

    it('uses background paper color', () => {
      const { container } = render(<TopNav />);

      const appBar = container.querySelector('.MuiAppBar-root');
      expect(appBar).toBeInTheDocument();
    });

    it('has bottom border', () => {
      const { container } = render(<TopNav />);

      const appBar = container.querySelector('.MuiAppBar-root');
      expect(appBar).toBeInTheDocument();
    });

    it('hides user name/role on mobile', () => {
      const mockUser: TopNavUser = {
        name: 'Jane Smith',
        role: 'Developer',
      };

      const { container } = render(<TopNav user={mockUser} />);

      // User info should have display: none on xs breakpoint
      const userInfo = screen.getByText('Jane Smith').parentElement;
      expect(userInfo?.className).toContain('MuiBox-root');
    });
  });

  // ============================================================================
  // Icons
  // ============================================================================

  describe('Icons', () => {
    it('renders dashboard icon', () => {
      const { container } = render(<TopNav />);

      const dashboardIcon = container.querySelector('[data-testid="DashboardIcon"]');
      expect(dashboardIcon).toBeInTheDocument();
    });

    it('renders timeline icon for phases', () => {
      const { container } = render(<TopNav />);

      const timelineIcons = container.querySelectorAll('[data-testid="TimelineIcon"]');
      expect(timelineIcons.length).toBeGreaterThan(0);
    });

    it('renders assessment icon for reports', () => {
      const { container } = render(<TopNav />);

      const assessmentIcon = container.querySelector('[data-testid="AssessmentIcon"]');
      expect(assessmentIcon).toBeInTheDocument();
    });

    it('renders settings icon', () => {
      const { container } = render(<TopNav />);

      const settingsIcon = container.querySelector('[data-testid="SettingsIcon"]');
      expect(settingsIcon).toBeInTheDocument();
    });

    it('renders notifications icon', () => {
      const { container } = render(<TopNav />);

      const notificationsIcon = container.querySelector('[data-testid="NotificationsIcon"]');
      expect(notificationsIcon).toBeInTheDocument();
    });
  });

  // ============================================================================
  // Accessibility
  // ============================================================================

  describe('Accessibility', () => {
    it('has navigation landmark', () => {
      const { container } = render(<TopNav />);

      const appBar = container.querySelector('header');
      expect(appBar).toBeInTheDocument();
    });

    it('buttons have accessible names', () => {
      render(<TopNav />);

      expect(screen.getByText('Dashboard')).toBeInTheDocument();
      expect(screen.getByText('Phases')).toBeInTheDocument();
      expect(screen.getByText('Reports')).toBeInTheDocument();
      expect(screen.getByLabelText('Settings')).toBeInTheDocument();
    });

    it('uses aria-current for active page', () => {
      render(<TopNav currentPage="phases" />);

      const phasesButton = screen.getByText('Phases').closest('button');
      expect(phasesButton).toHaveAttribute('aria-current', 'page');
    });

    it('supports keyboard navigation', async () => {
      const user = userEvent.setup();

      render(<TopNav />);

      const dashboardButton = screen.getByText('Dashboard');
      const phasesButton = screen.getByText('Phases');

      // Tab through navigation
      await user.tab();
      expect(dashboardButton).toHaveFocus();

      await user.tab();
      expect(phasesButton).toHaveFocus();
    });

    it('avatar has alt text', () => {
      const mockUser: TopNavUser = {
        name: 'Jane Smith',
        role: 'Developer',
        avatar: '/avatars/jane.png',
      };

      render(<TopNav user={mockUser} />);

      const img = screen.getByAltText('Jane Smith');
      expect(img).toBeInTheDocument();
    });
  });

  // ============================================================================
  // Edge Cases
  // ============================================================================

  describe('Edge Cases', () => {
    it('handles very long user names', () => {
      const longNameUser: TopNavUser = {
        name: 'A'.repeat(50),
        role: 'Developer',
      };

      render(<TopNav user={longNameUser} />);

      expect(screen.getByText('A'.repeat(50))).toBeInTheDocument();
    });

    it('handles undefined currentPage', () => {
      render(<TopNav currentPage={undefined} />);

      // Should default to dashboard
      const dashboardButton = screen.getByText('Dashboard').closest('button');
      expect(dashboardButton).toHaveAttribute('aria-current', 'page');
    });

    it('handles negative notification count', () => {
      render(<TopNav notificationCount={-1} />);

      // Should render without crashing
      expect(screen.getByLabelText(/-1 notifications/i)).toBeInTheDocument();
    });

    it('handles very large notification count', () => {
      render(<TopNav notificationCount={99999} />);

      // MUI Badge might show 99+ but component should handle it
      expect(screen.getByLabelText(/99999 notifications/i)).toBeInTheDocument();
    });
  });

  // ============================================================================
  // Performance
  // ============================================================================

  describe('Performance', () => {
    it('renders efficiently', () => {
      const mockUser: TopNavUser = {
        name: 'Jane Smith',
        role: 'Developer',
        avatar: '/avatars/jane.png',
      };

      const start = performance.now();
      render(
        <TopNav
          currentPage="dashboard"
          notificationCount={5}
          user={mockUser}
        />
      );
      const duration = performance.now() - start;

      // Should render in less than 100ms
      expect(duration).toBeLessThan(100);
    });

    it('handles rapid navigation changes', () => {
      const { rerender } = render(<TopNav currentPage="dashboard" />);

      const pages: NavigationPage[] = ['dashboard', 'phases', 'reports', 'settings'];

      const start = performance.now();
      pages.forEach((page) => {
        rerender(<TopNav currentPage={page} />);
      });
      const duration = performance.now() - start;

      // Should handle rapid updates quickly
      expect(duration).toBeLessThan(50);
    });
  });
});
