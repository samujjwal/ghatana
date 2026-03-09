/**
 * PhaseOverviewPage Component Tests
 *
 * @description Comprehensive test suite for PhaseOverviewPage
 * ensuring 100% code coverage and all edge cases.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { useParams } from 'react-router';

// Mock dependencies
vi.mock('react-router', () => ({
  useParams: vi.fn(),
}));

vi.mock('framer-motion', () => ({
  motion: {
    div: ({ children, ...props }: unknown) => <div {...props}>{children}</div>,
  },
}));

vi.mock('@ghatana/yappc-ui/utils', () => ({
  cn: (...classes: unknown[]) => classes.filter(Boolean).join(' '),
}));

vi.mock('@ghatana/yappc-ui/components/Button', () => ({
  Button: ({ children, onClick, ...props }: unknown) => (
    <button onClick={onClick} {...props}>{children}</button>
  ),
}));

// Import after mocks
import PhaseOverviewPage from './PhaseOverviewPage';

describe('PhaseOverviewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    (useParams as unknown).mockReturnValue({ 
      projectId: '123', 
      phase: 'development' 
    });
  });

  describe('Rendering', () => {
    it('should render the phase overview page', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText(/Overview/i)).toBeInTheDocument();
    });

    it('should display phase name in title', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('Development Overview')).toBeInTheDocument();
    });

    it('should render metrics grid', () => {
      render(<PhaseOverviewPage />);
      
      expect(screen.getByText('Phase Progress')).toBeInTheDocument();
      expect(screen.getByText('Tasks Completed')).toBeInTheDocument();
      expect(screen.getByText('Time Remaining')).toBeInTheDocument();
      expect(screen.getByText('Active Blockers')).toBeInTheDocument();
    });

    it('should render recent tasks section', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('Recent Tasks')).toBeInTheDocument();
    });

    it('should render AI suggestions panel', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('AI Suggestion')).toBeInTheDocument();
    });

    it('should render View All Tasks button', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('View All Tasks')).toBeInTheDocument();
    });
  });

  describe('Metrics Display', () => {
    it('should display progress metric', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('67%')).toBeInTheDocument();
    });

    it('should display tasks completed metric', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('24/36')).toBeInTheDocument();
    });

    it('should display time remaining metric', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('5 days')).toBeInTheDocument();
    });

    it('should display blockers metric', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('2')).toBeInTheDocument();
    });

    it('should display metric change indicators', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('+12%')).toBeInTheDocument();
      expect(screen.getByText('+3')).toBeInTheDocument();
    });
  });

  describe('Tasks List', () => {
    it('should display task titles', () => {
      render(<PhaseOverviewPage />);
      
      expect(screen.getByText('Complete infrastructure setup')).toBeInTheDocument();
      expect(screen.getByText('Review security configurations')).toBeInTheDocument();
      expect(screen.getByText('Deploy to staging environment')).toBeInTheDocument();
    });

    it('should display task statuses', () => {
      render(<PhaseOverviewPage />);
      
      expect(screen.getByText('in progress')).toBeInTheDocument();
      expect(screen.getByText('pending')).toBeInTheDocument();
      expect(screen.getByText('completed')).toBeInTheDocument();
    });

    it('should display task priorities', () => {
      render(<PhaseOverviewPage />);
      
      expect(screen.getAllByText(/high priority/i).length).toBeGreaterThan(0);
      expect(screen.getByText(/medium priority/i)).toBeInTheDocument();
    });

    it('should display due dates when available', () => {
      render(<PhaseOverviewPage />);
      
      expect(screen.getByText(/Due 2026-02-05/i)).toBeInTheDocument();
      expect(screen.getByText(/Due 2026-02-06/i)).toBeInTheDocument();
    });

    it('should render View button for each task', () => {
      render(<PhaseOverviewPage />);
      
      const viewButtons = screen.getAllByText('View');
      expect(viewButtons.length).toBe(3);
    });
  });

  describe('AI Suggestions', () => {
    it('should display AI suggestion content', () => {
      render(<PhaseOverviewPage />);
      
      expect(screen.getByText(/Based on your current progress/i)).toBeInTheDocument();
    });

    it('should render Apply Suggestion button', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('Apply Suggestion')).toBeInTheDocument();
    });

    it('should render Dismiss button', () => {
      render(<PhaseOverviewPage />);
      expect(screen.getByText('Dismiss')).toBeInTheDocument();
    });
  });

  describe('Phase Variations', () => {
    it('should display Bootstrap phase correctly', () => {
      (useParams as unknown).mockReturnValue({ 
        projectId: '123', 
        phase: 'bootstrap' 
      });

      render(<PhaseOverviewPage />);
      expect(screen.getByText('Bootstrap Overview')).toBeInTheDocument();
    });

    it('should display Initialize phase correctly', () => {
      (useParams as unknown).mockReturnValue({ 
        projectId: '123', 
        phase: 'init' 
      });

      render(<PhaseOverviewPage />);
      expect(screen.getByText('Init Overview')).toBeInTheDocument();
    });

    it('should display Operations phase correctly', () => {
      (useParams as unknown).mockReturnValue({ 
        projectId: '123', 
        phase: 'operations' 
      });

      render(<PhaseOverviewPage />);
      expect(screen.getByText('Operations Overview')).toBeInTheDocument();
    });

    it('should display Security phase correctly', () => {
      (useParams as unknown).mockReturnValue({ 
        projectId: '123', 
        phase: 'security' 
      });

      render(<PhaseOverviewPage />);
      expect(screen.getByText('Security Overview')).toBeInTheDocument();
    });
  });

  describe('Edge Cases', () => {
    it('should handle missing phase parameter', () => {
      (useParams as unknown).mockReturnValue({ projectId: '123' });

      render(<PhaseOverviewPage />);
      expect(screen.getByText('Phase Overview')).toBeInTheDocument();
    });

    it('should handle undefined phase', () => {
      (useParams as unknown).mockReturnValue({ 
        projectId: '123', 
        phase: undefined 
      });

      render(<PhaseOverviewPage />);
      expect(screen.getByText('Phase Overview')).toBeInTheDocument();
    });

    it('should capitalize phase name correctly', () => {
      (useParams as unknown).mockReturnValue({ 
        projectId: '123', 
        phase: 'collaboration' 
      });

      render(<PhaseOverviewPage />);
      expect(screen.getByText('Collaboration Overview')).toBeInTheDocument();
    });
  });

  describe('Styling', () => {
    it('should apply correct status colors', () => {
      render(<PhaseOverviewPage />);
      
      const completedStatus = screen.getByText('completed');
      expect(completedStatus).toHaveClass('bg-green-100');
      
      const inProgressStatus = screen.getByText('in progress');
      expect(inProgressStatus).toHaveClass('bg-blue-100');
      
      const pendingStatus = screen.getByText('pending');
      expect(pendingStatus).toHaveClass('bg-gray-100');
    });

    it('should apply correct trend colors', () => {
      render(<PhaseOverviewPage />);
      
      const positiveChange = screen.getByText('+12%');
      expect(positiveChange).toHaveClass('text-green-600');
    });
  });

  describe('Accessibility', () => {
    it('should have proper heading hierarchy', () => {
      render(<PhaseOverviewPage />);
      
      const h1 = screen.getByRole('heading', { level: 1 });
      expect(h1).toBeInTheDocument();
      
      const h2Elements = screen.getAllByRole('heading', { level: 2 });
      expect(h2Elements.length).toBeGreaterThan(0);
    });

    it('should have accessible task list', () => {
      render(<PhaseOverviewPage />);
      
      const taskTitles = screen.getAllByRole('heading', { level: 3 });
      expect(taskTitles.length).toBe(3);
    });
  });

  describe('Responsive Design', () => {
    it('should render metrics in grid layout', () => {
      const { container } = render(<PhaseOverviewPage />);
      
      const metricsGrid = container.querySelector('.grid');
      expect(metricsGrid).toBeInTheDocument();
    });
  });
});
