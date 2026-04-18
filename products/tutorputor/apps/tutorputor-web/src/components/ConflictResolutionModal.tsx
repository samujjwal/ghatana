/**
 * Conflict Resolution Modal
 *
 * Handles server vs local data conflicts with:
 * - Side-by-side comparison
 * - Manual resolution options
 * - Bulk resolution for multiple conflicts
 * - Preview before merge
 *
 * @doc.type component
 * @doc.purpose Resolve sync conflicts between local and server data
 * @doc.layer product
 * @doc.pattern Component
 */
import { useState } from "react";
import {
  AlertTriangle,
  Laptop,
  Server,
  Check,
  X,
  GitMerge,
  ChevronDown,
  ChevronUp,
} from "lucide-react";
import {
  Button,
  Card,
  Badge,
  AlertDialog,
  ScrollArea,
} from "@ghatana/design-system";

export interface Conflict {
  conflictId: string;
  itemType: string;
  itemId: string;
  itemName: string;
  localVersion: {
    modifiedAt: Date;
    data: Record<string, unknown>;
  };
  serverVersion: {
    modifiedAt: Date;
    data: Record<string, unknown>;
  };
  conflictingFields: string[];
}

export type ResolutionStrategy = "local" | "server" | "merge" | "skip";

export interface ConflictResolution {
  conflictId: string;
  strategy: ResolutionStrategy;
  mergedData?: Record<string, unknown>;
}

interface ConflictResolutionModalProps {
  conflicts: Conflict[];
  isOpen: boolean;
  onClose: () => void;
  onResolve: (resolutions: ConflictResolution[]) => Promise<void>;
  isResolving?: boolean;
}

