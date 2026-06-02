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
import { useTranslation } from "react-i18next";
import {
  getSurfaceSignal,
  isSurfaceAvailable,
  type SurfaceSignal,
  useSurfaceRegistry,
} from "../../api/surfaces.service";
import { DisabledSurfacePage } from "../../pages/DisabledSurfacePage";
import { LoadingState } from "../common/LoadingState";

export interface RuntimeCapabilityRouteGateProps {
  surfaceId: string;
  children: React.ReactNode;
  /** Rendered when surface is DISABLED, UNAVAILABLE, or MISCONFIGURED. */
  fallback?: React.ReactNode;
  /** When true, renders a full-height loading skeleton instead of children while registry loads. */
  blockWhileLoading?: boolean;
  /** WS1: Allows PREVIEW surfaces to render with a preview badge. Defaults to false - explicit opt-in required. */
  allowPreview?: boolean;
  /** WS1: Preview audience for controlled preview access. Must match backend surface audience. */
  allowPreviewFor?: "internal" | "operator" | "admin";
}

function surfaceNameFromSurfaceId(surfaceId: string, t: (key: string) => string): string {
  if (!surfaceId) {
    return t("runtimeGate.defaultSurfaceName");
  }
  return surfaceId
    .replace(/[_.-]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\b\w/g, (character) => character.toUpperCase());
}

function toDisabledStatus(
  status: SurfaceSignal["status"] | undefined,
  signal: SurfaceSignal | undefined,
):
  | "DISABLED"
  | "UNAVAILABLE"
  | "MISCONFIGURED"
  | "TARGET_ONLY"
  | "PREVIEW_NOT_ALLOWED" {
  if (signal?.targetOnly || signal?.readinessClass === "target-only") {
    return "TARGET_ONLY";
  }
  if (status === "PREVIEW") {
    return "PREVIEW_NOT_ALLOWED";
  }
  if (status === "MISCONFIGURED") {
    return "MISCONFIGURED";
  }
  if (status === "UNAVAILABLE" || status === undefined) {
    return "UNAVAILABLE";
  }
  return "DISABLED";
}

function renderSurfaceUnavailable(
  surfaceId: string,
  signal: SurfaceSignal | undefined,
  t: (key: string) => string,
): React.ReactElement {
  return (
    <DisabledSurfacePage
      surfaceName={signal?.label ?? surfaceNameFromSurfaceId(surfaceId, t)}
      status={toDisabledStatus(signal?.status, signal)}
      ownerPlane={signal?.ownerPlane}
      requiredDependencies={signal?.requiredDependencies}
      dependencyProbes={signal?.dependencyProbes}
      limitations={signal?.limitations}
      runtimeProfile={signal?.runtimeProfile}
      // WS1: Use enriched backend fields for better user guidance
      nextAction={signal?.recommendedAction ?? signal?.detail}
      actionHint={signal?.fallbackReason}
    />
  );
}

function RuntimePostureBanner({
  signal,
  t,
}: {
  signal: SurfaceSignal;
  t: (key: string, options?: Record<string, unknown>) => string;
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
      <span className="font-medium">
        {isPreview
          ? signal.label || t("runtimeGate.preview")
          : t("runtimeGate.degraded")}
      </span>
      {signal.limitations ? <span>: {signal.limitations}</span> : null}
    </div>
  );
}

/**
 * DC-P1-002: Safe loading state — never renders optional surfaces while registry is loading.
 * Optional surfaces are gated (blockWhileLoading defaults to true).
 *
 * WS1: Preview requires both backend surface status AND UI route registry audience match.
 */
export function RuntimeCapabilityRouteGate({
  surfaceId,
  children,
  fallback = null,
  blockWhileLoading = true,
  allowPreview = false,
  allowPreviewFor,
}: RuntimeCapabilityRouteGateProps): React.ReactElement {
  const { t } = useTranslation();
  const { data, isLoading } = useSurfaceRegistry();

  // DC-P1-002: Block rendering of optional surfaces until registry truth loads.
  if (isLoading && blockWhileLoading) {
    return (
      <LoadingState
        message={t("runtimeGate.loadingMessage")}
        className="w-full h-64"
      />
    );
  }

  const signal = getSurfaceSignal(data?.surfaces, [surfaceId]);

  // Fail closed when runtime truth is missing for optional surfaces.
  if (!signal) {
    return <>{fallback ?? renderSurfaceUnavailable(surfaceId, signal, t)}</>;
  }

  if (signal.targetOnly || signal.readinessClass === "target-only") {
    return <>{fallback ?? renderSurfaceUnavailable(surfaceId, signal, t)}</>;
  }

  const allowed = isSurfaceAvailable(signal, {
    allowPreview,
    previewAudience: allowPreviewFor,
  });

  if (!allowed) {
    return <>{fallback ?? renderSurfaceUnavailable(surfaceId, signal, t)}</>;
  }

  return (
    <>
      {signal ? <RuntimePostureBanner signal={signal} t={t} /> : null}
      {children}
    </>
  );
}
