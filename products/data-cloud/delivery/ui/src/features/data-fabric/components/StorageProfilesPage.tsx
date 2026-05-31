/**
 * Storage profiles page.
 *
 * Admin interface for managing storage profiles with create, edit, delete operations.
 *
 * G13: Updated to use store state, add i18n keys, and handle unauthorized/unavailable states.
 *
 * @doc.type page
 * @doc.purpose Storage profile administration
 * @doc.layer product
 * @doc.pattern Container Component
 */

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAtom } from "jotai";
import { Lock, Plus, ServerOff } from "lucide-react";
import React from "react";
import { toast } from "sonner";
import { LoadingState } from "../../../components/common/LoadingState";
import { StorageProfilesList } from "../components/StorageProfilesList";
import { storageProfileApi } from "../services/api";
import {
  allStorageProfilesAtom,
  deleteStorageProfileAtom,
  loadStorageProfilesAtom,
  setDefaultStorageProfileAtom,
  storageProfileErrorAtom,
  storageProfileLoadingAtom,
} from "../stores/storage-profile.store";
import type { StorageProfile } from "../types";

interface StorageProfilesPageProps {
  onCreateClick: () => void;
  onEditClick: (profile: StorageProfile) => void;
}

/**
 * G13: i18n keys for user-visible text
 */
const I18N_KEYS = {
  title: "storageProfiles.title",
  description: "storageProfiles.description",
  newProfile: "storageProfiles.newProfile",
  createFirstProfile: "storageProfiles.createFirstProfile",
  noProfilesTitle: "storageProfiles.noProfilesTitle",
  noProfilesDescription: "storageProfiles.noProfilesDescription",
  loading: "storageProfiles.loading",
  error: "storageProfiles.error",
  unauthorized: "storageProfiles.unauthorized",
  unavailable: "storageProfiles.unavailable",
  deleteSuccess: "storageProfiles.deleteSuccess",
  deleteFailed: "storageProfiles.deleteFailed",
  deleteConfirm: "storageProfiles.deleteConfirm",
  setDefaultSuccess: "storageProfiles.setDefaultSuccess",
  setDefaultFailed: "storageProfiles.setDefaultFailed",
} as const;

/**
 * Storage profiles admin page.
 *
 * Displays list of storage profiles with ability to create, edit, delete, and set defaults.
 */
export const StorageProfilesPage: React.FC<StorageProfilesPageProps> = ({
  onCreateClick,
  onEditClick,
}) => {
  const [, loadProfiles] = useAtom(loadStorageProfilesAtom);
  const [profiles] = useAtom(allStorageProfilesAtom);
  const [isLoading] = useAtom(storageProfileLoadingAtom);
  const [error] = useAtom(storageProfileErrorAtom);
  const [, deleteProfile] = useAtom(deleteStorageProfileAtom);
  const [, setDefault] = useAtom(setDefaultStorageProfileAtom);
  const queryClient = useQueryClient();

  const { isError } = useQuery({
    queryKey: ["storage-profiles"],
    staleTime: 30_000,
    queryFn: async () => {
      const data = await storageProfileApi.getAll();
      await loadProfiles(data);
      return data;
    },
    throwOnError: false,
  });

  if (isError) {
    toast.error(I18N_KEYS.error);
  }

  const handleDelete = async (profileId: string) => {
    if (!confirm(I18N_KEYS.deleteConfirm)) {
      return;
    }

    try {
      await storageProfileApi.delete(profileId);
      await deleteProfile(profileId);
      queryClient.invalidateQueries({ queryKey: ["storage-profiles"] });
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

  const handleSetDefault = async (profileId: string) => {
    try {
      await storageProfileApi.setDefault(profileId);
      await setDefault(profileId);
      toast.success(I18N_KEYS.setDefaultSuccess);
    } catch (error) {
      const apiError = error as { code?: string; message?: string };
      if (
        apiError?.code === "FEATURE_UNAVAILABLE" ||
        apiError?.code === "SURFACE_DEGRADED"
      ) {
        toast.error(apiError.message || I18N_KEYS.unavailable);
      } else {
        toast.error(
          `${I18N_KEYS.setDefaultFailed}: ${
            apiError?.message || "Unknown error"
          }`,
        );
      }
    }
  };

  // G13: Handle unauthorized/unavailable states
  if (error?.includes("401") || error?.includes("403")) {
    return (
      <div className="flex items-center justify-center py-12 bg-gray-50 rounded-lg border border-gray-200">
        <Lock className="w-12 h-12 text-gray-400 mb-4" />
        <div className="text-center">
          <h3 className="text-lg font-medium text-gray-900">
            {I18N_KEYS.unauthorized}
          </h3>
          <p className="mt-2 text-sm text-gray-600">
            You do not have permission to access storage profiles.
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
            Storage profiles are temporarily unavailable. Please try again
            later.
          </p>
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
          onClick={onCreateClick}
          className="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus size={16} className="mr-2" />
          {I18N_KEYS.newProfile}
        </button>
      </div>

      {/* Content */}
      {isLoading ? (
        <LoadingState message={I18N_KEYS.loading} className="py-12" />
      ) : (
        <StorageProfilesList
          onEdit={onEditClick}
          onDelete={handleDelete}
          onSetDefault={handleSetDefault}
        />
      )}

      {/* Empty state */}
      {!isLoading && profiles.length === 0 && (
        <div className="text-center py-12 bg-gray-50 rounded-lg border border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            {I18N_KEYS.noProfilesTitle}
          </h3>
          <p className="mt-2 text-sm text-gray-600">
            {I18N_KEYS.noProfilesDescription}
          </p>
          <button
            onClick={onCreateClick}
            className="mt-4 inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus size={16} className="mr-2" />
            {I18N_KEYS.createFirstProfile}
          </button>
        </div>
      )}
    </div>
  );
};
