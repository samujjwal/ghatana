/**
 * Storage profiles list component.
 *
 * Displays a paginated table of storage profiles with actions (edit, delete, set default).
 *
 * G14: Updated to add keyboard navigation, redaction indicators, and improved accessibility.
 *
 * @doc.type component
 * @doc.purpose Display storage profiles
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import clsx from "clsx";
import { useAtom } from "jotai";
import { CheckCircle, Edit, EyeOff, Trash2 } from "lucide-react";
import React, { useState } from "react";
import {
  allStorageProfilesAtom,
  selectStorageProfileAtom,
} from "../stores/storage-profile.store";
import type { StorageProfile } from "../types";

interface StorageProfilesListProps {
  onEdit: (profile: StorageProfile) => void;
  onDelete: (profileId: string) => void;
  onSetDefault: (profileId: string) => void;
}

/**
 * G14: i18n keys for user-visible text
 */
const I18N_KEYS = {
  name: "storageProfilesList.name",
  type: "storageProfilesList.type",
  status: "storageProfilesList.status",
  default: "storageProfilesList.default",
  actions: "storageProfilesList.actions",
  active: "storageProfilesList.active",
  inactive: "storageProfilesList.inactive",
  setAsDefault: "storageProfilesList.setAsDefault",
  editProfile: "storageProfilesList.editProfile",
  deleteProfile: "storageProfilesList.deleteProfile",
  noProfiles: "storageProfilesList.noProfiles",
  redacted: "storageProfilesList.redacted",
} as const;

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
  const [focusedIndex, setFocusedIndex] = useState<number | null>(null);

  const handleRowClick = (profile: StorageProfile, index: number) => {
    selectProfile(profile.id);
    setFocusedIndex(index);
  };

  const handleKeyDown = (
    e: React.KeyboardEvent,
    profile: StorageProfile,
    index: number,
  ) => {
    switch (e.key) {
      case "ArrowUp":
        e.preventDefault();
        if (index > 0) setFocusedIndex(index - 1);
        break;
      case "ArrowDown":
        e.preventDefault();
        if (index < profiles.length - 1) setFocusedIndex(index + 1);
        break;
      case "Enter":
        e.preventDefault();
        onEdit(profile);
        break;
      case "Delete":
        e.preventDefault();
        onDelete(profile.id);
        break;
    }
  };

  const handleRowFocus = (index: number) => {
    setFocusedIndex(index);
  };

  return (
    <div className="w-full bg-white rounded-lg border border-gray-200">
      <table className="min-w-full">
        <thead className="bg-gray-50 border-b border-gray-200">
          <tr>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              {I18N_KEYS.name}
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              {I18N_KEYS.type}
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              {I18N_KEYS.status}
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              {I18N_KEYS.default}
            </th>
            <th className="px-6 py-3 text-right text-sm font-semibold text-gray-900">
              {I18N_KEYS.actions}
            </th>
          </tr>
        </thead>
        <tbody>
          {profiles.length === 0 ? (
            <tr>
              <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                {I18N_KEYS.noProfiles}
              </td>
            </tr>
          ) : (
            profiles.map((profile: StorageProfile, index: number) => (
              <tr
                key={profile.id}
                onClick={() => handleRowClick(profile, index)}
                onKeyDown={(e) => handleKeyDown(e, profile, index)}
                onFocus={() => handleRowFocus(index)}
                tabIndex={0}
                className={clsx(
                  "border-b border-gray-200 cursor-pointer transition-colors",
                  focusedIndex === index ? "bg-blue-50" : "hover:bg-gray-50",
                )}
              >
                <td className="px-6 py-4 text-sm font-medium text-gray-900">
                  <div className="flex items-center gap-2">
                    {profile.name}
                    {profile.storageUriRedacted && (
                      <span title={I18N_KEYS.redacted}>
                        <EyeOff size={14} className="text-gray-400" />
                      </span>
                    )}
                  </div>
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
                        : "bg-gray-100 text-gray-800",
                    )}
                  >
                    {profile.isActive ? I18N_KEYS.active : I18N_KEYS.inactive}
                  </span>
                </td>
                <td className="px-6 py-4 text-sm">
                  {profile.isDefault ? (
                    <div className="flex items-center text-green-700">
                      <CheckCircle size={16} className="mr-1" />
                      <span className="text-xs font-medium">
                        {I18N_KEYS.default}
                      </span>
                    </div>
                  ) : (
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onSetDefault(profile.id);
                      }}
                      className="text-xs text-gray-600 hover:text-gray-900 underline"
                      aria-label={`Set ${profile.name} as default`}
                    >
                      {I18N_KEYS.setAsDefault}
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
                      title={I18N_KEYS.editProfile}
                      aria-label={`Edit profile ${profile.name}`}
                    >
                      <Edit size={16} />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(profile.id);
                      }}
                      className="p-1 text-red-600 hover:text-red-900 hover:bg-red-100 rounded"
                      title={I18N_KEYS.deleteProfile}
                      aria-label={`Delete profile ${profile.name}`}
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
