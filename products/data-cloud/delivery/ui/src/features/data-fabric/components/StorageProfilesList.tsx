/**
 * Storage profiles list component.
 *
 * Displays a paginated table of storage profiles with actions (edit, delete, set default).
 *
 * @doc.type component
 * @doc.purpose Display storage profiles
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React from "react";
import { useAtom } from "jotai";
import { Trash2, Edit, CheckCircle } from "lucide-react";
import clsx from "clsx";
import type { StorageProfile } from "../types";
import {
  allStorageProfilesAtom,
  selectStorageProfileAtom,
} from "../stores/storage-profile.store";

interface StorageProfilesListProps {
  onEdit: (profile: StorageProfile) => void;
  onDelete: (profileId: string) => void;
  onSetDefault: (profileId: string) => void;
}

/**
 * Storage profiles list component.
 *
 * Renders a table showing all available storage profiles with their types,
 * status, and available actions.
 */
export const StorageProfilesList: React.FC<StorageProfilesListProps> = ({
  onEdit,
  onDelete,
  onSetDefault,
}) => {
  const [profiles] = useAtom(allStorageProfilesAtom);
  const [, selectProfile] = useAtom(selectStorageProfileAtom);

  const handleRowClick = (profile: StorageProfile) => {
    selectProfile(profile.id);
  };

  return (
    <div className="w-full bg-white rounded-lg border border-gray-200">
      <table className="min-w-full">
        <thead className="bg-gray-50 border-b border-gray-200">
          <tr>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              Name
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              Type
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              Status
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              Default
            </th>
            <th className="px-6 py-3 text-right text-sm font-semibold text-gray-900">
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {profiles.length === 0 ? (
            <tr>
              <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                No storage profiles configured yet. Create one to get started.
              </td>
            </tr>
          ) : (
            profiles.map((profile: StorageProfile) => (
              <tr
                key={profile.id}
                onClick={() => handleRowClick(profile)}
                className="border-b border-gray-200 hover:bg-gray-50 cursor-pointer transition-colors"
              >
                <td className="px-6 py-4 text-sm font-medium text-gray-900">
                  {profile.name}
                </td>
                <td className="px-6 py-4 text-sm text-gray-700">
                  <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded text-xs font-medium">
                    {profile.type}
                  </span>
                </td>
                <td className="px-6 py-4 text-sm text-gray-700">
                  <span
                    className={clsx(
                      "px-2 py-1 rounded text-xs font-medium",
                      profile.isActive
                        ? "bg-green-100 text-green-800"
                        : "bg-gray-100 text-gray-800"
                    )}
                  >
                    {profile.isActive ? "Active" : "Inactive"}
                  </span>
                </td>
                <td className="px-6 py-4 text-sm">
                  {profile.isDefault ? (
                    <div className="flex items-center text-green-700">
                      <CheckCircle size={16} className="mr-1" />
                      <span className="text-xs font-medium">Default</span>
                    </div>
                  ) : (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onSetDefault(profile.id);
                      }}
                      className="text-xs text-gray-600 hover:text-gray-900 underline"
                    >
                      Set as default
                    </button>
                  )}
                </td>
                <td className="px-6 py-4 text-right">
                  <div className="flex justify-end gap-2">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onEdit(profile);
                      }}
                      className="p-1 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded"
                      title="Edit profile"
                    >
                      <Edit size={16} />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(profile.id);
                      }}
                      className="p-1 text-red-600 hover:text-red-900 hover:bg-red-100 rounded"
                      title="Delete profile"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
};
