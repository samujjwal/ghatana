/**
 * Requirement state atom
 *
 * @doc.type atom
 * @doc.purpose Track requirement list, selection and approval processing state
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';

import type { RequirementRecord, RequirementStatus } from '../../components/requirements/types';

export interface RequirementState {
  requirements: RequirementRecord[];
  selectedRequirementId: string | null;
  isSubmitting: boolean;
  isApproving: boolean;
  error?: string;
}

export const requirementAtom = atom<RequirementState>({
  requirements: [],
  selectedRequirementId: null,
  isSubmitting: false,
  isApproving: false,
});

export const selectedRequirementAtom = atom((get) => {
  const state = get(requirementAtom);
  return state.requirements.find((item) => item.id === state.selectedRequirementId) ?? null;
});

export const setRequirementsAtom = atom(
  null,
  (get, set, requirements: RequirementRecord[]) => {
    const current = get(requirementAtom);
    set(requirementAtom, {
      ...current,
      requirements,
      selectedRequirementId: current.selectedRequirementId ?? requirements[0]?.id ?? null,
      error: undefined,
    });
  }
);

export const selectRequirementAtom = atom(
  null,
  (get, set, requirementId: string | null) => {
    const current = get(requirementAtom);
    set(requirementAtom, {
      ...current,
      selectedRequirementId: requirementId,
    });
  }
);

export const updateRequirementStatusAtom = atom(
  null,
  (get, set, payload: { id: string; status: RequirementStatus }) => {
    const current = get(requirementAtom);
    set(requirementAtom, {
      ...current,
      requirements: current.requirements.map((item) =>
        item.id === payload.id ? { ...item, status: payload.status, updatedAt: new Date().toISOString() } : item
      ),
    });
  }
);

export const setRequirementSubmittingAtom = atom(
  null,
  (get, set, isSubmitting: boolean) => {
    const current = get(requirementAtom);
    set(requirementAtom, {
      ...current,
      isSubmitting,
    });
  }
);

export const setRequirementApprovingAtom = atom(
  null,
  (get, set, isApproving: boolean) => {
    const current = get(requirementAtom);
    set(requirementAtom, {
      ...current,
      isApproving,
    });
  }
);

export const setRequirementErrorAtom = atom(
  null,
  (get, set, error: string | undefined) => {
    const current = get(requirementAtom);
    set(requirementAtom, {
      ...current,
      error,
      isSubmitting: false,
      isApproving: false,
    });
  }
);