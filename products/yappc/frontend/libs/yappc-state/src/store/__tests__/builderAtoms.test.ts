/**
 * builderAtoms Tests
 *
 * Verifies builder editor session atoms using a standalone Jotai store so
 * no React component or DOM is required.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createStore } from 'jotai';

import {
  builderActiveDocumentIdAtom,
  builderAutosaveStatusAtom,
  builderLastSavedAtAtom,
  builderAIPendingReviewCountAtom,
  builderPreviewModeAtom,
  builderCollabSessionAtom,
  builderHasUnsavedChangesAtom,
} from '../builderAtoms';

describe('builderAtoms', () => {
  let store: ReturnType<typeof createStore>;

  beforeEach(() => {
    store = createStore();
  });

  it('builderActiveDocumentIdAtom initializes to null', () => {
    expect(store.get(builderActiveDocumentIdAtom)).toBeNull();
  });

  it('builderActiveDocumentIdAtom can be set', () => {
    store.set(builderActiveDocumentIdAtom, 'doc-abc');
    expect(store.get(builderActiveDocumentIdAtom)).toBe('doc-abc');
  });

  it('builderAutosaveStatusAtom initializes to idle', () => {
    expect(store.get(builderAutosaveStatusAtom)).toBe('idle');
  });

  it('builderAutosaveStatusAtom transitions through states', () => {
    store.set(builderAutosaveStatusAtom, 'pending');
    expect(store.get(builderAutosaveStatusAtom)).toBe('pending');
    store.set(builderAutosaveStatusAtom, 'saving');
    expect(store.get(builderAutosaveStatusAtom)).toBe('saving');
    store.set(builderAutosaveStatusAtom, 'saved');
    expect(store.get(builderAutosaveStatusAtom)).toBe('saved');
  });

  it('builderLastSavedAtAtom initializes to null', () => {
    expect(store.get(builderLastSavedAtAtom)).toBeNull();
  });

  it('builderLastSavedAtAtom can be set to an ISO timestamp', () => {
    const ts = new Date().toISOString();
    store.set(builderLastSavedAtAtom, ts);
    expect(store.get(builderLastSavedAtAtom)).toBe(ts);
  });

  it('builderAIPendingReviewCountAtom initializes to 0', () => {
    expect(store.get(builderAIPendingReviewCountAtom)).toBe(0);
  });

  it('builderAIPendingReviewCountAtom can be incremented', () => {
    store.set(builderAIPendingReviewCountAtom, 3);
    expect(store.get(builderAIPendingReviewCountAtom)).toBe(3);
  });

  it('builderPreviewModeAtom initializes to null', () => {
    expect(store.get(builderPreviewModeAtom)).toBeNull();
  });

  it('builderPreviewModeAtom can be set to a PreviewMode value', () => {
    store.set(builderPreviewModeAtom, 'trusted-local');
    expect(store.get(builderPreviewModeAtom)).toBe('trusted-local');
  });

  it('builderCollabSessionAtom initializes to null', () => {
    expect(store.get(builderCollabSessionAtom)).toBeNull();
  });

  it('builderCollabSessionAtom can be set to a session', () => {
    store.set(builderCollabSessionAtom, {
      sessionId: 'sess-1',
      participantCount: 2,
      connected: true,
    });
    expect(store.get(builderCollabSessionAtom)).toMatchObject({
      sessionId: 'sess-1',
      participantCount: 2,
      connected: true,
    });
  });

  describe('builderHasUnsavedChangesAtom (derived)', () => {
    it('is false when status is idle', () => {
      store.set(builderAutosaveStatusAtom, 'idle');
      expect(store.get(builderHasUnsavedChangesAtom)).toBe(false);
    });

    it('is false when status is saved', () => {
      store.set(builderAutosaveStatusAtom, 'saved');
      expect(store.get(builderHasUnsavedChangesAtom)).toBe(false);
    });

    it('is true when status is pending', () => {
      store.set(builderAutosaveStatusAtom, 'pending');
      expect(store.get(builderHasUnsavedChangesAtom)).toBe(true);
    });

    it('is true when status is saving', () => {
      store.set(builderAutosaveStatusAtom, 'saving');
      expect(store.get(builderHasUnsavedChangesAtom)).toBe(true);
    });

    it('is true when status is error', () => {
      store.set(builderAutosaveStatusAtom, 'error');
      expect(store.get(builderHasUnsavedChangesAtom)).toBe(true);
    });
  });
});
