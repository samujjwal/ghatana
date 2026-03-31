/**
 * @fileoverview Canvas Onboarding Tour Component
 * Interactive guided tour for new YAPPC users
 * 
 * @doc.type component
 * @doc.purpose Guide new users through canvas features
 * @doc.layer presentation
 * @doc.pattern UserOnboarding
 * 
 * @example
 * ```tsx
 * <OnboardingTour 
 *   isOpen={showOnboarding}
 *   onComplete={() => setShowOnboarding(false)}
 *   steps={defaultTourSteps}
 * />
 * ```
 */

import React, { useState, useEffect, useCallback } from 'react';

// ============================================================================
// Types
// ============================================================================

export interface TourStep {
  id: string;
  target: string;
  title: string;
  content: string;
  position: 'top' | 'bottom' | 'left' | 'right';
  action?: string;
  delay?: number;
}

export interface OnboardingTourProps {
  isOpen: boolean;
  onComplete: () => void;
  onSkip?: () => void;
  steps?: TourStep[];
  allowSkip?: boolean;
  autoStart?: boolean;
}

// ============================================================================
// Default Tour Steps for YAPPC Canvas
// ============================================================================

export const defaultTourSteps: TourStep[] = [
  {
    id: 'welcome',
    target: '[data-testid="canvas-container"]',
    title: 'Welcome to YAPPC Canvas',
    content: 'This is your infinite workspace for diagrams, sketches, and ideas. Let\'s take a quick tour of the key features.',
    position: 'bottom',
    delay: 500,
  },
  {
    id: 'toolbar',
    target: '[data-testid="canvas-toolbar"]',
    title: 'Canvas Toolbar',
    content: 'Access all your tools here: select, shapes, sketch, text, and more. Each tool has keyboard shortcuts for quick access.',
    position: 'right',
  },
  {
    id: 'shapes',
    target: '[data-testid="shape-tool"]',
    title: 'Shape Tools',
    content: 'Create rectangles, circles, diamonds, and other shapes. Double-click to add text inside shapes.',
    position: 'right',
    action: 'Try creating a rectangle by clicking and dragging on the canvas.',
  },
  {
    id: 'sketch',
    target: '[data-testid="sketch-tool"]',
    title: 'Sketch Tool',
    content: 'Draw freehand with the sketch tool. Perfect for quick diagrams and wireframes.',
    position: 'right',
  },
  {
    id: 'connectors',
    target: '[data-testid="connector-tool"]',
    title: 'Connect Elements',
    content: 'Link shapes with smart connectors. They automatically adjust when you move elements.',
    position: 'right',
  },
  {
    id: 'zoom',
    target: '[data-testid="zoom-controls"]',
    title: 'Zoom & Pan',
    content: 'Use mouse wheel to zoom, or these controls. Pan by holding space and dragging.',
    position: 'bottom',
  },
  {
    id: 'ai-assistant',
    target: '[data-testid="ai-assistant-button"]',
    title: 'AI Assistant',
    content: 'Need help? The AI assistant can generate diagrams from your descriptions. Just describe what you need!',
    position: 'left',
  },
  {
    id: 'collaboration',
    target: '[data-testid="share-button"]',
    title: 'Collaborate',
    content: 'Share your canvas with team members. Work together in real-time with live cursors and instant sync.',
    position: 'left',
  },
  {
    id: 'export',
    target: '[data-testid="export-button"]',
    title: 'Export & Share',
    content: 'Export your work as PNG, PDF, or JSON. Share with anyone, even if they don\'t have a YAPPC account.',
    position: 'left',
  },
  {
    id: 'complete',
    target: '[data-testid="canvas-container"]',
    title: 'You\'re Ready!',
    content: 'That\'s the basics! You can always replay this tour from the Help menu. Happy creating! 🎉',
    position: 'center',
  },
];

// ============================================================================
// Onboarding Tour Component
// ============================================================================

/**
 * Interactive Onboarding Tour
 * @doc.purpose Guide users through canvas features step by step
 */
