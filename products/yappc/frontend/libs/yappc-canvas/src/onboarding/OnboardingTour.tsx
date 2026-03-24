/**
 * Onboarding Tour System
 *
 * Interactive guided tour for first-time canvas users.
 * Highlights key features with step-by-step walkthroughs.
 *
 * @doc.type component
 * @doc.purpose User onboarding
 * @doc.layer product
 * @doc.pattern Tour
 */

import React, { useState, useEffect, useCallback } from 'react';
import { CANVAS_Z_INDEX } from '../config/z-index';

/**
 * Tour step definition
 */
export interface TourStep {
  /** Unique step ID */
  id: string;
  /** Step title */
  title: string;
  /** Step description */
  description: string;
  /** Target element selector */
  target: string;
  /** Placement of tooltip */
  placement: 'top' | 'bottom' | 'left' | 'right' | 'center';
  /** Actions to perform before showing step */
  onEnter?: () => void;
  /** Actions to perform after leaving step */
  onExit?: () => void;
  /** Whether to highlight target */
  highlight?: boolean;
  /** Optional action button */
  action?: {
    label: string;
    onClick: () => void;
  };
}

/**
 * Canvas onboarding tour steps
 */
export const CANVAS_TOUR_STEPS: TourStep[] = [
  {
    id: 'welcome',
    title: '👋 Welcome to Unified Canvas',
    description:
      "Let's take a quick tour of the canvas features. This will only take 2 minutes.",
    target: 'body',
    placement: 'center',
    highlight: false,
  },
  {
    id: 'frames',
    title: '📦 Frames Organize Your Work',
    description:
      'Frames group artifacts by lifecycle phase (Discover, Design, Build, Test, etc.). They help you visualize the project flow.',
    target: '.canvas-frame',
    placement: 'right',
    highlight: true,
  },
  {
    id: 'left-rail',
    title: '📋 Palette Panel',
    description:
      'The left rail contains the palette of artifacts you can add to frames. Drag them onto the canvas to create new elements.',
    target: '.canvas-left-rail-layer',
    placement: 'right',
    highlight: true,
  },
  {
    id: 'outline',
    title: '🗂️ Outline Navigator',
    description:
      'The outline panel shows a tree view of all frames and artifacts. Use search to quickly find elements.',
    target: '.canvas-outline-layer',
    placement: 'right',
    highlight: true,
  },
  {
    id: 'inspector',
    title: '🔍 Inspector Panel',
    description:
      'Select any element to see its properties in the inspector. You can edit attributes, add metadata, and more.',
    target: '.canvas-inspector-layer',
    placement: 'left',
    highlight: true,
  },
  {
    id: 'minimap',
    title: '🗺️ Minimap',
    description:
      'The minimap shows an overview of your entire canvas. Click to jump to any area quickly.',
    target: '.canvas-minimap-layer',
    placement: 'top',
    highlight: true,
  },
  {
    id: 'zoom',
    title: '🔍 Zoom Controls',
    description:
      'Use the zoom HUD to navigate. The indicator shows semantic zoom modes: Overview (🗺️), Focus (🎯), and Detail (🔍).',
    target: '.zoom-hud',
    placement: 'top',
    highlight: true,
  },
  {
    id: 'context-bar',
    title: '⚡ Context Bar',
    description:
      'When you select elements, a floating toolbar appears with contextual actions. Try selecting a frame!',
    target: 'body',
    placement: 'center',
    highlight: false,
  },
  {
    id: 'keyboard',
    title: '⌨️ Keyboard Shortcuts',
    description:
      'Press Cmd+K to open the command palette, or use Cmd+Shift+L/I/O/M to toggle panels. Press ? to see all shortcuts.',
    target: 'body',
    placement: 'center',
    highlight: false,
  },
  {
    id: 'complete',
    title: "🎉 You're All Set!",
    description:
      "You're ready to start building. Create your first frame with Cmd+Shift+1, or explore the palette.",
    target: 'body',
    placement: 'center',
    highlight: false,
    action: {
      label: 'Create Discovery Frame',
      onClick: () => {
        // Trigger frame creation
        console.log('Create discovery frame');
      },
    },
  },
];

/**
 * Tour overlay component
 */
