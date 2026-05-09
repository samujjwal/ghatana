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

import React from 'react';
import { useSurfaceRegistry, getSurfaceSignal, isSurfaceAvailable } from '../../api/surfaces.service';
import { LoadingState } from '../common/LoadingState';

export interface RuntimeCapabilityRouteGateProps {
  aliases: string[];
  children: React.ReactNode;
  /** Rendered when surface is DISABLED, UNAVAILABLE, or MISCONFIGURED. */
  fallback?: React.ReactNode;
  /** When true, renders a full-height loading skeleton instead of children while registry loads. */
  blockWhileLoading?: boolean;
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
}: RuntimeCapabilityRouteGateProps): React.ReactElement {
  const { data, isLoading } = useSurfaceRegistry();

  // DC-P1-002: Block rendering of optional surfaces until registry truth loads.
  if (isLoading && blockWhileLoading) {
    return <LoadingState message="Checking surface availability..." className="w-full h-64" />;
  }

  const signal = getSurfaceSignal(data?.surfaces, aliases);
  const allowed = isSurfaceAvailable(signal);

  if (!allowed) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}

