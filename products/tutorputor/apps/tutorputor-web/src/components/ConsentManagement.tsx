/**
 * Consent Management UI
 *
 * Learner-facing settings page to view, grant, and revoke consent categories.
 *
 * @doc.type component
 * @doc.purpose Consent management interface for learners
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useEffect } from "react";
import { useAuth, useTenantId } from "../contexts/AuthContext";
import { readAccessToken } from "@tutorputor/ui";

export interface ConsentCategory {
  id: string;
  name: string;
  description: string;
  required: boolean;
  granted: boolean;
  grantedAt?: string;
}

export function ConsentManagement() {
  const { user } = useAuth();
  const tenantId = useTenantId();
  const [consents, setConsents] = useState<ConsentCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);

  useEffect(() => {
    if (user) {
      fetchConsents();
    }
  }, [user, tenantId]);

  const fetchConsents = async () => {
    try {
      const token = readAccessToken();
      const response = await fetch("/api/v1/auth/consents", {
        headers: {
          Authorization: token ? `Bearer ${token}` : "",
        },
      });
      if (response.ok) {
        const data = await response.json();
        setConsents(data.consents || []);
      }
    } catch (error) {
      console.error("Failed to fetch consents:", error);
    } finally {
      setLoading(false);
    }
  };

  const toggleConsent = async (consentId: string, granted: boolean) => {
    setSaving(consentId);
    try {
      const token = readAccessToken();
      const response = await fetch("/api/v1/auth/consents", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: token ? `Bearer ${token}` : "",
        },
        body: JSON.stringify({
          consentId,
          granted,
        }),
      });

      if (response.ok) {
        setConsents(
          consents.map((c) =>
            c.id === consentId
              ? { ...c, granted, grantedAt: granted ? new Date().toISOString() : undefined }
              : c,
          ),
        );
      }
    } catch (error) {
      console.error("Failed to update consent:", error);
    } finally {
      setSaving(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
      </div>
    );
  }

  return (
    <div className="max-w-4xl mx-auto p-6">
      <h1 className="text-3xl font-bold mb-6">Consent Management</h1>
      <p className="text-gray-600 mb-8">
        Manage your consent preferences for data processing and AI features.
      </p>

      <div className="space-y-6">
        {consents.map((consent) => (
          <div
            key={consent.id}
            className="bg-white border border-gray-200 rounded-lg p-6 shadow-sm"
          >
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="flex items-center gap-3">
                  <h3 className="text-lg font-semibold">{consent.name}</h3>
                  {consent.required && (
                    <span className="px-2 py-1 text-xs font-medium bg-gray-100 text-gray-600 rounded">
                      Required
                    </span>
                  )}
                </div>
                <p className="text-gray-600 mt-1">{consent.description}</p>
                {consent.grantedAt && (
                  <p className="text-sm text-gray-500 mt-2">
                    Granted on {new Date(consent.grantedAt).toLocaleDateString()}
                  </p>
                )}
              </div>

              {!consent.required && (
                <button
                  onClick={() => toggleConsent(consent.id, !consent.granted)}
                  disabled={saving === consent.id}
                  className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${
                    consent.granted ? "bg-blue-600" : "bg-gray-200"
                  } ${saving === consent.id ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}`}
                >
                  <span
                    className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                      consent.granted ? "translate-x-6" : "translate-x-1"
                    }`}
                  />
                </button>
              )}
            </div>

            <div className="mt-4 pt-4 border-t border-gray-100">
              <span
                className={`text-sm font-medium ${
                  consent.granted ? "text-green-600" : "text-red-600"
                }`}
              >
                {consent.granted ? "✓ Granted" : "✗ Not Granted"}
              </span>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-8 p-4 bg-blue-50 border border-blue-200 rounded-lg">
        <h4 className="font-semibold text-blue-900 mb-2">About Your Consents</h4>
        <p className="text-sm text-blue-800">
          Your consent preferences determine how your data is processed and which features
          are available to you. Required consents are necessary for the platform to function.
          You can revoke optional consents at any time, which may affect certain features.
        </p>
      </div>
    </div>
  );
}
