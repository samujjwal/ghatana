import { atom, useAtom } from "jotai";
import { useEffect, useState } from "react";
import type { UserSummary, TenantId } from "@ghatana/tutorputor-contracts/v1/types";

interface AuthState {
  user: UserSummary | null;
  tenantId: TenantId | null;
  accessToken: string | null;
  isLoading: boolean;
}

const authAtom = atom<AuthState>({
  user: null,
  tenantId: null,
  accessToken: null,
  isLoading: true,
});

// Dev tenant ID - must match TUTORPUTOR_DEFAULT_TENANT_ID or "default"
const DEV_TENANT_ID =
  import.meta.env.VITE_TUTORPUTOR_TENANT_ID ||
  (import.meta.env.DEV ? "default" : null);

export function useAuth() {
  const [auth, setAuth] = useAtom(authAtom);
  const [isChecked, setIsChecked] = useState(false);

  useEffect(() => {
    if (isChecked) return;

    async function checkAuth() {
      try {
        const response = await fetch("/api/auth/me");
        if (response.ok) {
          const data = await response.json();
          setAuth({
            user: data.user ?? data,
            tenantId: data.tenantId,
            accessToken: data.accessToken,
            isLoading: false,
          });
        } else if (
          (response.status === 401 ||
            response.status === 404 ||
            response.status === 500) &&
          import.meta.env.DEV
        ) {
          // In development, if auth endpoint fails or doesn't exist, use seeded admin user
          setAuth({
            user: {
              id: "user-admin-001",
              email: "admin@demo.tutorputor.com",
              role: "admin",
              firstName: "Sarah",
              lastName: "Admin",
              fullName: "Sarah Admin",
            } as UserSummary,
            tenantId: (DEV_TENANT_ID ?? "default") as TenantId,
            accessToken: "dev-token",
            isLoading: false,
          });
        } else {
          setAuth({
            user: null,
            tenantId: null,
            accessToken: null,
            isLoading: false,
          });
        }
      } catch {
        if (import.meta.env.DEV) {
          // In development, use seeded admin user on any error
          setAuth({
            user: {
              id: "user-admin-001",
              email: "admin@demo.tutorputor.com",
              role: "admin",
              firstName: "Sarah",
              lastName: "Admin",
              fullName: "Sarah Admin",
            } as UserSummary,
            tenantId: (DEV_TENANT_ID ?? "default") as TenantId,
            accessToken: "dev-token",
            isLoading: false,
          });
        } else {
          setAuth({
            user: null,
            tenantId: null,
            accessToken: null,
            isLoading: false,
          });
        }
      }
      setIsChecked(true);
    }

    checkAuth();
  }, [isChecked, setAuth]);

  return {
    user: auth.user,
    tenantId: auth.tenantId,
    isAuthenticated: !!auth.user,
    isAdmin: auth.user?.role === "admin",
    isLoading: auth.isLoading,
    logout: async () => {
      await fetch("/auth/logout", { method: "POST" });
      setAuth({
        user: null,
        tenantId: null,
        accessToken: null,
        isLoading: false,
      });
      window.location.href = "/login";
    },
  };
}
