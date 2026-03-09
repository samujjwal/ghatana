/**
 * Jotai Atoms for Flashit State Management
 * Manages authentication, user data, and UI state
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import type { User, Sphere as BaseSphere, Moment as BaseMoment, SphereRole } from '@ghatana/flashit-shared';

// Extended types for web app (includes relations and computed fields)
export type { User };

export interface Sphere extends Omit<BaseSphere, 'userId'> {
  userId?: string;
  userRole: SphereRole;
  momentCount: number;
}

export interface Moment extends BaseMoment {
  sphere: {
    id: string;
    name: string;
    type: string;
  };
  user?: {
    id: string;
    displayName: string | null;
  };
}

// Auth atoms
export const authTokenAtom = atomWithStorage<string | null>('flashit_token', null);
export const currentUserAtom = atom<User | null>(null);
export const isAuthenticatedAtom = atom((get) => get(authTokenAtom) !== null);

// Spheres atoms
export const spheresAtom = atom<Sphere[]>([]);
export const selectedSphereIdAtom = atomWithStorage<string | null>('flashit_selected_sphere', null);
export const selectedSphereAtom = atom((get) => {
  const spheres = get(spheresAtom);
  const selectedId = get(selectedSphereIdAtom);
  return spheres.find((s) => s.id === selectedId) || null;
});

// Moments atoms
export const momentsAtom = atom<Moment[]>([]);
export const selectedMomentIdAtom = atom<string | null>(null);
export const selectedMomentAtom = atom((get) => {
  const moments = get(momentsAtom);
  const selectedId = get(selectedMomentIdAtom);
  return moments.find((m) => m.id === selectedId) || null;
});

// UI state atoms
export const isCaptureModalOpenAtom = atom(false);
export const isCreateSphereModalOpenAtom = atom(false);
export const searchQueryAtom = atom('');
export const selectedTagsAtom = atom<string[]>([]);
export const selectedEmotionsAtom = atom<string[]>([]);

