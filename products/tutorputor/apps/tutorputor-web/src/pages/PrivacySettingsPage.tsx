import { Link } from "react-router-dom";
import { useEffect, useState } from "react";
import { Card } from "@/components/ui";
import { Download, Shield, Trash2 } from "lucide-react";

const privacyControls = [
  {
    title: "AI tutor consent",
    consentType: "ai_tutor",
    description: "Controls whether TutorPutor can use grounded learner context for AI help.",
    status: "Enabled",
  },
  {
    title: "Learning telemetry",
    consentType: "learning_telemetry",
    description: "Controls evidence events used for mastery, remediation, and privacy export/delete.",
    status: "Enabled",
  },
  {
    title: "Personalization",
    consentType: "personalization",
    description: "Controls adaptive pathway recommendations from recent attempts and misconceptions.",
    status: "Enabled",
  },
];

export function PrivacySettingsPage() {
  const [message, setMessage] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);
  const [summary, setSummary] = useState<{
    exportRequests?: Array<{ id: string; status: string }>;
    deletionRequests?: Array<{ id: string; status: string }>;
  } | null>(null);

  useEffect(() => {
    void fetch("/api/v1/compliance/privacy-center")
      .then((response) => response.ok ? response.json() : null)
      .then((data) => setSummary(data))
      .catch(() => setSummary(null));
  }, []);

  const requestExport = async () => {
    setIsBusy(true);
    try {
      const response = await fetch("/api/v1/compliance/export", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
      if (!response.ok) throw new Error("Export request failed");
      const result = await response.json();
      setMessage(`Export requested. Request ${result.id ?? result.requestId} is ${result.status}.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Export request failed");
    } finally {
      setIsBusy(false);
    }
  };

  const requestDeletion = async () => {
    setIsBusy(true);
    try {
      const response = await fetch("/api/v1/compliance/deletion/request", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: "learner_privacy_center" }),
      });
      if (!response.ok) throw new Error("Deletion request failed");
      const result = await response.json();
      setMessage(`Deletion requested. Request ${result.id ?? result.requestId} is ${result.status}.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Deletion request failed");
    } finally {
      setIsBusy(false);
    }
  };

  const revokeConsent = async (consentType: string) => {
    setIsBusy(true);
    try {
      const response = await fetch("/api/v1/compliance/consent/revoke", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ consentType }),
      });
      if (!response.ok) throw new Error("Consent revocation failed");
      setMessage(`${consentType.replace("_", " ")} revoked.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Consent revocation failed");
    } finally {
      setIsBusy(false);
    }
  };

  const deleteTelemetry = async () => {
    setIsBusy(true);
    try {
      const response = await fetch("/api/v1/compliance/telemetry/delete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ anonymize: true }),
      });
      if (!response.ok) throw new Error("Telemetry deletion failed");
      const result = await response.json();
      setMessage(`Telemetry ${result.records?.[0]?.action ?? "processed"} for privacy request evidence.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "Telemetry deletion failed");
    } finally {
      setIsBusy(false);
    }
  };

  return (
    <main className="p-6">
      <div className="mx-auto max-w-4xl">
        <div className="mb-6 flex items-start gap-4">
          <Shield className="mt-1 h-8 w-8 text-blue-600" aria-hidden="true" />
          <div>
            <h1 className="text-3xl font-bold text-gray-950 dark:text-white">Privacy Center</h1>
            <p className="mt-2 text-gray-600 dark:text-gray-300">
              Review consent, export learner evidence, and request deletion from one place.
            </p>
          </div>
        </div>

        <div className="grid gap-4">
          {privacyControls.map((control) => (
            <Card key={control.title} className="p-4">
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="font-semibold">{control.title}</h2>
                  <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">{control.description}</p>
                </div>
                <span className="rounded-md bg-emerald-50 px-3 py-1 text-sm font-medium text-emerald-700">
                  {control.status}
                </span>
                <button
                  className="rounded-md border border-gray-300 px-3 py-1 text-sm font-medium"
                  disabled={isBusy}
                  onClick={() => void revokeConsent(control.consentType)}
                  type="button"
                >
                  Revoke
                </button>
              </div>
            </Card>
          ))}
        </div>

        <Card className="mt-6 p-4">
          <h2 className="font-semibold">Data actions</h2>
          <div className="mt-4 flex flex-wrap gap-3">
            <button
              className="inline-flex items-center gap-2 rounded-md bg-blue-600 px-4 py-2 font-medium text-white disabled:opacity-60"
              disabled={isBusy}
              onClick={() => void requestExport()}
              type="button"
            >
              <Download className="h-4 w-4" aria-hidden="true" />
              Export My Data
            </button>
            <button
              className="inline-flex items-center gap-2 rounded-md border border-red-300 px-4 py-2 font-medium text-red-700 disabled:opacity-60"
              disabled={isBusy}
              onClick={() => void requestDeletion()}
              type="button"
            >
              <Trash2 className="h-4 w-4" aria-hidden="true" />
              Request Deletion
            </button>
            <button
              className="inline-flex items-center gap-2 rounded-md border border-amber-300 px-4 py-2 font-medium text-amber-700 disabled:opacity-60"
              disabled={isBusy}
              onClick={() => void deleteTelemetry()}
              type="button"
            >
              Delete Telemetry
            </button>
          </div>
          {message && (
            <p className="mt-4 rounded-md bg-blue-50 px-3 py-2 text-sm text-blue-800" role="status">
              {message}
            </p>
          )}
          {summary && (
            <div className="mt-4 grid gap-2 text-sm text-gray-600 sm:grid-cols-2">
              <p>Export requests: {summary.exportRequests?.length ?? 0}</p>
              <p>Deletion requests: {summary.deletionRequests?.length ?? 0}</p>
            </div>
          )}
        </Card>

        <Link className="mt-6 inline-block text-sm font-medium text-blue-700" to="/settings">
          Back to Settings
        </Link>
      </div>
    </main>
  );
}
