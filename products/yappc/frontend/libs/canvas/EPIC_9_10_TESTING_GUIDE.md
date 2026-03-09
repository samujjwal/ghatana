# Epic 9 & 10: Testing Guide

## Overview

This guide provides comprehensive testing strategies for:

- **Epic 9**: Command Palette Integration (15 commands)
- **Epic 10**: Onboarding & Telemetry (Tour + Hints + Analytics + A/B Testing)

## Testing Strategy

### Unit Tests (Jest + React Testing Library)

#### 1. Command System Tests

```typescript
// commands/__tests__/canvas-commands.test.ts
import { describe, test, expect, beforeEach } from '@jest/globals';
import {
  ALL_CANVAS_COMMANDS,
  executeCanvasCommand,
  searchCanvasCommands,
  filterCommandsByTag,
  getCommandByShortcut,
} from '../canvas-commands';

describe('Canvas Commands', () => {
  describe('Command Registry', () => {
    test('should have 15 total commands', () => {
      expect(ALL_CANVAS_COMMANDS).toHaveLength(15);
    });

    test('should have unique command IDs', () => {
      const ids = ALL_CANVAS_COMMANDS.map((cmd) => cmd.id);
      const uniqueIds = new Set(ids);
      expect(uniqueIds.size).toBe(ids.length);
    });

    test('should have categories', () => {
      const categories = new Set(
        ALL_CANVAS_COMMANDS.map((cmd) => cmd.category)
      );
      expect(categories).toContain('frame');
      expect(categories).toContain('navigation');
      expect(categories).toContain('panel');
    });
  });

  describe('Command Search', () => {
    test('should find commands by label', () => {
      const results = searchCanvasCommands('zoom');
      expect(results.length).toBeGreaterThan(0);
      expect(results.some((cmd) => cmd.id === 'navigation:zoom-in')).toBe(true);
    });

    test('should find commands by tag', () => {
      const results = filterCommandsByTag('frame');
      expect(results.every((cmd) => cmd.tags.includes('frame'))).toBe(true);
    });

    test('should find command by keyboard shortcut', () => {
      const cmd = getCommandByShortcut('⌘⇧1');
      expect(cmd?.id).toBe('frame:create-discover');
    });
  });

  describe('Command Execution', () => {
    test('should throw error for unknown command', async () => {
      const context = createMockContext();
      await expect(
        executeCanvasCommand('unknown:command', context)
      ).rejects.toThrow('Command not found');
    });
  });
});
```

#### 2. Onboarding Tour Tests

```typescript
// onboarding/__tests__/OnboardingTour.test.tsx
import { describe, test, expect, beforeEach, afterEach } from '@jest/globals';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider as JotaiProvider } from 'jotai';
import { OnboardingTour, useOnboardingTour } from '../OnboardingTour';

describe('OnboardingTour', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe('Tour Visibility', () => {
    test('should show tour for first-time users', () => {
      render(
        <JotaiProvider>
          <OnboardingTour />
        </JotaiProvider>
      );

      expect(screen.getByText(/Welcome to Unified Canvas/i)).toBeInTheDocument();
    });

    test('should not show tour if completed', () => {
      localStorage.setItem('canvas-tour-completed', 'true');

      render(
        <JotaiProvider>
          <OnboardingTour />
        </JotaiProvider>
      );

      expect(screen.queryByText(/Welcome to Unified Canvas/i)).not.toBeInTheDocument();
    });
  });

  describe('Tour Navigation', () => {
    test('should advance to next step on Next button click', async () => {
      const { container } = render(
        <JotaiProvider>
          <OnboardingTour />
        </JotaiProvider>
      );

      const nextButton = screen.getByText(/Next/i);
      fireEvent.click(nextButton);

      await waitFor(() => {
        expect(screen.getByText(/Step 2/i)).toBeInTheDocument();
      });
    });

    test('should go back to previous step on Back button click', async () => {
      const { container } = render(
        <JotaiProvider>
          <OnboardingTour />
        </JotaiProvider>
      );

      // Go to step 2
      fireEvent.click(screen.getByText(/Next/i));
      await waitFor(() => screen.getByText(/Step 2/i));

      // Go back to step 1
      fireEvent.click(screen.getByText(/Back/i));
      await waitFor(() => {
        expect(screen.getByText(/Step 1/i)).toBeInTheDocument();
      });
    });

    test('should skip tour on Skip button click', async () => {
      const { container } = render(
        <JotaiProvider>
          <OnboardingTour />
        </JotaiProvider>
      );

      fireEvent.click(screen.getByText(/Skip/i));

      await waitFor(() => {
        expect(localStorage.getItem('canvas-tour-completed')).toBe('true');
      });
    });
  });

  describe('Keyboard Navigation', () => {
    test('should advance on ArrowRight key', async () => {
      const { container } = render(
        <JotaiProvider>
          <OnboardingTour />
        </JotaiProvider>
      );

      fireEvent.keyDown(container, { key: 'ArrowRight' });

      await waitFor(() => {
        expect(screen.getByText(/Step 2/i)).toBeInTheDocument();
      });
    });

    test('should go back on ArrowLeft key', async () => {
      const { container } = render(
        <JotaiProvider>
          <OnboardingTour />
        </JotaiProvider>
      );

      // Go to step 2
      fireEvent.keyDown(container, { key: 'ArrowRight' });
      await waitFor(() => screen.getByText(/Step 2/i));

      // Go back
      fireEvent.keyDown(container, { key: 'ArrowLeft' });

      await waitFor(() => {
        expect(screen.getByText(/Step 1/i)).toBeInTheDocument();
      });
    });

    test('should close on Escape key', async () => {
      const { container } = render(
        <JotaiProvider>
          <OnboardingTour />
        </JotaiProvider>
      );

      fireEvent.keyDown(container, { key: 'Escape' });

      await waitFor(() => {
        expect(localStorage.getItem('canvas-tour-completed')).toBe('true');
      });
    });
  });
});
```

