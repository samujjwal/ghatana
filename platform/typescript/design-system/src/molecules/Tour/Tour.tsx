import { useState, useEffect, useRef, ReactNode } from 'react';
import { Button } from '../../atoms/Button';

export interface TourStep {
  /** Unique step ID */
  id: string;
  /** CSS selector for target element */
  target: string;
  /** Step title */
  title: string;
  /** Step content */
  content: string | ReactNode;
  /** Placement relative to target */
  placement?: 'top' | 'bottom' | 'left' | 'right';
  /** Optional action to perform before showing step */
  beforeShow?: () => void | Promise<void>;
}

export interface TourProps {
  /** Array of tour steps */
  steps: TourStep[];
  /** Whether tour is active */
  active: boolean;
  /** Callback when tour completes */
  onComplete: () => void;
  /** Callback when tour is skipped */
  onSkip: () => void;
  /** Starting step index */
  startAt?: number;
}

/**
 * Onboarding Tour Component
 * 
 * Guided tour with spotlight effect and step-by-step instructions.
 * Fully keyboard accessible with ARIA live regions.
 * 
 * @doc.type component
 * @doc.purpose User onboarding and feature discovery
 * @doc.layer core
 * @doc.pattern Onboarding Component
 * 
 * @example
 * ```tsx
 * const steps: TourStep[] = [
 *   {
 *     id: 'welcome',
 *     target: '#app-logo',
 *     title: 'Welcome!',
 *     content: 'Let me show you around...',
 *     placement: 'bottom',
 *   },
 *   {
 *     id: 'create-button',
 *     target: '#create-btn',
 *     title: 'Create Content',
 *     content: 'Click here to create new content',
 *     placement: 'left',
 *   },
 * ];
 * 
 * <Tour
 *   steps={steps}
 *   active={showTour}
 *   onComplete={() => setShowTour(false)}
 *   onSkip={() => setShowTour(false)}
 * />
 * ```
 */
