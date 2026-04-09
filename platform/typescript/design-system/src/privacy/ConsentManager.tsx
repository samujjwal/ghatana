/**
 * ConsentManager - Generic consent management component
 *
 * @doc.type component
 * @doc.purpose Manage user consent for privacy-sensitive features
 * @doc.layer platform
 * @doc.pattern Privacy Component
 */

import React, { createContext, useContext, useState, useEffect, type ReactNode } from 'react';

/**
 * Consent record stored in localStorage.
 */
export interface ConsentRecord {
  id: string;
  userId: string;
  purpose: string;
  granted: boolean;
  timestamp: string;
  expiresAt?: string;
}

/**
 * ConsentManager component props.
 */
export interface ConsentManagerProps {
  purpose: string;
  onConsentChange?: (granted: boolean) => void;
  requireConsent?: boolean;
  fallback?: ReactNode;
  consentMessage?: string;
  children: ReactNode;
}

const ConsentContext = createContext<{
  consentGranted: boolean;
  grantConsent: () => void;
  revokeConsent: () => void;
} | null>(null);

/**
 * Hook to read and manage consent state for a given purpose.
 */
export function useConsent(purpose: string): {
  consentGranted: boolean;
  grantConsent: () => void;
  revokeConsent: () => void;
  isLoading: boolean;
} {
  const context = useContext(ConsentContext);

  if (!context) {
    // Allow usage outside ConsentManager for standalone consent checks
  }

  const [consentGranted, setConsentGranted] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const stored = localStorage.getItem(`consent_${purpose}`);
    if (stored) {
      const consent = JSON.parse(stored) as ConsentRecord;
      const now = new Date().toISOString();
      if (consent.expiresAt && consent.expiresAt < now) {
        localStorage.removeItem(`consent_${purpose}`);
        setConsentGranted(false);
      } else {
        setConsentGranted(consent.granted);
      }
    } else {
      setConsentGranted(false);
    }
    setIsLoading(false);
  }, [purpose]);

  const grantConsent = (): void => {
    const consent: ConsentRecord = {
      id: `consent_${Date.now()}`,
      userId: 'current',
      purpose,
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString(),
    };
    localStorage.setItem(`consent_${purpose}`, JSON.stringify(consent));
    setConsentGranted(true);
  };

  const revokeConsent = (): void => {
    const consent: ConsentRecord = {
      id: `consent_${Date.now()}`,
      userId: 'current',
      purpose,
      granted: false,
      timestamp: new Date().toISOString(),
    };
    localStorage.setItem(`consent_${purpose}`, JSON.stringify(consent));
    setConsentGranted(false);
  };

  return { consentGranted, grantConsent, revokeConsent, isLoading };
}

/**
 * Consent Manager Component — wraps children, optionally blocking access until
 * the user explicitly grants consent for the given purpose.
 */
export function ConsentManager({
  purpose,
  onConsentChange,
  requireConsent = false,
  fallback,
  consentMessage,
  children,
}: ConsentManagerProps): React.ReactElement | null {
  const { consentGranted, grantConsent, revokeConsent, isLoading } = useConsent(purpose);
  const [showDialog, setShowDialog] = useState(false);

  useEffect(() => {
    if (requireConsent && !isLoading && !consentGranted) {
      setShowDialog(true);
    }
  }, [requireConsent, isLoading, consentGranted]);

  useEffect(() => {
    onConsentChange?.(consentGranted);
  }, [consentGranted, onConsentChange]);

  if (isLoading) {
    return <div>Loading...</div>;
  }

  if (!consentGranted) {
    if (requireConsent && showDialog) {
      return (
        <ConsentContext.Provider value={{ consentGranted, grantConsent, revokeConsent }}>
          <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <div className="bg-white dark:bg-gray-800 rounded-lg p-6 max-w-md mx-4">
              <h2 className="text-lg font-semibold mb-4">Consent Required</h2>
              <p className="text-gray-600 dark:text-gray-300 mb-6">
                {consentMessage ?? `This feature requires your consent for ${purpose}.`}
              </p>
              <div className="flex justify-end space-x-3">
                <button
                  onClick={() => {
                    revokeConsent();
                    setShowDialog(false);
                  }}
                  className="px-4 py-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded"
                >
                  Deny
                </button>
                <button
                  onClick={() => {
                    grantConsent();
                    setShowDialog(false);
                  }}
                  className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
                >
                  Allow
                </button>
              </div>
            </div>
          </div>
        </ConsentContext.Provider>
      );
    }
    return fallback != null ? <>{fallback}</> : null;
  }

  return <>{children}</>;
}
