/**
 * SSO Login Components
 *
 * Components for enterprise SSO login, provider selection, and status display.
 * Uses @ghatana/ui for consistent styling.
 */

import React, { useEffect, useState, useCallback } from "react";
import { Button, Spinner, Badge } from "@ghatana/design-system";
import type { IdentityProviderConfig } from "@ghatana/tutorputor-contracts/v1/types";

// ===========================================================================
// Types
// ===========================================================================

export interface SsoProvider {
  id: string;
  displayName: string;
  type: "oidc" | "saml";
  iconUrl?: string;
}

export interface SsoLoginButtonProps {
  provider: SsoProvider;
  onClick?: (providerId: string) => void;
  loading?: boolean;
  disabled?: boolean;
  className?: string;
}

export interface SsoProviderListProps {
  tenantSlug: string;
  onProviderSelect: (providerId: string) => void;
  showEmailLogin?: boolean;
  onEmailLoginClick?: () => void;
  className?: string;
}

export interface SsoStatusBadgeProps {
  status: "connected" | "disconnected" | "error";
  providerName?: string;
  lastLoginAt?: string;
  className?: string;
}

// ===========================================================================
// Provider Icons
// ===========================================================================

const ProviderIcons: Record<string, React.ReactNode> = {
  google: (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
      <path
        d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"
        fill="#4285F4"
      />
      <path
        d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"
        fill="#34A853"
      />
      <path
        d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"
        fill="#FBBC05"
      />
      <path
        d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"
        fill="#EA4335"
      />
    </svg>
  ),
  microsoft: (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
      <path d="M11.4 11.4H2V2h9.4v9.4z" fill="#F35325" />
      <path d="M22 11.4h-9.4V2H22v9.4z" fill="#81BC06" />
      <path d="M11.4 22H2v-9.4h9.4V22z" fill="#05A6F0" />
      <path d="M22 22h-9.4v-9.4H22V22z" fill="#FFBA08" />
    </svg>
  ),
  okta: (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
      <circle cx="12" cy="12" r="10" fill="#007DC1" />
      <circle cx="12" cy="12" r="5" fill="white" />
    </svg>
  ),
  azure: (
    <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none">
      <path
        d="M7.65 3L2.7 19.5h4.95l3.15-10.5 2.7 10.5h4.5L12.3 3H7.65z"
        fill="#0078D4"
      />
    </svg>
  ),
};

function getProviderIcon(provider: SsoProvider): React.ReactNode {
  // Check for custom icon URL
  if (provider.iconUrl) {
    return (
      <img
        src={provider.iconUrl}
        alt={`${provider.displayName} icon`}
        className="w-5 h-5"
      />
    );
  }

  // Try to match known providers
  const name = provider.displayName.toLowerCase();
  if (name.includes("google")) return ProviderIcons.google;
  if (name.includes("microsoft") || name.includes("azure"))
    return ProviderIcons.microsoft;
  if (name.includes("okta")) return ProviderIcons.okta;

  // Default SSO icon
  return (
    <svg
      className="w-5 h-5"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
    >
      <circle cx="12" cy="8" r="4" />
      <path d="M4 20a8 8 0 0 1 16 0" />
    </svg>
  );
}

// ===========================================================================
// Components
// ===========================================================================

/**
 * Single SSO provider login button.
 */
export function SsoLoginButton({
  provider,
  onClick,
  loading = false,
  disabled = false,
  className = "",
}: SsoLoginButtonProps) {
  const handleClick = useCallback(() => {
    if (!loading && !disabled && onClick) {
      onClick(provider.id);
    }
  }, [provider.id, onClick, loading, disabled]);

  return (
    <Button
      variant="outline"
      size="lg"
      onClick={handleClick}
      disabled={disabled || loading}
      className={`w-full justify-start gap-3 ${className}`}
    >
      {loading ? (
        <Spinner size="sm" />
      ) : (
        getProviderIcon(provider)
      )}
      <span>
        Sign in with {provider.displayName}
      </span>
    </Button>
  );
}