export const OnboardingTour: React.FC<OnboardingTourProps> = ({
  isOpen,
  onComplete,
  onSkip,
  steps = defaultTourSteps,
  allowSkip = true,
  autoStart = true,
}) => {
  const [currentStep, setCurrentStep] = useState(0);
  const [targetRect, setTargetRect] = useState<DOMRect | null>(null);
  const [isVisible, setIsVisible] = useState(false);

  const step = steps[currentStep];

  // Calculate target element position
  useEffect(() => {
    if (!isOpen || !step) return;

    const updateTargetRect = () => {
      const target = document.querySelector(step.target);
      if (target) {
        setTargetRect(target.getBoundingClientRect());
      } else if (step.position === 'center') {
        // Center on screen if no target
        setTargetRect(new DOMRect(
          window.innerWidth / 2 - 150,
          window.innerHeight / 2 - 100,
          300,
          200
        ));
      }
    };

    // Initial update after delay
    const delayTimer = setTimeout(updateTargetRect, step.delay || 100);

    // Update on resize
    window.addEventListener('resize', updateTargetRect);
    window.addEventListener('scroll', updateTargetRect, true);

    // Fade in
    const visibilityTimer = setTimeout(() => setIsVisible(true), 50);

    return () => {
      clearTimeout(delayTimer);
      clearTimeout(visibilityTimer);
      window.removeEventListener('resize', updateTargetRect);
      window.removeEventListener('scroll', updateTargetRect, true);
    };
  }, [isOpen, step]);

  // Handle step navigation
  const handleNext = useCallback(() => {
    if (currentStep < steps.length - 1) {
      setIsVisible(false);
      setTimeout(() => {
        setCurrentStep(prev => prev + 1);
        setIsVisible(true);
      }, 200);
    } else {
      onComplete();
    }
  }, [currentStep, steps.length, onComplete]);

  const handlePrevious = useCallback(() => {
    if (currentStep > 0) {
      setIsVisible(false);
      setTimeout(() => {
        setCurrentStep(prev => prev - 1);
        setIsVisible(true);
      }, 200);
    }
  }, [currentStep]);

  const handleSkip = useCallback(() => {
    onSkip?.();
    onComplete();
  }, [onSkip, onComplete]);

  // Keyboard navigation
  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowRight' || e.key === 'Enter') {
        handleNext();
      } else if (e.key === 'ArrowLeft') {
        handlePrevious();
      } else if (e.key === 'Escape') {
        handleSkip();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, handleNext, handlePrevious, handleSkip]);

  // Auto-start
  useEffect(() => {
    if (autoStart && isOpen) {
      // Check if user has seen tour before
      const hasSeenTour = localStorage.getItem('yappc-tour-completed');
      if (hasSeenTour) {
        onComplete();
      }
    }
  }, [autoStart, isOpen, onComplete]);

  // Mark tour as completed
  useEffect(() => {
    if (currentStep === steps.length - 1 && isVisible) {
      localStorage.setItem('yappc-tour-completed', 'true');
      localStorage.setItem('yappc-tour-date', new Date().toISOString());
    }
  }, [currentStep, steps.length, isVisible]);

  if (!isOpen || !step || !targetRect) return null;

  // Calculate tooltip position
  const tooltipPosition = calculateTooltipPosition(targetRect, step.position);

  return (
    <div className="onboarding-overlay" style={overlayStyles}>
      {/* Highlight target element */}
      <div
        className="target-highlight"
        style={{
          position: 'absolute',
          left: targetRect.left - 8,
          top: targetRect.top - 8,
          width: targetRect.width + 16,
          height: targetRect.height + 16,
          border: '3px solid #0066cc',
          borderRadius: '8px',
          boxShadow: '0 0 0 9999px rgba(0,0,0,0.5)',
          transition: 'all 0.3s ease',
          zIndex: 10000,
        }}
      />

      {/* Tooltip */}
      <div
        className="tour-tooltip"
        style={{
          position: 'absolute',
          left: tooltipPosition.x,
          top: tooltipPosition.y,
          width: 320,
          background: 'white',
          borderRadius: '12px',
          padding: '24px',
          boxShadow: '0 25px 50px -12px rgba(0,0,0,0.25)',
          opacity: isVisible ? 1 : 0,
          transform: isVisible ? 'translateY(0)' : 'translateY(10px)',
          transition: 'all 0.2s ease',
          zIndex: 10001,
        }}
        role="dialog"
        aria-modal="true"
        aria-labelledby="tour-title"
      >
        {/* Progress indicator */}
        <div style={{ display: 'flex', gap: '4px', marginBottom: '16px' }}>
          {steps.map((_, idx) => (
            <div
              key={idx}
              style={{
                flex: 1,
                height: '4px',
                background: idx <= currentStep ? '#0066cc' : '#e5e7eb',
                borderRadius: '2px',
                transition: 'background 0.3s',
              }}
            />
          ))}
        </div>

        {/* Step counter */}
        <div style={{ 
          fontSize: '12px', 
          color: '#6b7280', 
          marginBottom: '8px',
          fontWeight: 500,
        }}>
          Step {currentStep + 1} of {steps.length}
        </div>

        {/* Title */}
        <h3 
          id="tour-title"
          style={{ 
            fontSize: '18px', 
            fontWeight: 600, 
            marginBottom: '12px',
            color: '#111827',
          }}
        >
          {step.title}
        </h3>

        {/* Content */}
        <p style={{ 
          fontSize: '14px', 
          lineHeight: 1.6, 
          color: '#4b5563',
          marginBottom: step.action ? '12px' : '20px',
        }}>
          {step.content}
        </p>

        {/* Action hint */}
        {step.action && (
          <div style={{
            background: '#f3f4f6',
            padding: '12px',
            borderRadius: '8px',
            fontSize: '13px',
            color: '#374151',
            marginBottom: '20px',
          }}>
            <span style={{ marginRight: '8px' }}>💡</span>
            {step.action}
          </div>
        )}

        {/* Navigation buttons */}
        <div style={{ 
          display: 'flex', 
          justifyContent: 'space-between',
          alignItems: 'center',
        }}>
          <div>
            {allowSkip && (
              <button
                onClick={handleSkip}
                style={{
                  fontSize: '14px',
                  color: '#6b7280',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  padding: '8px 12px',
                }}
              >
                Skip tour
              </button>
            )}
          </div>

          <div style={{ display: 'flex', gap: '8px' }}>
            {currentStep > 0 && (
              <button
                onClick={handlePrevious}
                style={{
                  padding: '8px 16px',
                  fontSize: '14px',
                  border: '1px solid #d1d5db',
                  borderRadius: '6px',
                  background: 'white',
                  cursor: 'pointer',
                }}
              >
                Previous
              </button>
            )}

            <button
              onClick={handleNext}
              style={{
                padding: '8px 16px',
                fontSize: '14px',
                border: 'none',
                borderRadius: '6px',
                background: '#0066cc',
                color: 'white',
                cursor: 'pointer',
                fontWeight: 500,
              }}
            >
              {currentStep === steps.length - 1 ? 'Get Started!' : 'Next'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

// ============================================================================
// Helper Functions
// ============================================================================

function calculateTooltipPosition(
  targetRect: DOMRect, 
  position: string
): { x: number; y: number } {
  const tooltipWidth = 320;
  const tooltipHeight = 200;
  const padding = 16;

  let x = targetRect.left + targetRect.width / 2 - tooltipWidth / 2;
  let y = targetRect.top - tooltipHeight - padding;

  switch (position) {
    case 'top':
      y = targetRect.top - tooltipHeight - padding;
      break;
    case 'bottom':
      y = targetRect.bottom + padding;
      break;
    case 'left':
      x = targetRect.left - tooltipWidth - padding;
      y = targetRect.top + targetRect.height / 2 - tooltipHeight / 2;
      break;
    case 'right':
      x = targetRect.right + padding;
      y = targetRect.top + targetRect.height / 2 - tooltipHeight / 2;
      break;
    case 'center':
      x = targetRect.left;
      y = targetRect.top;
      break;
  }

  // Keep within viewport
  const viewportWidth = window.innerWidth;
  const viewportHeight = window.innerHeight;

  x = Math.max(padding, Math.min(x, viewportWidth - tooltipWidth - padding));
  y = Math.max(padding, Math.min(y, viewportHeight - tooltipHeight - padding));

  return { x, y };
}

// ============================================================================
// Styles
// ============================================================================

const overlayStyles: React.CSSProperties = {
  position: 'fixed',
  top: 0,
  left: 0,
  right: 0,
  bottom: 0,
  zIndex: 9999,
  pointerEvents: 'none',
};

// ============================================================================
// Hook for triggering tour
// ============================================================================

/**
 * Hook to manage onboarding tour state
 * @doc.purpose Control tour visibility and completion
 */
export function useOnboardingTour() {
  const [isOpen, setIsOpen] = useState(false);

  const startTour = useCallback(() => {
    setIsOpen(true);
  }, []);

  const completeTour = useCallback(() => {
    setIsOpen(false);
    localStorage.setItem('yappc-tour-completed', 'true');
  }, []);

  const resetTour = useCallback(() => {
    localStorage.removeItem('yappc-tour-completed');
    localStorage.removeItem('yappc-tour-date');
    setIsOpen(true);
  }, []);

  const hasCompletedTour = useCallback(() => {
    return localStorage.getItem('yappc-tour-completed') === 'true';
  }, []);

  return {
    isOpen,
    startTour,
    completeTour,
    resetTour,
    hasCompletedTour,
  };
}

export default OnboardingTour;
