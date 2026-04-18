import type { ReactElement } from "react";
import { useSearchParams } from "react-router-dom";
import { LoginPage } from "../components/auth/SsoLogin";

const DEFAULT_TENANT_SLUG = "default";

function resolveTenantSlug(
  explicitTenantSlug: string | null,
  hostname: string,
): string {
  if (explicitTenantSlug && explicitTenantSlug.trim().length > 0) {
    return explicitTenantSlug.trim();
  }

  if (
    hostname === "localhost" ||
    hostname === "127.0.0.1" ||
    hostname.length === 0
  ) {
    return DEFAULT_TENANT_SLUG;
  }

  const [subdomain] = hostname.split(".");
  if (!subdomain || subdomain === "www") {
    return DEFAULT_TENANT_SLUG;
  }

  return subdomain;
}

export function LoginRoutePage(): ReactElement {
  const [searchParams] = useSearchParams();
  const redirectPath = searchParams.get("redirect") ?? "/dashboard";
  const tenantSlug = resolveTenantSlug(
    searchParams.get("tenant"),
    typeof window === "undefined" ? "localhost" : window.location.hostname,
  );

  return (
    <LoginPage
      tenantSlug={tenantSlug}
      redirectPath={redirectPath}
      title="Sign in to TutorPutor"
      subtitle="Use your organization sign-in to continue."
    />
  );
}