export function ConflictResolutionModal({
  conflicts,
  isOpen,
  onClose,
  onResolve,
  isResolving = false,
}: ConflictResolutionModalProps) {
  const [resolutions, setResolutions] = useState<
    Record<string, ConflictResolution>
  >({});
  const [expandedConflict, setExpandedConflict] = useState<string | null>(null);
  const [showBulkOptions, setShowBulkOptions] = useState(false);

  const handleResolve = async () => {
    const resolutionArray = Object.values(resolutions);
    if (resolutionArray.length === 0) return;

    await onResolve(resolutionArray);
  };

  const setResolution = (
    conflictId: string,
    strategy: ResolutionStrategy,
    mergedData?: Record<string, unknown>
  ) => {
    setResolutions((prev) => ({
      ...prev,
      [conflictId]: { conflictId, strategy, mergedData },
    }));
  };

  const applyBulkResolution = (strategy: ResolutionStrategy) => {
    const newResolutions: Record<string, ConflictResolution> = {};
    conflicts.forEach((conflict) => {
      newResolutions[conflict.conflictId] = {
        conflictId: conflict.conflictId,
        strategy,
      };
    });
    setResolutions(newResolutions);
    setShowBulkOptions(false);
  };

  const resolvedCount = Object.keys(resolutions).length;
  const allResolved = resolvedCount === conflicts.length;

  const getStrategyBadge = (strategy?: ResolutionStrategy) => {
    const labels: Record<ResolutionStrategy, string> = {
      local: "Use Local",
      server: "Use Server",
      merge: "Merge",
      skip: "Skip",
    };

    const variants: Record<ResolutionStrategy, string> = {
      local: "bg-blue-100 text-blue-800",
      server: "bg-green-100 text-green-800",
      merge: "bg-purple-100 text-purple-800",
      skip: "bg-gray-100 text-gray-800",
    };

    if (!strategy) return null;

    return (
      <Badge className={`${variants[strategy]} text-xs`}>
        {labels[strategy]}
      </Badge>
    );
  };

  const renderDiffView = (
    field: string,
    localValue: unknown,
    serverValue: unknown
  ) => {
    const isDifferent = JSON.stringify(localValue) !== JSON.stringify(serverValue);

    return (
      <div key={field} className="border rounded-md overflow-hidden">
        <div className="bg-gray-50 px-3 py-1 text-xs font-medium text-gray-600 border-b">
          {field}
        </div>
        <div className="grid grid-cols-2 divide-x">
          <div
            className={`p-3 ${isDifferent ? "bg-yellow-50" : ""}`}
          >
            <div className="flex items-center gap-1 text-xs text-gray-500 mb-1">
              <Laptop className="w-3 h-3" />
              Local
            </div>
            <div className="text-sm">
              {formatValue(localValue)}
            </div>
          </div>
          <div
            className={`p-3 ${isDifferent ? "bg-yellow-50" : ""}`}
          >
            <div className="flex items-center gap-1 text-xs text-gray-500 mb-1">
              <Server className="w-3 h-3" />
              Server
            </div>
            <div className="text-sm">
              {formatValue(serverValue)}
            </div>
          </div>
        </div>
      </div>
    );
  };

  if (!isOpen) return null;

  return (
    <AlertDialog open={isOpen} onOpenChange={onClose}>
      <AlertDialog.Content className="max-w-4xl max-h-[90vh] flex flex-col">
        <AlertDialog.Header>
          <AlertDialog.Title className="flex items-center gap-2 text-xl">
            <AlertTriangle className="w-6 h-6 text-yellow-500" />
            Resolve Sync Conflicts
          </AlertDialog.Title>
          <AlertDialog.Description className="space-y-2">
            <p>
              {conflicts.length} {conflicts.length === 1 ? "item" : "items"}{" "}
              have conflicting changes between your device and the server.
            </p>
            <p className="text-sm text-gray-500">
              Choose which version to keep for each item, or merge the changes.
            </p>
          </AlertDialog.Description>
        </AlertDialog.Header>

        {/* Bulk Actions */}
        <div className="flex items-center justify-between py-2 border-y my-4">
          <div className="text-sm text-gray-500">
            {resolvedCount} of {conflicts.length} resolved
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setShowBulkOptions(!showBulkOptions)}
            >
              Bulk Resolve
              {showBulkOptions ? (
                <ChevronUp className="w-4 h-4 ml-1" />
              ) : (
                <ChevronDown className="w-4 h-4 ml-1" />
              )}
            </Button>
          </div>
        </div>

        {showBulkOptions && (
          <Card className="p-3 mb-4 bg-gray-50">
            <p className="text-sm text-gray-600 mb-2">
              Apply same resolution to all conflicts:
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => applyBulkResolution("local")}
              >
                <Laptop className="w-4 h-4 mr-1" />
                Keep All Local
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => applyBulkResolution("server")}
              >
                <Server className="w-4 h-4 mr-1" />
                Keep All Server
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => applyBulkResolution("server")}
              >
                <GitMerge className="w-4 h-4 mr-1" />
                Smart Merge All
              </Button>
            </div>
          </Card>
        )}

        {/* Conflict List */}
        <ScrollArea className="flex-1 pr-4">
          <div className="space-y-3">
            {conflicts.map((conflict) => {
              const isExpanded = expandedConflict === conflict.conflictId;
              const resolution = resolutions[conflict.conflictId];

              return (
                <Card
                  key={conflict.conflictId}
                  className={`overflow-hidden ${
                    resolution ? "border-green-300" : ""
                  }`}
                >
                  {/* Header */}
                  <div
                    className="p-4 flex items-center justify-between cursor-pointer hover:bg-gray-50"
                    onClick={() =>
                      setExpandedConflict(isExpanded ? null : conflict.conflictId)
                    }
                  >
                    <div className="flex items-center gap-3">
                      <Badge variant="outline">{conflict.itemType}</Badge>
                      <span className="font-medium">{conflict.itemName}</span>
                      {getStrategyBadge(resolution?.strategy)}
                    </div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-gray-500">
                        {conflict.conflictingFields.length} conflicting fields
                      </span>
                      {isExpanded ? (
                        <ChevronUp className="w-4 h-4" />
                      ) : (
                        <ChevronDown className="w-4 h-4" />
                      )}
                    </div>
                  </div>

                  {/* Expanded Details */}
                  {isExpanded && (
                    <div className="border-t p-4 space-y-4">
                      {/* Timestamps */}
                      <div className="grid grid-cols-2 gap-4 text-sm">
                        <div className="flex items-center gap-2">
                          <Laptop className="w-4 h-4 text-blue-500" />
                          <span className="text-gray-500">Local modified:</span>
                          <span>
                            {new Date(
                              conflict.localVersion.modifiedAt
                            ).toLocaleString()}
                          </span>
                        </div>
                        <div className="flex items-center gap-2">
                          <Server className="w-4 h-4 text-green-500" />
                          <span className="text-gray-500">Server modified:</span>
                          <span>
                            {new Date(
                              conflict.serverVersion.modifiedAt
                            ).toLocaleString()}
                          </span>
                        </div>
                      </div>

                      {/* Diff View */}
                      <div className="space-y-2">
                        {conflict.conflictingFields.map((field) =>
                          renderDiffView(
                            field,
                            conflict.localVersion.data[field],
                            conflict.serverVersion.data[field]
                          )
                        )}
                      </div>

                      {/* Resolution Actions */}
                      <div className="flex gap-2 pt-2">
                        <Button
                          variant={
                            resolution?.strategy === "local" ? "default" : "outline"
                          }
                          size="sm"
                          onClick={() =>
                            setResolution(conflict.conflictId, "local")
                          }
                        >
                          <Laptop className="w-4 h-4 mr-1" />
                          Keep Local
                        </Button>
                        <Button
                          variant={
                            resolution?.strategy === "server"
                              ? "default"
                              : "outline"
                          }
                          size="sm"
                          onClick={() =>
                            setResolution(conflict.conflictId, "server")
                          }
                        >
                          <Server className="w-4 h-4 mr-1" />
                          Keep Server
                        </Button>
                        <Button
                          variant={
                            resolution?.strategy === "merge" ? "default" : "outline"
                          }
                          size="sm"
                          onClick={() =>
                            setResolution(
                              conflict.conflictId,
                              "merge",
                              mergeData(
                                conflict.localVersion.data,
                                conflict.serverVersion.data
                              )
                            )
                          }
                        >
                          <GitMerge className="w-4 h-4 mr-1" />
                          Smart Merge
                        </Button>
                        <Button
                          variant={
                            resolution?.strategy === "skip" ? "default" : "outline"
                          }
                          size="sm"
                          onClick={() =>
                            setResolution(conflict.conflictId, "skip")
                          }
                        >
                          <X className="w-4 h-4 mr-1" />
                          Skip
                        </Button>
                      </div>
                    </div>
                  )}
                </Card>
              );
            })}
          </div>
        </ScrollArea>

        <AlertDialog.Footer className="border-t pt-4 mt-4">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button
            onClick={handleResolve}
            disabled={!allResolved || isResolving}
          >
            {isResolving ? (
              <>
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin mr-2" />
                Resolving...
              </>
            ) : (
              <>
                <Check className="w-4 h-4 mr-1" />
                Apply Resolutions
              </>
            )}
          </Button>
        </AlertDialog.Footer>
      </AlertDialog.Content>
    </AlertDialog>
  );
}

