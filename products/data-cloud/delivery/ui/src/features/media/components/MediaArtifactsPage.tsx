/**
 * Media artifacts page.
 *
 * Admin interface for managing media artifacts with register, transcribe, analyze, and delete operations.
 *
 * G20: Media artifacts page with low-cognitive-load design.
 *
 * @doc.type page
 * @doc.purpose Media artifact administration
 * @doc.layer product
 * @doc.pattern Container Component
 */

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAtom } from "jotai";
import { Filter, Lock, Plus, Search, ServerOff } from "lucide-react";
import React from "react";
import { toast } from "sonner";
import { LoadingState } from "../../../components/common/LoadingState";
import { mediaApi } from "../services/api";
import {
  allMediaArtifactsAtom,
  deleteMediaArtifactAtom,
  loadMediaArtifactsAtom,
  mediaErrorAtom,
  mediaLoadingAtom,
  setMediaErrorAtom,
  upsertAnalysisJobAtom,
  upsertTranscriptionJobAtom,
} from "../stores/media.store";
import type { MediaArtifact } from "../types";

interface MediaArtifactsPageProps {
  onRegisterClick: () => void;
  onDetailsClick: (artifact: MediaArtifact) => void;
}

/**
 * G20: i18n keys for user-visible text
 */
const I18N_KEYS = {
  title: "mediaArtifacts.title",
  description: "mediaArtifacts.description",
  registerArtifact: "mediaArtifacts.registerArtifact",
  searchPlaceholder: "mediaArtifacts.searchPlaceholder",
  filter: "mediaArtifacts.filter",
  loading: "mediaArtifacts.loading",
  error: "mediaArtifacts.error",
  unauthorized: "mediaArtifacts.unauthorized",
  unavailable: "mediaArtifacts.unavailable",
  deleteSuccess: "mediaArtifacts.deleteSuccess",
  deleteFailed: "mediaArtifacts.deleteFailed",
  deleteConfirm: "mediaArtifacts.deleteConfirm",
  transcribeSuccess: "mediaArtifacts.transcribeSuccess",
  transcribeFailed: "mediaArtifacts.transcribeFailed",
  analyzeSuccess: "mediaArtifacts.analyzeSuccess",
  analyzeFailed: "mediaArtifacts.analyzeFailed",
  noArtifactsTitle: "mediaArtifacts.noArtifactsTitle",
  noArtifactsDescription: "mediaArtifacts.noArtifactsDescription",
  registerFirstArtifact: "mediaArtifacts.registerFirstArtifact",
} as const;

function mediaErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  if (typeof error === "object" && error !== null) {
    const apiError = error as {
      code?: string;
      message?: string;
      status?: number;
      surfaceDegraded?: boolean;
      surfaceUnavailable?: boolean;
    };
    const status = apiError.status ? `${apiError.status}` : "";
    const availability = apiError.surfaceUnavailable
      ? " unavailable"
      : apiError.surfaceDegraded
        ? " degraded"
        : "";
    return [status, apiError.code, apiError.message, availability]
      .filter(Boolean)
      .join(" ");
  }

  return String(error);
}

function isAudioArtifact(mediaType: string): boolean {
  return mediaType === "audio" || mediaType.startsWith("audio/");
}

function isVisualArtifact(mediaType: string): boolean {
  return (
    mediaType === "image" ||
    mediaType === "video" ||
    mediaType.startsWith("image/") ||
    mediaType.startsWith("video/")
  );
}

/**
 * Media artifacts admin page.
 *
 * Displays list of media artifacts with ability to register, transcribe, analyze, and delete.
 */
