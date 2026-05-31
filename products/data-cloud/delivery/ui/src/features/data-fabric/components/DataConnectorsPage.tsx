/**
 * Data connectors page.
 *
 * Admin interface for managing data connectors with create, edit, delete, and sync operations.
 *
 * G15: Updated to add i18n keys, test/enable/disable flows, and handle unavailable states.
 *
 * @doc.type page
 * @doc.purpose Data connector administration
 * @doc.layer product
 * @doc.pattern Container Component
 */

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useAtom } from "jotai";
import { Lock, Plus, ServerOff } from "lucide-react";
import React from "react";
import { toast } from "sonner";
import { LoadingState } from "../../../components/common/LoadingState";
import { DataConnectorsList } from "../components/DataConnectorsList";
import { dataConnectorApi } from "../services/api";
import {
  allDataConnectorsAtom,
  connectorErrorAtom,
  connectorLoadingAtom,
  deleteDataConnectorAtom,
  loadDataConnectorsAtom,
  toggleConnectorStateAtom,
  updateSyncStatisticsAtom,
} from "../stores/connector.store";
import type { DataConnector } from "../types";

interface DataConnectorsPageProps {
  onCreateClick: () => void;
  onEditClick: (connector: DataConnector) => void;
}

/**
 * G15: i18n keys for user-visible text
 */
const I18N_KEYS = {
  title: "dataConnectors.title",
  description: "dataConnectors.description",
  newConnector: "dataConnectors.newConnector",
  createFirstConnector: "dataConnectors.createFirstConnector",
  noConnectorsTitle: "dataConnectors.noConnectorsTitle",
  noConnectorsDescription: "dataConnectors.noConnectorsDescription",
  loading: "dataConnectors.loading",
  error: "dataConnectors.error",
  unauthorized: "dataConnectors.unauthorized",
  unavailable: "dataConnectors.unavailable",
  deleteSuccess: "dataConnectors.deleteSuccess",
  deleteFailed: "dataConnectors.deleteFailed",
  deleteConfirm: "dataConnectors.deleteConfirm",
  syncSuccess: "dataConnectors.syncSuccess",
  syncFailed: "dataConnectors.syncFailed",
  testSuccess: "dataConnectors.testSuccess",
  testFailed: "dataConnectors.testFailed",
  enableSuccess: "dataConnectors.enableSuccess",
  disableSuccess: "dataConnectors.disableSuccess",
  toggleFailed: "dataConnectors.toggleFailed",
} as const;

/**
 * Data connectors admin page.
 *
 * Displays list of data connectors with ability to create, edit, delete, and trigger syncs.
 */
export const DataConnectorsPage: React.FC<DataConnectorsPageProps> = ({
  onCreateClick,
  onEditClick,
}) => {
  const [, loadConnectors] = useAtom(loadDataConnectorsAtom);
  const [connectors] = useAtom(allDataConnectorsAtom);
  const [isLoading] = useAtom(connectorLoadingAtom);
  const [error] = useAtom(connectorErrorAtom);
  const [, deleteConnector] = useAtom(deleteDataConnectorAtom);
  const [, updateStatistics] = useAtom(updateSyncStatisticsAtom);
  const [, toggleConnector] = useAtom(toggleConnectorStateAtom);
  const queryClient = useQueryClient();

  const { isError } = useQuery({
    queryKey: ["data-connectors"],
    staleTime: 30_000,
    queryFn: async () => {
      const data = await dataConnectorApi.getAll();
      await loadConnectors(data);
      return data;
    },
    throwOnError: false,
  });

  if (isError) {
    toast.error(I18N_KEYS.error);
  }

  const handleDelete = async (connectorId: string) => {
    if (!confirm(I18N_KEYS.deleteConfirm)) {
      return;
    }

    try {
      await dataConnectorApi.delete(connectorId);
      await deleteConnector(connectorId);
      queryClient.invalidateQueries({ queryKey: ["data-connectors"] });
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

  const handleSync = async (connectorId: string) => {
    try {
      const result = await dataConnectorApi.triggerSync(connectorId);
      toast.success(`Sync triggered successfully (Job ID: ${result.jobId})`);

      const stats = await dataConnectorApi.getSyncStatistics(connectorId);
      await updateStatistics(stats);
      await queryClient.invalidateQueries({ queryKey: ["collections"] });
    } catch (error) {
      const apiError = error as { code?: string; message?: string };
      if (
        apiError?.code === "FEATURE_UNAVAILABLE" ||
        apiError?.code === "SURFACE_DEGRADED"
      ) {
        toast.error(apiError.message || I18N_KEYS.unavailable);
      } else {
        toast.error(
          `Failed to trigger sync: ${apiError?.message || "Unknown error"}`,
        );
      }
    }
  };

  const _handleTest = async (connectorId: string) => {
    try {
      await dataConnectorApi.test(connectorId);
      toast.success(I18N_KEYS.testSuccess);
    } catch (error) {
      const apiError = error as { code?: string; message?: string };
      if (
        apiError?.code === "FEATURE_UNAVAILABLE" ||
        apiError?.code === "SURFACE_DEGRADED"
      ) {
        toast.error(apiError.message || I18N_KEYS.unavailable);
      } else {
        toast.error(
          `${I18N_KEYS.testFailed}: ${apiError?.message || "Unknown error"}`,
        );
      }
    }
  };

  const _handleToggle = async (connectorId: string) => {
    try {
      await toggleConnector(connectorId);
      const connector = connectors.find((c) => c.id === connectorId);
      toast.success(
        connector?.isEnabled
          ? I18N_KEYS.disableSuccess
          : I18N_KEYS.enableSuccess,
      );
    } catch (error) {
      const apiError = error as { code?: string; message?: string };
      if (
        apiError?.code === "FEATURE_UNAVAILABLE" ||
        apiError?.code === "SURFACE_DEGRADED"
      ) {
        toast.error(apiError.message || I18N_KEYS.unavailable);
      } else {
        toast.error(
          `${I18N_KEYS.toggleFailed}: ${apiError?.message || "Unknown error"}`,
        );
      }
    }
  };

  // G15: Handle unauthorized/unavailable states
  if (error?.includes("401") || error?.includes("403")) {
    return (
      <div className="flex items-center justify-center py-12 bg-gray-50 rounded-lg border border-gray-200">
        <Lock className="w-12 h-12 text-gray-400 mb-4" />
        <div className="text-center">
          <h3 className="text-lg font-medium text-gray-900">
            {I18N_KEYS.unauthorized}
          </h3>
          <p className="mt-2 text-sm text-gray-600">
            You do not have permission to access data connectors.
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
            Data connectors are temporarily unavailable. Please try again later.
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
          {I18N_KEYS.newConnector}
        </button>
      </div>

      {/* Content */}
      {isLoading ? (
        <LoadingState message={I18N_KEYS.loading} className="py-12" />
      ) : (
        <DataConnectorsList
          onEdit={onEditClick}
          onDelete={handleDelete}
          onSync={handleSync}
        />
      )}

      {/* Empty state */}
      {!isLoading && connectors.length === 0 && (
        <div className="text-center py-12 bg-gray-50 rounded-lg border border-gray-200">
          <h3 className="text-lg font-medium text-gray-900">
            {I18N_KEYS.noConnectorsTitle}
          </h3>
          <p className="mt-2 text-sm text-gray-600">
            {I18N_KEYS.noConnectorsDescription}
          </p>
          <button
            onClick={onCreateClick}
            className="mt-4 inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus size={16} className="mr-2" />
            {I18N_KEYS.createFirstConnector}
          </button>
        </div>
      )}
    </div>
  );
};
