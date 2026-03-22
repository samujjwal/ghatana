/**
 * Breadcrumbs Component Tests
 *
 * @description Comprehensive test suite for Breadcrumbs component
 * ensuring 100% code coverage and all edge cases.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { useNavigate } from 'react-router';
import { useAtomValue } from 'jotai';
import { Breadcrumbs } from './Breadcrumbs';

// Define Breadcrumb type locally to avoid import issues in tests
interface Breadcrumb {
  id: string;
  label: string;
  href: string;
  icon?: string;
}

// Mock dependencies
vi.mock('react-router', () => ({
  useNavigate: vi.fn(),
}));

vi.mock('jotai', () => ({
  useAtomValue: vi.fn(),
}));

describe('Breadcrumbs', () => {
  const mockNavigate = vi.fn();
  const mockBreadcrumbs: Breadcrumb[] = [
    { id: '1', label: 'Projects', href: '/projects' },
    { id: '2', label: 'My Project', href: '/project/123' },
    { id: '3', label: 'Development', href: '/project/123/dev' },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    (useNavigate as unknown).mockReturnValue(mockNavigate);
  });

  describe('Rendering', () => {
    it('should render breadcrumbs correctly', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs />);

      expect(screen.getByText('Projects')).toBeInTheDocument();
      expect(screen.getByText('My Project')).toBeInTheDocument();
      expect(screen.getByText('Development')).toBeInTheDocument();
    });

    it('should render home icon when showHome is true', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs showHome={true} />);

      expect(screen.getByLabelText('Home')).toBeInTheDocument();
    });

    it('should not render home icon when showHome is false', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs showHome={false} />);

      expect(screen.queryByLabelText('Home')).not.toBeInTheDocument();
    });

    it('should render nothing when no breadcrumbs and showHome is false', () => {
      (useAtomValue as unknown).mockReturnValue([]);

      const { container } = render(<Breadcrumbs showHome={false} />);

      expect(container.firstChild).toBeNull();
    });

    it('should apply custom className', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      const { container } = render(<Breadcrumbs className="custom-class" />);

      expect(container.querySelector('.custom-class')).toBeInTheDocument();
    });
  });

  describe('Navigation', () => {
    it('should navigate to breadcrumb href when clicked', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs />);

      fireEvent.click(screen.getByText('Projects'));

      expect(mockNavigate).toHaveBeenCalledWith('/projects');
    });

    it('should navigate to dashboard when home icon is clicked', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs showHome={true} />);

      fireEvent.click(screen.getByLabelText('Home'));

      expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
    });

    it('should not navigate when last breadcrumb is clicked', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs />);

      fireEvent.click(screen.getByText('Development'));

      expect(mockNavigate).not.toHaveBeenCalled();
    });

    it('should mark last breadcrumb with aria-current', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs />);

      const lastBreadcrumb = screen.getByText('Development');
      expect(lastBreadcrumb).toHaveAttribute('aria-current', 'page');
    });
  });

  describe('Truncation', () => {
    it('should truncate breadcrumbs when exceeding maxItems', () => {
      const manyBreadcrumbs: Breadcrumb[] = [
        { id: '1', label: 'Level 1', href: '/1' },
        { id: '2', label: 'Level 2', href: '/2' },
        { id: '3', label: 'Level 3', href: '/3' },
        { id: '4', label: 'Level 4', href: '/4' },
        { id: '5', label: 'Level 5', href: '/5' },
        { id: '6', label: 'Level 6', href: '/6' },
      ];

      (useAtomValue as unknown).mockReturnValue(manyBreadcrumbs);

      render(<Breadcrumbs maxItems={3} />);

      expect(screen.getByText('Level 1')).toBeInTheDocument();
      expect(screen.getByText('...')).toBeInTheDocument();
      expect(screen.getByText('Level 5')).toBeInTheDocument();
      expect(screen.getByText('Level 6')).toBeInTheDocument();
      expect(screen.queryByText('Level 2')).not.toBeInTheDocument();
      expect(screen.queryByText('Level 3')).not.toBeInTheDocument();
      expect(screen.queryByText('Level 4')).not.toBeInTheDocument();
    });

    it('should not truncate when breadcrumbs count equals maxItems', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs maxItems={3} />);

      expect(screen.queryByText('...')).not.toBeInTheDocument();
      expect(screen.getByText('Projects')).toBeInTheDocument();
      expect(screen.getByText('My Project')).toBeInTheDocument();
      expect(screen.getByText('Development')).toBeInTheDocument();
    });

    it('should not truncate when breadcrumbs count is less than maxItems', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs maxItems={5} />);

      expect(screen.queryByText('...')).not.toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('should have proper ARIA labels', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs />);

      const nav = screen.getByRole('navigation');
      expect(nav).toHaveAttribute('aria-label', 'Breadcrumb');
    });

    it('should disable last breadcrumb button', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs />);

      const lastBreadcrumb = screen.getByText('Development');
      expect(lastBreadcrumb).toBeDisabled();
    });

    it('should not disable non-last breadcrumbs', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs />);

      const firstBreadcrumb = screen.getByText('Projects');
      expect(firstBreadcrumb).not.toBeDisabled();
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty breadcrumbs array', () => {
      (useAtomValue as unknown).mockReturnValue([]);

      render(<Breadcrumbs showHome={true} />);

      expect(screen.getByLabelText('Home')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: /Projects/i })).not.toBeInTheDocument();
    });

    it('should handle single breadcrumb', () => {
      (useAtomValue as unknown).mockReturnValue([mockBreadcrumbs[0]]);

      render(<Breadcrumbs />);

      expect(screen.getByText('Projects')).toBeInTheDocument();
      expect(screen.getByText('Projects')).toBeDisabled();
    });

    it('should handle breadcrumbs with special characters', () => {
      const specialBreadcrumbs: Breadcrumb[] = [
        { id: '1', label: 'Project & Settings', href: '/settings' },
        { id: '2', label: 'User <Admin>', href: '/admin' },
      ];

      (useAtomValue as unknown).mockReturnValue(specialBreadcrumbs);

      render(<Breadcrumbs />);

      expect(screen.getByText('Project & Settings')).toBeInTheDocument();
      expect(screen.getByText('User <Admin>')).toBeInTheDocument();
    });
  });

  describe('Styling', () => {
    it('should apply correct styles to last breadcrumb', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs />);

      const lastBreadcrumb = screen.getByText('Development');
      expect(lastBreadcrumb).toHaveClass('font-medium');
    });

    it('should apply hover styles to non-last breadcrumbs', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs />);

      const firstBreadcrumb = screen.getByText('Projects');
      expect(firstBreadcrumb).toHaveClass('hover:text-gray-900');
    });
  });

  describe('Integration', () => {
    it('should work with React Router navigation', () => {
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      render(<Breadcrumbs />);

      fireEvent.click(screen.getByText('My Project'));

      expect(mockNavigate).toHaveBeenCalledWith('/project/123');
      expect(mockNavigate).toHaveBeenCalledTimes(1);
    });

    it('should update when breadcrumbs atom changes', () => {
      const { rerender } = render(<Breadcrumbs />);
      (useAtomValue as unknown).mockReturnValue(mockBreadcrumbs);

      rerender(<Breadcrumbs />);

      expect(screen.getByText('Projects')).toBeInTheDocument();

      const newBreadcrumbs: Breadcrumb[] = [
        { id: '1', label: 'New Path', href: '/new' },
      ];
      (useAtomValue as unknown).mockReturnValue(newBreadcrumbs);

      rerender(<Breadcrumbs />);

      expect(screen.getByText('New Path')).toBeInTheDocument();
      expect(screen.queryByText('Projects')).not.toBeInTheDocument();
    });
  });
});
