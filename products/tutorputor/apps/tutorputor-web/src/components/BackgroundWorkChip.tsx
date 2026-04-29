/**
 * Background Work Chip
 *
 * Displays the status of background content generation worker.
 * Shows a warning banner when the content worker is unavailable.
 *
 * @doc.type component
 * @doc.purpose Display background work status and worker availability
 * @doc.layer product
 * @doc.pattern Status Indicator
 */
import React, { useState, useEffect } from "react";

interface WorkerStatus {
  available: boolean;
  lastCheck?: Date;
}

export function BackgroundWorkChip() {
  const [workerStatus, setWorkerStatus] = useState<WorkerStatus>({
    available: true,
  });
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    // Check worker status periodically
    const checkWorkerStatus = async () => {
      try {
        const response = await fetch("/api/v1/health", {
          method: "GET",
        });
        const data = await response.json();
        
        // Check if content worker is available based on health check
        const isWorkerAvailable = data.checks?.content_worker === "ok" || 
                                data.checks?.content_worker === undefined; // Assume ok if not explicitly failed
        
        setWorkerStatus({
          available: isWorkerAvailable,
          lastCheck: new Date(),
        });

        // Show banner if worker is unavailable
        setIsVisible(!isWorkerAvailable);
      } catch {
        // If health check fails, assume worker is unavailable
        setWorkerStatus({
          available: false,
          lastCheck: new Date(),
        });
        setIsVisible(true);
      }
    };

    // Initial check
    checkWorkerStatus();

    // Check every 30 seconds
    const interval = setInterval(checkWorkerStatus, 30000);

    return () => clearInterval(interval);
  }, []);

  if (!isVisible) {
    return null;
  }

  return (
    <div className="bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-lg p-2 mb-2">
      <div className="flex items-center gap-2 text-xs">
        <div className="w-2 h-2 bg-amber-500 rounded-full animate-pulse" />
        <span className="text-amber-800 dark:text-amber-200 font-medium">
          Content generation worker unavailable
        </span>
        <span className="text-amber-600 dark:text-amber-400">
          Some features may be limited
        </span>
      </div>
    </div>
  );
}
