/**
 * Data connectors list component.
 *
 * Displays a paginated table of data connectors with status and actions.
 *
 * @doc.type component
 * @doc.purpose Display data connectors
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import React from "react";
import { useAtom } from "jotai";
import { Trash2, Edit, Play, AlertCircle } from "lucide-react";
import clsx from "clsx";
import type { DataConnector } from "../types";
import {
  allDataConnectorsAtom,
  selectDataConnectorAtom,
} from "../stores/connector.store";

interface DataConnectorsListProps {
  onEdit: (connector: DataConnector) => void;
  onDelete: (connectorId: string) => void;
  onSync: (connectorId: string) => void;
}

/**
 * Data connectors list component.
 *
 * Renders a table showing all available data connectors with their source type,
 * status, and available actions (edit, delete, sync).
 */
export const DataConnectorsList: React.FC<DataConnectorsListProps> = ({
  onEdit,
  onDelete,
  onSync,
}) => {
  const [connectors] = useAtom(allDataConnectorsAtom);
  const [, selectConnector] = useAtom(selectDataConnectorAtom);

  const handleRowClick = (connector: DataConnector) => {
    selectConnector(connector.id);
  };

  const getStatusColor = (
    status: "active" | "inactive" | "error" | "testing"
  ) => {
    switch (status) {
      case "active":
        return "bg-green-100 text-green-800";
      case "inactive":
        return "bg-gray-100 text-gray-800";
      case "error":
        return "bg-red-100 text-red-800";
      case "testing":
        return "bg-yellow-100 text-yellow-800";
      default:
        return "bg-gray-100 text-gray-800";
    }
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
              Source Type
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              Status
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              Last Sync
            </th>
            <th className="px-6 py-3 text-right text-sm font-semibold text-gray-900">
              Actions
            </th>
          </tr>
        </thead>
        <tbody>
          {connectors.length === 0 ? (
            <tr>
              <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                No data connectors configured yet. Create one to get started.
              </td>
            </tr>
          ) : (
            connectors.map((connector: DataConnector) => (
              <tr
                key={connector.id}
                onClick={() => handleRowClick(connector)}
                className="border-b border-gray-200 hover:bg-gray-50 cursor-pointer transition-colors"
              >
                <td className="px-6 py-4 text-sm font-medium text-gray-900">
                  {connector.name}
                </td>
                <td className="px-6 py-4 text-sm text-gray-700">
                  <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded text-xs font-medium">
                    {connector.sourceType}
                  </span>
                </td>
                <td className="px-6 py-4 text-sm">
                  <div className="flex items-center">
                    <span className={clsx(
                      "px-2 py-1 rounded text-xs font-medium",
                      getStatusColor(connector.status)
                    )}>
                      {connector.status}
                    </span>
                    {connector.status === "error" && connector.statusMessage && (
                      <div className="ml-2 flex items-center group relative">
                        <AlertCircle size={14} className="text-red-600" />
                        <div className="hidden group-hover:block absolute bottom-full left-0 mb-2 bg-gray-900 text-white text-xs py-1 px-2 rounded whitespace-nowrap z-10">
                          {connector.statusMessage}
                        </div>
                      </div>
                    )}
                  </div>
                </td>
                <td className="px-6 py-4 text-sm text-gray-700">
                  {connector.lastSyncAt
                    ? new Date(connector.lastSyncAt).toLocaleDateString()
                    : "Never"}
                </td>
                <td className="px-6 py-4 text-right">
                  <div className="flex justify-end gap-2">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onSync(connector.id);
                      }}
                      className="p-1 text-blue-600 hover:text-blue-900 hover:bg-blue-100 rounded"
                      title="Trigger sync"
                      disabled={!connector.isEnabled}
                    >
                      <Play size={16} />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onEdit(connector);
                      }}
                      className="p-1 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded"
                      title="Edit connector"
                    >
                      <Edit size={16} />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(connector.id);
                      }}
                      className="p-1 text-red-600 hover:text-red-900 hover:bg-red-100 rounded"
                      title="Delete connector"
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
