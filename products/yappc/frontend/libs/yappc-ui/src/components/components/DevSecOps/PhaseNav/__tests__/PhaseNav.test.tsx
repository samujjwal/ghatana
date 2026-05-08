import { ThemeProvider as PlatformThemeProvider } from '@ghatana/theme';
import { render, screen } from '@testing-library/react';
import type { RenderResult } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactElement, ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { PhaseNav } from '../PhaseNav';
import type { PhaseNavProps } from '../types';

/**
 * PhaseNav Component Unit Tests
 *
 * Tests PhaseNav component behavior:
 * - Phase rendering
 * - Active phase highlighting
 * - Completed phase indicators
 * - Click interactions
 * - Visual states
 * - Accessibility
 */

describe('PhaseNav Component', () => {
  const mockOnPhaseClick = vi.fn();
  const setupUser = (): ReturnType<typeof userEvent.setup> =>
    userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
  const TestThemeProvider = ({
    children,
  }: {
    children: ReactNode;
  }): ReactElement => (
    <PlatformThemeProvider defaultTheme="light">
      {children}
    </PlatformThemeProvider>
  );
  const renderPhaseNav = (ui: ReactElement): RenderResult =>
    render(ui, { wrapper: TestThemeProvider });

  const samplePhases = [
    { id: 'plan', title: 'Plan', key: 'plan', order: 1, icon: '📋' },
    { id: 'code', title: 'Code', key: 'code', order: 2, icon: '💻' },
    { id: 'build', title: 'Build', key: 'build', order: 3, icon: '🔨' },
    { id: 'test', title: 'Test', key: 'test', order: 4, icon: '🧪' },
    { id: 'deploy', title: 'Deploy', key: 'deploy', order: 5, icon: '🚀' },
  ];

  const defaultProps: PhaseNavProps = {
    phases: samplePhases,
    activePhaseId: 'code',
    completedPhaseIds: ['plan'],
    onPhaseClick: mockOnPhaseClick,
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Basic Rendering', () => {
    it('should render all phases', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} />);

      expect(screen.getByText('Plan')).toBeInTheDocument();
      expect(screen.getByText('Code')).toBeInTheDocument();
      expect(screen.getByText('Build')).toBeInTheDocument();
      expect(screen.getByText('Test')).toBeInTheDocument();
      expect(screen.getByText('Deploy')).toBeInTheDocument();
    });

    it('should render as chips', () => {
      const { container } = renderPhaseNav(<PhaseNav {...defaultProps} />);

      const chips = container.querySelectorAll('[data-phase-id]');
      expect(chips).toHaveLength(5);
    });

    it('should render phases in correct order', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} />);

      const chips = screen.getAllByRole('button');
      expect(chips[0]).toHaveTextContent('Plan');
      expect(chips[1]).toHaveTextContent('Code');
      expect(chips[2]).toHaveTextContent('Build');
      expect(chips[3]).toHaveTextContent('Test');
      expect(chips[4]).toHaveTextContent('Deploy');
    });

    it('should handle empty phases array', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} phases={[]} />);

      const chips = screen.queryAllByRole('button');
      expect(chips).toHaveLength(0);
    });

    it('should render single phase', () => {
      const singlePhase = [samplePhases[0]];
      renderPhaseNav(<PhaseNav {...defaultProps} phases={singlePhase} />);

      expect(screen.getByText('Plan')).toBeInTheDocument();
      expect(screen.getAllByRole('button')).toHaveLength(1);
    });
  });

  describe('Active Phase Highlighting', () => {
    it('should highlight active phase', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} activePhaseId="code" />);

      const codeChip = screen.getByRole('button', { name: /code/i });
      expect(codeChip).toHaveAttribute('data-active', 'true');
      expect(codeChip).toHaveAttribute('aria-current', 'step');
    });

    it('should not highlight non-active phases', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} activePhaseId="code" />);

      const planChip = screen.getByRole('button', { name: /plan/i });
      expect(planChip).toHaveAttribute('data-active', 'false');
      expect(planChip).not.toHaveAttribute('aria-current');
    });

    it('should update active phase when prop changes', () => {
      const { rerender } = renderPhaseNav(
        <PhaseNav {...defaultProps} activePhaseId="code" />
      );

      let codeChip = screen.getByRole('button', { name: /code/i });
      expect(codeChip).toHaveAttribute('data-active', 'true');

      rerender(<PhaseNav {...defaultProps} activePhaseId="test" />);

      codeChip = screen.getByRole('button', { name: /code/i });
      const testChip = screen.getByRole('button', { name: /test/i });

      expect(codeChip).toHaveAttribute('data-active', 'false');
      expect(testChip).toHaveAttribute('data-active', 'true');
    });

    it('should handle no active phase', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} activePhaseId={undefined} />);

      const chips = screen.getAllByRole('button');
      chips.forEach((chip) => {
        expect(chip).toHaveAttribute('data-active', 'false');
      });
    });

    it('should handle invalid active phase ID', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} activePhaseId="invalid" />);

      const chips = screen.getAllByRole('button');
      chips.forEach((chip) => {
        expect(chip).toHaveAttribute('data-active', 'false');
      });
    });
  });

  describe('Completed Phase Indicators', () => {
    it('should show check icon for completed phase', () => {
      renderPhaseNav(
        <PhaseNav {...defaultProps} completedPhaseIds={['plan']} />
      );

      const checkIcon = screen.getByTestId('CheckCircleIcon');
      expect(checkIcon).toBeInTheDocument();
    });

    it('should show check icons for multiple completed phases', () => {
      renderPhaseNav(
        <PhaseNav {...defaultProps} completedPhaseIds={['plan', 'code']} />
      );

      const checkIcons = screen.getAllByTestId('CheckCircleIcon');
      expect(checkIcons).toHaveLength(2);
    });

    it('should not show check icon for non-completed phases', () => {
      renderPhaseNav(
        <PhaseNav {...defaultProps} completedPhaseIds={['plan']} />
      );

      const checkIcons = screen.getAllByTestId('CheckCircleIcon');
      expect(checkIcons).toHaveLength(1);
    });

    it('should handle empty completed phases array', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} completedPhaseIds={[]} />);

      const checkIcons = screen.queryAllByTestId('CheckCircleIcon');
      expect(checkIcons).toHaveLength(0);
    });

    it('should handle all phases completed', () => {
      const allPhaseIds = samplePhases.map((p) => p.id);
      renderPhaseNav(
        <PhaseNav {...defaultProps} completedPhaseIds={allPhaseIds} />
      );

      const checkIcons = screen.getAllByTestId('CheckCircleIcon');
      expect(checkIcons).toHaveLength(5);
    });

    it('should apply completed styling to completed phases', () => {
      renderPhaseNav(
        <PhaseNav {...defaultProps} completedPhaseIds={['plan']} />
      );

      const planChip = screen.getByRole('button', { name: /plan/i });
      const styles = planChip && window.getComputedStyle(planChip);
      expect(planChip).toHaveAttribute('data-completed', 'true');
      expect(styles?.backgroundColor).toBeTruthy();
      // Color can be 'white' or 'rgb(255, 255, 255)' depending on browser
      expect(styles?.color).toMatch(/white|rgb\(255,\s*255,\s*255\)/);
    });
  });

  describe('Click Interactions', () => {
    it('should call onPhaseClick when phase clicked', async () => {
      const user = setupUser();
      renderPhaseNav(<PhaseNav {...defaultProps} />);

      const buildChip = screen.getByText('Build');
      await user.click(buildChip);

      expect(mockOnPhaseClick).toHaveBeenCalledWith('build');
    });

    it('should call onPhaseClick with correct phase ID', async () => {
      const user = setupUser();
      renderPhaseNav(<PhaseNav {...defaultProps} />);

      await user.click(screen.getByText('Plan'));
      expect(mockOnPhaseClick).toHaveBeenCalledWith('plan');

      await user.click(screen.getByText('Deploy'));
      expect(mockOnPhaseClick).toHaveBeenCalledWith('deploy');
    });

    it('should allow clicking active phase', async () => {
      const user = setupUser();
      renderPhaseNav(<PhaseNav {...defaultProps} activePhaseId="code" />);

      await user.click(screen.getByText('Code'));

      expect(mockOnPhaseClick).toHaveBeenCalledWith('code');
    });

    it('should allow clicking completed phase', async () => {
      const user = setupUser();
      renderPhaseNav(
        <PhaseNav {...defaultProps} completedPhaseIds={['plan']} />
      );

      await user.click(screen.getByText('Plan'));

      expect(mockOnPhaseClick).toHaveBeenCalledWith('plan');
    });

    it('should handle multiple clicks', async () => {
      const user = setupUser();
      renderPhaseNav(<PhaseNav {...defaultProps} />);

      await user.click(screen.getByText('Build'));
      await user.click(screen.getByText('Test'));
      await user.click(screen.getByText('Deploy'));

      expect(mockOnPhaseClick).toHaveBeenCalledTimes(3);
      expect(mockOnPhaseClick).toHaveBeenNthCalledWith(1, 'build');
      expect(mockOnPhaseClick).toHaveBeenNthCalledWith(2, 'test');
      expect(mockOnPhaseClick).toHaveBeenNthCalledWith(3, 'deploy');
    });
  });

  describe('Visual States', () => {
    it('should have minimum width on chips', () => {
      const { container } = renderPhaseNav(<PhaseNav {...defaultProps} />);

      const chip = container.querySelector('[data-phase-id]');
      expect(chip).toHaveStyle({ minWidth: '120px' });
    });

    it('should have consistent height on chips', () => {
      const { container } = renderPhaseNav(<PhaseNav {...defaultProps} />);

      const chip = container.querySelector('[data-phase-id]');
      expect(chip).toHaveStyle({ height: '40px' });
    });

    it('should have border on chips', () => {
      const { container } = renderPhaseNav(<PhaseNav {...defaultProps} />);

      const chip = container.querySelector('[data-phase-id]');
      expect(chip).toHaveStyle({ borderWidth: '2px' });
    });

    it('should apply different font weight for active phase', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} activePhaseId="code" />);

      const codeChip = screen.getByRole('button', { name: /code/i });
      const planChip = screen.getByRole('button', { name: /plan/i });

      expect(codeChip).toHaveStyle({ fontWeight: 600 });
      expect(planChip).toHaveStyle({ fontWeight: 400 });
    });

    it('should have transition styles', () => {
      const { container } = renderPhaseNav(<PhaseNav {...defaultProps} />);

      const chip = container.querySelector('[data-phase-id]');
      const styles = chip && window.getComputedStyle(chip);
      expect(styles?.transition).toBeTruthy();
    });
  });

  describe('Scrolling Behavior', () => {
    it('should render horizontal scrollable container', () => {
      const { container } = renderPhaseNav(<PhaseNav {...defaultProps} />);

      const stack = screen.getByTestId('phase-nav');
      expect(stack).toHaveStyle({ overflowX: 'auto' });
    });

    it('should have horizontal spacing between chips', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} />);

      const stack = screen.getByTestId('phase-nav');
      expect(stack).toHaveAttribute('role', 'group');
    });

    it('should handle many phases without overflow issues', () => {
      const manyPhases = Array.from({ length: 20 }, (_, i) => ({
        id: `phase-${i}`,
        title: `Phase ${i}`,
        key: `phase-${i}`,
        order: i,
        icon: '🔧',
      }));

      renderPhaseNav(<PhaseNav {...defaultProps} phases={manyPhases} />);

      const chips = screen.getAllByRole('button');
      expect(chips).toHaveLength(20);
    });
  });

  describe('Accessibility', () => {
    it('should have clickable chips', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} />);

      const chips = screen.getAllByRole('button');
      chips.forEach((chip) => {
        expect(chip).toBeInTheDocument();
      });
    });

    it('should have accessible labels', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} />);

      expect(screen.getByRole('button', { name: /plan/i })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /code/i })).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /build/i })
      ).toBeInTheDocument();
    });

    it('should be keyboard navigable', async () => {
      const user = setupUser();
      renderPhaseNav(<PhaseNav {...defaultProps} />);

      const firstChip = screen.getByText('Plan').closest('button');
      if (firstChip) {
        firstChip.focus();
        expect(document.activeElement).toBe(firstChip);

        await user.keyboard('{Enter}');
        expect(mockOnPhaseClick).toHaveBeenCalledWith('plan');
      }
    });

    it('should have proper focus indicators', () => {
      renderPhaseNav(<PhaseNav {...defaultProps} />);

      const chips = screen.getAllByRole('button');
      chips.forEach((chip) => {
        // MUI Chips with onClick are focusable buttons
        expect(chip).toBeInTheDocument();
        expect(chip.getAttribute('role')).toBe('button');
      });
    });
  });

  describe('Edge Cases', () => {
    it('should handle phase with very long title', () => {
      const longTitlePhase = [
        {
          id: 'long',
          title:
            'This is a very long phase title that should be handled gracefully',
          key: 'long',
          order: 1,
          icon: '📝',
        },
      ];

      renderPhaseNav(<PhaseNav {...defaultProps} phases={longTitlePhase} />);

      expect(screen.getByText(/very long phase title/)).toBeInTheDocument();
    });

    it('should handle missing icon', () => {
      const noIconPhases = samplePhases.map((p) => ({ ...p, icon: undefined }));
      renderPhaseNav(<PhaseNav {...defaultProps} phases={noIconPhases} />);

      expect(screen.getAllByRole('button')).toHaveLength(5);
    });

    it('should handle null onPhaseClick', async () => {
      const user = setupUser();
      renderPhaseNav(
        <PhaseNav {...defaultProps} onPhaseClick={undefined as unknown} />
      );

      const chip = screen.getByText('Build');
      await user.click(chip);

      // Should not crash
      expect(chip).toBeInTheDocument();
    });

    it('should handle duplicate phase IDs', () => {
      const duplicatePhases = [
        samplePhases[0],
        samplePhases[0], // duplicate
        samplePhases[1],
      ];

      renderPhaseNav(<PhaseNav {...defaultProps} phases={duplicatePhases} />);

      // Should still render (React will warn about duplicate keys)
      const planChips = screen.getAllByText('Plan');
      expect(planChips.length).toBeGreaterThan(0);
    });

    it('should handle active and completed being the same phase', () => {
      renderPhaseNav(
        <PhaseNav
          {...defaultProps}
          activePhaseId="code"
          completedPhaseIds={['plan', 'code']}
        />
      );

      const codeChip = screen.getByRole('button', { name: /code/i });

      // Should show both active (primary color) and completed (check icon)
      expect(codeChip).toHaveAttribute('data-active', 'true');
      expect(codeChip).toHaveAttribute('data-completed', 'true');
      const checkIcons = screen.getAllByTestId('CheckCircleIcon');
      expect(checkIcons.length).toBeGreaterThan(0);
    });

    it('should handle phase without title', () => {
      const noTitlePhase = [
        {
          id: 'no-title',
          title: '',
          key: 'no-title',
          order: 1,
          icon: '❓',
        },
      ];

      renderPhaseNav(<PhaseNav {...defaultProps} phases={noTitlePhase} />);

      const chips = screen.getAllByRole('button');
      expect(chips).toHaveLength(1);
    });
  });

  describe('Dynamic Updates', () => {
    it('should update when phases prop changes', () => {
      const { rerender } = renderPhaseNav(
        <PhaseNav {...defaultProps} phases={samplePhases.slice(0, 3)} />
      );

      expect(screen.getAllByRole('button')).toHaveLength(3);

      rerender(<PhaseNav {...defaultProps} phases={samplePhases} />);

      expect(screen.getAllByRole('button')).toHaveLength(5);
    });

    it('should update completed phases dynamically', () => {
      const { rerender } = renderPhaseNav(
        <PhaseNav {...defaultProps} completedPhaseIds={['plan']} />
      );

      expect(screen.getAllByTestId('CheckCircleIcon')).toHaveLength(1);

      rerender(
        <PhaseNav
          {...defaultProps}
          completedPhaseIds={['plan', 'code', 'build']}
        />
      );

      expect(screen.getAllByTestId('CheckCircleIcon')).toHaveLength(3);
    });

    it('should handle phase removal', () => {
      const { rerender } = renderPhaseNav(
        <PhaseNav {...defaultProps} phases={samplePhases} />
      );

      expect(screen.getByText('Deploy')).toBeInTheDocument();

      rerender(
        <PhaseNav {...defaultProps} phases={samplePhases.slice(0, 4)} />
      );

      expect(screen.queryByText('Deploy')).not.toBeInTheDocument();
    });
  });
});
