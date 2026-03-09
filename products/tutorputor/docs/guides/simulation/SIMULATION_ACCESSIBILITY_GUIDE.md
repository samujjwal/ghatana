# Simulation System Accessibility Guide

## Overview

This guide ensures TutorPutor simulations are accessible to all learners, including those with disabilities, following WCAG 2.1 Level AA standards.

## Accessibility Requirements

### WCAG 2.1 Level AA Compliance

| Guideline | Requirement | Implementation |
|-----------|-------------|----------------|
| 1.1 Text Alternatives | Provide text alternatives for non-text content | Alt text, ARIA labels |
| 1.3 Adaptable | Create content that can be presented in different ways | Semantic HTML, ARIA |
| 1.4 Distinguishable | Make it easier to see and hear content | High contrast, captions |
| 2.1 Keyboard Accessible | Make all functionality available from keyboard | Focus management, shortcuts |
| 2.4 Navigable | Provide ways to help users navigate | Skip links, landmarks |
| 3.1 Readable | Make text content readable and understandable | Clear language, labels |
| 3.2 Predictable | Make pages appear and operate in predictable ways | Consistent navigation |
| 3.3 Input Assistance | Help users avoid and correct mistakes | Error messages, validation |
| 4.1 Compatible | Maximize compatibility with assistive technologies | Valid HTML, ARIA |

## Implementation Guide

### 1. Keyboard Navigation

#### Focus Management
```typescript
// Implement keyboard controls
function SimulationPlayer({ manifest }: Props) {
  const playerRef = useRef<HTMLDivElement>(null);
  
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      switch (e.key) {
        case ' ':
        case 'k':
          e.preventDefault();
          togglePlayPause();
          break;
        case 'ArrowRight':
        case 'l':
          e.preventDefault();
          stepForward();
          break;
        case 'ArrowLeft':
        case 'j':
          e.preventDefault();
          stepBackward();
          break;
        case 'f':
          e.preventDefault();
          toggleFullscreen();
          break;
        case '?':
          e.preventDefault();
          showKeyboardShortcuts();
          break;
      }
    };
    
    playerRef.current?.addEventListener('keydown', handleKeyDown);
    return () => playerRef.current?.removeEventListener('keydown', handleKeyDown);
  }, []);
  
  return (
    <div ref={playerRef} tabIndex={0} role="application" aria-label="Simulation Player">
      {/* Player content */}
    </div>
  );
}
```

#### Focus Indicators
```css
/* Clear focus indicators */
.simulation-control:focus {
  outline: 3px solid #0066cc;
  outline-offset: 2px;
}

.simulation-control:focus:not(:focus-visible) {
  outline: none;
}

.simulation-control:focus-visible {
  outline: 3px solid #0066cc;
  outline-offset: 2px;
}
```

### 2. Screen Reader Support

#### ARIA Labels
```tsx
<button
  onClick={togglePlay}
  aria-label={isPlaying ? 'Pause simulation' : 'Play simulation'}
  aria-pressed={isPlaying}
>
  {isPlaying ? <PauseIcon /> : <PlayIcon />}
</button>

<div
  role="region"
  aria-label="Simulation canvas"
  aria-describedby="simulation-description"
>
  <canvas ref={canvasRef} />
</div>

<div id="simulation-description" className="sr-only">
  {manifest.description}
</div>
```

#### Live Regions
```tsx
// Announce state changes
<div
  role="status"
  aria-live="polite"
  aria-atomic="true"
  className="sr-only"
>
  {currentStep && `Step ${currentStepIndex + 1} of ${totalSteps}: ${currentStep.narrative}`}
</div>

<div
  role="alert"
  aria-live="assertive"
  className="sr-only"
>
  {error && `Error: ${error}`}
</div>
```

#### Screen Reader Narration
```typescript
// Implement narration mode
interface AccessibilityConfig {
  screenReaderNarration: boolean;
  narrateSteps: boolean;
  narrateEntityChanges: boolean;
}

function narrateStep(step: SimulationStep) {
  if (!config.screenReaderNarration) return;
  
  const narration = [
    step.narrative,
    `This step affects ${step.actions.length} entities.`,
    step.actions.map(a => describeAction(a)).join('. ')
  ].filter(Boolean).join('. ');
  
  announceToScreenReader(narration);
}

function announceToScreenReader(message: string) {
  const announcement = document.createElement('div');
  announcement.setAttribute('role', 'status');
  announcement.setAttribute('aria-live', 'polite');
  announcement.className = 'sr-only';
  announcement.textContent = message;
  
  document.body.appendChild(announcement);
  setTimeout(() => announcement.remove(), 1000);
}
```

### 3. Visual Accessibility

#### High Contrast Mode
```typescript
// Detect and support high contrast mode
const prefersHighContrast = window.matchMedia('(prefers-contrast: high)').matches;

function getAccessibleColors(entity: SimEntity): Colors {
  if (prefersHighContrast) {
    return {
      fill: '#000000',
      stroke: '#FFFFFF',
      text: '#FFFFFF'
    };
  }
  
  // Ensure WCAG AA contrast ratio (4.5:1 for normal text)
  return ensureContrastRatio(entity.visual.fillColor, backgroundColor, 4.5);
}
```

