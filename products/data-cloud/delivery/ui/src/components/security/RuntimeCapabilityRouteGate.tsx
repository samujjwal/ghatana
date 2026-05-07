/**
 * Runtime capability route gate.
 *
 * Guards optional route surfaces based on live capability registry state so UI
 * route accessibility follows backend capability truth.
 *
 * @doc.type component
 * @doc.purpose Route-level runtime capability gate for optional surfaces
 * @doc.layer frontend
 * @doc.pattern Guard
 */

import React from 'react';
import { useCapabilityGate, type GateMode } from '../../hooks/useCapabilityGate';
import { useCapabilityRegistry } from '../../api/capabilities.service';

export interface RuntimeCapabilityRouteGateProps {
  aliases: string[];
  mode?: GateMode;
  children: React.ReactNode;
  fallback?: React.ReactNode;
}

export function RuntimeCapabilityRouteGate({
  aliases,
  mode = 'notUnavailable',
  children,
  fallback = null,
}: RuntimeCapabilityRouteGateProps): React.ReactElement {
  const { data, isLoading } = useCapabilityRegistry();
  const allowed = useCapabilityGate(aliases, mode);

  // Keep route stable while capabilities are loading to avoid redirect flicker.
  if (isLoading && !data) {
    return <>{children}</>;
  }

  if (!allowed) {
    return <>{fallback}</>;
  }

  return <>{children}</>;
}
