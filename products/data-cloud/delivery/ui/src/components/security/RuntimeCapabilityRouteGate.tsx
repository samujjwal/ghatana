/**
 * Runtime surface route gate.
 *
 * Guards optional route surfaces based on live surface registry state so UI
 * route accessibility follows backend runtime truth.
 *
 * DC-P1-001: Updated to use surfaces.service.ts (canonical /surfaces endpoint).
 * DC-P1-002: Shows safe skeleton/disabled state while registry is loading —
 *   optional surfaces never flash visible before runtime truth loads.
 *
 * @doc.type component
 * @doc.purpose Route-level runtime surface gate for optional surfaces
 * @doc.layer frontend
 * @doc.pattern Guard
 */

import React from "react";
import {
  getSurfaceSignal,
  isSurfaceAvailable,
  type SurfaceSignal,
  useSurfaceRegistry,
} from "../../api/surfaces.service";
import { DisabledSurfacePage } from "../../pages/DisabledSurfacePage";
import { LoadingState } from "../common/LoadingState";

export interface RuntimeCapabilityRouteGateProps {
  aliases: string[];
  children: React.ReactNode;
  /** Rendered when surface is DISABLED, UNAVAILABLE, or MISCONFIGURED. */
  fallback?: React.ReactNode;
  /** When true, renders a full-height loading skeleton instead of children while registry loads. */
  blockWhileLoading?: boolean;
  /** Allows PREVIEW surfaces to render with a preview badge. */
  allowPreview?: boolean;
}

function surfaceNameFromAliases(aliases: readonly string[]): string {
  const [firstAlias] = aliases;
  if (!firstAlias) {
    return "This surface";
  }
  return firstAlias
    .replace(/[_.-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (character) => character.toUpperCase());
}

function toDisabledStatus(
  status: SurfaceSignal["status"] | undefined,
): "DISABLED" | "UNAVAILABLE" | "MISCONFIGURED" {
  if (status === "MISCONFIGURED") {
    return "MISCONFIGURED";
  }
  if (status === "UNAVAILABLE" || status === undefined) {
    return "UNAVAILABLE";
  }
  return "DISABLED";
}

function renderSurfaceUnavailable(
  aliases: readonly string[],
  signal: SurfaceSignal | undefined,
): React.ReactElement {
  return (
    <DisabledSurfacePage
      surfaceName={signal?.label ?? surfaceNameFromAliases(aliases)}
      status={toDisabledStatus(signal?.status)}
      ownerPlane={signal?.ownerPlane}
      requiredDependencies={signal?.requiredDependencies}
      dependencyProbes={signal?.dependencyProbes}
      limitations={signal?.limitations}
      runtimeProfile={signal?.runtimeProfile}
      nextAction={signal?.detail}
    />
  );
}

function RuntimePostureBanner({
  signal,
}: {
  signal: SurfaceSignal;
}): React.ReactElement | null {
  if (signal.status !== "DEGRADED" && signal.status !== "PREVIEW") {
    return null;
  }
  const isPreview = signal.status === "PREVIEW";
  return (
    <div
      role={isPreview ? "status" : "alert"}
      className={
        isPreview
          ? "mb-3 rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900 dark:border-blue-700 dark:bg-blue-950/40 dark:text-blue-100"
          : "mb-3 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-100"
      }
    >
      <span className="font-medium">{isPreview ? "Preview" : "Degraded"}</span>
      {signal.limitations ? <span>: {signal.limitations}</span> : null}
    </div>
  );
}

/**
 * DC-P1-002: Safe loading state — never renders optional surfaces while registry is loading.
 * Optional surfaces are gated (blockWhileLoading defaults to true).
 */
export function RuntimeCapabilityRouteGate({
  aliases,
  children,
  fallback = null,
  blockWhileLoading = true,
  allowPreview = false,
}: RuntimeCapabilityRouteGateProps): React.ReactElement {
  const { data, isLoading } = useSurfaceRegistry();

  // DC-P1-002: Block rendering of optional surfaces until registry truth loads.
  if (isLoading && blockWhileLoading) {
    return (
      <LoadingState
        message="Checking surface availability..."
        className="w-full h-64"
      />
    );
  }

  const signal = getSurfaceSignal(data?.surfaces, aliases);
  const allowed =
    isSurfaceAvailable(signal) &&
    (signal?.status !== "PREVIEW" || allowPreview);

  if (!allowed) {
    return <>{fallback ?? renderSurfaceUnavailable(aliases, signal)}</>;
  }

  return (
    <>
      {signal ? <RuntimePostureBanner signal={signal} /> : null}
      {children}
    </>
  );
}
