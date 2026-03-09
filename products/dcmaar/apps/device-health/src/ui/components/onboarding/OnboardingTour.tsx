/**
 * @fileoverview Onboarding Tour Component
 *
 * Step-by-step walkthrough for first-time users highlighting key features:
 * metrics explanation, alerts system, insights panel, and action guidance.
 *
 * @module ui/components/onboarding
 * @since 2.0.0
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Card } from '@ghatana/dcmaar-shared-ui-tailwind';

export interface TourStep {
  id: string;
  target: string; // CSS selector
  title: string;
  content: string;
  position?: 'top' | 'bottom' | 'left' | 'right';
  action?: {
    label: string;
    onClick: () => void;
  };
}

export interface OnboardingTourProps {
  steps?: TourStep[];
  isOpen: boolean;
  onComplete: () => void;
  onSkip: () => void;
  onStepChange?: (step: number) => void;
}

const STORAGE_KEY = 'dcmaar:onboarding-completed:v1';

const defaultSteps: TourStep[] = [
  {
    id: 'welcome',
    target: '.dashboard-header',
    title: 'Welcome to DCMAAR Analytics!',
    content: 'This dashboard helps you monitor and improve your website\'s performance. Let\'s take a quick tour of the key features.',
    position: 'bottom',
  },
  {
    id: 'metrics',
    target: '.metric-cards-grid',
    title: 'Performance Metrics',
    content: 'These cards show your Core Web Vitals and other key performance indicators. Hover over any metric name to see detailed explanations and thresholds.',
    position: 'top',
  },
  {
    id: 'charts',
    target: '.metric-charts',
    title: 'Performance Trends',
    content: 'Charts show how your metrics change over time. The colored zones indicate good (green), warning (yellow), and critical (red) thresholds.',
    position: 'top',
  },
  {
    id: 'alerts',
    target: '.alert-panel',
    title: 'Performance Alerts',
    content: 'When metrics exceed thresholds, alerts appear here. Click "Show Fix Guide" on any alert to get step-by-step improvement instructions.',
    position: 'top',
  },
  {
    id: 'insights',
    target: '.insights-panel',
    title: 'AI-Powered Insights',
    content: 'This panel provides intelligent recommendations based on your performance data. Each insight links to specific actions you can take.',
    position: 'top',
  },
  {
    id: 'comparison',
    target: '.comparison-toggle',
    title: 'Time Comparison',
    content: 'Toggle comparison mode to analyze how your performance changes between different time periods (today vs yesterday, this week vs last week, etc.).',
    position: 'bottom',
  },
];

export const OnboardingTour: React.FC<OnboardingTourProps> = ({
  steps = defaultSteps,
  isOpen,
  onComplete,
  onSkip,
  onStepChange,
}) => {
  const [currentStep, setCurrentStep] = useState(0);
  const [targetElement, setTargetElement] = useState<HTMLElement | null>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);

  // Update target element when step changes
  useEffect(() => {
    if (!isOpen || currentStep >= steps.length) return;

    const step = steps[currentStep];
    const element = document.querySelector(step.target) as HTMLElement;
    setTargetElement(element);
    onStepChange?.(currentStep);
  }, [currentStep, isOpen, steps, onStepChange]);

  // Position tooltip relative to target
  useEffect(() => {
    if (!targetElement || !tooltipRef.current) return;

    const tooltip = tooltipRef.current;
    const targetRect = targetElement.getBoundingClientRect();
    const tooltipRect = tooltip.getBoundingClientRect();
    const step = steps[currentStep];
    const position = step.position || 'bottom';

    let top = 0;
    let left = 0;

    switch (position) {
      case 'top':
        top = targetRect.top - tooltipRect.height - 16;
        left = targetRect.left + (targetRect.width - tooltipRect.width) / 2;
        break;
      case 'bottom':
        top = targetRect.bottom + 16;
        left = targetRect.left + (targetRect.width - tooltipRect.width) / 2;
        break;
      case 'left':
        top = targetRect.top + (targetRect.height - tooltipRect.height) / 2;
        left = targetRect.left - tooltipRect.width - 16;
        break;
      case 'right':
        top = targetRect.top + (targetRect.height - tooltipRect.height) / 2;
        left = targetRect.right + 16;
        break;
    }

    // Keep tooltip in viewport
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;

    if (left < 16) left = 16;
    if (left + tooltipRect.width > viewportWidth - 16) {
      left = viewportWidth - tooltipRect.width - 16;
    }

    if (top < 16) top = 16;
    if (top + tooltipRect.height > viewportHeight - 16) {
      top = viewportHeight - tooltipRect.height - 16;
    }

    tooltip.style.top = `${top}px`;
    tooltip.style.left = `${left}px`;
  }, [targetElement, currentStep, steps]);

  const handleNext = useCallback(() => {
    if (currentStep < steps.length - 1) {
      setCurrentStep(currentStep + 1);
    } else {
      // Tour complete
      localStorage.setItem(STORAGE_KEY, 'true');
      onComplete();
    }
  }, [currentStep, steps.length, onComplete]);

  const handlePrevious = useCallback(() => {
    if (currentStep > 0) {
      setCurrentStep(currentStep - 1);
    }
  }, [currentStep]);

  const handleSkip = useCallback(() => {
    localStorage.setItem(STORAGE_KEY, 'true');
    onSkip();
  }, [onSkip]);

  const handleStepAction = useCallback(() => {
    const step = steps[currentStep];
    if (step.action) {
      step.action.onClick();
    }
  }, [currentStep, steps]);

  if (!isOpen || currentStep >= steps.length) {
    return null;
  }

  const step = steps[currentStep];

  return (
    <>
      {/* Overlay with spotlight effect */}
      <div
        ref={overlayRef}
        className="fixed inset-0 z-40 pointer-events-none"
        style={{ backgroundColor: 'rgba(0, 0, 0, 0.5)' }}
      >
        {targetElement && (
          <div
            className="absolute bg-white rounded-lg shadow-lg"
            style={{
              top: targetElement.offsetTop - 8,
              left: targetElement.offsetLeft - 8,
              width: targetElement.offsetWidth + 16,
              height: targetElement.offsetHeight + 16,
              boxShadow: '0 0 0 9999px rgba(0, 0, 0, 0.5)',
            }}
          />
        )}
      </div>

      {/* Tooltip */}
      <div
        ref={tooltipRef}
        className="fixed z-50 pointer-events-auto"
        style={{ position: 'fixed' }}
      >
        <Card className="max-w-sm p-6 shadow-xl border-2 border-blue-200 bg-white">
          {/* Progress indicator */}
          <div className="flex items-center justify-between mb-4">
            <div className="flex space-x-1">
              {steps.map((_, index) => (
                <div
                  key={index}
                  className={`w-2 h-2 rounded-full ${index === currentStep
                      ? 'bg-blue-500'
                      : index < currentStep
                        ? 'bg-blue-300'
                        : 'bg-slate-200'
                    }`}
                />
              ))}
            </div>
            <span className="text-xs text-slate-500">
              {currentStep + 1} of {steps.length}
            </span>
          </div>

          {/* Content */}
          <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-2">
            {step.title}
          </h3>
          <p className="text-sm text-slate-600 dark:text-slate-400 mb-4">
            {step.content}
          </p>

          {/* Action button (if available) */}
          {step.action && (
            <button
              onClick={handleStepAction}
              className="w-full mb-3 px-3 py-2 text-sm bg-blue-50 text-blue-700 border border-blue-200 rounded hover:bg-blue-100 transition-colors"
            >
              {step.action.label}
            </button>
          )}

          {/* Navigation */}
          <div className="flex justify-between items-center">
            <button
              onClick={handleSkip}
              className="text-xs text-slate-500 hover:text-slate-700 underline"
            >
              Skip tour
            </button>

            <div className="flex space-x-2">
              {currentStep > 0 && (
                <button
                  onClick={handlePrevious}
                  className="px-3 py-1 text-xs text-slate-600 dark:text-slate-400 border border-slate-300 dark:border-slate-600 rounded hover:bg-slate-50 dark:hover:bg-slate-700"
                >
                  Previous
                </button>
              )}
              <button
                onClick={handleNext}
                className="px-3 py-1 text-xs bg-blue-500 text-white rounded hover:bg-blue-600"
              >
                {currentStep === steps.length - 1 ? 'Finish' : 'Next'}
              </button>
            </div>
          </div>
        </Card>
      </div>
    </>
  );
};

/**
 * Hook to check if onboarding should be shown
 */
export const useOnboardingStatus = () => {
  const [shouldShow, setShouldShow] = useState(false);
  const [isChecking, setIsChecking] = useState(true);

  useEffect(() => {
    const checkOnboardingStatus = () => {
      const completed = localStorage.getItem(STORAGE_KEY);
      setShouldShow(!completed);
      setIsChecking(false);
    };

    // Small delay to ensure DOM is ready
    const timer = setTimeout(checkOnboardingStatus, 500);
    return () => clearTimeout(timer);
  }, []);

  const markCompleted = useCallback(() => {
    localStorage.setItem(STORAGE_KEY, 'true');
    setShouldShow(false);
  }, []);

  const reset = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setShouldShow(true);
  }, []);

  return {
    shouldShow,
    isChecking,
    markCompleted,
    reset,
  };
};

export default OnboardingTour;