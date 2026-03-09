# Data Fabric Admin UI - Integration Guide

## Overview

This guide explains how to integrate the Data Fabric Admin UI feature into your main application, including routing, layout integration, and form implementation.

## Step 1: Add Routes

### React Router Setup

Add routes for the data fabric admin pages in your main routing configuration:

```typescript
// src/App.tsx or your main routing file

import { BrowserRouter, Routes, Route } from "react-router-dom";
import { StorageProfilesPage, DataConnectorsPage } from "@/features/data-fabric";
import AdminLayout from "@/layouts/AdminLayout";

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* Other routes... */}

        {/* Data Fabric Admin Routes */}
        <Route path="/admin" element={<AdminLayout />}>
          <Route path="data-fabric/profiles" element={<StorageProfilesPage />} />
          <Route path="data-fabric/connectors" element={<DataConnectorsPage />} />
        </Route>

        {/* Other routes... */}
      </Routes>
    </BrowserRouter>
  );
}
```

### Navigation Menu Integration

Add navigation links to your admin menu:

```typescript
// src/components/AdminNavigation.tsx

import { Link } from "react-router-dom";
import { Database, Link2 } from "lucide-react";

export function AdminNavigation() {
  return (
    <nav className="space-y-2">
      {/* Other nav items */}

      {/* Data Fabric Section */}
      <div className="mt-8 pt-8 border-t border-gray-200">
        <h3 className="px-4 text-sm font-semibold text-gray-900 mb-4">Data Fabric</h3>

        <Link
          to="/admin/data-fabric/profiles"
          className="flex items-center px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-md"
        >
          <Database className="w-4 h-4 mr-3" />
          Storage Profiles
        </Link>

        <Link
          to="/admin/data-fabric/connectors"
          className="flex items-center px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-md"
        >
          <Link2 className="w-4 h-4 mr-3" />
          Data Connectors
        </Link>
      </div>

      {/* Other nav items */}
    </nav>
  );
}
```

## Step 2: Create Form Components

### Storage Profile Form

