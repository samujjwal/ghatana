/**
 * Tests for RBACGuard component
 */

import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RBACGuard, usePermission } from "../RBACGuard";

describe("RBACGuard", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders children when permission is granted", async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ granted: true }),
    });

    render(
      <RBACGuard permission="entities:delete" resource="test" action="delete">
        <div>Protected Content</div>
      </RBACGuard>,
    );

    await waitFor(() => {
      expect(screen.getByText("Protected Content")).toBeInTheDocument();
    });
  });

  it("renders fallback when permission is denied", async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ granted: false }),
    });

    render(
      <RBACGuard
        permission="entities:delete"
        resource="test"
        action="delete"
        fallback={<div>Access Denied</div>}
      >
        <div>Protected Content</div>
      </RBACGuard>,
    );

    await waitFor(() => {
      expect(screen.getByText("Access Denied")).toBeInTheDocument();
      expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
    });
  });

  it("renders loading fallback while checking permission", () => {
    (global.fetch as any).mockImplementation(() => new Promise(() => {}));

    render(
      <RBACGuard
        permission="entities:delete"
        resource="test"
        action="delete"
        loadingFallback={<div>Loading...</div>}
      >
        <div>Protected Content</div>
      </RBACGuard>,
    );

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("renders nothing when permission check fails", async () => {
    (global.fetch as any).mockRejectedValueOnce(new Error("Network error"));

    render(
      <RBACGuard permission="entities:delete" resource="test" action="delete">
        <div>Protected Content</div>
      </RBACGuard>,
    );

    await waitFor(() => {
      expect(screen.queryByText("Protected Content")).not.toBeInTheDocument();
    });
  });

  it("uses custom endpoint when provided", async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ granted: true }),
    });

    render(
      <RBACGuard
        permission="entities:delete"
        resource="test"
        action="delete"
        endpoint="/custom/permission-check"
      >
        <div>Protected Content</div>
      </RBACGuard>,
    );

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        "/custom/permission-check",
        expect.objectContaining({
          method: "POST",
        }),
      );
    });
  });
});

describe("usePermission hook", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns hasPermission as true when permission is granted", async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ granted: true }),
    });

    const TestComponent = () => {
      const { hasPermission, isLoading } = usePermission(
        "entities:delete",
        "test",
        "delete",
      );

      if (isLoading) return <div>Loading</div>;
      return <div>{hasPermission ? "Allowed" : "Denied"}</div>;
    };

    render(<TestComponent />);

    await waitFor(() => {
      expect(screen.getByText("Allowed")).toBeInTheDocument();
    });
  });

  it("returns hasPermission as false when permission is denied", async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ granted: false }),
    });

    const TestComponent = () => {
      const { hasPermission, isLoading } = usePermission(
        "entities:delete",
        "test",
        "delete",
      );

      if (isLoading) return <div>Loading</div>;
      return <div>{hasPermission ? "Allowed" : "Denied"}</div>;
    };

    render(<TestComponent />);

    await waitFor(() => {
      expect(screen.getByText("Denied")).toBeInTheDocument();
    });
  });

  it("returns hasPermission as false when permission check fails", async () => {
    (global.fetch as any).mockRejectedValueOnce(new Error("Network error"));

    const TestComponent = () => {
      const { hasPermission, isLoading } = usePermission(
        "entities:delete",
        "test",
        "delete",
      );

      if (isLoading) return <div>Loading</div>;
      return <div>{hasPermission ? "Allowed" : "Denied"}</div>;
    };

    render(<TestComponent />);

    await waitFor(() => {
      expect(screen.getByText("Denied")).toBeInTheDocument();
    });
  });
});
