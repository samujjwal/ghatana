/**
 * Jotai store for media artifact state management.
 *
 * Manages media artifacts with app-scoped state, supporting CRUD operations,
 * selection, and processing job tracking.
 *
 * G19: Media artifact store with explicit state management.
 *
 * @doc.type store
 * @doc.purpose Media artifact state management
 * @doc.layer product
 * @doc.pattern State Store
 */

import { atom, Getter, Setter } from "jotai";
import type { AnalysisJob, MediaArtifact, TranscriptionJob } from "../types";

/**
 * G19: Explicit store states for better state management
 */
export type MediaArtifactStoreState =
  | "idle"
  | "loading"
  | "loaded"
  | "empty"
  | "error";

/**
 * Media artifact state container.
 */
type MediaArtifactState = {
  artifacts: MediaArtifact[];
  selectedArtifactId: string | null;
  storeState: MediaArtifactStoreState;
  error: string | null;
  transcriptionJobs: Record<string, TranscriptionJob>;
  analysisJobs: Record<string, AnalysisJob>;
};

const initialState: MediaArtifactState = {
  artifacts: [],
  selectedArtifactId: null,
  storeState: "idle",
  error: null,
  transcriptionJobs: {},
  analysisJobs: {},
};

/**
 * Core media artifact atom.
 *
 * Holds all media artifacts and selection state.
 */
export const mediaArtifactAtom = atom<MediaArtifactState>(initialState);

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
 * Derived atom: artifacts grouped by media type.
 */
export const artifactsByTypeAtom = atom((get: Getter) => {
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
 * Derived atom: artifacts grouped by agent ID.
 */
export const artifactsByAgentAtom = atom((get: Getter) => {
  const artifacts = get(allMediaArtifactsAtom);
  return artifacts.reduce(
    (acc: Record<string, MediaArtifact[]>, artifact: MediaArtifact) => {
      if (!acc[artifact.agentId]) {
        acc[artifact.agentId] = [];
      }
      acc[artifact.agentId].push(artifact);
      return acc;
    },
    {} as Record<string, MediaArtifact[]>,
  );
});

/**
 * Derived atom: loading state.
 */
export const mediaArtifactLoadingAtom = atom(
  (get: Getter) => get(mediaArtifactAtom).storeState === "loading",
);

/**
 * Derived atom: error state.
 */
export const mediaArtifactErrorAtom = atom(
  (get: Getter) => get(mediaArtifactAtom).error,
);

/**
 * Derived atom: transcription jobs for selected artifact.
 */
export const selectedArtifactTranscriptionJobsAtom = atom((get: Getter) => {
  const state = get(mediaArtifactAtom);
  if (!state.selectedArtifactId) return [];
  return Object.values(state.transcriptionJobs).filter(
    (job: TranscriptionJob) => job.artifactId === state.selectedArtifactId,
  );
});

/**
 * Derived atom: analysis jobs for selected artifact.
 */
export const selectedArtifactAnalysisJobsAtom = atom((get: Getter) => {
  const state = get(mediaArtifactAtom);
  if (!state.selectedArtifactId) return [];
  return Object.values(state.analysisJobs).filter(
    (job: AnalysisJob) => job.artifactId === state.selectedArtifactId,
  );
});

/**
 * Action atom: load media artifacts.
 */
export const loadMediaArtifactsAtom = atom(
  null,
  async (get: Getter, set: Setter, artifacts: MediaArtifact[]) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      artifacts,
      storeState: artifacts.length === 0 ? "empty" : "loaded",
      error: null,
    }));
  },
);

/**
 * Action atom: add media artifact.
 */
export const addMediaArtifactAtom = atom(
  null,
  (get: Getter, set: Setter, artifact: MediaArtifact) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      artifacts: [...prev.artifacts, artifact],
      storeState: "loaded",
    }));
  },
);

/**
 * Action atom: update media artifact.
 */
export const updateMediaArtifactAtom = atom(
  null,
  (get: Getter, set: Setter, artifact: MediaArtifact) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      artifacts: prev.artifacts.map((a: MediaArtifact) =>
        a.artifactId === artifact.artifactId ? artifact : a,
      ),
    }));
  },
);

/**
 * Action atom: delete media artifact.
 */
export const deleteMediaArtifactAtom = atom(
  null,
  (get: Getter, set: Setter, artifactId: string) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      artifacts: prev.artifacts.filter(
        (a: MediaArtifact) => a.artifactId !== artifactId,
      ),
      selectedArtifactId:
        prev.selectedArtifactId === artifactId ? null : prev.selectedArtifactId,
      storeState: prev.artifacts.length === 1 ? "empty" : "loaded",
    }));
  },
);

/**
 * Action atom: select media artifact.
 */
export const selectMediaArtifactAtom = atom(
  null,
  (get: Getter, set: Setter, artifactId: string) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      selectedArtifactId: artifactId,
    }));
  },
);

/**
 * Action atom: set loading state.
 */
export const setMediaArtifactLoadingAtom = atom(
  null,
  (get: Getter, set: Setter, isLoading: boolean) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      storeState: isLoading ? "loading" : prev.storeState,
    }));
  },
);

/**
 * Action atom: set error state.
 */
export const setMediaArtifactErrorAtom = atom(
  null,
  (get: Getter, set: Setter, error: string | null) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      error,
      storeState: error ? "error" : prev.storeState,
    }));
  },
);

/**
 * Action atom: add transcription job.
 */
export const addTranscriptionJobAtom = atom(
  null,
  (get: Getter, set: Setter, job: TranscriptionJob) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      transcriptionJobs: {
        ...prev.transcriptionJobs,
        [job.jobId]: job,
      },
    }));
  },
);

/**
 * Action atom: update transcription job.
 */
export const updateTranscriptionJobAtom = atom(
  null,
  (get: Getter, set: Setter, job: TranscriptionJob) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      transcriptionJobs: {
        ...prev.transcriptionJobs,
        [job.jobId]: job,
      },
    }));
  },
);

/**
 * Action atom: add analysis job.
 */
export const addAnalysisJobAtom = atom(
  null,
  (get: Getter, set: Setter, job: AnalysisJob) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      analysisJobs: {
        ...prev.analysisJobs,
        [job.jobId]: job,
      },
    }));
  },
);

/**
 * Action atom: update analysis job.
 */
export const updateAnalysisJobAtom = atom(
  null,
  (get: Getter, set: Setter, job: AnalysisJob) => {
    set(mediaArtifactAtom, (prev: MediaArtifactState) => ({
      ...prev,
      analysisJobs: {
        ...prev.analysisJobs,
        [job.jobId]: job,
      },
    }));
  },
);
