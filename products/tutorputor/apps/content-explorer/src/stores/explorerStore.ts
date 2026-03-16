import { atom } from "jotai";
import type { ContentFilters, ContentType, DifficultyLevel } from "@/types/content";
import { EMPTY_FILTERS } from "@/types/content";

// ─────────────────────────────────────────────────────────────────────────────
// Explorer filter state
// ─────────────────────────────────────────────────────────────────────────────

export const filtersAtom = atom<ContentFilters>(EMPTY_FILTERS);

export const currentPageAtom = atom<number>(1);
export const pageSizeAtom = atom<number>(24);

// Derived: whether any non-default filters are active
export const hasActiveFiltersAtom = atom((get) => {
  const f = get(filtersAtom);
  return (
    f.search !== "" ||
    f.types.length > 0 ||
    f.statuses.length > 0 ||
    f.subjects.length > 0 ||
    f.gradeLevels.length > 0 ||
    f.difficulties.length > 0 ||
    f.aiGeneratedOnly ||
    f.minQualityScore !== null
  );
});

// ─────────────────────────────────────────────────────────────────────────────
// Generation form state
// ─────────────────────────────────────────────────────────────────────────────

export interface GenerationFormState {
  type: ContentType;
  subject: string;
  topic: string;
  gradeLevel: string;
  difficulty: DifficultyLevel;
  learningObjectives: string[];
  additionalInstructions: string;
}

export const generationFormAtom = atom<GenerationFormState>({
  type: "lesson",
  subject: "",
  topic: "",
  gradeLevel: "Grade 8",
  difficulty: "intermediate",
  learningObjectives: [""],
  additionalInstructions: "",
});

// Active generation job ID (for polling)
export const activeJobIdAtom = atom<string | null>(null);

// Template prefill: written by TemplatesPage, consumed + cleared by GeneratePage
export const templatePrefillAtom = atom<Partial<GenerationFormState> | null>(null);

// ─────────────────────────────────────────────────────────────────────────────
// UI state
// ─────────────────────────────────────────────────────────────────────────────

export type ViewMode = "grid" | "list";
export const viewModeAtom = atom<ViewMode>("grid");

export const selectedContentIdAtom = atom<string | null>(null);
