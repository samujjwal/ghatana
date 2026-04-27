/**
 * Unit tests for the onboarding Jotai atoms.
 *
 * Exercises: derived atom creation, startTour/advanceTourStep/cancelTour
 * write atoms, featureDiscovery flag atoms.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createStore } from 'jotai';
import {
  activeTourIdAtom,
  activeTourStepAtom,
  featureDiscoveryOpenAtom,
  activeFeatureIdAtom,
  startTourAtom,
  advanceTourStepAtom,
  cancelTourAtom,
  createDismissedOnboardingAtom,
} from '../onboardingAtom';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeStore() {
  return createStore();
}

// ---------------------------------------------------------------------------
// Tests: initial values
// ---------------------------------------------------------------------------

describe('onboardingAtom – initial state', () => {
  it('activeTourIdAtom starts as null', () => {
    const store = makeStore();
    expect(store.get(activeTourIdAtom)).toBeNull();
  });

  it('activeTourStepAtom starts at 0', () => {
    const store = makeStore();
    expect(store.get(activeTourStepAtom)).toBe(0);
  });

  it('featureDiscoveryOpenAtom starts as false', () => {
    const store = makeStore();
    expect(store.get(featureDiscoveryOpenAtom)).toBe(false);
  });

  it('activeFeatureIdAtom starts as null', () => {
    const store = makeStore();
    expect(store.get(activeFeatureIdAtom)).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// Tests: startTourAtom
// ---------------------------------------------------------------------------

describe('startTourAtom', () => {
  it('sets the active tour ID', () => {
    const store = makeStore();
    store.set(startTourAtom, 'welcome-tour');
    expect(store.get(activeTourIdAtom)).toBe('welcome-tour');
  });

  it('resets step to 0 when starting', () => {
    const store = makeStore();
    store.set(activeTourStepAtom, 3);
    store.set(startTourAtom, 'another-tour');
    expect(store.get(activeTourStepAtom)).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// Tests: advanceTourStepAtom
// ---------------------------------------------------------------------------

describe('advanceTourStepAtom', () => {
  it('increments step when steps remain', () => {
    const store = makeStore();
    store.set(startTourAtom, 'demo-tour');
    store.set(advanceTourStepAtom, 5);
    expect(store.get(activeTourStepAtom)).toBe(1);
  });

  it('ends tour when last step is advanced past', () => {
    const store = makeStore();
    store.set(startTourAtom, 'demo-tour');
    store.set(activeTourStepAtom, 4);
    // advance with totalSteps = 5 (current = 4, next = 5 >= 5 → end)
    store.set(advanceTourStepAtom, 5);
    expect(store.get(activeTourIdAtom)).toBeNull();
    expect(store.get(activeTourStepAtom)).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// Tests: cancelTourAtom
// ---------------------------------------------------------------------------

describe('cancelTourAtom', () => {
  it('clears the active tour without completing it', () => {
    const store = makeStore();
    store.set(startTourAtom, 'welcome-tour');
    store.set(activeTourStepAtom, 2);
    store.set(cancelTourAtom, undefined);
    expect(store.get(activeTourIdAtom)).toBeNull();
    expect(store.get(activeTourStepAtom)).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// Tests: createDismissedOnboardingAtom
// ---------------------------------------------------------------------------

describe('createDismissedOnboardingAtom', () => {
  const userId = 'user-1';
  const workspaceId = 'ws-1';

  beforeEach(() => {
    // Clear localStorage between tests
    if (typeof localStorage !== 'undefined') {
      localStorage.clear();
    }
  });

  it('returns an atom with empty defaults', () => {
    const store = makeStore();
    const atom = createDismissedOnboardingAtom(userId, workspaceId);
    const val = store.get(atom);
    expect(val.tours).toEqual([]);
    expect(val.hints).toEqual([]);
  });

  it('creates distinct atoms for different user+workspace pairs', () => {
    const atom1 = createDismissedOnboardingAtom('user-a', 'ws-1');
    const atom2 = createDismissedOnboardingAtom('user-b', 'ws-1');
    expect(atom1).not.toBe(atom2);
  });

  it('can store and retrieve dismissed tours', () => {
    const store = makeStore();
    const atom = createDismissedOnboardingAtom(userId, workspaceId);
    store.set(atom, { tours: ['welcome-tour'], hints: [] });
    expect(store.get(atom).tours).toContain('welcome-tour');
  });
});
