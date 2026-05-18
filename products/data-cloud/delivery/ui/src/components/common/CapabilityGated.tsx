/**
 * Capability-gated render wrapper (P0.7).
 *
 * Conditionally renders children only when the runtime surface registry
 * reports the required capability as active (or degraded, depending on mode).
 *
 * @doc.type component
 * @doc.purpose Runtime capability-based UI gating
 * @doc.layer frontend
 */

import React from "react";
import {
  useCapabilityGate,
  type GateMode,
} from "../../hooks/useCapabilityGate";
import { useSurfaceRegistry } from "../../api/surfaces.service";
import { Loader2 } from "lucide-react";

interface CapabilityGatedProps {
  /** Capability aliases to check (e.g., 'ai.assist', 'search.openSearch') */
  aliases: string[];
  /** Gate strictness — active: only active; activeOrDegraded: active or degraded; notUnavailable: anything but unavailable */
  mode?: GateMode;
  /** Shown while capability state is loading */
  loadingFallback?: React.ReactNode;
  /** Shown when capability is gated */
  fallback?: React.ReactNode;
  children: React.ReactNode;
}

export const CapabilityGated: React.FC<CapabilityGatedProps> = ({
  aliases,
  mode = "active",
  loadingFallback,
  fallback,
  children,
}) => {
  const { data: registry, isLoading } = useSurfaceRegistry();
  const allowed = useCapabilityGate(aliases, mode);

  if (isLoading && !registry) {
    if (loadingFallback !== undefined) {
      return <>{loadingFallback}</>;
    }
    return (
      <span className="inline-flex items-center gap-2 text-sm text-gray-500">
        <Loader2 className="h-4 w-4 animate-spin" />
        Checking runtime surface...
      </span>
    );
  }

  if (!allowed) {
    if (fallback !== undefined) {
      return <>{fallback}</>;
    }
    return null;
  }

  return <>{children}</>;
};