function formatValue(value: unknown): string {
  if (value === null) return "null";
  if (value === undefined) return "undefined";
  if (typeof value === "object") {
    const str = JSON.stringify(value);
    return str.length > 100 ? str.substring(0, 100) + "..." : str;
  }
  return String(value);
}

function mergeData(
  local: Record<string, unknown>,
  server: Record<string, unknown>
): Record<string, unknown> {
  // Simple merge: take the newer values, preferring server for conflicts
  // In a real implementation, this would be more sophisticated
  return { ...local, ...server };
}

// Hook for conflict management
export function useConflictResolution(tenantId: string, userId: string) {
  const [conflicts, setConflicts] = useState<Conflict[]>([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [isResolving, setIsResolving] = useState(false);

  const checkForConflicts = async () => {
    try {
      // This would check for conflicts between local and server data
      const detectedConflicts: Conflict[] = []; // Would fetch from sync service
      setConflicts(detectedConflicts);

      if (detectedConflicts.length > 0) {
        setIsModalOpen(true);
      }

      return detectedConflicts;
    } catch (error) {
      console.error("Failed to check for conflicts:", error);
      return [];
    }
  };

  const resolveConflicts = async (resolutions: ConflictResolution[]) => {
    setIsResolving(true);
    try {
      // Apply resolutions
      for (const resolution of resolutions) {
        if (resolution.strategy === "skip") continue;

        // Apply the resolution strategy
        await applyResolution(resolution);
      }

      // Refresh conflicts
      await checkForConflicts();
      setIsModalOpen(false);
    } catch (error) {
      console.error("Failed to resolve conflicts:", error);
      throw error;
    } finally {
      setIsResolving(false);
    }
  };

  return {
    conflicts,
    isModalOpen,
    isResolving,
    checkForConflicts,
    resolveConflicts,
    closeModal: () => setIsModalOpen(false),
  };
}

async function applyResolution(_resolution: ConflictResolution): Promise<void> {
  // Would apply the resolution to the local data
  // and sync the result to the server
}