/**
 * List of SSO providers for a tenant with optional email login.
 */
export function SsoProviderList({
  tenantSlug,
  onProviderSelect,
  showEmailLogin = true,
  onEmailLoginClick,
  className = "",
}: SsoProviderListProps) {
  const [providers, setProviders] = useState<SsoProvider[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedProvider, setSelectedProvider] = useState<string | null>(null);

  useEffect(() => {
    async function fetchProviders() {
      try {
        setLoading(true);
        setError(null);

        const response = await fetch(
          `/auth/providers?tenantSlug=${encodeURIComponent(tenantSlug)}`
        );

        if (!response.ok) {
          throw new Error("Failed to fetch identity providers");
        }

        const data = await response.json();
        setProviders(data.providers || []);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Unknown error");
      } finally {
        setLoading(false);
      }
    }

    fetchProviders();
  }, [tenantSlug]);

  const handleProviderClick = useCallback(
    (providerId: string) => {
      setSelectedProvider(providerId);
      onProviderSelect(providerId);
    },
    [onProviderSelect]
  );

  if (loading) {
    return (
      <div className={`flex justify-center py-8 ${className}`}>
        <Spinner size="lg" />
      </div>
    );
  }

  if (error) {
    return (
      <div className={`text-center py-4 text-red-600 ${className}`}>
        <p>Unable to load sign-in options.</p>
        <p className="text-sm text-gray-500 mt-1">{error}</p>
      </div>
    );
  }

  return (
    <div className={`space-y-3 ${className}`}>
      {providers.map((provider) => (
        <SsoLoginButton
          key={provider.id}
          provider={provider}
          onClick={handleProviderClick}
          loading={selectedProvider === provider.id}
          disabled={selectedProvider !== null && selectedProvider !== provider.id}
        />
      ))}

      {providers.length > 0 && showEmailLogin && (
        <div className="relative my-6">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-gray-200 dark:border-gray-700" />
          </div>
          <div className="relative flex justify-center text-sm">
            <span className="px-2 bg-white dark:bg-gray-900 text-gray-500">
              or continue with
            </span>
          </div>
        </div>
      )}

      {showEmailLogin && (
        <Button
          variant="secondary"
          size="lg"
          onClick={onEmailLoginClick}
          className="w-full"
        >
          <svg
            className="w-5 h-5 mr-3"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
          >
            <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z" />
            <polyline points="22,6 12,13 2,6" />
          </svg>
          Sign in with Email
        </Button>
      )}

      {providers.length === 0 && !showEmailLogin && (
        <p className="text-center text-gray-500 py-4">
          No sign-in options available for this organization.
        </p>
      )}
    </div>
  );
}

/**
 * Status badge showing SSO connection state.
 */
export function SsoStatusBadge({
  status,
  providerName,
  lastLoginAt,
  className = "",
}: SsoStatusBadgeProps) {
  const statusConfig = {
    connected: {
      variant: "success" as const,
      text: "Connected",
    },
    disconnected: {
      variant: "secondary" as const,
      text: "Not connected",
    },
    error: {
      variant: "destructive" as const,
      text: "Connection error",
    },
  };

  const config = statusConfig[status];

  return (
    <div className={`flex items-center gap-2 ${className}`}>
      <Badge variant={config.variant}>{config.text}</Badge>
      {providerName && (
        <span className="text-sm text-gray-600 dark:text-gray-400">
          via {providerName}
        </span>
      )}
      {lastLoginAt && status === "connected" && (
        <span className="text-xs text-gray-500">
          Last login: {new Date(lastLoginAt).toLocaleDateString()}
        </span>
      )}
    </div>
  );
}

/**
 * SSO error display component.
 */