export const MediaArtifactsPage: React.FC<MediaArtifactsPageProps> = ({
  onRegisterClick,
  onDetailsClick,
}) => {
  const [, loadArtifacts] = useAtom(loadMediaArtifactsAtom);
  const [artifacts] = useAtom(allMediaArtifactsAtom);
  const [isLoading] = useAtom(mediaLoadingAtom);
  const [error] = useAtom(mediaErrorAtom);
  const [, deleteArtifact] = useAtom(deleteMediaArtifactAtom);
  const [, setMediaError] = useAtom(setMediaErrorAtom);
  const [, addTranscriptionJob] = useAtom(upsertTranscriptionJobAtom);
  const [, addAnalysisJob] = useAtom(upsertAnalysisJobAtom);
  const queryClient = useQueryClient();

  const {
    isError,
    error: queryError,
    isLoading: isQueryLoading,
  } = useQuery({
    queryKey: ["media-artifacts"],
    staleTime: 30_000,
    queryFn: async () => {
      const data = await mediaApi.getAll();
      await loadArtifacts(data.items);
      return data;
    },
    throwOnError: false,
  });

  React.useEffect(() => {
    if (isError) {
      toast.error(I18N_KEYS.error);
      setMediaError(mediaErrorMessage(queryError));
    }
  }, [isError, queryError, setMediaError]);

  const handleDelete = async (artifactId: string) => {
    if (!confirm(I18N_KEYS.deleteConfirm)) {
      return;
    }

    try {
      await mediaApi.delete(artifactId);
      await deleteArtifact(artifactId);
      queryClient.invalidateQueries({ queryKey: ["media-artifacts"] });
      toast.success(I18N_KEYS.deleteSuccess);
    } catch (error) {
      const apiError = error as { code?: string; message?: string };
      if (
        apiError?.code === "FEATURE_UNAVAILABLE" ||
        apiError?.code === "SURFACE_DEGRADED"
      ) {
        toast.error(apiError.message || I18N_KEYS.unavailable);
      } else {
        toast.error(
          `${I18N_KEYS.deleteFailed}: ${apiError?.message || "Unknown error"}`,
        );
      }
    }
  };

  const handleTranscribe = async (artifactId: string) => {
    try {
      const result = await mediaApi.transcribe(artifactId);
      toast.success(`${I18N_KEYS.transcribeSuccess} (Job ID: ${result.jobId})`);
      await addTranscriptionJob({
        jobId: result.jobId,
        artifactId,
        status: result.status,
        estimatedDurationMs: result.estimatedDurationMs,
        createdAt: new Date().toISOString(),
      });
    } catch (error) {
      const apiError = error as { code?: string; message?: string };
      if (
        apiError?.code === "FEATURE_UNAVAILABLE" ||
        apiError?.code === "SURFACE_DEGRADED"
      ) {
        toast.error(apiError.message || I18N_KEYS.unavailable);
      } else {
        toast.error(
          `${I18N_KEYS.transcribeFailed}: ${
            apiError?.message || "Unknown error"
          }`,
        );
      }
    }
  };

  const handleAnalyze = async (artifactId: string) => {
    try {
      const result = await mediaApi.analyze(artifactId, {
        analysisType: "object_detection",
      });
      toast.success(`${I18N_KEYS.analyzeSuccess} (Job ID: ${result.jobId})`);
      await addAnalysisJob({
        jobId: result.jobId,
        artifactId,
        status: result.status,
        analysisType: "object_detection",
        estimatedDurationMs: result.estimatedDurationMs,
        createdAt: new Date().toISOString(),
      });
    } catch (error) {
      const apiError = error as { code?: string; message?: string };
      if (
        apiError?.code === "FEATURE_UNAVAILABLE" ||
        apiError?.code === "SURFACE_DEGRADED"
      ) {
        toast.error(apiError.message || I18N_KEYS.unavailable);
      } else {
        toast.error(
          `${I18N_KEYS.analyzeFailed}: ${apiError?.message || "Unknown error"}`,
        );
      }
    }
  };

  // G20: Handle unauthorized/unavailable states
  if (error?.includes("401") || error?.includes("403")) {
    return (
      <div className="flex items-center justify-center py-12 bg-gray-50 rounded-lg border border-gray-200">
        <Lock className="w-12 h-12 text-gray-400 mb-4" />
        <div className="text-center">
          <h3 className="text-lg font-medium text-gray-900">
            {I18N_KEYS.unauthorized}
          </h3>
          <p className="mt-2 text-sm text-gray-600">
            You do not have permission to access media artifacts.
          </p>
        </div>
      </div>
    );
  }

  if (error?.includes("503") || error?.includes("unavailable")) {
    return (
      <div className="flex items-center justify-center py-12 bg-gray-50 rounded-lg border border-gray-200">
        <ServerOff className="w-12 h-12 text-gray-400 mb-4" />
        <div className="text-center">
          <h3 className="text-lg font-medium text-gray-900">
            {I18N_KEYS.unavailable}
          </h3>
          <p className="mt-2 text-sm text-gray-600">
            Media artifact services are temporarily unavailable. Please try
            again later.
          </p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center py-12 bg-gray-50 rounded-lg border border-gray-200">
        <div className="text-center">
          <h3 className="text-lg font-medium text-gray-900">
            {I18N_KEYS.error}
          </h3>
          <p className="mt-2 text-sm text-gray-600">{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">
            {I18N_KEYS.title}
          </h1>
          <p className="mt-1 text-sm text-gray-600">{I18N_KEYS.description}</p>
        </div>
        <button
          onClick={onRegisterClick}
          className="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus size={16} className="mr-2" />
          {I18N_KEYS.registerArtifact}
        </button>
      </div>

      {/* Search and Filter Bar */}
      <div className="flex items-center gap-4">
        <div className="relative flex-1">
          <Search
            className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400"
            size={16}
          />
          <input
            type="text"
            placeholder={I18N_KEYS.searchPlaceholder}
            className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
        </div>
        <button className="inline-flex items-center px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors">
          <Filter size={16} className="mr-2" />
          {I18N_KEYS.filter}
        </button>
      </div>

      {/* Content */}
      {isLoading || isQueryLoading ? (
        <LoadingState message={I18N_KEYS.loading} className="py-12" />
      ) : (
        <div className="w-full bg-white rounded-lg border border-gray-200">
          <table className="min-w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                  Artifact ID
                </th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                  Type
                </th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                  Size
                </th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                  Duration
                </th>
                <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
                  Created
                </th>
                <th className="px-6 py-3 text-right text-sm font-semibold text-gray-900">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody>
              {artifacts.length === 0 ? (
                <tr>
                  <td
                    colSpan={6}
                    className="px-6 py-8 text-center text-gray-500"
                  >
                    {I18N_KEYS.noArtifactsTitle}
                  </td>
                </tr>
              ) : (
                artifacts.map((artifact: MediaArtifact) => (
                  <tr
                    key={artifact.artifactId}
                    onClick={() => onDetailsClick(artifact)}
                    className="border-b border-gray-200 hover:bg-gray-50 cursor-pointer transition-colors"
                  >
                    <td className="px-6 py-4 text-sm font-medium text-gray-900">
                      {artifact.artifactId}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-700">
                      <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded text-xs font-medium">
                        {artifact.mediaType}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-700">
                      {(artifact.sizeBytes / 1024 / 1024).toFixed(2)} MB
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-700">
                      {artifact.durationMs
                        ? `${(artifact.durationMs / 1000).toFixed(2)}s`
                        : "-"}
                    </td>
                    <td className="px-6 py-4 text-sm text-gray-700">
                      {new Date(artifact.createdAt).toLocaleDateString()}
                    </td>
                    <td className="px-6 py-4 text-right">
                      <div className="flex justify-end gap-2">
                        {isAudioArtifact(artifact.mediaType) && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleTranscribe(artifact.artifactId);
                            }}
                            className="p-1 text-purple-600 hover:text-purple-900 hover:bg-purple-100 rounded"
                            title="Transcribe"
                            aria-label={`Transcribe artifact ${artifact.artifactId}`}
                          >
                            <Filter size={16} />
                          </button>
                        )}
                        {isVisualArtifact(artifact.mediaType) && (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleAnalyze(artifact.artifactId);
                            }}
                            className="p-1 text-green-600 hover:text-green-900 hover:bg-green-100 rounded"
                            title="Analyze"
                            aria-label={`Analyze artifact ${artifact.artifactId}`}
                          >
                            <Filter size={16} />
                          </button>
                        )}
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDelete(artifact.artifactId);
                          }}
                          className="p-1 text-red-600 hover:text-red-900 hover:bg-red-100 rounded"
                          title="Delete"
                          aria-label={`Delete artifact ${artifact.artifactId}`}
                        >
                          <Plus size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Empty state */}
      {!isLoading && artifacts.length === 0 && (
        <div className="text-center py-12 bg-gray-50 rounded-lg border border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            {I18N_KEYS.noArtifactsTitle}
          </h3>
          <p className="mt-2 text-sm text-gray-600">
            {I18N_KEYS.noArtifactsDescription}
          </p>
          <button
            onClick={onRegisterClick}
            className="mt-4 inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus size={16} className="mr-2" />
            {I18N_KEYS.registerFirstArtifact}
          </button>
        </div>
      )}
    </div>
  );
};
