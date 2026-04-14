/**
 * @ghatana/privacy-ui — Privacy UI platform stub.
 *
 * Stub implementation until the full platform package is built.
 *
 * @doc.type module
 * @doc.purpose Privacy consent management components and hooks
 * @doc.layer platform
 * @doc.pattern Library
 */

import React from 'react';

// ── Types ──────────────────────────────────────────────────────────────────────

export interface ConsentRecord {
  id: string;
  userId: string;
  purpose: string;
  granted: boolean;
  timestamp: string;
  expiresAt?: string;
}

export interface ConsentManagerProps {
  purpose: string;
  onConsentChange?: (granted: boolean) => void;
  requireConsent?: boolean;
  consentMessage?: string;
  children?: React.ReactNode;
}

export interface UseConsentReturn {
  consentGranted: boolean;
  grantConsent: () => void;
  revokeConsent: () => void;
}

// ── Helpers ────────────────────────────────────────────────────────────────────

function loadConsentRecord(purpose: string): ConsentRecord | null {
  try {
    const raw = localStorage.getItem(`consent_${purpose}`);
    if (!raw) return null;
    return JSON.parse(raw) as ConsentRecord;
  } catch {
    return null;
  }
}

function isConsentValid(record: ConsentRecord | null): boolean {
  if (!record || !record.granted) return false;
  if (record.expiresAt && new Date(record.expiresAt) < new Date()) return false;
  return true;
}

// ── Components ─────────────────────────────────────────────────────────────────

export const ConsentManager: React.FC<ConsentManagerProps> = ({
  purpose,
  onConsentChange,
  requireConsent = false,
  consentMessage,
  children,
}) => {
  const record = loadConsentRecord(purpose);
  const [consented, setConsented] = React.useState<boolean | null>(
    isConsentValid(record) ? true : record !== null ? false : null
  );

  const defaultMessage = `voice input for speech recognition and ${purpose} processing`;

  const grant = (): void => {
    setConsented(true);
    onConsentChange?.(true);
  };

  const deny = (): void => {
    setConsented(false);
    onConsentChange?.(false);
  };

  if (consented === true) {
    return React.createElement(React.Fragment, null, children);
  }

  if (consented === null && requireConsent) {
    return React.createElement(
      'div',
      null,
      React.createElement('h2', null, 'Consent Required'),
      React.createElement('p', null, consentMessage ?? defaultMessage),
      React.createElement('button', { onClick: grant }, 'Allow'),
      React.createElement('button', { onClick: deny }, 'Deny'),
    );
  }

  if (requireConsent) {
    return React.createElement(
      'div',
      null,
      React.createElement('h2', null, 'Consent Required'),
      React.createElement('p', null, consentMessage ?? defaultMessage),
      React.createElement('button', { onClick: grant }, 'Allow'),
      React.createElement('button', { onClick: deny }, 'Deny'),
    );
  }

  return React.createElement(
    'div',
    null,
    React.createElement('p', null, `This feature requires your consent to ${purpose}.`),
  );
};

// ── Hooks ──────────────────────────────────────────────────────────────────────

export function useConsent(purpose: string): UseConsentReturn {
  const record = loadConsentRecord(purpose);
  const [granted, setGranted] = React.useState(isConsentValid(record));
  return {
    consentGranted: granted,
    grantConsent: () => setGranted(true),
    revokeConsent: () => setGranted(false),
  };
}
