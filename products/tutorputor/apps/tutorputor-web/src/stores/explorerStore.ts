import { atom } from 'jotai';

export interface ExplorerFilters {
  domain?: string;
  difficulty?: string;
  tags?: string[];
  searchQuery?: string;
}

export interface ExplorerState {
  filters: ExplorerFilters;
  selectedModuleId?: string;
  viewMode: 'grid' | 'list';
}

export const explorerFiltersAtom = atom<ExplorerFilters>({});
export const selectedModuleIdAtom = atom<string | undefined>(undefined);
export const viewModeAtom = atom<'grid' | 'list'>('grid');

export const explorerStateAtom = atom<ExplorerState>((get) => ({
  filters: get(explorerFiltersAtom),
  selectedModuleId: get(selectedModuleIdAtom),
  viewMode: get(viewModeAtom),
}));

export const currentPageAtom = atom<number>(1);
export const pageSizeAtom = atom<number>(20);

export interface GenerationForm {
  contentType: string;
  topic: string;
  difficulty?: string;
  domain?: string;
}

export const generationFormAtom = atom<GenerationForm>({
  contentType: 'lesson',
  topic: '',
});

export const templatePrefillAtom = atom<Partial<GenerationForm> | null>(null);
