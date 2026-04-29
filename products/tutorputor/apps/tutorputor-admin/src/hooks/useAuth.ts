/**
 * useAuth — TutorPutor Admin product hook.
 *
 * Verifies the session against `/api/v1/auth/me` and stores the result in a
 * Jotai atom. Exposes `user`, `tenantId`, and `accessToken` for admin views.
 *
 * For generic token-level auth shared across products, use
 * For generic token-level auth state shared across products, use
 * `authTokenAtom` from `@ghatana/state`.
 *
 * @doc.type hook
 * @doc.purpose TutorPutor Admin session check + auth state
 * @doc.layer product
 * @doc.pattern Custom Hook
 */
import { atom, useAtom } from "jotai";
import { useEffect, useState } from "react";
import type { UserSummary, TenantId } from "@tutorputor/contracts/v1/types";
import { setAuthToken } from "../services/contentStudioApi";
import {
  AUTH_TOKEN_KEY,
  REFRESH_TOKEN_KEY,
  TENANT_ID_KEY,
  getSafeStorage,
  readAccessToken,
  clearAuthStorage,
} from "@tutorputor/ui";

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

// Dev tenant ID - must match TUTORPUTOR_DEFAULT_TENANT_ID
// No fallback to "default" to prevent production misconfig
const DEV_TENANT_ID = import.meta.env.VITE_TUTORPUTOR_TENANT_ID || null;

export function useAuth() {
  const [auth, setAuth] = useAtom(authAtom);
  const [isChecked, setIsChecked] = useState(false);

  useEffect(() => {
    if (isChecked) return;

    async function checkAuth() {
      const storedAccessToken = readAccessToken();
      const storedRefreshToken = getSafeStorage().getItem(REFRESH_TOKEN_KEY);

      try {
        const response = await fetch("/api/v1/auth/me", {
          headers: storedAccessToken
            ? { Authorization: `Bearer ${storedAccessToken}` }
            : undefined,
        });
        if (response.ok) {
          const data = (await response.json()) as UserSummary & {
            accessToken?: string;
            user?: UserSummary;
            tenantId?: TenantId;
          };
          const resolvedUser = data.user ?? data;
          const resolvedToken = data.accessToken ?? storedAccessToken ?? null;
          setAuthToken(resolvedToken);
          setAuth({
            user: resolvedUser,
            tenantId: (data.tenantId ?? resolvedUser.tenantId ?? DEV_TENANT_ID ?? null) as TenantId | null,
            accessToken: resolvedToken,
            isLoading: false,
          });
        } else if (
          (response.status === 401 ||
            response.status === 404 ||
            response.status === 500) &&
          import.meta.env.VITE_DEV_AUTH_BYPASS === "true"
        ) {
          // In development, if auth endpoint fails or doesn't exist, use seeded admin user
          setAuthToken("dev-token");
          setAuth({
            user: {
              id: "user-admin-001",
              email: "admin@demo.tutorputor.com",
              role: "admin",
              firstName: "Sarah",
              lastName: "Admin",
              fullName: "Sarah Admin",
            } as UserSummary,
            tenantId: DEV_TENANT_ID as TenantId,
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
        if (import.meta.env.VITE_DEV_AUTH_BYPASS === "true") {
          // In development, use seeded admin user on any error
          setAuthToken("dev-token");
          setAuth({
            user: {
              id: "user-admin-001",
              email: "admin@demo.tutorputor.com",
              role: "admin",
              firstName: "Sarah",
              lastName: "Admin",
              fullName: "Sarah Admin",
            } as UserSummary,
            tenantId: DEV_TENANT_ID as TenantId,
            accessToken: "dev-token",
            isLoading: false,
          });
        } else {
          setAuthToken(null);
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
      await fetch("/api/v1/auth/logout", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(auth.accessToken ? { Authorization: `Bearer ${auth.accessToken}` } : {}),
        },
        body: JSON.stringify({
          refreshToken: getSafeStorage().getItem(REFRESH_TOKEN_KEY),
        }),
      });
      clearAuthStorage();
      setAuthToken(null);
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