export function SsoErrorMessage({
  error,
  errorDescription,
  onRetry,
  className = "",
}: {
  error: string;
  errorDescription?: string;
  onRetry?: () => void;
  className?: string;
}) {
  const errorMessages: Record<string, string> = {
    sso_init_failed: "Failed to start the sign-in process. Please try again.",
    state_mismatch:
      "Security validation failed. This may happen if you waited too long. Please try again.",
    provider_not_found: "This sign-in option is not available.",
    provider_disabled:
      "This sign-in option has been disabled. Please contact your administrator.",
    domain_not_allowed:
      "Your email domain is not authorized for this organization.",
    token_verification_failed:
      "We couldn't verify your identity. Please try again.",
    internal_error: "An unexpected error occurred. Please try again later.",
    saml_error:
      "There was a problem with the SAML authentication. Please try again.",
  };

  const message = errorMessages[error] || errorDescription || error;

  return (
    <div
      className={`bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4 ${className}`}
    >
      <div className="flex items-start gap-3">
        <svg
          className="w-5 h-5 text-red-500 mt-0.5 shrink-0"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
        >
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
        <div className="flex-1">
          <h3 className="font-medium text-red-800 dark:text-red-200">
            Sign-in failed
          </h3>
          <p className="text-sm text-red-600 dark:text-red-300 mt-1">
            {message}
          </p>
          {onRetry && (
            <Button
              variant="outline"
              size="sm"
              onClick={onRetry}
              className="mt-3"
            >
              Try again
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Full login page component with SSO and email options.
 */
export function LoginPage({
  tenantSlug,
  onEmailLogin,
  logo,
  title = "Welcome back",
  subtitle = "Sign in to continue to your account",
}: {
  tenantSlug: string;
  onEmailLogin?: () => void;
  logo?: React.ReactNode;
  title?: string;
  subtitle?: string;
}) {
  const [error, setError] = useState<string | null>(null);
  const [errorDescription, setErrorDescription] = useState<string | null>(null);

  // Check for error in URL params
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const errorParam = params.get("error");
    const errorDescParam = params.get("error_description");

    if (errorParam) {
      setError(errorParam);
      setErrorDescription(errorDescParam);

      // Clean up URL
      const url = new URL(window.location.href);
      url.searchParams.delete("error");
      url.searchParams.delete("error_description");
      window.history.replaceState({}, "", url.toString());
    }
  }, []);

  const handleProviderSelect = useCallback((providerId: string) => {
    // Redirect to SSO login endpoint
    const redirectUri = encodeURIComponent(
      `${window.location.origin}/dashboard`
    );
    window.location.href = `/auth/login/${providerId}?redirect_uri=${redirectUri}`;
  }, []);

  const handleRetry = useCallback(() => {
    setError(null);
    setErrorDescription(null);
  }, []);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50 dark:bg-gray-900 py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        {/* Logo */}
        {logo && <div className="flex justify-center">{logo}</div>}

        {/* Header */}
        <div className="text-center">
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
            {title}
          </h2>
          <p className="mt-2 text-gray-600 dark:text-gray-400">{subtitle}</p>
        </div>

        {/* Error Message */}
        {error && (
          <SsoErrorMessage
            error={error}
            errorDescription={errorDescription ?? undefined}
            onRetry={handleRetry}
          />
        )}

        {/* SSO Providers */}
        <div className="bg-white dark:bg-gray-800 rounded-xl shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <SsoProviderList
            tenantSlug={tenantSlug}
            onProviderSelect={handleProviderSelect}
            showEmailLogin={!!onEmailLogin}
            onEmailLoginClick={onEmailLogin}
          />
        </div>

        {/* Footer */}
        <p className="text-center text-sm text-gray-500">
          By signing in, you agree to our{" "}
          <a href="/terms" className="text-primary-600 hover:underline">
            Terms of Service
          </a>{" "}
          and{" "}
          <a href="/privacy" className="text-primary-600 hover:underline">
            Privacy Policy
          </a>
        </p>
      </div>
    </div>
  );
}