```typescript
// src/features/data-fabric/components/StorageProfileForm.tsx

import React, { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { StorageProfile, StorageType } from "@/features/data-fabric/types";
import { storageProfileApi } from "@/features/data-fabric/services/api";
import { toast } from "sonner";

interface StorageProfileFormProps {
  profile?: StorageProfile | null;
  onSuccess: () => void;
  onCancel: () => void;
}

/**
 * Form component for creating and editing storage profiles.
 *
 * @param profile - Existing profile to edit (null for create)
 * @param onSuccess - Callback on successful save
 * @param onCancel - Callback on cancel
 */
export default function StorageProfileForm({
  profile,
  onSuccess,
  onCancel,
}: StorageProfileFormProps) {
  const { register, handleSubmit, watch, formState: { errors } } = useForm<any>({
    defaultValues: profile || {
      name: "",
      type: "S3",
      encryption: { type: "NONE" },
      compression: { type: "NONE" },
    },
  });

  const [isSubmitting, setIsSubmitting] = useState(false);
  const storageType = watch("type");

  const onSubmit = async (data: any) => {
    setIsSubmitting(true);
    try {
      if (profile) {
        await storageProfileApi.update(profile.id, data);
        toast.success("Storage profile updated");
      } else {
        await storageProfileApi.create(data);
        toast.success("Storage profile created");
      }
      onSuccess();
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Failed to save profile"
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      {/* Profile Name */}
      <div>
        <label className="block text-sm font-medium text-gray-700">
          Profile Name
        </label>
        <input
          type="text"
          {...register("name", { required: "Name is required" })}
          className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
          placeholder="e.g., Production S3"
        />
        {errors.name && (
          <p className="mt-1 text-sm text-red-600">{errors.name.message}</p>
        )}
      </div>

      {/* Storage Type */}
      <div>
        <label className="block text-sm font-medium text-gray-700">
          Storage Type
        </label>
        <select
          {...register("type", { required: "Type is required" })}
          className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
          disabled={!!profile} // Disable type change on edit
        >
          <option value="S3">Amazon S3</option>
          <option value="AZURE_BLOB">Azure Blob Storage</option>
          <option value="GCS">Google Cloud Storage</option>
          <option value="POSTGRESQL">PostgreSQL</option>
          <option value="MONGODB">MongoDB</option>
          <option value="SNOWFLAKE">Snowflake</option>
          <option value="DATABRICKS">Databricks</option>
          <option value="HDFS">HDFS</option>
        </select>
        {errors.type && (
          <p className="mt-1 text-sm text-red-600">{errors.type.message}</p>
        )}
      </div>

      {/* Storage-Type Specific Config */}
      {storageType === "S3" && (
        <>
          <div>
            <label className="block text-sm font-medium text-gray-700">
              S3 Bucket
            </label>
            <input
              type="text"
              {...register("config.bucket", {
                required: "Bucket name is required",
              })}
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
              placeholder="my-data-bucket"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              AWS Region
            </label>
            <input
              type="text"
              {...register("config.region")}
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
              placeholder="us-west-2"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700">
              S3 Prefix (optional)
            </label>
            <input
              type="text"
              {...register("config.prefix")}
              className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
              placeholder="prod/"
            />
          </div>
        </>
      )}

      {/* Encryption */}
      <div>
        <label className="block text-sm font-medium text-gray-700">
          Encryption Type
        </label>
        <select
          {...register("encryption.type")}
          className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
        >
          <option value="NONE">None</option>
          <option value="AES_256">AES-256</option>
          <option value="KMS">AWS KMS</option>
          <option value="MANAGED">Provider Managed</option>
        </select>
      </div>

      {/* Compression */}
      <div>
        <label className="block text-sm font-medium text-gray-700">
          Compression
        </label>
        <select
          {...register("compression.type")}
          className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
        >
          <option value="NONE">None</option>
          <option value="GZIP">GZIP</option>
          <option value="SNAPPY">Snappy</option>
          <option value="ZSTD">Zstandard</option>
        </select>
      </div>

      {/* Form Actions */}
      <div className="flex justify-end gap-3 pt-6 border-t border-gray-200">
        <button
          type="button"
          onClick={onCancel}
          className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
        >
          Cancel
        </button>
        <button
          type="submit"
          disabled={isSubmitting}
          className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isSubmitting ? "Saving..." : profile ? "Update Profile" : "Create Profile"}
        </button>
      </div>
    </form>
  );
}
```

### Data Connector Form

