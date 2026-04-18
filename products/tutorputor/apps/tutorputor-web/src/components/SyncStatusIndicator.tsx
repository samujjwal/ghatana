/**
 * Sync Status Indicator Component
 *
 * Shows real-time sync state including:
 * - Online/offline status
 * - Pending changes count
 * - Last sync time
 * - Sync progress indicator
 * - Manual sync trigger
 *
 * @doc.type component
 * @doc.purpose Display synchronization state for offline-first features
 * @doc.layer product
 * @doc.pattern Component
 */
import { useState, useEffect, useCallback } from "react";
import {
  Cloud,
  CloudOff,
  CheckCircle2,
  Loader2,
  RefreshCw,
  ArrowUp,
  ArrowDown,
} from "lucide-react";
import { Badge, Button, Tooltip } from "@ghatana/design-system";

export type SyncState = "idle" | "syncing" | "error" | "offline";

export interface SyncStatus {
  state: SyncState;
  pendingUploads: number;
  pendingDownloads: number;
  lastSyncAt: Date | null;
  lastError?: string;
  syncProgress?: {
    current: number;
    total: number;
    operation: string;
  };
}

interface SyncStatusIndicatorProps {
  status: SyncStatus;
  onSync: () => Promise<void>;
  showDetails?: boolean;
  compact?: boolean;
}

export function SyncStatusIndicator({
  status,
  onSync,
  showDetails = true,
  compact = false,
}: SyncStatusIndicatorProps) {
  const [isSyncing, setIsSyncing] = useState(false);

  const handleSync = useCallback(async () => {
    if (isSyncing || status.state === "offline") return;

    setIsSyncing(true);
    try {
      await onSync();
    } finally {
      setIsSyncing(false);
    }
  }, [isSyncing, status.state, onSync]);

  const getStatusIcon = () => {
    if (isSyncing || status.state === "syncing") {
      return (
        <Loader2 className="w-4 h-4 animate-spin text-blue-500" />
      );
    }

    switch (status.state) {
      case "offline":
        return <CloudOff className="w-4 h-4 text-gray-400" />;
      case "error":
        return <CloudOff className="w-4 h-4 text-red-500" />;
      case "idle":
        if (status.pendingUploads > 0 || status.pendingDownloads > 0) {
          return <RefreshCw className="w-4 h-4 text-yellow-500" />;
        }
        return <CheckCircle2 className="w-4 h-4 text-green-500" />;
      default:
        return <Cloud className="w-4 h-4 text-blue-500" />;
    }
  };

  const getStatusLabel = () => {
    if (isSyncing || status.state === "syncing") {
      if (status.syncProgress) {
        return `Syncing... ${Math.round(
          (status.syncProgress.current / status.syncProgress.total) * 100
        )}%`;
      }
      return "Syncing...";
    }

    switch (status.state) {
      case "offline":
        return "Offline";
      case "error":
        return "Sync Error";
      case "idle":
        if (status.pendingUploads > 0 || status.pendingDownloads > 0) {
          return "Changes Pending";
        }
        return "Synced";
      default:
        return "Unknown";
    }
  };

  const getBadgeVariant = () => {
    if (isSyncing || status.state === "syncing") return "default";
    if (status.state === "offline") return "secondary";
    if (status.state === "error") return "destructive";
    if (status.pendingUploads > 0 || status.pendingDownloads > 0)
      return "outline";
    return "default";
  };

  if (compact) {
    return (
      <Tooltip content={getStatusLabel()}>
        <button
          onClick={handleSync}
          disabled={isSyncing || status.state === "offline"}
          className="p-2 hover:bg-gray-100 rounded-full transition-colors disabled:opacity-50"
        >
          {getStatusIcon()}
          {(status.pendingUploads > 0 || status.pendingDownloads > 0) && (
            <span className="absolute -top-1 -right-1 w-2 h-2 bg-red-500 rounded-full" />
          )}
        </button>
      </Tooltip>
    );
  }

  return (
    <div className="flex items-center gap-3 p-3 bg-white rounded-lg border shadow-sm">
      <div className="flex items-center gap-2">
        {getStatusIcon()}
        <Badge variant={getBadgeVariant()} className="text-xs">
          {getStatusLabel()}
        </Badge>
      </div>

      {showDetails && status.state !== "offline" && (
        <div className="flex items-center gap-4 text-sm text-gray-500">
          {status.pendingUploads > 0 && (
            <div className="flex items-center gap-1">
              <ArrowUp className="w-3 h-3" />
              <span>{status.pendingUploads} to upload</span>
            </div>
          )}

          {status.pendingDownloads > 0 && (
            <div className="flex items-center gap-1">
              <ArrowDown className="w-3 h-3" />
              <span>{status.pendingDownloads} to download</span>
            </div>
          )}

          {status.lastSyncAt && (
            <span className="text-xs">
              Last sync: {formatLastSync(status.lastSyncAt)}
            </span>
          )}
        </div>
      )}

      {status.state === "offline" && (
        <span className="text-sm text-gray-500">
          Working offline - changes saved locally
        </span>
      )}

      {status.state === "error" && status.lastError && (
        <span className="text-sm text-red-500">{status.lastError}</span>
      )}

      <Button
        variant="ghost"
        size="sm"
        onClick={handleSync}
        disabled={isSyncing || status.state === "offline"}
        className="ml-auto"
      >
        <RefreshCw
          className={`w-4 h-4 mr-1 ${isSyncing ? "animate-spin" : ""}`}
        />
        Sync Now
      </Button>
    </div>
  );
}

