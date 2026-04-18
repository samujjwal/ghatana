/**
 * Offline Indicator Component
 *
 * Visual indicator for offline/online state with:
 * - Connection status display
 * - Automatic reconnection attempts
 * - Offline mode badge
 * - Queue status for pending actions
 *
 * @doc.type component
 * @doc.purpose Display offline/online connectivity status
 * @doc.layer product
 * @doc.pattern Component
 */
import { useState, useEffect } from "react";
import { Wifi, WifiOff, RefreshCw, CloudOff } from "lucide-react";
import { Badge, Button } from "@ghatana/design-system";

export type ConnectionState = "online" | "offline" | "reconnecting";

export interface OfflineIndicatorProps {
  showWhenOnline?: boolean;
  position?: "top" | "bottom" | "inline";
  pendingActions?: number;
  onReconnect?: () => void;
}

export function OfflineIndicator({
  showWhenOnline = false,
  position = "top",
  pendingActions = 0,
  onReconnect,
}: OfflineIndicatorProps) {
  const [connectionState, setConnectionState] = useState<ConnectionState>(
    navigator.onLine ? "online" : "offline"
  );
  const [lastOnlineAt, setLastOnlineAt] = useState<Date | null>(
    navigator.onLine ? new Date() : null
  );

  useEffect(() => {
    const handleOnline = () => {
      setConnectionState("online");
      setLastOnlineAt(new Date());
    };

    const handleOffline = () => {
      setConnectionState("offline");
    };

    window.addEventListener("online", handleOnline);
    window.addEventListener("offline", handleOffline);

    // Check connection periodically
    const checkInterval = setInterval(() => {
      if (navigator.onLine && connectionState === "offline") {
        setConnectionState("online");
        setLastOnlineAt(new Date());
      }
    }, 5000);

    return () => {
      window.removeEventListener("online", handleOnline);
      window.removeEventListener("offline", handleOffline);
      clearInterval(checkInterval);
    };
  }, [connectionState]);

  const handleReconnect = () => {
    setConnectionState("reconnecting");
    onReconnect?.();

    // Reset after a delay
    setTimeout(() => {
      if (navigator.onLine) {
        setConnectionState("online");
      } else {
        setConnectionState("offline");
      }
    }, 3000);
  };

  if (connectionState === "online" && !showWhenOnline) {
    return null;
  }

  const getPositionClasses = () => {
    switch (position) {
      case "top":
        return "fixed top-0 left-0 right-0 z-50";
      case "bottom":
        return "fixed bottom-0 left-0 right-0 z-50";
      case "inline":
        return "relative";
    }
  };

  const getContent = () => {
    switch (connectionState) {
      case "online":
        return (
          <div className="flex items-center gap-2 px-4 py-2 bg-green-50 text-green-800">
            <Wifi className="w-4 h-4" />
            <span className="text-sm font-medium">Back Online</span>
            {lastOnlineAt && (
              <span className="text-xs text-green-600">
                Connected at {lastOnlineAt.toLocaleTimeString()}
              </span>
            )}
          </div>
        );

      case "offline":
        return (
          <div className="flex items-center justify-between px-4 py-3 bg-amber-50 text-amber-800 border-b border-amber-200">
            <div className="flex items-center gap-2">
              <WifiOff className="w-4 h-4" />
              <span className="text-sm font-medium">You are offline</span>
              {pendingActions > 0 && (
                <Badge variant="outline" className="text-xs bg-amber-100">
                  {pendingActions} pending
                </Badge>
              )}
            </div>
            <Button
              variant="ghost"
              size="sm"
              onClick={handleReconnect}
              className="text-amber-700 hover:text-amber-800 hover:bg-amber-100"
            >
              <RefreshCw className="w-4 h-4 mr-1" />
              Retry
            </Button>
          </div>
        );

      case "reconnecting":
        return (
          <div className="flex items-center gap-2 px-4 py-2 bg-blue-50 text-blue-800">
            <RefreshCw className="w-4 h-4 animate-spin" />
            <span className="text-sm font-medium">Reconnecting...</span>
          </div>
        );
    }
  };

  return (
    <div className={getPositionClasses()}>
      {getContent()}
    </div>
  );
}

// Compact badge version for inline use
export function ConnectionBadge({
  className,
}: {
  className?: string;
}) {
  const [isOnline, setIsOnline] = useState(navigator.onLine);

  useEffect(() => {
    const update = () => setIsOnline(navigator.onLine);
    window.addEventListener("online", update);
    window.addEventListener("offline", update);
    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);

  return (
    <Badge
      variant={isOnline ? "default" : "secondary"}
      className={`${className} ${isOnline ? "bg-green-100 text-green-800" : ""}`}
    >
      {isOnline ? (
        <>
          <Wifi className="w-3 h-3 mr-1" />
          Online
        </>
      ) : (
        <>
          <CloudOff className="w-3 h-3 mr-1" />
          Offline
        </>
      )}
    </Badge>
  );
}

// Hook for connection status
export function useConnectionStatus() {
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [connectionType, setConnectionType] = useState<string>("unknown");

  useEffect(() => {
    const update = () => {
      setIsOnline(navigator.onLine);
      if ("connection" in navigator) {
        const conn = (navigator as Navigator & { connection?: NetworkInformation }).connection;
        setConnectionType(conn?.effectiveType || "unknown");
      }
    };

    window.addEventListener("online", update);
    window.addEventListener("offline", update);

    if ("connection" in navigator) {
      const conn = (navigator as Navigator & { connection?: NetworkInformation }).connection;
      conn?.addEventListener("change", update);
    }

    update();

    return () => {
      window.removeEventListener("online", update);
      window.removeEventListener("offline", update);
    };
  }, []);

  return { isOnline, connectionType };
}

// Network Information interface (not fully supported in all browsers)
interface NetworkInformation {
  effectiveType: string;
  addEventListener: (type: string, listener: () => void) => void;
}