```typescript
// src/features/data-fabric/components/DataConnectorForm.tsx

import React, { useState, useEffect } from "react";
import { useForm } from "react-hook-form";
import { useAtom } from "jotai";
import {
  DataConnector,
  allStorageProfilesAtom,
} from "@/features/data-fabric";
import { dataConnectorApi } from "@/features/data-fabric/services/api";
import { toast } from "sonner";

interface DataConnectorFormProps {
  connector?: DataConnector | null;
  onSuccess: () => void;
  onCancel: () => void;
}

/**
 * Form component for creating and editing data connectors.
 *
 * @param connector - Existing connector to edit (null for create)
 * @param onSuccess - Callback on successful save
 * @param onCancel - Callback on cancel
 */
export default function DataConnectorForm({
  connector,
  onSuccess,
  onCancel,
}: DataConnectorFormProps) {
  const [storageProfiles] = useAtom(allStorageProfilesAtom);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState<any>(null);

  const { register, handleSubmit, formState: { errors } } = useForm<any>({
    defaultValues: connector || {
      name: "",
      sourceType: "PostgreSQL",
      syncSchedule: "0 0 * * *",
    },
  });

  const onSubmit = async (data: any) => {
    setIsSubmitting(true);
    try {
      if (connector) {
        await dataConnectorApi.update(connector.id, data);
        toast.success("Connector updated");
      } else {
        await dataConnectorApi.create(data);
        toast.success("Connector created");
      }
      onSuccess();
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Failed to save connector"
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleTest = async () => {
    if (!connector) {
      toast.error("Save connector first before testing");
      return;
    }

    setIsTesting(true);
    try {
      const result = await dataConnectorApi.test(connector.id);
      setTestResult(result);
      if (result.success) {
        toast.success("Connection test passed");
      } else {
        toast.error(result.message || "Connection test failed");
      }
    } catch (error) {
      toast.error("Connection test failed");
    } finally {
      setIsTesting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-6">
      {/* Connector Name */}
      <div>
        <label className="block text-sm font-medium text-gray-700">
          Connector Name
        </label>
        <input
          type="text"
          {...register("name", { required: "Name is required" })}
          className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
          placeholder="e.g., PostgreSQL to S3"
        />
        {errors.name && (
          <p className="mt-1 text-sm text-red-600">{errors.name.message}</p>
        )}
      </div>

      {/* Source Type */}
      <div>
        <label className="block text-sm font-medium text-gray-700">
          Source Type
        </label>
        <select
          {...register("sourceType", { required: "Source type is required" })}
          className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
        >
          <option value="PostgreSQL">PostgreSQL</option>
          <option value="MySQL">MySQL</option>
          <option value="MongoDB">MongoDB</option>
          <option value="API">REST API</option>
          <option value="FileSystem">File System</option>
        </select>
        {errors.sourceType && (
          <p className="mt-1 text-sm text-red-600">
            {errors.sourceType.message}
          </p>
        )}
      </div>

      {/* Storage Profile */}
      <div>
        <label className="block text-sm font-medium text-gray-700">
          Storage Profile
        </label>
        <select
          {...register("storageProfileId", {
            required: "Storage profile is required",
          })}
          className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
        >
          <option value="">-- Select Profile --</option>
          {storageProfiles.map((profile) => (
            <option key={profile.id} value={profile.id}>
              {profile.name} ({profile.type})
            </option>
          ))}
        </select>
        {errors.storageProfileId && (
          <p className="mt-1 text-sm text-red-600">
            {errors.storageProfileId.message}
          </p>
        )}
      </div>

      {/* Connection Config */}
      <div>
        <label className="block text-sm font-medium text-gray-700">
          Connection Config (JSON)
        </label>
        <textarea
          {...register("connectionConfig", {
            required: "Connection config is required",
            validate: (val) => {
              try {
                JSON.parse(JSON.stringify(val || {}));
                return true;
              } catch {
                return "Invalid JSON";
              }
            },
          })}
          className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 font-mono text-sm"
          rows={6}
          placeholder={`{
  "host": "localhost",
  "port": 5432,
  "database": "mydb",
  "user": "postgres",
  "password": "secret"
}`}
        />
        {errors.connectionConfig && (
          <p className="mt-1 text-sm text-red-600">
            {errors.connectionConfig.message}
          </p>
        )}
      </div>

      {/* Sync Schedule */}
      <div>
        <label className="block text-sm font-medium text-gray-700">
          Sync Schedule (Cron)
        </label>
        <input
          type="text"
          {...register("syncSchedule")}
          className="mt-1 w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 font-mono text-sm"
          placeholder="0 0 * * * (daily at midnight)"
        />
        <p className="mt-1 text-xs text-gray-500">
          Cron format: minute hour day month weekday
        </p>
      </div>

      {/* Test Connection Result */}
      {testResult && (
        <div
          className={`p-4 rounded-md ${
            testResult.success
              ? "bg-green-50 border border-green-200"
              : "bg-red-50 border border-red-200"
          }`}
        >
          <p
            className={`text-sm font-medium ${
              testResult.success ? "text-green-800" : "text-red-800"
            }`}
          >
            {testResult.message}
          </p>
          {testResult.details && (
            <pre className="mt-2 text-xs overflow-auto">
              {JSON.stringify(testResult.details, null, 2)}
            </pre>
          )}
        </div>
      )}

      {/* Form Actions */}
      <div className="flex justify-between pt-6 border-t border-gray-200">
        <button
          type="button"
          onClick={handleTest}
          disabled={!connector || isTesting}
          className="px-4 py-2 text-blue-600 border border-blue-300 rounded-md hover:bg-blue-50 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {isTesting ? "Testing..." : "Test Connection"}
        </button>

        <div className="flex gap-3">
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={isSubmitting}
            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSubmitting ? "Saving..." : connector ? "Update Connector" : "Create Connector"}
          </button>
        </div>
      </div>
    </form>
  );
}
```

## Step 3: Create Modal Container

### Modal Manager Component