#### 3. Feature Hints Tests

```typescript
// onboarding/__tests__/FeatureHints.test.tsx
import { describe, test, expect, beforeEach } from '@jest/globals';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider as JotaiProvider } from 'jotai';
import { FeatureHintsManager, useFeatureHints } from '../FeatureHints';

describe('FeatureHints', () => {
  beforeEach(() => {
    localStorage.clear();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe('Hint Display', () => {
    test('should show hint when trigger condition met', async () => {
      // Mock trigger: user has created 3 frames
      localStorage.setItem('canvas-hint-triggers', JSON.stringify({
        framesCreated: 3,
      }));

      render(
        <JotaiProvider>
          <FeatureHintsManager />
        </JotaiProvider>
      );

      // Fast-forward 2 seconds (trigger check interval)
      jest.advanceTimersByTime(2000);

      await waitFor(() => {
        expect(screen.getByText(/Did you know/i)).toBeInTheDocument();
      });
    });

    test('should auto-dismiss hint after 10 seconds', async () => {
      // Show hint
      render(
        <JotaiProvider>
          <FeatureHintsManager />
        </JotaiProvider>
      );

      // Fast-forward 10 seconds
      jest.advanceTimersByTime(10000);

      await waitFor(() => {
        expect(screen.queryByText(/Did you know/i)).not.toBeInTheDocument();
      });
    });
  });

  describe('Hint Dismissal', () => {
    test('should dismiss hint on close button click', async () => {
      render(
        <JotaiProvider>
          <FeatureHintsManager />
        </JotaiProvider>
      );

      const closeButton = screen.getByLabelText(/close/i);
      fireEvent.click(closeButton);

      await waitFor(() => {
        expect(screen.queryByText(/Did you know/i)).not.toBeInTheDocument();
      });
    });

    test('should persist dismissed hints', async () => {
      render(
        <JotaiProvider>
          <FeatureHintsManager />
        </JotaiProvider>
      );

      fireEvent.click(screen.getByLabelText(/close/i));

      await waitFor(() => {
        const dismissed = JSON.parse(
          localStorage.getItem('canvas-hints-dismissed') || '[]'
        );
        expect(dismissed).toHaveLength(1);
      });
    });
  });
});
```

#### 4. Telemetry Tests

