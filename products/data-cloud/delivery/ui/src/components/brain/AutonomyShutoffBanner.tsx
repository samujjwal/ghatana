/**
 * AutonomyShutoffBanner — Global emergency autonomy shutoff toggle and status banner.
 *
 * Renders a persistent warning banner when autonomous actions are halted (SUGGEST level).
 * The toggle is gated by the ADMIN role via RBACGuard.
 * Satisfies B9: "Emergency autonomy shutoff has no UI".
 *
 * @doc.type component
 * @doc.purpose Emergency global autonomy shutoff banner for operator safety
 * @doc.layer product
 * @doc.pattern Control Component
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Shield, ShieldOff, X } from "lucide-react";
import React, { useCallback, useState } from "react";
import { brainService } from "../../api/brain.service";
import { cn } from "../../lib/theme";
import { RBACGuard } from "../security/RBACGuard";

interface ShutoffLevel {
  globalOverride: string;
  shutoffActive: boolean;
  domainCount: number;
}

interface AutonomyShutoffBannerProps {
  /** Called after a successful level change so the parent can refresh state. */
  onLevelChange?: (shutoffActive: boolean) => void;
}

/**
 * AutonomyShutoffBanner
 *
 * Shows a persistent amber/red banner when `shutoffActive` is true (SUGGEST mode).
 * The toggle button is only visible to ADMIN-role users (RBACGuard).
 */