function formatLastSync(date: Date): string {
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);

  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes}m ago`;
  if (hours < 24) return `${hours}h ago`;
  return date.toLocaleDateString();
}

// Hook for managing sync status
export function useSyncStatus(tenantId: string, userId: string) {
  const [status, setStatus] = useState<SyncStatus>({
    state: "idle",
    pendingUploads: 0,
    pendingDownloads: 0,
    lastSyncAt: null,
  });
  const [isOnline, setIsOnline] = useState(navigator.onLine);

  // Monitor online status
  useEffect(() => {
    const handleOnline = () => setIsOnline(true);
    const handleOffline = () => setIsOnline(false);

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
    };
  }, []);

  // Update sync status when online changes
  useEffect(() => {
    setStatus((prev) => ({
      ...prev,
      state: isOnline ? prev.state : "offline",
    }));
  }, [isOnline]);

  // Fetch pending changes count
  const refreshStatus = useCallback(async () => {
    if (!isOnline) return;

    try {
      // This would query IndexedDB for pending changes
      const pendingUploads = await getPendingUploadsCount(tenantId, userId);
      const pendingDownloads = await getPendingDownloadsCount(tenantId, userId);

      setStatus((prev) => ({
        ...prev,
        pendingUploads,
        pendingDownloads,
      }));
    } catch (error) {
      console.error("Failed to refresh sync status:", error);
    }
  }, [tenantId, userId, isOnline]);

  // Initial fetch and periodic refresh
  useEffect(() => {
    refreshStatus();
    const interval = setInterval(refreshStatus, 30000);
    return () => clearInterval(interval);
  }, [refreshStatus]);

  const sync = useCallback(async () => {
    if (!isOnline) return;

    setStatus((prev) => ({ ...prev, state: "syncing" }));

    try {
      // Perform sync operations
      await performSync(tenantId, userId);

      setStatus({
        state: "idle",
        pendingUploads: 0,
        pendingDownloads: 0,
        lastSyncAt: new Date(),
      });
    } catch (error) {
      setStatus((prev) => ({
        ...prev,
        state: "error",
        lastError: error instanceof Error ? error.message : "Sync failed",
      }));
    }
  }, [tenantId, userId, isOnline]);

  return {
    status,
    isOnline,
    sync,
    refreshStatus,
  };
}

// Placeholder functions - would integrate with actual sync infrastructure
async function getPendingUploadsCount(
  _tenantId: string,
  _userId: string,
): Promise<number> {
  // Would query IndexedDB for pending changes
  return 0;
}

async function getPendingDownloadsCount(
  _tenantId: string,
  _userId: string,
): Promise<number> {
  // Would query server for available updates
  return 0;
}

async function performSync(_tenantId: string, _userId: string): Promise<void> {
  // Would perform actual sync operations
  await new Promise((resolve) => setTimeout(resolve, 1000));
}
