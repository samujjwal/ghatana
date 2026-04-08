/**
 * ConsentManager - Generic consent management component
 * 
 * @doc.type component
 * @doc.purpose Manage user consent for privacy-sensitive features
 * @doc.layer frontend
 * @doc.pattern Privacy Component
 */

import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';

/**
 * Consent record interface
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
 * Consent manager props
 */
export interface ConsentManagerProps {
  purpose: string;
  onConsentChange?: (granted: boolean) => void;
  requireConsent?: boolean;
  fallback?: ReactNode;
  consentMessage?: string;
  children: ReactNode;
}

/**
 * Consent context
 */
const ConsentContext = createContext<{
  consentGranted: boolean;
  grantConsent: () => void;
  revokeConsent: () => void;
}>({
  consentGranted: false,
  grantConsent: () => {},
  revokeConsent: () => {},
});

/**
 * Hook for managing consent
 */
export function useConsent(purpose: string) {
  const context = useContext(ConsentContext);
  
  if (!context) {
    throw new Error('useConsent must be used within a ConsentManager');
  }

  const [consentGranted, setConsentGranted] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const checkConsent = () => {
      const stored = localStorage.getItem(`consent_${purpose}`);
      if (stored) {
        const consent: ConsentRecord = JSON.parse(stored);
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
    };

    checkConsent();
  }, [purpose]);

  const grantConsent = () => {
    const consent: ConsentRecord = {
      id: `consent_${Date.now()}`,
      userId: 'current', // In real implementation, get from auth context
      purpose,
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString(), // 1 year
    };

    localStorage.setItem(`consent_${purpose}`, JSON.stringify(consent));
    setConsentGranted(true);
  };

  const revokeConsent = () => {
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

  return {
    consentGranted,
    grantConsent,
    revokeConsent,
    isLoading,
  };
}

/**
 * Consent Manager Component
 */
export function ConsentManager({
  purpose,
  onConsentChange,
  requireConsent = false,
  fallback,
  consentMessage,
  children,
}: ConsentManagerProps) {
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
    if (requireConsent) {
      if (showDialog) {
        return (
          <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
            <div className="bg-white dark:bg-gray-800 rounded-lg p-6 max-w-md mx-4">
              <h2 className="text-lg font-semibold mb-4">Consent Required</h2>
              <p className="text-gray-600 dark:text-gray-300 mb-6">
                {consentMessage || `This feature requires your consent for ${purpose}.`}
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
        );
      }
      return fallback || null;
    }
    return fallback || null;
  }

  return <>{children}</>;
}