#### Color Blindness Support
```typescript
// Provide alternative visual indicators
function renderEntity(entity: SimEntity, ctx: CanvasRenderingContext2D) {
  // Use patterns in addition to colors
  if (entity.highlighted) {
    ctx.fillStyle = createPattern('diagonal-stripes');
  }
  
  // Add text labels
  if (config.showLabels) {
    ctx.fillText(entity.label, entity.x, entity.y);
  }
  
  // Use shapes to distinguish types
  switch (entity.type) {
    case 'node':
      drawCircle(ctx, entity);
      break;
    case 'edge':
      drawArrow(ctx, entity);
      break;
  }
}
```

#### Reduced Motion
```typescript
// Respect prefers-reduced-motion
const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function getAnimationDuration(step: SimulationStep): number {
  if (prefersReducedMotion) {
    return 0; // Instant transitions
  }
  return step.duration || 1000;
}

// Provide toggle
function SimulationSettings() {
  const [reducedMotion, setReducedMotion] = useState(prefersReducedMotion);
  
  return (
    <label>
      <input
        type="checkbox"
        checked={reducedMotion}
        onChange={(e) => setReducedMotion(e.target.checked)}
      />
      Reduce motion
    </label>
  );
}
```

### 4. Captions and Transcripts

#### Step Narration
```typescript
// Provide text alternatives for visual content
interface SimulationStep {
  id: string;
  actions: Action[];
  narrative: string; // Required for accessibility
  caption?: string; // Optional detailed caption
}

// Display captions
function CaptionPanel({ step }: { step: SimulationStep }) {
  return (
    <div
      role="complementary"
      aria-label="Step caption"
      className="caption-panel"
    >
      <p>{step.caption || step.narrative}</p>
    </div>
  );
}
```

#### Audio Descriptions
```typescript
// Provide audio descriptions for complex visuals
interface AudioDescription {
  stepId: string;
  audioUrl: string;
  transcript: string;
}

function SimulationWithAudio({ manifest, audioDescriptions }: Props) {
  const [audioEnabled, setAudioEnabled] = useState(false);
  
  useEffect(() => {
    if (audioEnabled && currentStep) {
      const audio = audioDescriptions.find(a => a.stepId === currentStep.id);
      if (audio) {
        playAudio(audio.audioUrl);
      }
    }
  }, [currentStep, audioEnabled]);
  
  return (
    <>
      <button onClick={() => setAudioEnabled(!audioEnabled)}>
        {audioEnabled ? 'Disable' : 'Enable'} audio descriptions
      </button>
      {/* Simulation content */}
    </>
  );
}
```

### 5. Form Accessibility

#### Labels and Instructions
```tsx
<div className="form-group">
  <label htmlFor="simulation-title" className="required">
    Simulation Title
  </label>
  <input
    id="simulation-title"
    type="text"
    aria-required="true"
    aria-describedby="title-help"
  />
  <div id="title-help" className="help-text">
    Enter a descriptive title for your simulation
  </div>
</div>
```

#### Error Messages
```tsx
<div className="form-group">
  <label htmlFor="entity-count">Number of Entities</label>
  <input
    id="entity-count"
    type="number"
    aria-invalid={errors.entityCount ? 'true' : 'false'}
    aria-describedby={errors.entityCount ? 'entity-count-error' : undefined}
  />
  {errors.entityCount && (
    <div id="entity-count-error" role="alert" className="error-message">
      {errors.entityCount}
    </div>
  )}
</div>
```

## Testing Checklist

### Automated Testing
- [ ] Run axe-core accessibility tests
- [ ] Validate HTML with W3C validator
- [ ] Check color contrast ratios
- [ ] Test with Lighthouse accessibility audit

### Manual Testing
- [ ] Navigate entire simulation with keyboard only
- [ ] Test with screen reader (NVDA, JAWS, VoiceOver)
- [ ] Verify high contrast mode
- [ ] Test with browser zoom (200%, 400%)
- [ ] Verify reduced motion mode
- [ ] Test with color blindness simulators

### Assistive Technology Testing
- [ ] NVDA (Windows)
- [ ] JAWS (Windows)
- [ ] VoiceOver (macOS, iOS)
- [ ] TalkBack (Android)
- [ ] Dragon NaturallySpeaking (voice control)

## Accessibility Manifest Fields

```typescript
interface SimulationManifest {
  // ... other fields
  
  accessibility: {
    altText: string; // Required: Brief description
    longDescription?: string; // Detailed description
    screenReaderNarration: boolean; // Enable narration
    reducedMotion: boolean; // Support reduced motion
    highContrast: boolean; // Support high contrast
    keyboardShortcuts: KeyboardShortcut[]; // Document shortcuts
    audioDescriptions?: AudioDescription[]; // Audio descriptions
    captions: boolean; // Enable captions
    transcriptUrl?: string; // Link to full transcript
  };
}
```

## Resources

- [WCAG 2.1 Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
- [ARIA Authoring Practices](https://www.w3.org/WAI/ARIA/apg/)
- [WebAIM Resources](https://webaim.org/resources/)
- [Inclusive Components](https://inclusive-components.design/)
- [A11y Project Checklist](https://www.a11yproject.com/checklist/)
