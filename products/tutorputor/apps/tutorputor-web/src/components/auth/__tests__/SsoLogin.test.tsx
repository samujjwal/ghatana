import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import {
  LoginPage,
  SsoErrorMessage,
  SsoProviderList,
} from "../SsoLogin";

type MockFetchResponse = {
  ok: boolean;
  json: () => Promise<unknown>;
};

const mockFetch = vi.fn<(...args: unknown[]) => Promise<MockFetchResponse>>();

describe("SsoLogin", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", mockFetch);
    mockFetch.mockReset();
    window.history.replaceState({}, "", "/");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe("SsoProviderList", () => {
    it("loads tenant-scoped identity providers from the canonical auth endpoint", async () => {
      const onProviderSelect = vi.fn();
      const onEmailLoginClick = vi.fn();

      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          providers: [
            {
              id: "google-school",
              displayName: "Google Workspace",
              type: "oidc",
            },
            {
              id: "okta-school",
              displayName: "Okta",
              type: "saml",
            },
          ],
        }),
      });

      render(
        <SsoProviderList
          tenantSlug="school"
          onProviderSelect={onProviderSelect}
          onEmailLoginClick={onEmailLoginClick}
        />,
      );

      await waitFor(() => {
        expect(screen.getByRole("button", { name: /sign in with google workspace/i })).toBeInTheDocument();
      });

      expect(mockFetch).toHaveBeenCalledWith(
        "/api/v1/auth/sso/providers?tenantSlug=school",
      );
      expect(screen.getByRole("button", { name: /sign in with okta/i })).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /sign in with email/i })).toBeInTheDocument();

      fireEvent.click(screen.getByRole("button", { name: /sign in with google workspace/i }));
      expect(onProviderSelect).toHaveBeenCalledWith("google-school");

      fireEvent.click(screen.getByRole("button", { name: /sign in with email/i }));
      expect(onEmailLoginClick).toHaveBeenCalledTimes(1);
    });

    it("renders a visible error state when provider discovery fails", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: false,
        json: async () => ({}),
      });

      render(
        <SsoProviderList tenantSlug="school" onProviderSelect={vi.fn()} />,
      );

      await waitFor(() => {
        expect(screen.getByText(/unable to load sign-in options/i)).toBeInTheDocument();
      });

      expect(screen.getByText(/failed to fetch identity providers/i)).toBeInTheDocument();
    });

    it("shows the no-options message when no SSO providers or email login exist", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ providers: [] }),
      });

      render(
        <SsoProviderList
          tenantSlug="school"
          onProviderSelect={vi.fn()}
          showEmailLogin={false}
        />,
      );

      await waitFor(() => {
        expect(screen.getByText(/no sign-in options available for this organization/i)).toBeInTheDocument();
      });
    });
  });

  describe("SsoErrorMessage", () => {
    it("maps known auth errors to user-facing copy", () => {
      render(<SsoErrorMessage error="state_mismatch" />);

      expect(screen.getByText(/sign-in failed/i)).toBeInTheDocument();
      expect(
        screen.getByText(/security validation failed\. this may happen if you waited too long\./i),
      ).toBeInTheDocument();
    });
  });

  describe("LoginPage", () => {
    it("reads SSO error parameters from the URL and cleans them after rendering", async () => {
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: async () => ({ providers: [] }),
      });

      window.history.replaceState(
        {},
        "",
        "/login?error=sso_init_failed&error_description=temporary%20issue",
      );

      render(<LoginPage tenantSlug="school" onEmailLogin={vi.fn()} />);

      await waitFor(() => {
        expect(screen.getByText(/failed to start the sign-in process/i)).toBeInTheDocument();
      });

      expect(window.location.search).toBe("");
    });
  });
});