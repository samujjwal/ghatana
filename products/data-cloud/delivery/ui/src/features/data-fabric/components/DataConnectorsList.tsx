/**
 * Data connectors list component.
 *
 * Displays a paginated table of data connectors with status and actions.
 *
 * G16: Updated to add keyboard navigation, redaction indicators, and improved accessibility.
 *
 * @doc.type component
 * @doc.purpose Display data connectors
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import clsx from "clsx";
import { useAtom } from "jotai";
import {
  AlertCircle,
  Edit,
  EyeOff,
  Play,
  Power,
  TestTube,
  Trash2,
} from "lucide-react";
import React, { useState } from "react";
import {
  allDataConnectorsAtom,
  selectDataConnectorAtom,
} from "../stores/connector.store";
import type { DataConnector } from "../types";

interface DataConnectorsListProps {
  onEdit: (connector: DataConnector) => void;
  onDelete: (connectorId: string) => void;
  onSync: (connectorId: string) => void;
  onTest?: (connectorId: string) => void;
  onToggle?: (connectorId: string) => void;
}

/**
 * G16: i18n keys for user-visible text
 */
const I18N_KEYS = {
  name: "dataConnectorsList.name",
  sourceType: "dataConnectorsList.sourceType",
  status: "dataConnectorsList.status",
  lastSync: "dataConnectorsList.lastSync",
  actions: "dataConnectorsList.actions",
  active: "dataConnectorsList.active",
  inactive: "dataConnectorsList.inactive",
  error: "dataConnectorsList.error",
  testing: "dataConnectorsList.testing",
  degraded: "dataConnectorsList.degraded",
  unavailable: "dataConnectorsList.unavailable",
  never: "dataConnectorsList.never",
  triggerSync: "dataConnectorsList.triggerSync",
  testConnection: "dataConnectorsList.testConnection",
  editConnector: "dataConnectorsList.editConnector",
  deleteConnector: "dataConnectorsList.deleteConnector",
  enableConnector: "dataConnectorsList.enableConnector",
  disableConnector: "dataConnectorsList.disableConnector",
  noConnectors: "dataConnectorsList.noConnectors",
  redacted: "dataConnectorsList.redacted",
} as const;

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
  onTest,
  onToggle,
}) => {
  const [connectors] = useAtom(allDataConnectorsAtom);
  const [, selectConnector] = useAtom(selectDataConnectorAtom);
  const [focusedIndex, setFocusedIndex] = useState<number | null>(null);

  const handleRowClick = (connector: DataConnector, index: number) => {
    selectConnector(connector.id);
    setFocusedIndex(index);
  };

  const handleKeyDown = (
    e: React.KeyboardEvent,
    connector: DataConnector,
    index: number,
  ) => {
    switch (e.key) {
      case "ArrowUp":
        e.preventDefault();
        if (index > 0) setFocusedIndex(index - 1);
        break;
      case "ArrowDown":
        e.preventDefault();
        if (index < connectors.length - 1) setFocusedIndex(index + 1);
        break;
      case "Enter":
        e.preventDefault();
        onEdit(connector);
        break;
      case "Delete":
        e.preventDefault();
        onDelete(connector.id);
        break;
    }
  };

  const handleRowFocus = (index: number) => {
    setFocusedIndex(index);
  };

  const getStatusColor = (
    status:
      | "active"
      | "inactive"
      | "error"
      | "testing"
      | "degraded"
      | "unavailable",
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
      case "degraded":
        return "bg-orange-100 text-orange-800";
      case "unavailable":
        return "bg-purple-100 text-purple-800";
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
              {I18N_KEYS.name}
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              {I18N_KEYS.sourceType}
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              {I18N_KEYS.status}
            </th>
            <th className="px-6 py-3 text-left text-sm font-semibold text-gray-900">
              {I18N_KEYS.lastSync}
            </th>
            <th className="px-6 py-3 text-right text-sm font-semibold text-gray-900">
              {I18N_KEYS.actions}
            </th>
          </tr>
        </thead>
        <tbody>
          {connectors.length === 0 ? (
            <tr>
              <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                {I18N_KEYS.noConnectors}
              </td>
            </tr>
          ) : (
            connectors.map((connector: DataConnector, index: number) => (
              <tr
                key={connector.id}
                onClick={() => handleRowClick(connector, index)}
                onKeyDown={(e) => handleKeyDown(e, connector, index)}
                onFocus={() => handleRowFocus(index)}
                tabIndex={0}
                className={clsx(
                  "border-b border-gray-200 cursor-pointer transition-colors",
                  focusedIndex === index ? "bg-blue-50" : "hover:bg-gray-50",
                )}
              >
                <td className="px-6 py-4 text-sm font-medium text-gray-900">
                  <div className="flex items-center gap-2">
                    {connector.name}
                    {connector.credentialsRedacted && (
                      <span title={I18N_KEYS.redacted}>
                        <EyeOff size={14} className="text-gray-400" />
                      </span>
                    )}
                  </div>
                </td>
                <td className="px-6 py-4 text-sm text-gray-700">
                  <span className="px-2 py-1 bg-blue-100 text-blue-800 rounded text-xs font-medium">
                    {connector.sourceType}
                  </span>
                </td>
                <td className="px-6 py-4 text-sm">
                  <div className="flex items-center">
                    <span
                      className={clsx(
                        "px-2 py-1 rounded text-xs font-medium",
                        getStatusColor(connector.status),
                      )}
                    >
                      {connector.status}
                    </span>
                    {connector.status === "error" &&
                      connector.statusMessage && (
                        <div
                          className="ml-2 flex items-center group relative"
                          tabIndex={0}
                        >
                          <AlertCircle size={14} className="text-red-600" />
                          <div className="hidden group-hover:block group-focus-within:block absolute bottom-full left-0 mb-2 bg-gray-900 text-white text-xs py-1 px-2 rounded whitespace-nowrap z-10">
                            {connector.statusMessage}
                          </div>
                        </div>
                      )}
                  </div>
                </td>
                <td className="px-6 py-4 text-sm text-gray-700">
                  {connector.lastSyncAt
                    ? new Date(connector.lastSyncAt).toLocaleDateString()
                    : I18N_KEYS.never}
                </td>
                <td className="px-6 py-4 text-right">
                  <div className="flex justify-end gap-2">
                    {onTest && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          onTest(connector.id);
                        }}
                        className="p-1 text-purple-600 hover:text-purple-900 hover:bg-purple-100 rounded"
                        title={I18N_KEYS.testConnection}
                        aria-label={`Test connection for ${connector.name}`}
                      >
                        <TestTube size={16} />
                      </button>
                    )}
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onSync(connector.id);
                      }}
                      className="p-1 text-blue-600 hover:text-blue-900 hover:bg-blue-100 rounded"
                      title="Trigger sync"
                      aria-label={`Trigger sync for ${connector.name}`}
                      disabled={!connector.isEnabled}
                    >
                      <Play size={16} />
                    </button>
                    {onToggle && (
                      <button
                        onClick={(e) => {
                          e.stopPropagation();
                          onToggle(connector.id);
                        }}
                        className="p-1 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded"
                        title={
                          connector.isEnabled
                            ? I18N_KEYS.disableConnector
                            : I18N_KEYS.enableConnector
                        }
                        aria-label={`${connector.isEnabled ? "Disable" : "Enable"} connector ${connector.name}`}
                      >
                        <Power size={16} />
                      </button>
                    )}
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onEdit(connector);
                      }}
                      className="p-1 text-gray-600 hover:text-gray-900 hover:bg-gray-100 rounded"
                      title={I18N_KEYS.editConnector}
                      aria-label={`Edit connector ${connector.name}`}
                    >
                      <Edit size={16} />
                    </button>
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(connector.id);
                      }}
                      className="p-1 text-red-600 hover:text-red-900 hover:bg-red-100 rounded"
                      title={I18N_KEYS.deleteConnector}
                      aria-label={`Delete connector ${connector.name}`}
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
