import { useMutation } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import { useBillingCapabilities } from "../hooks/useBillingCapabilities";

/**
 * Settings Page
 *
 * @doc.type component
 * @doc.purpose User account and subscription settings
 * @doc.layer product
 * @doc.pattern Page
 */
export default function Settings() {
  const { token } = useAuth();
  const { billingPortalEnabled } = useBillingCapabilities();

  const portalMutation = useMutation<void, Error>({
    mutationFn: async (): Promise<void> => {
      const response = await fetch("/api/v1/integration/billing/portal", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ returnUrl: window.location.href }),
      });
      if (!response.ok) {
        const body: unknown = await response.json().catch(() => ({}));
        const message =
          typeof body === "object" && body !== null && "message" in body
            ? String((body as Record<string, unknown>)["message"])
            : `HTTP ${response.status}`;
        throw new Error(message);
      }
      const data: unknown = await response.json();
      const portalUrl =
        typeof data === "object" && data !== null && "url" in data
          ? String((data as Record<string, unknown>)["url"])
          : null;
      if (portalUrl) {
        window.location.assign(portalUrl);
      }
    },
  });

  return (
    <div style={{ padding: 24 }}>
      <h1>Settings</h1>

      {/* Privacy section — always shown */}
      <section style={{ marginTop: 32 }}>
        <h2>Privacy</h2>
        <p>Manage how TutorPutor uses your data.</p>
        <Link to="/settings/privacy" style={{ display: "inline-block", marginTop: 8 }}>
          Manage Privacy Settings →
        </Link>
      </section>

      {/* Subscription section — only rendered when billing portal is enabled */}
      {billingPortalEnabled ? (
        <section style={{ marginTop: 32 }}>
          <h2>Subscription</h2>
          <p>Manage your subscription, update payment methods, or view billing history.</p>
          <button
            type="button"
            disabled={portalMutation.isPending}
            onClick={() => { portalMutation.mutate(); }}
            style={{
              marginTop: 12,
              padding: "8px 16px",
              cursor: portalMutation.isPending ? "not-allowed" : "pointer",
            }}
          >
            {portalMutation.isPending ? "Opening…" : "Manage Subscription"}
          </button>
          {portalMutation.isError ? (
            <p style={{ color: "red", marginTop: 8 }}>
              {portalMutation.error.message}
            </p>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}

