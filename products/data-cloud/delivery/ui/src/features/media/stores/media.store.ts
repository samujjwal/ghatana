/**
 * Jotai store for media artifact state management.
 *
 * Manages media artifacts with app-scoped state, supporting CRUD operations,
 * selection, filtering, and job tracking.
 *
 * G19: Updated to add explicit state management (idle, loading, loaded, empty, error).
 *
 * @doc.type store
 * @doc.purpose Media artifact state management
 * @doc.layer product
 * @doc.pattern State Store
 */

import { atom, Getter, Setter } from "jotai";
import type {
  AnalysisJob,
  MediaArtifact,
  MediaArtifactListQuery,
  TranscriptionJob,
} from "../types";

/**
 * G19: Explicit store states for better state management
 */
export type MediaStoreState =
  | "idle"
  | "loading"
  | "loaded"
  | "empty"
  | "error";

/**
 * Media artifact state container.
 */
type MediaState = {
  artifacts: MediaArtifact[];
  selectedArtifactId: string | null;
  storeState: MediaStoreState;
  error: string | null;
  transcriptionJobs: Record<string, TranscriptionJob>;
  analysisJobs: Record<string, AnalysisJob>;
  query: MediaArtifactListQuery;
};

const initialState: MediaState = {
  artifacts: [],
  selectedArtifactId: null,
  storeState: "idle",
  error: null,
  transcriptionJobs: {},
  analysisJobs: {},
  query: {},
};

/**
 * Core media artifact atom.
 *
 * Holds all artifacts and selection state.
 */
export const mediaArtifactAtom = atom<MediaState>(initialState);

/**
 * Derived atom: all artifacts.
 */
export const allMediaArtifactsAtom = atom(
  (get: Getter) => get(mediaArtifactAtom).artifacts,
);

/**
 * Derived atom: selected artifact.
 */
export const selectedMediaArtifactAtom = atom((get: Getter) => {
  const state = get(mediaArtifactAtom);
  return (
    state.artifacts.find(
      (a: MediaArtifact) => a.artifactId === state.selectedArtifactId,
    ) ?? null
  );
});

/**
 * Derived atom: artifacts by media type.
 */
export const artifactsByMediaTypeAtom = atom((get: Getter) => {
  const artifacts = get(allMediaArtifactsAtom);
  return artifacts.reduce(
    (acc: Record<string, MediaArtifact[]>, artifact: MediaArtifact) => {
      if (!acc[artifact.mediaType]) {
        acc[artifact.mediaType] = [];
      }
      acc[artifact.mediaType].push(artifact);
      return acc;
    },
    {} as Record<string, MediaArtifact[]>,
  );
});

/**
 * Derived atom: loading state.
 */
export const mediaLoadingAtom = atom(
  (get: Getter) => get(mediaArtifactAtom).storeState === "loading",
);

/**
 * Derived atom: error state.
 */
export const mediaErrorAtom = atom(
  (get: Getter) => get(mediaArtifactAtom).error,
);

/**
 * Derived atom: transcription jobs for selected artifact.
 */
export const selectedArtifactTranscriptionJobsAtom = atom(
  (get: Getter) => {
    const state = get(mediaArtifactAtom);
    if (!state.selectedArtifactId) return [];
    return Object.values(state.transcriptionJobs).filter(
      (job) => job.artifactId === state.selectedArtifactId,
    );
  },
);

/**
 * Derived atom: analysis jobs for selected artifact.
 */
export const selectedArtifactAnalysisJobsAtom = atom((get: Getter) => {
  const state = get(mediaArtifactAtom);
  if (!state.selectedArtifactId) return [];
  return Object.values(state.analysisJobs).filter(
    (job) => job.artifactId === state.selectedArtifactId,
  );
});

/**
 * Action atom: load artifacts.
 */
export const loadMediaArtifactsAtom = atom(
  null,
  async (get: Getter, set: Setter, artifacts: MediaArtifact[]) => {
    set(mediaArtifactAtom, (prev: MediaState) => ({
      ...prev,
      artifacts,
      storeState: artifacts.length === 0 ? "empty" : "loaded",
      error: null,
    }));
  },
);

/**
 * Action atom: set loading state.
 */
export const setMediaLoadingAtom = atom(
  null,
  (get: Getter, set: Setter, isLoading: boolean) => {
    set(mediaArtifactAtom, (prev: MediaState) => ({
      ...prev,
      storeState: isLoading ? "loading" : prev.storeState,
    }));
  },
);

/**
 * Action atom: set error state.
 */
export const setMediaErrorAtom = atom(
  null,
  (get: Getter, set: Setter, error: string | null) => {
    set(mediaArtifactAtom, (prev: MediaState) => ({
      ...prev,
      storeState: error ? "error" : prev.storeState,
      error,
    }));
  },
);

/**
 * Action atom: select an artifact.
 */
export const selectMediaArtifactAtom = atom(
  null,
  (get: Getter, set: Setter, artifactId: string | null) => {
    set(mediaArtifactAtom, (prev: MediaState) => ({
      ...prev,
      selectedArtifactId: artifactId,
    }));
  },
);

/**
 * Action atom: add a new artifact.
 */
export const addMediaArtifactAtom = atom(
  null,
  (get: Getter, set: Setter, artifact: MediaArtifact) => {
    set(mediaArtifactAtom, (prev: MediaState) => ({
      ...prev,
      artifacts: [...prev.artifacts, artifact],
    }));
  },
);

/**
 * Action atom: update an existing artifact.
 */
export const updateMediaArtifactAtom = atom(
  null,
  (get: Getter, set: Setter, artifact: MediaArtifact) => {
    set(mediaArtifactAtom, (prev: MediaState) => ({
      ...prev,
      artifacts: prev.artifacts.map((a: MediaArtifact) =>
        a.artifactId === artifact.artifactId ? artifact : a,
      ),
    }));
  },
);

/**
 * Action atom: delete an artifact.
 */
export const deleteMediaArtifactAtom = atom(
  null,
  (get: Getter, set: Setter, artifactId: string) => {
    set(mediaArtifactAtom, (prev: MediaState) => ({
      ...prev,
      artifacts: prev.artifacts.filter((a: MediaArtifact) => a.artifactId !== artifactId),
      selectedArtifactId:
        prev.selectedArtifactId === artifactId
          ? null
          : prev.selectedArtifactId,
    }));
  },
);

/**
 * Action atom: add or update transcription job.
 */
export const upsertTranscriptionJobAtom = atom(
  null,
  (get: Getter, set: Setter, job: TranscriptionJob) => {
    set(mediaArtifactAtom, (prev: MediaState) => ({
      ...prev,
      transcriptionJobs: {
        ...prev.transcriptionJobs,
        [job.jobId]: job,
      },
    }));
  },
);

/**
 * Action atom: add or update analysis job.
 */
export const upsertAnalysisJobAtom = atom(
  null,
  (get: Getter, set: Setter, job: AnalysisJob) => {
    set(mediaArtifactAtom, (prev: MediaState) => ({
      ...prev,
      analysisJobs: {
        ...prev.analysisJobs,
        [job.jobId]: job,
      },
    }));
  },
);

/**
 * Action atom: set query filters.
 */
export const setMediaQueryAtom = atom(
  null,
  (get: Getter, set: Setter, query: MediaArtifactListQuery) => {
    set(mediaArtifactAtom, (prev: MediaState) => ({
      ...prev,
      query,
    }));
  },
);

/**
 * Action atom: reset to initial state.
 */
export const resetMediaAtom = atom(null, (get: Getter, set: Setter) => {
  set(mediaArtifactAtom, initialState);
});
