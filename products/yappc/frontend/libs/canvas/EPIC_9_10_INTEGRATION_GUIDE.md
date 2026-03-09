/\*\*

- Epic 9 & 10: Command Palette Integration + Onboarding & Telemetry
- Integration Guide for Unified Canvas
-
- This guide demonstrates how to integrate all Epic 9 & 10 features
- into a complete canvas application with command palette, onboarding,
- telemetry, and A/B testing.
  \*/

import React, { useEffect } from 'react';
import { Provider as JotaiProvider } from 'jotai';
import {
// Command System (Epic 9)
CanvasCommandProvider,
useCanvasCommands,
useCanvasCommandsForPalette,

// Onboarding (Epic 10)
OnboardingTour,
useOnboardingTour,
FeatureHintsManager,

// Telemetry (Epic 10)
getCanvasTelemetry,
useCanvasTelemetry,
CanvasTelemetryEvent,

// A/B Testing (Epic 10)
useABTest,
useFeatureFlag,

// Core Canvas Components
UnifiedCanvasApp,
CanvasChromeLayout,
} from '@canvas/core';

// ============================================================================
// EXAMPLE 1: Full-Featured Canvas with All Epic 9 & 10 Features
// ============================================================================

export function FullFeaturedCanvas() {
return (
<JotaiProvider>
<CanvasCommandProvider>
<CanvasWithCommandsAndOnboarding />
</CanvasCommandProvider>
</JotaiProvider>
);
}

function CanvasWithCommandsAndOnboarding() {
const telemetry = useCanvasTelemetry();
const { startTour, resetTour } = useOnboardingTour();

// A/B Testing: Calm Mode Default (Test ID: calm-mode-default)
const calmModeTest = useABTest('calm-mode-default');
const shouldDefaultToCalmMode = calmModeTest.isVariant('enabled');

// A/B Testing: Context Bar Position (Test ID: context-bar-position)
const contextBarTest = useABTest('context-bar-position');
const contextBarPosition = contextBarTest.variant; // 'center' | 'near' | 'top'

// Feature Flag: Semantic Zoom Thresholds (Inactive example)
const useNewZoomThresholds = useFeatureFlag('semantic-zoom-thresholds');

useEffect(() => {
// Track canvas mount
telemetry.track(CanvasTelemetryEvent.CANVAS_MOUNT, {
calmModeDefault: shouldDefaultToCalmMode,
contextBarPosition,
semanticZoomEnabled: useNewZoomThresholds,
});

    return () => {
      telemetry.track(CanvasTelemetryEvent.CANVAS_UNMOUNT, {
        sessionDuration: performance.now(),
      });
    };

}, []);

return (
<>
<UnifiedCanvasApp
        initialCalmMode={shouldDefaultToCalmMode}
        contextBarPosition={contextBarPosition}
      />
<OnboardingTour />
<FeatureHintsManager />
</>
);
}

// ============================================================================
// EXAMPLE 2: Command Palette Integration
// ============================================================================

/\*\*

- Integrate canvas commands with your app's command palette.
- This example shows how to merge canvas commands with app-level commands.
  \*/
  function AppWithCommandPalette() {
  const canvasCommands = useCanvasCommandsForPalette();
  const [isOpen, setIsOpen] = React.useState(false);

// Merge with app-level commands
const allCommands = React.useMemo(() => [
...canvasCommands,
// Your app commands
{
id: 'app:save',
label: 'Save Project',
description: 'Save current project to cloud',
shortcut: '⌘S',
execute: async () => {
// Save logic
},
},
], [canvasCommands]);

// Example: Simple command palette UI
return (
<>
<button onClick={() => setIsOpen(true)}>
Open Command Palette (⌘K)
</button>

      {isOpen && (
        <CommandPaletteModal
          commands={allCommands}
          onClose={() => setIsOpen(false)}
        />
      )}

      <FullFeaturedCanvas />
    </>

);
}

// ============================================================================
// EXAMPLE 3: Custom Telemetry Integration
// ============================================================================

/\*\*

- Track custom events alongside canvas telemetry.
- All events are batched and respect privacy consent.
  \*/
  function CanvasWithCustomTracking() {
  const telemetry = useCanvasTelemetry();

const handleCustomAction = () => {
// Track custom event
telemetry.track(CanvasTelemetryEvent.CUSTOM, {
action: 'user_exported_diagram',
format: 'svg',
elementCount: 42,
});
};

const handleError = (error: Error) => {
// Track error
telemetry.trackError(CanvasTelemetryEvent.ERROR, error, {
context: 'export_operation',
});
};

return (

<div>
<button onClick={handleCustomAction}>Export Diagram</button>
<FullFeaturedCanvas />
</div>
);
}

// ============================================================================
// EXAMPLE 4: A/B Test Variant Components
// ============================================================================

/\*\*

- Render different UI based on A/B test variant.
- Uses consistent hashing for stable user assignments.
  \*/
  function ContextBarWithVariants() {
  const { variant, config } = useABTest('context-bar-position');

// Different positioning based on variant
const positionStyles = {
center: { left: '50%', transform: 'translateX(-50%)' },
near: { left: '320px' }, // Near selection
top: { top: '0', left: '50%', transform: 'translateX(-50%)' },
};

return (

<div style={positionStyles[variant]}>
Context Bar (Position: {variant})
</div>
);
}

// ============================================================================
// EXAMPLE 5: Onboarding Customization
// ============================================================================

