import { Box } from '@ghatana/ui';
import { ReactFlowProvider } from '@xyflow/react';
import {
  OnboardingTour,
  onboardingTourManager,
  AccessibilityDashboard,
  ContextualHelp,
  ShortcutHelper,
} from '@ghatana/yappc-ui';

import React, { useState } from 'react';

import AdvancedCanvas from './advanced-canvas';

// Basic usage analytics panel
/**
 *
 */
function UsageAnalyticsPanel({
  isVisible,
  onClose,
}: {
  isVisible: boolean;
  onClose: () => void;
}) {
  if (!isVisible) return null;
  return (
    <div
      style={{
        position: 'fixed',
        bottom: 20,
        right: 20,
        zIndex: 3000,
        background: 'white',
        border: '1px solid #eee',
        borderRadius: 8,
        padding: 16,
        boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
      }}
    >
      <h3>Usage Analytics</h3>
      <p>Canvas interaction metrics and insights will appear here.</p>
      <button onClick={onClose}>Close</button>
    </div>
  );
}

// Basic health dashboard panel
/**
 *
 */
function HealthDashboard({
  isVisible,
  onClose,
}: {
  isVisible: boolean;
  onClose: () => void;
}) {
  if (!isVisible) return null;
  return (
    <div
      style={{
        position: 'fixed',
        bottom: 20,
        left: 20,
        zIndex: 3000,
        background: 'white',
        border: '1px solid #eee',
        borderRadius: 8,
        padding: 16,
        boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
      }}
    >
      <h3>Health Dashboard</h3>
      <p>System health and performance metrics will appear here.</p>
      <button onClick={onClose}>Close</button>
    </div>
  );
}

/**
 *
 */
export default function CanvasDemo() {
  const isDev =
    typeof import.meta !== 'undefined' &&
    Boolean((import.meta as unknown).env?.DEV);

  const launchCanvasTour = React.useCallback(async () => {
    const started = await onboardingTourManager.startTour('canvas-basics');
    if (!started) {
      const fallback = await onboardingTourManager.startTour('getting-started');
      if (!fallback) {
        console.warn(
          'Failed to start onboarding tour; prerequisites may still be unmet.'
        );
      }
    }
  }, []);

  const markGettingStartedComplete = React.useCallback(() => {
    try {
      const stored = localStorage.getItem('completed-tours');
      const completed = stored
        ? new Set<string>(JSON.parse(stored))
        : new Set<string>();
      completed.add('getting-started');
      localStorage.setItem(
        'completed-tours',
        JSON.stringify(Array.from(completed))
      );
      window.dispatchEvent(
        new CustomEvent('onboarding-tour:completed-dev', {
          detail: { tourId: 'getting-started' },
        })
      );
      console.info('Marked getting-started tour complete (dev helper).');
    } catch (error) {
      console.error(
        'Failed to mark getting-started tour complete (dev helper)',
        error
      );
    }
  }, []);

  // Example: start onboarding tour automatically on mount
  React.useEffect(() => {
    launchCanvasTour();
  }, [launchCanvasTour]);

  // Example: show accessibility dashboard and help panel
  const [showAccessibility, setShowAccessibility] = useState(false);
  const [showHelp, setShowHelp] = useState(false);
  const [showShortcuts, setShowShortcuts] = useState(false);
  const [showAnalytics, setShowAnalytics] = useState(false);
  const [showHealth, setShowHealth] = useState(false);

  return (
    <ReactFlowProvider>
      <Box className="h-screen w-screen relative">
        {/* Main advanced canvas */}
        <AdvancedCanvas />

        {/* Onboarding tour trigger and UI */}
        {/* Wire up onboarding state and handlers */}
        {(() => {
          const [onboardingState, setOnboardingState] = React.useState(
            onboardingTourManager.getCurrentState()
          );
          React.useEffect(
            () => onboardingTourManager.addListener(setOnboardingState),
            []
          );
          return (
            <>
              <OnboardingTour
                isActive={onboardingState.isActive}
                currentTour={onboardingState.currentTour}
                currentStepIndex={onboardingState.currentStepIndex}
                onNext={() => onboardingTourManager.nextStep()}
                onPrevious={() => onboardingTourManager.previousStep()}
                onSkip={() => onboardingTourManager.skipTour()}
                onComplete={() => onboardingTourManager.completeTour()}
                onClose={() => onboardingTourManager.stopTour()}
              />
              <button
                style={{
                  position: 'absolute',
                  top: 16,
                  right: 16,
                  zIndex: 2000,
                }}
                onClick={launchCanvasTour}
              >
                Start Onboarding Tour
              </button>
              {isDev && (
                <button
                  style={{
                    position: 'absolute',
                    top: 56,
                    right: 16,
                    zIndex: 2000,
                  }}
                  onClick={markGettingStartedComplete}
                >
                  Dev: Complete Getting Started
                </button>
              )}
            </>
          );
        })()}

        {/* Accessibility dashboard toggle */}
        <button
          style={{
            position: 'absolute',
            top: isDev ? 96 : 56,
            right: 16,
            zIndex: 2000,
          }}
          onClick={() => setShowAccessibility((v) => !v)}
        >
          Accessibility Dashboard
        </button>
        {showAccessibility && <AccessibilityDashboard />}

        {/* Contextual help toggle */}
        <button
          style={{
            position: 'absolute',
            top: isDev ? 136 : 96,
            right: 16,
            zIndex: 2000,
          }}
          onClick={() => setShowHelp((v) => !v)}
        >
          Show Help
        </button>
        {showHelp && <ContextualHelp context="canvas" />}

        {/* Keyboard shortcuts panel toggle */}
        <button
          style={{
            position: 'absolute',
            top: isDev ? 176 : 136,
            right: 16,
            zIndex: 2000,
          }}
          onClick={() => setShowShortcuts((v) => !v)}
        >
          Keyboard Shortcuts
        </button>
        {showShortcuts && (
          <ShortcutHelper
            isVisible={showShortcuts}
            onClose={() => setShowShortcuts(false)}
          />
        )}

        {/* Usage analytics panel toggle */}
        <button
          style={{
            position: 'absolute',
            top: isDev ? 216 : 176,
            right: 16,
            zIndex: 2000,
          }}
          onClick={() => setShowAnalytics((v) => !v)}
        >
          Usage Analytics
        </button>
        <UsageAnalyticsPanel
          isVisible={showAnalytics}
          onClose={() => setShowAnalytics(false)}
        />

        {/* Health dashboard toggle */}
        <button
          style={{
            position: 'absolute',
            top: isDev ? 256 : 216,
            right: 16,
            zIndex: 2000,
          }}
          onClick={() => setShowHealth((v) => !v)}
        >
          Health Dashboard
        </button>
        <HealthDashboard
          isVisible={showHealth}
          onClose={() => setShowHealth(false)}
        />
      </Box>
    </ReactFlowProvider>
  );
}
