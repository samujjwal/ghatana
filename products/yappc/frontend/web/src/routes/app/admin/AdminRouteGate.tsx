import type React from 'react';

import { useCapabilityGate } from '../../../hooks/useCapabilityGate';

interface AdminRouteGateMessages {
  readonly permissionDenied: string;
  readonly loginRequired: string;
  readonly unavailable: string;
}

const DEFAULT_MESSAGES: AdminRouteGateMessages = {
  permissionDenied: 'You do not have permission to access this page.',
  loginRequired: 'Please log in to access this page.',
  unavailable: 'This feature is not yet available.',
};

interface AdminRouteGateProps {
  readonly capability: string;
  readonly children: React.ReactNode;
  readonly messages?: AdminRouteGateMessages;
  readonly deniedTestId?: string;
}

export function AdminRouteGate({
  capability,
  children,
  messages = DEFAULT_MESSAGES,
  deniedTestId,
}: AdminRouteGateProps): React.ReactNode {
  const { granted, reason } = useCapabilityGate(capability);

  if (!granted) {
    const message =
      reason === 'insufficient-role'
        ? messages.permissionDenied
        : reason === 'unauthenticated'
          ? messages.loginRequired
          : messages.unavailable;

    return (
      <div className="flex min-h-[60vh] items-center justify-center" data-testid={deniedTestId}>
        <div className="space-y-2 text-center">
          <p className="text-sm text-fg-muted">{message}</p>
        </div>
      </div>
    );
  }

  return children;
}
