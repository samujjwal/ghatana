/**
 * Jotai store for storage profile state management.
 *
 * Manages storage profiles with app-scoped state, supporting CRUD operations,
 * selection, and metrics tracking.
 *
 * @doc.type store
 * @doc.purpose Storage profile state management
 * @doc.layer product
 * @doc.pattern State Store
 */

import { atom, Getter, Setter } from "jotai";
import type { StorageProfile, StorageMetrics } from "../types";

/**
 * Storage profile state container.
 */
type StorageProfileState = {
  profiles: StorageProfile[];
  selectedProfileId: string | null;
  isLoading: boolean;
  error: string | null;
  metrics: Record<string, StorageMetrics>;
};

const initialState: StorageProfileState = {
  profiles: [],
  selectedProfileId: null,
  isLoading: false,
  error: null,
  metrics: {},
};

/**
 * Core storage profile atom.
 *
 * Holds all storage profiles and selection state.
 */
export const storageProfileAtom = atom<StorageProfileState>(initialState);

/**
 * Derived atom: all profiles.
 */
export const allStorageProfilesAtom = atom((get: Getter) =>
  get(storageProfileAtom).profiles
);

/**
 * Derived atom: selected profile.
 */
export const selectedStorageProfileAtom = atom((get: Getter) => {
  const state = get(storageProfileAtom);
  return state.profiles.find((p: StorageProfile) => p.id === state.selectedProfileId) ?? null;
});

/**
 * Derived atom: profiles grouped by type.
 */
export const profilesByTypeAtom = atom((get: Getter) => {
  const profiles = get(allStorageProfilesAtom);
  return profiles.reduce(
    (acc: Record<string, StorageProfile[]>, profile: StorageProfile) => {
      if (!acc[profile.type]) {
        acc[profile.type] = [];
      }
      acc[profile.type].push(profile);
      return acc;
    },
    {} as Record<string, StorageProfile[]>
  );
});

/**
 * Derived atom: loading state.
 */
export const storageProfileLoadingAtom = atom(
  (get: Getter) => get(storageProfileAtom).isLoading
);

/**
 * Derived atom: error state.
 */
export const storageProfileErrorAtom = atom(
  (get: Getter) => get(storageProfileAtom).error
);

/**
 * Derived atom: storage metrics for selected profile.
 */
export const selectedProfileMetricsAtom = atom((get: Getter) => {
  const state = get(storageProfileAtom);
  if (!state.selectedProfileId) return null;
  return state.metrics[state.selectedProfileId] ?? null;
});

/**
 * Action atom: load profiles.
 */
export const loadStorageProfilesAtom = atom(
  null,
  async (get: Getter, set: Setter, profiles: StorageProfile[]) => {
    set(storageProfileAtom, (prev: StorageProfileState) => ({
      ...prev,
      profiles,
      isLoading: false,
      error: null,
    }));
  }
);

/**
 * Action atom: set loading state.
 */
export const setStorageProfileLoadingAtom = atom(
  null,
  (get: Getter, set: Setter, isLoading: boolean) => {
    set(storageProfileAtom, (prev: StorageProfileState) => ({
      ...prev,
      isLoading,
    }));
  }
);

/**
 * Action atom: set error state.
 */
export const setStorageProfileErrorAtom = atom(
  null,
  (get: Getter, set: Setter, error: string | null) => {
    set(storageProfileAtom, (prev: StorageProfileState) => ({
      ...prev,
      error,
    }));
  }
);

/**
 * Action atom: select a profile.
 */
export const selectStorageProfileAtom = atom(
  null,
  (get: Getter, set: Setter, profileId: string | null) => {
    set(storageProfileAtom, (prev: StorageProfileState) => ({
      ...prev,
      selectedProfileId: profileId,
    }));
  }
);

/**
 * Action atom: add a new profile.
 */
export const addStorageProfileAtom = atom(
  null,
  (get: Getter, set: Setter, profile: StorageProfile) => {
    set(storageProfileAtom, (prev: StorageProfileState) => ({
      ...prev,
      profiles: [...prev.profiles, profile],
    }));
  }
);

/**
 * Action atom: update an existing profile.
 */
export const updateStorageProfileAtom = atom(
  null,
  (get: Getter, set: Setter, profile: StorageProfile) => {
    set(storageProfileAtom, (prev: StorageProfileState) => ({
      ...prev,
      profiles: prev.profiles.map((p: StorageProfile) => (p.id === profile.id ? profile : p)),
    }));
  }
);

/**
 * Action atom: delete a profile.
 */
export const deleteStorageProfileAtom = atom(
  null,
  (get: Getter, set: Setter, profileId: string) => {
    set(storageProfileAtom, (prev: StorageProfileState) => ({
      ...prev,
      profiles: prev.profiles.filter((p: StorageProfile) => p.id !== profileId),
      selectedProfileId:
        prev.selectedProfileId === profileId ? null : prev.selectedProfileId,
    }));
  }
);

/**
 * Action atom: update storage metrics.
 */
export const updateStorageMetricsAtom = atom(
  null,
  (get: Getter, set: Setter, metrics: StorageMetrics) => {
    set(storageProfileAtom, (prev: StorageProfileState) => ({
      ...prev,
      metrics: {
        ...prev.metrics,
        [metrics.profileId]: metrics,
      },
    }));
  }
);

/**
 * Action atom: set default profile.
 */
export const setDefaultStorageProfileAtom = atom(
  null,
  (get: Getter, set: Setter, profileId: string) => {
    set(storageProfileAtom, (prev: StorageProfileState) => ({
      ...prev,
      profiles: prev.profiles.map((p: StorageProfile) => ({
        ...p,
        isDefault: p.id === profileId,
      })),
    }));
  }
);

/**
 * Action atom: reset to initial state.
 */
export const resetStorageProfileAtom = atom(null, (get: Getter, set: Setter) => {
  set(storageProfileAtom, initialState);
});
