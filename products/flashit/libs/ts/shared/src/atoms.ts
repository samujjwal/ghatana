/**
 * Shared Jotai atoms for Flashit state management
 * Used by both web and mobile applications
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import type { User, Sphere, Moment } from './types';

// Storage adapter for React Native
export interface StorageAdapter {
  getItem: (key: string) => Promise<string | null> | string | null;
  setItem: (key: string, value: string) => Promise<void> | void;
  removeItem: (key: string) => Promise<void> | void;
}

// Factory function to create atoms with custom storage
export function createFlashitAtoms(storage?: StorageAdapter) {
  // Use provided storage or default to localStorage
  const createStorageAtom = <T>(key: string, initialValue: T) => {
    if (storage) {
      return atomWithStorage<T>(key, initialValue, storage as any);
    }
    return atomWithStorage<T>(key, initialValue);
  };

  // Auth atoms
  const authTokenAtom = createStorageAtom<string | null>('flashit_token', null);
  const currentUserAtom = atom<User | null>(null);
  const isAuthenticatedAtom = atom((get) => get(authTokenAtom) !== null);

  // Spheres atoms
  const spheresAtom = atom<Sphere[]>([]);
  const selectedSphereIdAtom = createStorageAtom<string | null>('flashit_selected_sphere', null);
  const selectedSphereAtom = atom((get) => {
    const spheres = get(spheresAtom);
    const selectedId = get(selectedSphereIdAtom);
    return spheres.find((s) => s.id === selectedId) || null;
  });

  // Moments atoms
  const momentsAtom = atom<Moment[]>([]);
  const selectedMomentIdAtom = atom<string | null>(null);
  const selectedMomentAtom = atom((get) => {
    const moments = get(momentsAtom);
    const selectedId = get(selectedMomentIdAtom);
    return moments.find((m) => m.id === selectedId) || null;
  });

  // UI state atoms
  const isCaptureModalOpenAtom = atom(false);
  const isCreateSphereModalOpenAtom = atom(false);
  const searchQueryAtom = atom('');
  const selectedTagsAtom = atom<string[]>([]);
  const selectedEmotionsAtom = atom<string[]>([]);

  return {
    authTokenAtom,
    currentUserAtom,
    isAuthenticatedAtom,
    spheresAtom,
    selectedSphereIdAtom,
    selectedSphereAtom,
    momentsAtom,
    selectedMomentIdAtom,
    selectedMomentAtom,
    isCaptureModalOpenAtom,
    isCreateSphereModalOpenAtom,
    searchQueryAtom,
    selectedTagsAtom,
    selectedEmotionsAtom,
  };
}

// Default atoms for web (uses localStorage)
export const {
  authTokenAtom,
  currentUserAtom,
  isAuthenticatedAtom,
  spheresAtom,
  selectedSphereIdAtom,
  selectedSphereAtom,
  momentsAtom,
  selectedMomentIdAtom,
  selectedMomentAtom,
  isCaptureModalOpenAtom,
  isCreateSphereModalOpenAtom,
  searchQueryAtom,
  selectedTagsAtom,
  selectedEmotionsAtom,
} = createFlashitAtoms();

