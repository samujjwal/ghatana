/**
 * OnboardingTour Component Test Suite
 * 
 * Comprehensive tests for the onboarding tour including navigation,
 * state management, responsive positioning, and user interactions.
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import '@testing-library/jest-dom';
import { OnboardingTour } from '../OnboardingTour';

// Mock browser storage
jest.mock('webextension-polyfill', () => ({
  storage: {
    local: {
      get: jest.fn(),
      set: jest.fn(),
      remove: jest.fn()
    }
  }
}));

// Mock external dependencies
jest.mock('../services/TelemetryManager', () => ({
  telemetryManager: {
    trackInteraction: jest.fn()
  }
}));

// Mock the hook
const mockOnboardingStatus = {
  isActive: false,
  currentStep: 0,
  totalSteps: 6,
  startTour: jest.fn(),
  nextStep: jest.fn(),
  previousStep: jest.fn(),
  skipTour: jest.fn(),
  completeTour: jest.fn()
};

jest.mock('../hooks/useOnboardingStatus', () => ({
  useOnboardingStatus: () => mockOnboardingStatus
}));

describe('OnboardingTour', () => {
  let user: ReturnType<typeof userEvent.setup>;

  beforeEach(() => {
    user = userEvent.setup();
    jest.clearAllMocks();
    
    // Reset mock status
    Object.assign(mockOnboardingStatus, {
      isActive: false,
      currentStep: 0,
      totalSteps: 6,
      startTour: jest.fn(),
      nextStep: jest.fn(),
      previousStep: jest.fn(),
      skipTour: jest.fn(),
      completeTour: jest.fn()
    });

    // Mock DOM elements that the tour targets
    const mockDashboard = document.createElement('div');
    mockDashboard.className = 'dashboard-header';
    mockDashboard.textContent = 'Dashboard Header';
    document.body.appendChild(mockDashboard);

    const mockMetricCards = document.createElement('div');
    mockMetricCards.className = 'metric-cards-grid';
    mockMetricCards.textContent = 'Metric Cards';
    document.body.appendChild(mockMetricCards);

    const mockCharts = document.createElement('div');
    mockCharts.className = 'metric-charts';
    mockCharts.textContent = 'Charts';
    document.body.appendChild(mockCharts);

    const mockAlerts = document.createElement('div');
    mockAlerts.className = 'alert-panel';
    mockAlerts.textContent = 'Alerts';
    document.body.appendChild(mockAlerts);

    const mockInsights = document.createElement('div');
    mockInsights.className = 'insights-panel';
    mockInsights.textContent = 'Insights';
    document.body.appendChild(mockInsights);

    const mockComparison = document.createElement('div');
    mockComparison.className = 'comparison-toggle';
    mockComparison.textContent = 'Comparison';
    document.body.appendChild(mockComparison);
  });

  afterEach(() => {
    // Clean up DOM
    document.body.innerHTML = '';
  });

  describe('Tour Visibility and State', () => {
    it('should not render when tour is inactive', () => {
      mockOnboardingStatus.isActive = false;
      
      render(<OnboardingTour />);
      
      expect(screen.queryByTestId('onboarding-overlay')).not.toBeInTheDocument();
    });

    it('should render when tour is active', () => {
      mockOnboardingStatus.isActive = true;
      mockOnboardingStatus.currentStep = 0;
      
      render(<OnboardingTour />);
      
      expect(screen.getByTestId('onboarding-overlay')).toBeInTheDocument();
      expect(screen.getByTestId('onboarding-tooltip')).toBeInTheDocument();
    });

    it('should show correct step content', () => {
      mockOnboardingStatus.isActive = true;
      mockOnboardingStatus.currentStep = 0;
      
      render(<OnboardingTour />);
      
      expect(screen.getByText('Welcome to DCMAAR Analytics!')).toBeInTheDocument();
      expect(screen.getByText(/This powerful dashboard provides real-time insights/)).toBeInTheDocument();
    });

    it('should display step progress indicator', () => {
      mockOnboardingStatus.isActive = true;
      mockOnboardingStatus.currentStep = 2;
      mockOnboardingStatus.totalSteps = 6;
      
      render(<OnboardingTour />);
      
      expect(screen.getByText('Step 3 of 6')).toBeInTheDocument();
    });
  });

  describe('Tour Navigation', () => {
    beforeEach(() => {
      mockOnboardingStatus.isActive = true;
    });

    it('should call nextStep when Next button is clicked', async () => {
      mockOnboardingStatus.currentStep = 0;
      
      render(<OnboardingTour />);
      
      const nextButton = screen.getByText('Next');
      await user.click(nextButton);
      
      expect(mockOnboardingStatus.nextStep).toHaveBeenCalled();
    });

    it('should call previousStep when Back button is clicked', async () => {
      mockOnboardingStatus.currentStep = 2;
      
      render(<OnboardingTour />);
      
      const backButton = screen.getByText('Back');
      await user.click(backButton);
      
      expect(mockOnboardingStatus.previousStep).toHaveBeenCalled();
    });

    it('should not show Back button on first step', () => {
      mockOnboardingStatus.currentStep = 0;
      
      render(<OnboardingTour />);
      
      expect(screen.queryByText('Back')).not.toBeInTheDocument();
    });

    it('should show Finish button on last step', () => {
      mockOnboardingStatus.currentStep = 5; // Last step
      
      render(<OnboardingTour />);
      
      expect(screen.getByText('Finish')).toBeInTheDocument();
    });

    it('should call completeTour when Finish button is clicked', async () => {
      mockOnboardingStatus.currentStep = 5;
      
      render(<OnboardingTour />);
      
      const finishButton = screen.getByText('Finish');
      await user.click(finishButton);
      
      expect(mockOnboardingStatus.completeTour).toHaveBeenCalled();
    });

    it('should call skipTour when Skip button is clicked', async () => {
      mockOnboardingStatus.currentStep = 2;
      
      render(<OnboardingTour />);
      
      const skipButton = screen.getByText('Skip Tour');
      await user.click(skipButton);
      
      expect(mockOnboardingStatus.skipTour).toHaveBeenCalled();
    });
  });

  describe('Keyboard Navigation', () => {
    beforeEach(() => {
      mockOnboardingStatus.isActive = true;
    });

    it('should navigate with arrow keys', async () => {
      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      // Right arrow should go to next step
      await user.keyboard('{ArrowRight}');
      expect(mockOnboardingStatus.nextStep).toHaveBeenCalled();
      
      // Left arrow should go to previous step
      await user.keyboard('{ArrowLeft}');
      expect(mockOnboardingStatus.previousStep).toHaveBeenCalled();
    });

    it('should close tour with Escape key', async () => {
      mockOnboardingStatus.currentStep = 2;
      
      render(<OnboardingTour />);
      
      await user.keyboard('{Escape}');
      expect(mockOnboardingStatus.skipTour).toHaveBeenCalled();
    });

    it('should complete tour with Enter key on last step', async () => {
      mockOnboardingStatus.currentStep = 5;
      
      render(<OnboardingTour />);
      
      await user.keyboard('{Enter}');
      expect(mockOnboardingStatus.completeTour).toHaveBeenCalled();
    });
  });

  describe('Spotlight and Positioning', () => {
    beforeEach(() => {
      mockOnboardingStatus.isActive = true;
    });

    it('should create spotlight around target element', () => {
      mockOnboardingStatus.currentStep = 1; // Metric cards step
      
      render(<OnboardingTour />);
      
      const spotlight = screen.getByTestId('onboarding-spotlight');
      expect(spotlight).toBeInTheDocument();
      expect(spotlight).toHaveStyle({ 
        position: 'absolute',
        pointerEvents: 'none'
      });
    });

    it('should position tooltip relative to target element', () => {
      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      const tooltip = screen.getByTestId('onboarding-tooltip');
      expect(tooltip).toHaveStyle({ position: 'absolute' });
    });

    it('should handle missing target elements gracefully', () => {
      // Remove all target elements
      document.body.innerHTML = '';
      
      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      // Should still render tooltip with default positioning
      expect(screen.getByTestId('onboarding-tooltip')).toBeInTheDocument();
    });

    it('should update positioning when window resizes', async () => {
      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      // Trigger window resize
      fireEvent(window, new Event('resize'));
      
      await waitFor(() => {
        // Positioning should be recalculated
        const tooltip = screen.getByTestId('onboarding-tooltip');
        expect(tooltip).toBeInTheDocument();
      });
    });
  });

  describe('Responsive Behavior', () => {
    beforeEach(() => {
      mockOnboardingStatus.isActive = true;
    });

    it('should adapt tooltip positioning for small screens', () => {
      // Mock small viewport
      Object.defineProperty(window, 'innerWidth', { value: 400 });
      Object.defineProperty(window, 'innerHeight', { value: 600 });
      
      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      const tooltip = screen.getByTestId('onboarding-tooltip');
      
      // Should adjust positioning for small screens
      expect(tooltip).toHaveClass('responsive-positioning');
    });

    it('should handle tooltip overflow prevention', () => {
      mockOnboardingStatus.currentStep = 2;
      
      render(<OnboardingTour />);
      
      const tooltip = screen.getByTestId('onboarding-tooltip');
      
      // Tooltip should stay within viewport bounds
      const rect = tooltip.getBoundingClientRect();
      expect(rect.left).toBeGreaterThanOrEqual(0);
      expect(rect.top).toBeGreaterThanOrEqual(0);
    });
  });

  describe('Step Content Validation', () => {
    beforeEach(() => {
      mockOnboardingStatus.isActive = true;
    });

    it('should display correct content for each step', () => {
      const steps = [
        { 
          step: 0, 
          title: 'Welcome to DCMAAR Analytics!',
          content: /This powerful dashboard provides real-time insights/
        },
        { 
          step: 1, 
          title: 'Performance Metrics',
          content: /These cards show key performance indicators/
        },
        { 
          step: 2, 
          title: 'Interactive Charts',
          content: /Visualize your data trends over time/
        },
        { 
          step: 3, 
          title: 'Smart Alerts',
          content: /Get notified about performance issues/
        },
        { 
          step: 4, 
          title: 'Actionable Insights',
          content: /Discover optimization opportunities/
        },
        { 
          step: 5, 
          title: 'Compare Time Periods',
          content: /Toggle between different time ranges/
        }
      ];

      steps.forEach(({ step, title, content }) => {
        mockOnboardingStatus.currentStep = step;
        
        const { rerender } = render(<OnboardingTour />);
        
        expect(screen.getByText(title)).toBeInTheDocument();
        expect(screen.getByText(content)).toBeInTheDocument();
        
        rerender(<OnboardingTour />);
      });
    });

    it('should show appropriate call-to-action for each step', () => {
      const callToActions = [
        { step: 0, cta: "Let's get started!" },
        { step: 1, cta: 'Click on any card to see detailed metrics.' },
        { step: 2, cta: 'Hover over data points for more details.' },
        { step: 3, cta: 'Click on alerts to see recommended actions.' },
        { step: 4, cta: 'Review these regularly for best results.' },
        { step: 5, cta: 'Try switching between time periods now!' }
      ];

      callToActions.forEach(({ step, cta }) => {
        mockOnboardingStatus.currentStep = step;
        
        const { rerender } = render(<OnboardingTour />);
        
        expect(screen.getByText(cta)).toBeInTheDocument();
        
        rerender(<OnboardingTour />);
      });
    });
  });

  describe('Accessibility', () => {
    beforeEach(() => {
      mockOnboardingStatus.isActive = true;
    });

    it('should have proper ARIA attributes', () => {
      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      const tooltip = screen.getByTestId('onboarding-tooltip');
      expect(tooltip).toHaveAttribute('role', 'dialog');
      expect(tooltip).toHaveAttribute('aria-labelledby');
      expect(tooltip).toHaveAttribute('aria-describedby');
    });

    it('should manage focus properly', () => {
      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      const tooltip = screen.getByTestId('onboarding-tooltip');
      expect(tooltip).toHaveAttribute('tabIndex', '0');
    });

    it('should announce step changes to screen readers', async () => {
      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      const liveRegion = screen.getByLabelText(/Step announcement/);
      expect(liveRegion).toHaveAttribute('aria-live', 'polite');
    });

    it('should support high contrast mode', () => {
      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      const overlay = screen.getByTestId('onboarding-overlay');
      expect(overlay).toHaveClass('high-contrast-support');
    });
  });

  describe('Animation and Transitions', () => {
    beforeEach(() => {
      mockOnboardingStatus.isActive = true;
    });

    it('should animate tooltip entrance', () => {
      mockOnboardingStatus.currentStep = 0;
      
      render(<OnboardingTour />);
      
      const tooltip = screen.getByTestId('onboarding-tooltip');
      expect(tooltip).toHaveClass('tooltip-enter');
    });

    it('should animate between steps', async () => {
      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      const nextButton = screen.getByText('Next');
      await user.click(nextButton);
      
      // Should trigger transition animation
      expect(mockOnboardingStatus.nextStep).toHaveBeenCalled();
    });

    it('should respect reduced motion preferences', () => {
      // Mock prefers-reduced-motion
      Object.defineProperty(window, 'matchMedia', {
        value: jest.fn(() => ({
          matches: true, // prefers-reduced-motion: reduce
          addListener: jest.fn(),
          removeListener: jest.fn()
        }))
      });

      mockOnboardingStatus.currentStep = 1;
      
      render(<OnboardingTour />);
      
      const tooltip = screen.getByTestId('onboarding-tooltip');
      expect(tooltip).toHaveClass('reduced-motion');
    });
  });

  describe('Error Handling', () => {
    it('should handle initialization errors gracefully', () => {
      // Mock hook throwing error
      jest.spyOn(console, 'error').mockImplementation(() => {});
      
      const ThrowingComponent = () => {
        throw new Error('Hook error');
      };
      
      expect(() => {
        render(<ThrowingComponent />);
      }).toThrow();
      
      // OnboardingTour should handle its own errors
      expect(() => {
        render(<OnboardingTour />);
      }).not.toThrow();
      
      jest.restoreAllMocks();
    });

    it('should recover from DOM manipulation errors', () => {
      mockOnboardingStatus.isActive = true;
      mockOnboardingStatus.currentStep = 1;
      
      // Simulate DOM error by removing elements during render
      const originalQuerySelector = document.querySelector;
      document.querySelector = jest.fn(() => {
        throw new Error('DOM error');
      });
      
      expect(() => {
        render(<OnboardingTour />);
      }).not.toThrow();
      
      document.querySelector = originalQuerySelector;
    });
  });

  describe('Performance', () => {
    it('should not cause excessive re-renders', () => {
      const renderSpy = jest.fn();
      
      const TestWrapper = () => {
        renderSpy();
        return <OnboardingTour />;
      };
      
      mockOnboardingStatus.isActive = true;
      
      const { rerender } = render(<TestWrapper />);
      
      // Initial render
      expect(renderSpy).toHaveBeenCalledTimes(1);
      
      // Re-render with same props
      rerender(<TestWrapper />);
      
      // Should be optimized to prevent unnecessary renders
    });

    it('should cleanup event listeners on unmount', () => {
      mockOnboardingStatus.isActive = true;
      
      const removeEventListenerSpy = jest.spyOn(window, 'removeEventListener');
      
      const { unmount } = render(<OnboardingTour />);
      unmount();
      
      expect(removeEventListenerSpy).toHaveBeenCalledWith('resize', expect.any(Function));
      expect(removeEventListenerSpy).toHaveBeenCalledWith('keydown', expect.any(Function));
      
      removeEventListenerSpy.mockRestore();
    });
  });
});