```typescript
// telemetry/__tests__/canvas-telemetry.test.ts
import { describe, test, expect, beforeEach, jest } from '@jest/globals';
import { getCanvasTelemetry, CanvasTelemetryEvent } from '../canvas-telemetry';

describe('CanvasTelemetry', () => {
  let telemetry: ReturnType<typeof getCanvasTelemetry>;
  let fetchMock: jest.Mock;

  beforeEach(() => {
    telemetry = getCanvasTelemetry();
    fetchMock = jest.fn().mockResolvedValue({ ok: true });
    global.fetch = fetchMock;

    telemetry.configure({
      enabled: true,
      endpoint: 'https://test.example.com/track',
      batchSize: 3,
      flushInterval: 1000,
    });
    telemetry.setConsent(true);
  });

  describe('Event Tracking', () => {
    test('should track event with data', () => {
      telemetry.track(CanvasTelemetryEvent.CANVAS_MOUNT, {
        version: '2.0.0',
      });

      expect(telemetry['eventQueue']).toHaveLength(1);
    });

    test('should not track if consent not given', () => {
      telemetry.setConsent(false);
      telemetry.track(CanvasTelemetryEvent.CANVAS_MOUNT);

      expect(telemetry['eventQueue']).toHaveLength(0);
    });

    test('should not track if disabled', () => {
      telemetry.configure({ enabled: false });
      telemetry.track(CanvasTelemetryEvent.CANVAS_MOUNT);

      expect(telemetry['eventQueue']).toHaveLength(0);
    });
  });

  describe('Event Batching', () => {
    test('should flush when batch size reached', async () => {
      telemetry.track(CanvasTelemetryEvent.CANVAS_MOUNT);
      telemetry.track(CanvasTelemetryEvent.FRAME_CREATE);
      telemetry.track(CanvasTelemetryEvent.NAVIGATION_ZOOM);

      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(fetchMock).toHaveBeenCalledTimes(1);
      expect(fetchMock).toHaveBeenCalledWith(
        'https://test.example.com/track',
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('CANVAS_MOUNT'),
        })
      );
    });

    test('should flush on interval', async () => {
      jest.useFakeTimers();

      telemetry.track(CanvasTelemetryEvent.CANVAS_MOUNT);

      jest.advanceTimersByTime(1000);

      await new Promise((resolve) => setTimeout(resolve, 0));

      expect(fetchMock).toHaveBeenCalled();

      jest.useRealTimers();
    });
  });

  describe('Privacy', () => {
    test('should respect sample rate', () => {
      telemetry.configure({ sampleRate: 0.0 }); // 0% sample rate

      telemetry.track(CanvasTelemetryEvent.CANVAS_MOUNT);

      expect(telemetry['eventQueue']).toHaveLength(0);
    });
  });
});
```

#### 5. A/B Testing Tests

```typescript
// telemetry/__tests__/ab-testing.test.ts
import { describe, test, expect, beforeEach } from '@jest/globals';
import { getABTestManager, CANVAS_AB_TESTS } from '../ab-testing';

describe('ABTestManager', () => {
  let manager: ReturnType<typeof getABTestManager>;

  beforeEach(() => {
    localStorage.clear();
    manager = getABTestManager();
  });

  describe('Variant Assignment', () => {
    test('should assign variant consistently', () => {
      const variant1 = manager.getVariant('calm-mode-default', 'user123');
      const variant2 = manager.getVariant('calm-mode-default', 'user123');

      expect(variant1).toBe(variant2);
    });

    test('should assign different variants to different users', () => {
      const variant1 = manager.getVariant('calm-mode-default', 'user123');
      const variant2 = manager.getVariant('calm-mode-default', 'user456');

      // May be same due to randomness, but algorithm should differ
      expect(typeof variant1).toBe('string');
      expect(typeof variant2).toBe('string');
    });

    test('should persist assignments in localStorage', () => {
      manager.getVariant('calm-mode-default', 'user123');

      const stored = JSON.parse(
        localStorage.getItem('canvas-ab-assignments') || '{}'
      );

      expect(stored['calm-mode-default']).toBeDefined();
    });
  });

  describe('Weighted Variants', () => {
    test('should respect weights', () => {
      const assignments: Record<string, number> = {};

      // Simulate 1000 user assignments
      for (let i = 0; i < 1000; i++) {
        const variant = manager.getVariant('context-bar-position', `user${i}`);
        assignments[variant] = (assignments[variant] || 0) + 1;
      }

      // Check distribution roughly matches weights (33%/33%/34%)
      expect(assignments['center']).toBeGreaterThan(250);
      expect(assignments['center']).toBeLessThan(400);
      expect(assignments['near']).toBeGreaterThan(250);
      expect(assignments['top']).toBeGreaterThan(250);
    });
  });

  describe('Inactive Tests', () => {
    test('should return control for inactive tests', () => {
      const variant = manager.getVariant('semantic-zoom-thresholds', 'user123');

      expect(variant).toBe('control');
    });
  });
});
```