export function AutonomyShutoffBanner({
  onLevelChange,
}: AutonomyShutoffBannerProps): React.ReactElement | null {
  const queryClient = useQueryClient();
  const [confirmPending, setConfirmPending] = useState<
    "halt" | "restore" | null
  >(null);

  const { data: globalLevel } = useQuery<ShutoffLevel>({
    queryKey: ["autonomy", "global-level"],
    queryFn: () => brainService.getGlobalAutonomyLevel(),
    refetchInterval: 60_000,
    staleTime: 30_000,
    retry: 1,
  });

  const setLevelMutation = useMutation({
    mutationFn: ({
      level,
      reason,
    }: {
      level: "SUGGEST" | "CONFIRM" | "NOTIFY" | "AUTONOMOUS";
      reason: string;
    }) => brainService.setGlobalAutonomyLevel(level, reason),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ["autonomy"] });
      const shutoffActive = result.globalLevel === "SUGGEST";
      onLevelChange?.(shutoffActive);
      setConfirmPending(null);
    },
  });

  const handleHalt = useCallback(() => {
    setConfirmPending("halt");
  }, []);

  const handleRestore = useCallback(() => {
    setConfirmPending("restore");
  }, []);

  const confirmHalt = useCallback(() => {
    setLevelMutation.mutate({
      level: "SUGGEST",
      reason: "Operator emergency shutoff via AppShell banner",
    });
  }, [setLevelMutation]);

  const confirmRestore = useCallback(() => {
    setLevelMutation.mutate({
      level: "NOTIFY",
      reason: "Operator restored autonomy via AppShell banner",
    });
  }, [setLevelMutation]);

  const cancelConfirm = useCallback(() => {
    setConfirmPending(null);
  }, []);

  // Don't render until we have state from the API
  if (!globalLevel) return null;

  const { shutoffActive } = globalLevel;

  return (
    <>
      {/* Persistent banner — only visible when shutoff is active */}
      {shutoffActive && (
        <div
          role="alert"
          aria-live="assertive"
          className={cn(
            "flex items-center justify-between gap-4 px-4 py-2.5",
            "bg-amber-50 border-b border-amber-300 text-amber-900 text-sm",
          )}
        >
          <div className="flex items-center gap-2">
            <ShieldOff
              className="h-4 w-4 text-amber-600 flex-shrink-0"
              aria-hidden="true"
            />
            <span className="font-medium">
              Autonomy HALTED — all AI actions require human approval
            </span>
          </div>
          <RBACGuard permission="ADMIN">
            <button
              onClick={handleRestore}
              disabled={setLevelMutation.isPending}
              className="flex items-center gap-1.5 px-3 py-1 rounded-md bg-amber-600 hover:bg-amber-700 text-white text-xs font-medium transition-colors disabled:opacity-50"
              aria-label="Restore autonomy to notify level"
            >
              <Shield className="h-3 w-3" />
              Restore Autonomy
            </button>
          </RBACGuard>
        </div>
      )}

      {/* Inline toggle shown in header for ADMIN users when autonomy is active */}
      {!shutoffActive && (
        <RBACGuard permission="ADMIN">
          <div className="flex items-center gap-2 px-4">
            <button
              onClick={handleHalt}
              disabled={setLevelMutation.isPending}
              title="Emergency: halt all autonomous AI actions"
              aria-label="Emergency halt all autonomous AI actions"
              className={cn(
                "flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium transition-colors",
                "bg-gray-100 hover:bg-red-50 hover:text-red-700 text-gray-600",
                "border border-gray-200 hover:border-red-200",
                "disabled:opacity-50",
              )}
            >
              <ShieldOff className="h-3.5 w-3.5" />
              <span className="sr-only sm:not-sr-only">Halt Autonomy</span>
            </button>
          </div>
        </RBACGuard>
      )}

      {/* Confirmation dialog */}
      {confirmPending && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="autonomy-confirm-title"
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
        >
          <div className="bg-white rounded-xl shadow-xl p-6 w-full max-w-md mx-4">
            <div className="flex items-start justify-between mb-4">
              <div className="flex items-center gap-3">
                <div
                  className={cn(
                    "p-2 rounded-lg",
                    confirmPending === "halt" ? "bg-red-100" : "bg-green-100",
                  )}
                >
                  {confirmPending === "halt" ? (
                    <ShieldOff className="h-5 w-5 text-red-600" />
                  ) : (
                    <Shield className="h-5 w-5 text-green-600" />
                  )}
                </div>
                <h2
                  id="autonomy-confirm-title"
                  className="text-lg font-semibold text-gray-900"
                >
                  {confirmPending === "halt"
                    ? "Halt all autonomous actions?"
                    : "Restore autonomous actions?"}
                </h2>
              </div>
              <button
                onClick={cancelConfirm}
                aria-label="Cancel"
                className="p-1 rounded hover:bg-gray-100"
              >
                <X className="h-5 w-5 text-gray-400" />
              </button>
            </div>

            <p className="text-sm text-gray-600 mb-6">
              {confirmPending === "halt" ? (
                <>
                  This will set all {globalLevel.domainCount} AI domains to{" "}
                  <strong>SUGGEST</strong> level — no action will be taken
                  without explicit human approval. A persistent warning banner
                  will appear until autonomy is restored. This override is
                  logged in the autonomy audit trail.
                </>
              ) : (
                <>
                  This will restore autonomy to <strong>NOTIFY</strong> level
                  across all domains (AI acts and notifies). Upgrade individual
                  domains in the Autonomy settings if needed. This action is
                  logged in the audit trail.
                </>
              )}
            </p>

            <div className="flex gap-3 justify-end">
              <button
                onClick={cancelConfirm}
                className="px-4 py-2 rounded-lg border border-gray-300 text-sm text-gray-700 hover:bg-gray-50 transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={
                  confirmPending === "halt" ? confirmHalt : confirmRestore
                }
                disabled={setLevelMutation.isPending}
                className={cn(
                  "px-4 py-2 rounded-lg text-sm font-medium text-white transition-colors disabled:opacity-50",
                  confirmPending === "halt"
                    ? "bg-red-600 hover:bg-red-700"
                    : "bg-green-600 hover:bg-green-700",
                )}
              >
                {setLevelMutation.isPending
                  ? "Applying…"
                  : confirmPending === "halt"
                    ? "Halt Autonomy"
                    : "Restore Autonomy"}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}

export default AutonomyShutoffBanner;
