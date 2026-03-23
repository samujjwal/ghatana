import { atom } from 'jotai';

// Config selection state
export const selectedDomainIdAtom = atom<string | null>(null);
export const selectedWorkflowIdAtom = atom<string | null>(null);
export const selectedPhaseIdAtom = atom<string | null>(null);
export const selectedTaskIdAtom = atom<string | null>(null);

// Filter state
export const domainFilterAtom = atom<{
    search: string;
    category?: string;
    tags?: string[];
}>({
    search: '',
});

export const workflowFilterAtom = atom<{
    search: string;
    category?: string;
}>({
    search: '',
});

// UI state
export const configPanelOpenAtom = atom(false);
export const configViewModeAtom = atom<'list' | 'grid' | 'detail'>('list');

// Derived atoms for current selections
export const selectedDomainAtom = atom(
    (get) => get(selectedDomainIdAtom)
);

export const selectedWorkflowAtom = atom(
    (get) => get(selectedWorkflowIdAtom)
);

export const selectedPhaseAtom = atom(
    (get) => get(selectedPhaseIdAtom)
);

export const selectedTaskAtom = atom(
    (get) => get(selectedTaskIdAtom)
);

// Config loading state
export const configLoadingAtom = atom(false);
export const configErrorAtom = atom<string | null>(null);