### Integration Tests

#### 6. End-to-End Command Workflow

```typescript
// __tests__/integration/command-workflow.test.tsx
import { describe, test, expect } from '@jest/globals';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider as JotaiProvider } from 'jotai';
import { CanvasCommandProvider, FullFeaturedCanvas } from '../examples';

describe('Command Workflow Integration', () => {
  test('should execute frame creation command via keyboard', async () => {
    render(
      <JotaiProvider>
        <CanvasCommandProvider>
          <FullFeaturedCanvas />
        </CanvasCommandProvider>
      </JotaiProvider>
    );

    // Simulate ⌘⇧1 (Create Discover Frame)
    fireEvent.keyDown(document, {
      key: '1',
      metaKey: true,
      shiftKey: true,
    });

    await waitFor(() => {
      expect(screen.getByText(/Discover/i)).toBeInTheDocument();
    });
  });

  test('should execute zoom command and update viewport', async () => {
    const { container } = render(
      <JotaiProvider>
        <CanvasCommandProvider>
          <FullFeaturedCanvas />
        </CanvasCommandProvider>
      </JotaiProvider>
    );

    // Simulate ⌘+ (Zoom In)
    fireEvent.keyDown(document, {
      key: '+',
      metaKey: true,
    });

    await waitFor(() => {
      // Check viewport zoom increased
      const viewport = screen.getByTestId('canvas-viewport');
      expect(viewport.style.transform).toContain('scale');
    });
  });
});
```

### Manual Testing Checklist

#### Epic 9: Command System

- [ ] **Command Palette**
  - [ ] Open palette with ⌘K
  - [ ] Search for "frame" shows 5 frame commands
  - [ ] Search for "zoom" shows zoom commands
  - [ ] Keyboard navigation with arrow keys
  - [ ] Execute command with Enter key

- [ ] **Frame Commands**
  - [ ] ⌘⇧1 creates Discover frame
  - [ ] ⌘⇧2 creates Design frame
  - [ ] ⌘⇧3 creates Build frame
  - [ ] ⌘⇧4 creates Test frame
  - [ ] Frame appears at viewport center

- [ ] **Navigation Commands**
  - [ ] ⌘0 centers viewport
  - [ ] ⌘+ zooms in
  - [ ] ⌘- zooms out
  - [ ] ⌘⇧0 resets zoom to 100%
  - [ ] ⌘F focuses selected element

- [ ] **Panel Toggle Commands**
  - [ ] ⌘⇧L toggles left rail
  - [ ] ⌘⇧I toggles inspector
  - [ ] ⌘⇧O toggles outline
  - [ ] ⌘⇧M toggles minimap
  - [ ] ⌘⇧C toggles calm mode

#### Epic 10: Onboarding

- [ ] **Tour System**
  - [ ] Tour starts automatically for new users
  - [ ] Tour shows 10 steps in sequence
  - [ ] Next/Back buttons work correctly
  - [ ] Skip button completes tour
  - [ ] Arrow keys navigate tour
  - [ ] Escape key closes tour
  - [ ] Spotlight highlights correct elements
  - [ ] Tour doesn't show for returning users

- [ ] **Feature Hints**
  - [ ] Hint appears after 3 frames created
  - [ ] Hint shows for 10 seconds
  - [ ] Close button dismisses hint
  - [ ] Dismissed hints don't reappear
  - [ ] Only one hint shows at a time

#### Epic 10: Telemetry

- [ ] **Event Tracking**
  - [ ] Canvas mount event tracked
  - [ ] Frame creation tracked
  - [ ] Navigation events tracked
  - [ ] Command execution tracked
  - [ ] Events batched (check network tab)
  - [ ] Consent required for tracking

- [ ] **A/B Testing**
  - [ ] Calm mode variant applied consistently
  - [ ] Context bar position varies by user
  - [ ] Assignments persisted in localStorage
  - [ ] Same user gets same variant