/\*\*

- Control when onboarding tour starts and provide custom triggers.
  \*/
  function CanvasWithDelayedOnboarding() {
  const { startTour, skipTour } = useOnboardingTour();
  const onboardingTest = useABTest('onboarding-timing');

useEffect(() => {
// A/B test: Immediate vs delayed onboarding
if (onboardingTest.variant === 'delayed') {
// Start after 5 seconds
const timer = setTimeout(() => startTour(), 5000);
return () => clearTimeout(timer);
} else {
// Start immediately
startTour();
}
}, [onboardingTest.variant]);

return (

<div>
<button onClick={startTour}>Restart Tour</button>
<button onClick={skipTour}>Skip Tour</button>
<FullFeaturedCanvas />
</div>
);
}

// ============================================================================
// EXAMPLE 6: Privacy-Respecting Telemetry Setup
// ============================================================================

/\*\*

- Configure telemetry with privacy consent.
- Must be called before any tracking occurs.
  \*/
  export function setupTelemetry() {
  const telemetry = getCanvasTelemetry();

// Configure telemetry
telemetry.configure({
enabled: true,
endpoint: 'https://analytics.example.com/track',
batchSize: 50,
flushInterval: 10000, // 10 seconds
sampleRate: 1.0, // Track 100% of users
});

// Set privacy consent (required!)
telemetry.setConsent(true);

// Track app initialization
telemetry.track(CanvasTelemetryEvent.CANVAS_INIT, {
version: '2.0.0',
platform: 'web',
});
}

// ============================================================================
// EXAMPLE 7: Performance Monitoring
// ============================================================================

/\*\*

- Monitor canvas render performance automatically.
  \*/
  function PerformanceMonitoredCanvas() {
  // Tracks time from mount to unmount
  usePerformanceTracking(CanvasTelemetryEvent.CANVAS_RENDER_COMPLETE);

const telemetry = useCanvasTelemetry();

const handleHeavyOperation = async () => {
const start = performance.now();

    // Heavy operation (e.g., layout algorithm)
    await performComplexLayout();

    const duration = performance.now() - start;
    telemetry.trackTiming(CanvasTelemetryEvent.PERFORMANCE_METRIC, duration, {
      operation: 'complex_layout',
    });

};

return <FullFeaturedCanvas />;
}

// ============================================================================
// EXAMPLE 8: Command Execution from Code
// ============================================================================

/\*\*

- Programmatically execute canvas commands.
- Useful for toolbar buttons, menu items, or automated workflows.
  \*/
  function CanvasWithProgrammaticCommands() {
  const { executeCommand, getCommandById } = useCanvasCommands();

const handleCreateDiscoverFrame = async () => {
const command = getCommandById('frame:create-discover');
if (command) {
await executeCommand('frame:create-discover');
}
};

const handleZoomIn = async () => {
await executeCommand('navigation:zoom-in');
};

const handleToggleCalmMode = async () => {
await executeCommand('panel:toggle-calm-mode');
};

return (

<div>
<div className="toolbar">
<button onClick={handleCreateDiscoverFrame}>
New Discover Frame
</button>
<button onClick={handleZoomIn}>Zoom In (⌘+)</button>
<button onClick={handleToggleCalmMode}>
Toggle Calm Mode (⌘⇧C)
</button>
</div>
<FullFeaturedCanvas />
</div>
);
}

// ============================================================================
// TYPE DEFINITIONS FOR EXAMPLES
// ============================================================================

interface CommandPaletteModalProps {
commands: Array<{
id: string;
label: string;
description?: string;
shortcut?: string;
execute: () => Promise<void>;
}>;
onClose: () => void;
}

function CommandPaletteModal({ commands, onClose }: CommandPaletteModalProps) {
// Implementation would use your app's modal component
return (

<div className="modal">
<h2>Commands</h2>
{commands.map(cmd => (
<button key={cmd.id} onClick={() => {
cmd.execute();
onClose();
}}>
{cmd.label} {cmd.shortcut && <kbd>{cmd.shortcut}</kbd>}
</button>
))}
</div>
);
}

async function performComplexLayout() {
// Placeholder for heavy operation
await new Promise(resolve => setTimeout(resolve, 100));
}

// ============================================================================
// INITIALIZATION CHECKLIST
// ============================================================================

/\*\*

- To integrate Epic 9 & 10 features into your app:
-
- 1.  ✅ Setup Jotai Provider at app root
- 2.  ✅ Wrap canvas in CanvasCommandProvider
- 3.  ✅ Call setupTelemetry() before rendering canvas
- 4.  ✅ Add OnboardingTour component to app
- 5.  ✅ Add FeatureHintsManager component to app
- 6.  ✅ (Optional) Integrate useCanvasCommandsForPalette with your command palette
- 7.  ✅ (Optional) Configure A/B test assignments based on user ID
- 8.  ✅ (Optional) Customize onboarding timing and triggers
-
- Performance Impact:
- - Commands: Negligible (event listeners only)
- - Onboarding: ~10KB bundle size, only active for new users
- - Telemetry: Batched, flushes every 10s or 50 events
- - A/B Testing: One-time assignment calculation, cached in localStorage
-
- Privacy Compliance:
- - Telemetry requires explicit consent (setConsent(true))
- - All tracking can be disabled via config
- - No PII collected without opt-in
- - A/B assignments stored locally, never sent to server
    \*/
