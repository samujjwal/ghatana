/**
 * @yappc/product-theme — lifecycle-presets unit tests
 *
 * Covers PHASE_THEMES shape, getPhaseTheme, getPhaseLabel, getPhaseIcon.
 */

import { describe, it, expect } from 'vitest';
import {
  PHASE_THEMES,
  getPhaseTheme,
  getPhaseLabel,
  getPhaseIcon,
  type LifecyclePhase,
  type PhaseTheme,
} from '../lifecycle-presets';

const ALL_PHASES: LifecyclePhase[] = [
  'intent',
  'shape',
  'validate',
  'generate',
  'build',
  'run',
  'observe',
  'improve',
];

describe('PHASE_THEMES', () => {
  it('contains an entry for every lifecycle phase', () => {
    for (const phase of ALL_PHASES) {
      expect(PHASE_THEMES[phase]).toBeDefined();
    }
  });

  it('each theme has all required PhaseTheme fields', () => {
    const requiredKeys: (keyof PhaseTheme)[] = [
      'canvasBg',
      'accent',
      'border',
      'text',
      'icon',
      'hover',
    ];
    for (const phase of ALL_PHASES) {
      for (const key of requiredKeys) {
        expect(PHASE_THEMES[phase][key]).toBeTruthy();
      }
    }
  });

  it('all color values are valid CSS color strings', () => {
    for (const phase of ALL_PHASES) {
      const theme = PHASE_THEMES[phase];
      for (const value of Object.values(theme)) {
        // Must be a hex colour of form #RRGGBB or #RGB
        expect(value).toMatch(/^#([0-9A-Fa-f]{3}){1,2}$/);
      }
    }
  });
});

describe('getPhaseTheme', () => {
  it('returns the correct theme for each known phase', () => {
    for (const phase of ALL_PHASES) {
      expect(getPhaseTheme(phase)).toEqual(PHASE_THEMES[phase]);
    }
  });

  it('falls back to the intent theme for an unknown phase value', () => {
    const result = getPhaseTheme('unknown' as LifecyclePhase);
    expect(result).toEqual(PHASE_THEMES.intent);
  });
});

describe('getPhaseLabel', () => {
  it('returns a non-empty string for every phase', () => {
    for (const phase of ALL_PHASES) {
      const label = getPhaseLabel(phase);
      expect(typeof label).toBe('string');
      expect(label.length).toBeGreaterThan(0);
    }
  });

  it('returns readable title-case labels', () => {
    expect(getPhaseLabel('intent')).toBe('Intent');
    expect(getPhaseLabel('shape')).toBe('Shape');
    expect(getPhaseLabel('validate')).toBe('Validate');
    expect(getPhaseLabel('generate')).toBe('Generate');
    expect(getPhaseLabel('build')).toBe('Build');
    expect(getPhaseLabel('run')).toBe('Run');
    expect(getPhaseLabel('observe')).toBe('Observe');
    expect(getPhaseLabel('improve')).toBe('Improve');
  });

  it('falls back to the raw phase value for an unknown phase', () => {
    expect(getPhaseLabel('mystery' as LifecyclePhase)).toBe('mystery');
  });
});

describe('getPhaseIcon', () => {
  it('returns a non-empty string for every phase', () => {
    for (const phase of ALL_PHASES) {
      const icon = getPhaseIcon(phase);
      expect(typeof icon).toBe('string');
      expect(icon.length).toBeGreaterThan(0);
    }
  });

  it('returns distinct icon names across phases', () => {
    const icons = ALL_PHASES.map(getPhaseIcon);
    const unique = new Set(icons);
    // At least 5 distinct icons across 8 phases
    expect(unique.size).toBeGreaterThanOrEqual(5);
  });

  it('falls back to a default icon for an unknown phase', () => {
    const icon = getPhaseIcon('unknown' as LifecyclePhase);
    expect(typeof icon).toBe('string');
    expect(icon.length).toBeGreaterThan(0);
  });
});