export function Tour({
  steps,
  active,
  onComplete,
  onSkip,
  startAt = 0,
}: TourProps) {
  const [currentStep, setCurrentStep] = useState(startAt);
  const [position, setPosition] = useState({ top: 0, left: 0 });
  const [targetRect, setTargetRect] = useState<DOMRect | null>(null);
  const tooltipRef = useRef<HTMLDivElement>(null);

  const step = steps[currentStep];
  const isLastStep = currentStep === steps.length - 1;
  const isFirstStep = currentStep === 0;

  useEffect(() => {
    if (!active || !step) return;

    const updatePosition = async () => {
      // Execute beforeShow action if present
      if (step.beforeShow) {
        await step.beforeShow();
        // Wait for DOM updates
        await new Promise((resolve) => setTimeout(resolve, 100));
      }

      const target = document.querySelector(step.target);
      if (!target) {
        console.warn(`Tour target not found: ${step.target}`);
        return;
      }

      const rect = target.getBoundingClientRect();
      setTargetRect(rect);

      // Calculate tooltip position based on placement
      const tooltipRect = tooltipRef.current?.getBoundingClientRect();
      const placement = step.placement || 'bottom';
      const spacing = 12;

      let top = 0;
      let left = 0;

      switch (placement) {
        case 'top':
          top = rect.top - (tooltipRect?.height || 0) - spacing;
          left = rect.left + rect.width / 2;
          break;
        case 'bottom':
          top = rect.bottom + spacing;
          left = rect.left + rect.width / 2;
          break;
        case 'left':
          top = rect.top + rect.height / 2;
          left = rect.left - (tooltipRect?.width || 0) - spacing;
          break;
        case 'right':
          top = rect.top + rect.height / 2;
          left = rect.right + spacing;
          break;
      }

      setPosition({ top, left });

      // Scroll target into view
      target.scrollIntoView({ behavior: 'smooth', block: 'center' });
    };

    updatePosition();

    // Update on window resize
    window.addEventListener('resize', updatePosition);
    return () => window.removeEventListener('resize', updatePosition);
  }, [active, currentStep, step]);

  const handleNext = () => {
    if (isLastStep) {
      onComplete();
    } else {
      setCurrentStep(currentStep + 1);
    }
  };

  const handlePrev = () => {
    if (!isFirstStep) {
      setCurrentStep(currentStep - 1);
    }
  };

  if (!active || !step) return null;

  return (
    <>
      {/* Backdrop with spotlight */}
      <div
        className="fixed inset-0 z-[100] pointer-events-none"
        style={{
          background: targetRect
            ? `radial-gradient(circle at ${targetRect.left + targetRect.width / 2}px ${
                targetRect.top + targetRect.height / 2
              }px, transparent ${Math.max(targetRect.width, targetRect.height) / 2 + 10}px, rgba(0,0,0,0.7) ${
                Math.max(targetRect.width, targetRect.height) / 2 + 20
              }px)`
            : 'rgba(0,0,0,0.7)',
        }}
        aria-hidden="true"
      />

      {/* Tooltip */}
      <div
        ref={tooltipRef}
        role="dialog"
        aria-modal="false"
        aria-labelledby="tour-title"
        aria-describedby="tour-content"
        aria-live="polite"
        className="fixed z-[101] bg-white dark:bg-gray-800 rounded-lg shadow-2xl max-w-md animate-scaleIn"
        style={{
          top: `${position.top}px`,
          left: `${position.left}px`,
          transform:
            step.placement === 'top' || step.placement === 'bottom'
              ? 'translateX(-50%)'
              : step.placement === 'left'
              ? 'translate(-100%, -50%)'
              : 'translateY(-50%)',
        }}
      >
        {/* Header */}
        <div className="px-6 pt-6 pb-4 flex items-start justify-between">
          <div className="flex-1">
            <div className="text-sm text-gray-500 dark:text-gray-400 mb-1">
              Step {currentStep + 1} of {steps.length}
            </div>
            <h3
              id="tour-title"
              className="text-lg font-semibold text-gray-900 dark:text-white"
            >
              {step.title}
            </h3>
          </div>
          <button
            onClick={onSkip}
            className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 focus:outline-none focus:ring-2 focus:ring-primary-500 rounded p-1"
            aria-label="Skip tour"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="px-6 pb-6">
          <div id="tour-content" className="text-gray-600 dark:text-gray-300 text-sm">
            {step.content}
          </div>
        </div>

        {/* Actions */}
        <div className="px-6 py-4 bg-gray-50 dark:bg-gray-900 rounded-b-lg flex gap-3 justify-between">
          <Button
            variant="ghost"
            onClick={handlePrev}
            disabled={isFirstStep}
            aria-label="Previous step"
          >
            Previous
          </Button>
          <div className="flex gap-3">
            <Button variant="outline" onClick={onSkip} aria-label="Skip tour">
              Skip
            </Button>
            <Button onClick={handleNext} aria-label={isLastStep ? 'Finish tour' : 'Next step'}>
              {isLastStep ? 'Finish' : 'Next'}
            </Button>
          </div>
        </div>
      </div>
    </>
  );
}

/**
 * Hook for managing tour state with localStorage persistence
 * 
 * @param tourId - Unique identifier for the tour
 * @returns Tour state and controls
 */
export function useTour(tourId: string) {
  const storageKey = `tour-completed-${tourId}`;
  const [active, setActive] = useState(false);

  useEffect(() => {
    // Check if tour was already completed
    const completed = localStorage.getItem(storageKey);
    if (!completed) {
      // Auto-start tour after delay
      const timer = setTimeout(() => setActive(true), 1000);
      return () => clearTimeout(timer);
    }
  }, [storageKey]);

  const start = () => setActive(true);

  const complete = () => {
    setActive(false);
    localStorage.setItem(storageKey, 'true');
  };

  const skip = () => {
    setActive(false);
    localStorage.setItem(storageKey, 'true');
  };

  const reset = () => {
    localStorage.removeItem(storageKey);
    setActive(true);
  };

  return {
    active,
    start,
    complete,
    skip,
    reset,
  };
}

export default Tour;