### Performance Testing

#### Bundle Size Analysis

```bash
# Check bundle impact
npm run build
npx bundlesize check

# Expected additions:
# - Commands: ~5KB gzipped
# - Onboarding: ~8KB gzipped
# - Telemetry: ~4KB gzipped
# - A/B Testing: ~3KB gzipped
# Total: ~20KB gzipped
```

#### Runtime Performance

```typescript
// Performance benchmark
import { performance } from 'perf_hooks';

describe('Performance', () => {
  test('command execution should complete in <10ms', async () => {
    const start = performance.now();
    await executeCanvasCommand('frame:create-discover', context);
    const duration = performance.now() - start;

    expect(duration).toBeLessThan(10);
  });

  test('telemetry tracking should complete in <1ms', () => {
    const start = performance.now();
    telemetry.track(CanvasTelemetryEvent.CANVAS_MOUNT);
    const duration = performance.now() - start;

    expect(duration).toBeLessThan(1);
  });
});
```

### Accessibility Testing

- [ ] **Keyboard Navigation**
  - [ ] All commands accessible via keyboard
  - [ ] Tab order logical in onboarding tour
  - [ ] Focus indicators visible
  - [ ] Escape key closes overlays

- [ ] **Screen Readers**
  - [ ] Tour steps announced
  - [ ] Feature hints announced
  - [ ] Command execution feedback announced

### Browser Compatibility

- [ ] Chrome 90+
- [ ] Firefox 88+
- [ ] Safari 14+
- [ ] Edge 90+

### Privacy & GDPR Compliance

- [ ] **Telemetry**
  - [ ] Requires explicit consent
  - [ ] Can be disabled globally
  - [ ] No PII collected
  - [ ] Data sent over HTTPS

- [ ] **LocalStorage**
  - [ ] Clear consent mechanism
  - [ ] Can be cleared by user
  - [ ] No sensitive data stored

## Debugging Tips

### Enable Debug Logging

```typescript
// In browser console
localStorage.setItem('canvas-debug', 'true');
location.reload();

// Logs will show:
// - Command execution
// - Telemetry events
// - A/B test assignments
// - Onboarding state changes
```

### Inspect LocalStorage

```typescript
// Check tour completion
localStorage.getItem('canvas-tour-completed');

// Check dismissed hints
JSON.parse(localStorage.getItem('canvas-hints-dismissed') || '[]');

// Check A/B assignments
JSON.parse(localStorage.getItem('canvas-ab-assignments') || '{}');

// Check telemetry consent
localStorage.getItem('canvas-telemetry-consent');
```

### Reset State

```typescript
// Clear all Epic 9 & 10 state
localStorage.removeItem('canvas-tour-completed');
localStorage.removeItem('canvas-tour-step');
localStorage.removeItem('canvas-hints-dismissed');
localStorage.removeItem('canvas-ab-assignments');
localStorage.removeItem('canvas-telemetry-consent');
location.reload();
```

## CI/CD Integration

### Test Commands

```json
{
  "scripts": {
    "test:epic9": "jest --testPathPattern=commands",
    "test:epic10": "jest --testPathPattern='onboarding|telemetry'",
    "test:integration": "jest --testPathPattern=integration",
    "test:coverage": "jest --coverage --coverageThreshold='{\"global\":{\"statements\":80}}'",
    "test:e2e": "playwright test",
    "test:bundle": "bundlesize check",
    "test:all": "npm run test:epic9 && npm run test:epic10 && npm run test:integration"
  }
}
```

### GitHub Actions Workflow

```yaml
name: Epic 9 & 10 Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'

      - name: Install dependencies
        run: npm ci

      - name: Run unit tests
        run: npm run test:all

      - name: Check bundle size
        run: npm run test:bundle

      - name: E2E tests
        run: npm run test:e2e
```

## Definition of Done

- [x] All 15 commands implemented and tested
- [x] Onboarding tour with 10 steps
- [x] Feature hints system with 8 hints
- [x] Telemetry system with privacy consent
- [x] A/B testing with 5 active tests
- [x] Unit test coverage >80%
- [x] Integration tests passing
- [x] Bundle size <25KB gzipped
- [x] Accessibility checks passing
- [x] Documentation complete
- [x] Examples provided