```typescript
// src/components/DataFabricModal.tsx

import React, { useState } from "react";
import { StorageProfile, DataConnector } from "@/features/data-fabric/types";
import StorageProfileForm from "@/features/data-fabric/components/StorageProfileForm";
import DataConnectorForm from "@/features/data-fabric/components/DataConnectorForm";
import { X } from "lucide-react";

type ModalType = "storage-profile" | "connector" | null;

/**
 * Modal manager for data fabric create/edit operations.
 *
 * Usage in admin page:
 * ```
 * const { modal, openModal, closeModal } = useDataFabricModal();
 * return (
 *   <>
 *     <StorageProfilesPage
 *       onCreateClick={() => openModal('storage-profile')}
 *       onEditClick={(profile) => openModal('storage-profile', profile)}
 *     />
 *     <DataFabricModal {...modal} {...{ openModal, closeModal }} />
 *   </>
 * );
 * ```
 */
interface DataFabricModalProps {
  type: ModalType;
  editingProfile?: StorageProfile | null;
  editingConnector?: DataConnector | null;
  onClose: () => void;
  onRefresh: () => void;
}

export default function DataFabricModal({
  type,
  editingProfile,
  editingConnector,
  onClose,
  onRefresh,
}: DataFabricModalProps) {
  if (!type) return null;

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg shadow-lg max-w-2xl w-full mx-4 max-h-[90vh] overflow-y-auto">
        {/* Modal Header */}
        <div className="flex justify-between items-center p-6 border-b border-gray-200">
          <h2 className="text-lg font-semibold text-gray-900">
            {type === "storage-profile"
              ? editingProfile
                ? "Edit Storage Profile"
                : "Create Storage Profile"
              : editingConnector
              ? "Edit Data Connector"
              : "Create Data Connector"}
          </h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600"
          >
            <X className="w-6 h-6" />
          </button>
        </div>

        {/* Modal Body */}
        <div className="p-6">
          {type === "storage-profile" && (
            <StorageProfileForm
              profile={editingProfile}
              onSuccess={() => {
                onRefresh();
                onClose();
              }}
              onCancel={onClose}
            />
          )}

          {type === "connector" && (
            <DataConnectorForm
              connector={editingConnector}
              onSuccess={() => {
                onRefresh();
                onClose();
              }}
              onCancel={onClose}
            />
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Hook to manage modal state.
 */
export function useDataFabricModal() {
  const [type, setType] = useState<ModalType>(null);
  const [editingProfile, setEditingProfile] = useState<StorageProfile | null>(null);
  const [editingConnector, setEditingConnector] = useState<DataConnector | null>(null);

  return {
    modal: {
      type,
      editingProfile,
      editingConnector,
      onClose: () => {
        setType(null);
        setEditingProfile(null);
        setEditingConnector(null);
      },
    },
    openModal: (
      modalType: ModalType,
      profile?: StorageProfile,
      connector?: DataConnector
    ) => {
      setType(modalType);
      if (profile) setEditingProfile(profile);
      if (connector) setEditingConnector(connector);
    },
  };
}
```

## Step 4: Update Admin Pages

### Enhanced Storage Profiles Page

