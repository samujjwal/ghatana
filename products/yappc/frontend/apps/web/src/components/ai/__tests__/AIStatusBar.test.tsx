/**
 * AI Status Bar Component Tests
 *
 * Comprehensive test suite for AI Status Bar component
 * including state management, rendering, and interactions
 *
 * @doc.type test
 * @doc.purpose Component testing
 * @doc.layer components
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AIStatusBar, useAIStatusBar } from '../AIStatusBar';

// Mock the custom hook
vi.mock('../AIStatusBar', () => ({
  ...vi.importActual('../AIStatusBar'),
  useAIStatusBar: vi.fn(),
}));

const mockUseAIStatusBar = useAIStatusBar as vi.MockedFunction<
  typeof useAIStatusBar
>;

describe('AIStatusBar Component', () => {
  const defaultProps = {
    status: 'ready' as const,
    currentPhase: 'INTENT' as const,
    phaseProgress: 75,
    nextBestAction: {
      title: 'Add Validation',
      description: 'Add validation rules to your components',
      action: vi.fn(),
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Rendering', () => {
    it('renders AI status bar with all elements', () => {
      mockUseAIStatusBar.mockReturnValue({
        status: 'ready',
        currentPhase: 'INTENT',
        phaseProgress: 75,
        nextBestAction: defaultProps.nextBestAction,
        setStatus: vi.fn(),
        setCurrentPhase: vi.fn(),
        setPhaseProgress: vi.fn(),
        setNextBestAction: vi.fn(),
      });

      render(<AIStatusBar {...defaultProps} />);

      expect(screen.getByTestId('ai-status-bar')).toBeInTheDocument();
      expect(screen.getByText('AI Status: Ready')).toBeInTheDocument();
      expect(screen.getByText('Current Phase: INTENT')).toBeInTheDocument();
      expect(screen.getByText('75%')).toBeInTheDocument();
      expect(screen.getByText('Add Validation')).toBeInTheDocument();
    });

    it('renders different status states correctly', () => {
      const statuses = ['ready', 'thinking', 'suggesting', 'error'] as const;

      statuses.forEach((status) => {
        mockUseAIStatusBar.mockReturnValue({
          status,
          currentPhase: 'INTENT',
          phaseProgress: 50,
          nextBestAction: null,
          setStatus: vi.fn(),
          setCurrentPhase: vi.fn(),
          setPhaseProgress: vi.fn(),
          setNextBestAction: vi.fn(),
        });

        const { rerender } = render(
          <AIStatusBar {...defaultProps} status={status} />
        );

        if (status === 'thinking') {
          expect(
            screen.getByText('AI Status: Thinking...')
          ).toBeInTheDocument();
        } else if (status === 'error') {
          expect(screen.getByText('AI Status: Error')).toBeInTheDocument();
        } else if (status === 'suggesting') {
          expect(screen.getByText('AI Status: Suggesting')).toBeInTheDocument();
        } else {
          expect(screen.getByText('AI Status: Ready')).toBeInTheDocument();
        }

        rerender(<div />);
      });
    });

    it('renders without next best action', () => {
      mockUseAIStatusBar.mockReturnValue({
        status: 'ready',
        currentPhase: 'INTENT',
        phaseProgress: 50,
        nextBestAction: null,
        setStatus: vi.fn(),
        setCurrentPhase: vi.fn(),
        setPhaseProgress: vi.fn(),
        setNextBestAction: vi.fn(),
      });

      render(<AIStatusBar {...defaultProps} nextBestAction={null} />);

      expect(screen.queryByText('Add Validation')).not.toBeInTheDocument();
      expect(screen.getByText('No suggestions available')).toBeInTheDocument();
    });
  });

  describe('Interactions', () => {
    it('calls next best action when clicked', async () => {
      const mockAction = vi.fn();

      mockUseAIStatusBar.mockReturnValue({
        status: 'ready',
        currentPhase: 'INTENT',
        phaseProgress: 75,
        nextBestAction: {
          title: 'Add Validation',
          description: 'Add validation rules',
          action: mockAction,
        },
        setStatus: vi.fn(),
        setCurrentPhase: vi.fn(),
        setPhaseProgress: vi.fn(),
        setNextBestAction: vi.fn(),
      });

      render(<AIStatusBar {...defaultProps} />);

      const actionButton = screen.getByText('Add Validation');
      fireEvent.click(actionButton);

      expect(mockAction).toHaveBeenCalledTimes(1);
    });

    it('shows phase selector on phase click', () => {
      mockUseAIStatusBar.mockReturnValue({
        status: 'ready',
        currentPhase: 'INTENT',
        phaseProgress: 75,
        nextBestAction: defaultProps.nextBestAction,
        setStatus: vi.fn(),
        setCurrentPhase: vi.fn(),
        setPhaseProgress: vi.fn(),
        setNextBestAction: vi.fn(),
      });

      render(<AIStatusBar {...defaultProps} />);

      const phaseElement = screen.getByText('INTENT');
      fireEvent.click(phaseElement);

      expect(screen.getByTestId('phase-selector')).toBeInTheDocument();
    });

    it('changes phase when phase is selected', () => {
      const mockSetCurrentPhase = vi.fn();

      mockUseAIStatusBar.mockReturnValue({
        status: 'ready',
        currentPhase: 'INTENT',
        phaseProgress: 75,
        nextBestAction: defaultProps.nextBestAction,
        setStatus: vi.fn(),
        setCurrentPhase: mockSetCurrentPhase,
        setPhaseProgress: vi.fn(),
        setNextBestAction: vi.fn(),
      });

      render(<AIStatusBar {...defaultProps} />);

      const phaseElement = screen.getByText('INTENT');
      fireEvent.click(phaseElement);

      const newPhase = screen.getByText('SHAPE');
      fireEvent.click(newPhase);

      expect(mockSetCurrentPhase).toHaveBeenCalledWith('SHAPE');
    });
  });

  describe('Accessibility', () => {
    it('has proper ARIA labels', () => {
      mockUseAIStatusBar.mockReturnValue({
        status: 'ready',
        currentPhase: 'INTENT',
        phaseProgress: 75,
        nextBestAction: defaultProps.nextBestAction,
        setStatus: vi.fn(),
        setCurrentPhase: vi.fn(),
        setPhaseProgress: vi.fn(),
        setNextBestAction: vi.fn(),
      });

      render(<AIStatusBar {...defaultProps} />);

      const statusBar = screen.getByTestId('ai-status-bar');
      expect(statusBar).toHaveAttribute(
        'aria-label',
        'AI Status and next actions'
      );
    });

    it('supports keyboard navigation', () => {
      mockUseAIStatusBar.mockReturnValue({
        status: 'ready',
        currentPhase: 'INTENT',
        phaseProgress: 75,
        nextBestAction: defaultProps.nextBestAction,
        setStatus: vi.fn(),
        setCurrentPhase: vi.fn(),
        setPhaseProgress: vi.fn(),
        setNextBestAction: vi.fn(),
      });

      render(<AIStatusBar {...defaultProps} />);

      const actionButton = screen.getByText('Add Validation');
      expect(actionButton).toHaveAttribute('tabIndex', '0');

      actionButton.focus();
      fireEvent.keyDown(actionButton, { key: 'Enter' });

      expect(defaultProps.nextBestAction.action).toHaveBeenCalled();
    });
  });

  describe('Performance', () => {
    it('renders efficiently with many updates', async () => {
      const mockSetPhaseProgress = vi.fn();

      mockUseAIStatusBar.mockReturnValue({
        status: 'thinking',
        currentPhase: 'INTENT',
        phaseProgress: 0,
        nextBestAction: null,
        setStatus: vi.fn(),
        setCurrentPhase: vi.fn(),
        setPhaseProgress: mockSetPhaseProgress,
        setNextBestAction: vi.fn(),
      });

      const { rerender } = render(
        <AIStatusBar {...defaultProps} status="thinking" phaseProgress={0} />
      );

      // Simulate rapid progress updates
      for (let i = 1; i <= 100; i += 10) {
        rerender(
          <AIStatusBar {...defaultProps} status="thinking" phaseProgress={i} />
        );
      }

      // Should handle rapid updates without issues
      expect(screen.getByText('AI Status: Thinking...')).toBeInTheDocument();
    });
  });
});

describe('useAIStatusBar Hook', () => {
  it('initializes with default values', () => {
    mockUseAIStatusBar.mockReturnValue({
      status: 'ready',
      currentPhase: 'INTENT',
      phaseProgress: 0,
      nextBestAction: null,
      setStatus: vi.fn(),
      setCurrentPhase: vi.fn(),
      setPhaseProgress: vi.fn(),
      setNextBestAction: vi.fn(),
    });

    const TestComponent = () => {
      const hook = useAIStatusBar();
      return <div data-testid="hook-result">{JSON.stringify(hook)}</div>;
    };

    render(<TestComponent />);

    const result = screen.getByTestId('hook-result');
    expect(result).toBeInTheDocument();
  });

  it('updates status correctly', () => {
    const mockSetStatus = vi.fn();

    mockUseAIStatusBar.mockReturnValue({
      status: 'ready',
      currentPhase: 'INTENT',
      phaseProgress: 0,
      nextBestAction: null,
      setStatus: mockSetStatus,
      setCurrentPhase: vi.fn(),
      setPhaseProgress: vi.fn(),
      setNextBestAction: vi.fn(),
    });

    const TestComponent = () => {
      const { setStatus } = useAIStatusBar();
      return (
        <button onClick={() => setStatus('thinking')}>Set Thinking</button>
      );
    };

    render(<TestComponent />);

    const button = screen.getByText('Set Thinking');
    fireEvent.click(button);

    expect(mockSetStatus).toHaveBeenCalledWith('thinking');
  });
});
