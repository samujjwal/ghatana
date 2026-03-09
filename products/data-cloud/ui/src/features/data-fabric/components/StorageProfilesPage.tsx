/**
 * Storage profiles page.
 *
 * Admin interface for managing storage profiles with create, edit, delete operations.
 *
 * @doc.type page
 * @doc.purpose Storage profile administration
 * @doc.layer product
 * @doc.pattern Container Component
 */

import React, { useEffect, useState } from "react";
import { useAtom } from "jotai";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import {
  allStorageProfilesAtom,
  loadStorageProfilesAtom,
  deleteStorageProfileAtom,
  setDefaultStorageProfileAtom,
} from "../stores/storage-profile.store";
import { StorageProfilesList } from "../components/StorageProfilesList";
import { storageProfileApi } from "../services/api";
import type { StorageProfile } from "../types";

interface StorageProfilesPageProps {
  onCreateClick: () => void;
  onEditClick: (profile: StorageProfile) => void;
}

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
  const [, deleteProfile] = useAtom(deleteStorageProfileAtom);
  const [, setDefault] = useAtom(setDefaultStorageProfileAtom);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      try {
        setIsLoading(true);
        const data = await storageProfileApi.getAll();
        await loadProfiles(data);
      } catch (error) {
        toast.error(
          `Failed to load storage profiles: ${
            error instanceof Error ? error.message : "Unknown error"
          }`
        );
      } finally {
        setIsLoading(false);
      }
    };

    loadData();
  }, [loadProfiles]);

  const handleDelete = async (profileId: string) => {
    if (
      !confirm(
        "Are you sure you want to delete this storage profile? This action cannot be undone."
      )
    ) {
      return;
    }

    try {
      await storageProfileApi.delete(profileId);
      await deleteProfile(profileId);
      toast.success("Storage profile deleted successfully");
    } catch (error) {
      toast.error(
        `Failed to delete profile: ${
          error instanceof Error ? error.message : "Unknown error"
        }`
      );
    }
  };

  const handleSetDefault = async (profileId: string) => {
    try {
      await storageProfileApi.setDefault(profileId);
      await setDefault(profileId);
      toast.success("Default profile updated");
    } catch (error) {
      toast.error(
        `Failed to set default profile: ${
          error instanceof Error ? error.message : "Unknown error"
        }`
      );
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Storage Profiles</h1>
          <p className="mt-1 text-sm text-gray-600">
            Manage storage backends and data repository configurations
          </p>
        </div>
        <button
          onClick={onCreateClick}
          className="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus size={16} className="mr-2" />
          New Profile
        </button>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
        </div>
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
            No storage profiles yet
          </h3>
          <p className="mt-2 text-sm text-gray-600">
            Create your first storage profile to connect to cloud or local storage systems.
          </p>
          <button
            onClick={onCreateClick}
            className="mt-4 inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus size={16} className="mr-2" />
            Create First Profile
          </button>
        </div>
      )}
    </div>
  );
};