```typescript
// src/features/data-fabric/components/StorageProfilesPage.tsx (updated)

import React, { useEffect } from "react";
import { useAtom } from "jotai";
import {
  loadStorageProfilesAtom,
  allStorageProfilesAtom,
  deleteStorageProfileAtom,
  setDefaultStorageProfileAtom,
  type StorageProfile,
} from "@/features/data-fabric";
import { storageProfileApi } from "@/features/data-fabric/services/api";
import StorageProfilesList from "./StorageProfilesList";
import { toast } from "sonner";
import { Plus, Loader } from "lucide-react";

interface StorageProfilesPageProps {
  onCreateClick: () => void;
  onEditClick: (profile: StorageProfile) => void;
}

export default function StorageProfilesPage({
  onCreateClick,
  onEditClick,
}: StorageProfilesPageProps) {
  const [, loadProfiles] = useAtom(loadStorageProfilesAtom);
  const [profiles] = useAtom(allStorageProfilesAtom);
  const [, deleteProfile] = useAtom(deleteStorageProfileAtom);
  const [, setDefaultProfile] = useAtom(setDefaultStorageProfileAtom);
  const [isLoading, setIsLoading] = React.useState(false);

  useEffect(() => {
    loadProfiles();
  }, [loadProfiles]);

  const handleDelete = async (profileId: string) => {
    if (!confirm("Are you sure you want to delete this profile?")) return;

    try {
      await deleteProfile(profileId);
      toast.success("Profile deleted");
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Failed to delete profile"
      );
    }
  };

  const handleSetDefault = async (profileId: string) => {
    try {
      await setDefaultProfile(profileId);
      toast.success("Default profile updated");
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Failed to set default profile"
      );
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-12">
        <Loader className="w-8 h-8 animate-spin text-blue-600" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-start">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Storage Profiles</h1>
          <p className="mt-1 text-gray-600">
            Manage cloud and local storage backends for data collection.
          </p>
        </div>
        <button
          onClick={onCreateClick}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
        >
          <Plus className="w-4 h-4" />
          New Profile
        </button>
      </div>

      {/* List */}
      {profiles.length > 0 ? (
        <StorageProfilesList
          onEdit={onEditClick}
          onDelete={handleDelete}
          onSetDefault={handleSetDefault}
        />
      ) : (
        <div className="text-center py-12 bg-gray-50 rounded-lg">
          <h3 className="text-lg font-medium text-gray-900">
            No storage profiles found
          </h3>
          <p className="mt-2 text-gray-600">
            Create your first storage profile to get started with data fabric.
          </p>
          <button
            onClick={onCreateClick}
            className="mt-4 px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700"
          >
            Create Profile
          </button>
        </div>
      )}
    </div>
  );
}
```

## Step 5: Complete Integration Example

```typescript
// src/pages/DataFabricAdminPage.tsx

import React from "react";
import { StorageProfilesPage, DataConnectorsPage } from "@/features/data-fabric";
import { useDataFabricModal } from "@/components/DataFabricModal";
import DataFabricModal from "@/components/DataFabricModal";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/Tabs";

/**
 * Main data fabric admin page with tab-based navigation.
 * 
 * Combines storage profiles and connector management with
 * integrated modal forms for create/edit operations.
 */
export default function DataFabricAdminPage() {
  const { modal, openModal } = useDataFabricModal();
  const [refreshKey, setRefreshKey] = React.useState(0);

  const handleRefresh = () => {
    setRefreshKey((prev) => prev + 1);
  };

  return (
    <div className="space-y-6">
      <Tabs defaultValue="profiles" className="w-full">
        <TabsList className="border-b border-gray-200">
          <TabsTrigger value="profiles">Storage Profiles</TabsTrigger>
          <TabsTrigger value="connectors">Data Connectors</TabsTrigger>
        </TabsList>

        <TabsContent value="profiles" className="space-y-6">
          <StorageProfilesPage
            key={`profiles-${refreshKey}`}
            onCreateClick={() => openModal("storage-profile")}
            onEditClick={(profile) =>
              openModal("storage-profile", profile)
            }
          />
        </TabsContent>

        <TabsContent value="connectors" className="space-y-6">
          <DataConnectorsPage
            key={`connectors-${refreshKey}`}
            onCreateClick={() => openModal("connector")}
            onEditClick={(connector) =>
              openModal("connector", null, connector)
            }
          />
        </TabsContent>
      </Tabs>

      {/* Modal */}
      <DataFabricModal
        {...modal}
        editingProfile={modal.type === "storage-profile" ? modal.editingProfile : undefined}
        editingConnector={modal.type === "connector" ? modal.editingConnector : undefined}
        onRefresh={handleRefresh}
      />
    </div>
  );
}
```

## Environment Configuration

Ensure your backend API is properly configured:

```typescript
// src/config/api.ts

export const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";
export const DATA_FABRIC_BASE_URL = `${API_BASE_URL}/api/v1/data-fabric`;
```

Add to `.env`:

```
VITE_API_URL=http://localhost:8080
```

## Next Steps

1. **Backend Implementation**: Implement `/api/v1/data-fabric/*` endpoints
2. **Authentication**: Integrate with your auth system (extract tenant ID from JWT)
3. **Testing**: Add integration tests with real backend
4. **Permissions**: Add role-based access control (RBAC) for admin operations
5. **Monitoring**: Add analytics tracking for admin operations

See `API_CONTRACTS.md` for endpoint specifications and `TESTING_GUIDE.md` for testing patterns.