export interface OnboardingTourProps {
  /** Whether tour is active */
  active: boolean;
  /** Starting step index */
  startStep?: number;
  /** Callback when tour completes */
  onComplete: () => void;
  /** Callback when tour is skipped */
  onSkip: () => void;
  /** Custom steps (optional) */
  steps?: TourStep[];
}

/**
 * Onboarding Tour Component
 */
export const OnboardingTour: React.FC<OnboardingTourProps> = ({
  active,
  startStep = 0,
  onComplete,
  onSkip,
  steps = CANVAS_TOUR_STEPS,
}) => {
  const [currentStep, setCurrentStep] = useState(startStep);
  const [targetRect, setTargetRect] = useState<DOMRect | null>(null);

  const step = steps[currentStep];
  const isLastStep = currentStep === steps.length - 1;
  const isFirstStep = currentStep === 0;

  // Update target rect when step changes
  useEffect(() => {
    if (!active || !step) return;

    // Call onEnter
    step.onEnter?.();

    // Find target element
    const target = document.querySelector(step.target);
    if (target) {
      const rect = target.getBoundingClientRect();
      setTargetRect(rect);
    } else {
      setTargetRect(null);
    }

    // Cleanup
    return () => {
      step.onExit?.();
    };
  }, [active, step, currentStep]);

  // Handle next step
  const handleNext = useCallback(() => {
    if (isLastStep) {
      onComplete();
    } else {
      setCurrentStep((prev) => prev + 1);
    }
  }, [isLastStep, onComplete]);

  // Handle previous step
  const handlePrevious = useCallback(() => {
    if (!isFirstStep) {
      setCurrentStep((prev) => prev - 1);
    }
  }, [isFirstStep]);

  // Handle skip
  const handleSkip = useCallback(() => {
    onSkip();
  }, [onSkip]);

  // Handle keyboard navigation
  useEffect(() => {
    if (!active) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        handleSkip();
      } else if (e.key === 'ArrowRight' || e.key === 'Enter') {
        handleNext();
      } else if (e.key === 'ArrowLeft') {
        handlePrevious();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [active, handleNext, handlePrevious, handleSkip]);

  if (!active || !step) return null;

  // Calculate tooltip position
  const getTooltipStyle = (): React.CSSProperties => {
    if (step.placement === 'center' || !targetRect) {
      return {
        position: 'fixed',
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
      };
    }

    const padding = 16;
    let style: React.CSSProperties = { position: 'fixed' };

    switch (step.placement) {
      case 'top':
        style.left = targetRect.left + targetRect.width / 2;
        style.top = targetRect.top - padding;
        style.transform = 'translate(-50%, -100%)';
        break;
      case 'bottom':
        style.left = targetRect.left + targetRect.width / 2;
        style.top = targetRect.bottom + padding;
        style.transform = 'translateX(-50%)';
        break;
      case 'left':
        style.left = targetRect.left - padding;
        style.top = targetRect.top + targetRect.height / 2;
        style.transform = 'translate(-100%, -50%)';
        break;
      case 'right':
        style.left = targetRect.right + padding;
        style.top = targetRect.top + targetRect.height / 2;
        style.transform = 'translateY(-50%)';
        break;
    }

    return style;
  };

  return (
    <>
      {/* Overlay */}
      <div
        className="tour-overlay"
        style={{
          position: 'fixed',
          inset: 0,
          background: 'rgba(0, 0, 0, 0.5)',
          zIndex: CANVAS_Z_INDEX.MODAL,
          pointerEvents: 'auto',
        }}
        onClick={handleSkip}
      />

      {/* Highlight */}
      {step.highlight && targetRect && (
        <div
          className="tour-highlight"
          style={{
            position: 'fixed',
            left: targetRect.left - 4,
            top: targetRect.top - 4,
            width: targetRect.width + 8,
            height: targetRect.height + 8,
            border: '3px solid #1976d2',
            borderRadius: '8px',
            boxShadow: '0 0 0 9999px rgba(0, 0, 0, 0.5)',
            zIndex: CANVAS_Z_INDEX.MODAL + 1,
            pointerEvents: 'none',
            animation: 'tour-highlight-pulse 2s ease-in-out infinite',
          }}
        />
      )}

      {/* Tooltip */}
      <div
        className="tour-tooltip"
        style={{
          ...getTooltipStyle(),
          maxWidth: '400px',
          background: 'white',
          borderRadius: '12px',
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.2)',
          zIndex: CANVAS_Z_INDEX.MODAL + 2,
          pointerEvents: 'auto',
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div
          style={{
            padding: '20px 20px 16px',
            borderBottom: '1px solid #e0e0e0',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: '8px',
            }}
          >
            <h3
              style={{
                margin: 0,
                fontSize: '18px',
                fontWeight: 600,
                color: '#212121',
              }}
            >
              {step.title}
            </h3>
            <button
              onClick={handleSkip}
              style={{
                background: 'transparent',
                border: 'none',
                cursor: 'pointer',
                fontSize: '20px',
                color: '#757575',
                padding: '4px',
                lineHeight: 1,
              }}
            >
              ✕
            </button>
          </div>
          <p
            style={{
              margin: 0,
              fontSize: '14px',
              color: '#616161',
              lineHeight: 1.5,
            }}
          >
            {step.description}
          </p>
        </div>

        {/* Footer */}
        <div
          style={{
            padding: '16px 20px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          {/* Progress */}
          <div
            style={{
              fontSize: '12px',
              color: '#757575',
              fontWeight: 600,
            }}
          >
            {currentStep + 1} / {steps.length}
          </div>

          {/* Actions */}
          <div style={{ display: 'flex', gap: '8px' }}>
            {!isFirstStep && (
              <button
                onClick={handlePrevious}
                style={{
                  padding: '8px 16px',
                  border: '1px solid #e0e0e0',
                  borderRadius: '6px',
                  background: 'white',
                  cursor: 'pointer',
                  fontSize: '14px',
                  fontWeight: 500,
                  color: '#212121',
                }}
              >
                Back
              </button>
            )}

            {step.action ? (
              <button
                onClick={() => {
                  step.action!.onClick();
                  handleNext();
                }}
                style={{
                  padding: '8px 16px',
                  border: 'none',
                  borderRadius: '6px',
                  background: '#1976d2',
                  cursor: 'pointer',
                  fontSize: '14px',
                  fontWeight: 500,
                  color: 'white',
                }}
              >
                {step.action.label}
              </button>
            ) : (
              <button
                onClick={handleNext}
                style={{
                  padding: '8px 16px',
                  border: 'none',
                  borderRadius: '6px',
                  background: '#1976d2',
                  cursor: 'pointer',
                  fontSize: '14px',
                  fontWeight: 500,
                  color: 'white',
                }}
              >
                {isLastStep ? 'Finish' : 'Next'}
              </button>
            )}
          </div>
        </div>
      </div>

      <style>{`
        @keyframes tour-highlight-pulse {
          0%, 100% {
            opacity: 1;
          }
          50% {
            opacity: 0.7;
          }
        }
      `}</style>
    </>
  );
};

/**
 * Hook to manage onboarding tour state
 */
export function useOnboardingTour() {
  const [tourActive, setTourActive] = useState(false);
  const [tourCompleted, setTourCompleted] = useState(() => {
    // Check if user has completed tour before
    return localStorage.getItem('canvas-tour-completed') === 'true';
  });

  const startTour = useCallback(() => {
    setTourActive(true);
  }, []);

  const completeTour = useCallback(() => {
    setTourActive(false);
    setTourCompleted(true);
    localStorage.setItem('canvas-tour-completed', 'true');
  }, []);

  const skipTour = useCallback(() => {
    setTourActive(false);
    setTourCompleted(true);
    localStorage.setItem('canvas-tour-completed', 'true');
  }, []);

  const resetTour = useCallback(() => {
    setTourCompleted(false);
    localStorage.removeItem('canvas-tour-completed');
  }, []);

  // Auto-start tour for first-time users
  useEffect(() => {
    if (!tourCompleted && !tourActive) {
      // Delay to allow canvas to render
      const timer = setTimeout(() => {
        setTourActive(true);
      }, 1000);
      return () => clearTimeout(timer);
    }
  }, [tourCompleted, tourActive]);

  return {
    tourActive,
    tourCompleted,
    startTour,
    completeTour,
    skipTour,
    resetTour,
  };
}
