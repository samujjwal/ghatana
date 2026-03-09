/**
 * Data connectors page.
 *
 * Admin interface for managing data connectors with create, edit, delete, and sync operations.
 *
 * @doc.type page
 * @doc.purpose Data connector administration
 * @doc.layer product
 * @doc.pattern Container Component
 */

import React, { useEffect, useState } from "react";
import { useAtom } from "jotai";
import { Plus } from "lucide-react";
import { toast } from "sonner";
import {
  allDataConnectorsAtom,
  loadDataConnectorsAtom,
  deleteDataConnectorAtom,
  updateSyncStatisticsAtom,
} from "../stores/connector.store";
import { DataConnectorsList } from "../components/DataConnectorsList";
import { dataConnectorApi } from "../services/api";
import type { DataConnector } from "../types";

interface DataConnectorsPageProps {
  onCreateClick: () => void;
  onEditClick: (connector: DataConnector) => void;
}

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
  const [, deleteConnector] = useAtom(deleteDataConnectorAtom);
  const [, updateStatistics] = useAtom(updateSyncStatisticsAtom);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const loadData = async () => {
      try {
        setIsLoading(true);
        const data = await dataConnectorApi.getAll();
        await loadConnectors(data);
      } catch (error) {
        toast.error(
          `Failed to load data connectors: ${
            error instanceof Error ? error.message : "Unknown error"
          }`
        );
      } finally {
        setIsLoading(false);
      }
    };

    loadData();
  }, [loadConnectors]);

  const handleDelete = async (connectorId: string) => {
    if (
      !confirm(
        "Are you sure you want to delete this data connector? This action cannot be undone."
      )
    ) {
      return;
    }

    try {
      await dataConnectorApi.delete(connectorId);
      await deleteConnector(connectorId);
      toast.success("Data connector deleted successfully");
    } catch (error) {
      toast.error(
        `Failed to delete connector: ${
          error instanceof Error ? error.message : "Unknown error"
        }`
      );
    }
  };

  const handleSync = async (connectorId: string) => {
    try {
      const result = await dataConnectorApi.triggerSync(connectorId);
      toast.success(`Sync triggered successfully (Job ID: ${result.jobId})`);

      // Fetch updated statistics
      const stats = await dataConnectorApi.getSyncStatistics(connectorId);
      await updateStatistics(stats);
    } catch (error) {
      toast.error(
        `Failed to trigger sync: ${
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
          <h1 className="text-2xl font-bold text-gray-900">Data Connectors</h1>
          <p className="mt-1 text-sm text-gray-600">
            Connect data sources to storage backends and manage synchronization
          </p>
        </div>
        <button
          onClick={onCreateClick}
          className="inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          <Plus size={16} className="mr-2" />
          New Connector
        </button>
      </div>

      {/* Content */}
      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
        </div>
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
            No data connectors yet
          </h3>
          <p className="mt-2 text-sm text-gray-600">
            Create your first data connector to connect data sources to storage backends.
          </p>
          <button
            onClick={onCreateClick}
            className="mt-4 inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            <Plus size={16} className="mr-2" />
            Create First Connector
          </button>
        </div>
      )}
    </div>
  );
};
