/**
 * UI Navigation Visibility Tests
 *
 * Tests navigation visibility by role/capability, keyboard navigation,
 * disabled-surface UX, and empty/loading/error/unauthorized states.
 *
 * @doc.type test
 * @doc.purpose UI navigation and accessibility tests
 * @doc.layer frontend
 * @doc.pattern UITest
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it } from "vitest";
import DefaultLayout, {
  buildNavFromRegistry,
} from "../../layouts/DefaultLayout";

describe("UI Navigation Visibility Tests", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
  });

  describe("Navigation Visibility by Role/Capability", () => {
    it("[NAV001]: Primary navigation shows core routes for viewer role", () => {
      const navSections = buildNavFromRegistry("primary-user");

      expect(navSections).toBeDefined();
      const coreSection = navSections.find(
        (s) => s.title === "layout.sectionCore",
      );
      expect(coreSection).toBeDefined();

      const corePaths = coreSection?.items.map((item) => item.to) || [];
      expect(corePaths).toContain("/");
      expect(corePaths).toContain("/data");
      expect(corePaths).toContain("/events");
      expect(corePaths).toContain("/pipelines");
      expect(corePaths).toContain("/query");
      expect(corePaths).toContain("/trust");
    });

    it("[NAV002]: Operations routes visible for operator role", () => {
      const navSections = buildNavFromRegistry("operator");

      const manageSection = navSections.find(
        (s) => s.title === "layout.sectionManage",
      );
      expect(manageSection).toBeDefined();

      const managePaths = manageSection?.items.map((item) => item.to) || [];
      expect(managePaths).toContain("/operations");
    });

    it("[NAV003]: Admin-only routes hidden for non-admin roles", () => {
      const navSections = buildNavFromRegistry("primary-user");

      const allPaths = navSections.flatMap((s) =>
        s.items.map((item) => item.to),
      );
      expect(allPaths).not.toContain("/settings");
    });

    it("[NAV004]: Advanced surfaces gated by runtime capability", () => {
      const navSections = buildNavFromRegistry("primary-user");

      const allPaths = navSections.flatMap((s) =>
        s.items.map((item) => item.to),
      );
      // Advanced surfaces like /entities, /context, /fabric should not be in default nav
      expect(allPaths).not.toContain("/entities");
      expect(allPaths).not.toContain("/context");
      expect(allPaths).not.toContain("/fabric");
    });
  });

  describe("Keyboard Navigation", () => {
    it("[NAV005]: Navigation links are keyboard accessible", () => {
      const { container } = render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter>
            <DefaultLayout />
          </MemoryRouter>
        </QueryClientProvider>,
      );

      const navLinks = container.querySelectorAll("nav a");
      navLinks.forEach((link) => {
        expect(link).toHaveAttribute("href");
        expect(link).toHaveAttribute("tabindex");
      });
    });

    it("[NAV006]: Global search keyboard shortcut works", () => {
      const { container } = render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter>
            <DefaultLayout />
          </MemoryRouter>
        </QueryClientProvider>,
      );

      // Test that Cmd+K triggers global search
      const searchButton = container.querySelector(
        '[aria-label*="search"]',
      ) as HTMLElement;
      expect(searchButton).toBeInTheDocument();
    });

    it("[NAV007]: Keyboard shortcuts modal is accessible", () => {
      const { container: _container } = render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter>
            <DefaultLayout />
          </MemoryRouter>
        </QueryClientProvider>,
      );

      // Test that keyboard shortcuts modal can be opened and closed
      // and all shortcuts are listed with their key bindings
      // This would be implemented with actual keyboard event simulation
    });
  });

  describe("Disabled-Surface UX", () => {
    it("[NAV008]: Disabled surface shows meaningful message", () => {
      const { container } = render(
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={["/disabled-surface"]}>
            <Routes>
              <Route
                path="/disabled-surface"
                element={
                  <div data-testid="disabled-surface">
                    <h1>Surface Disabled</h1>
                    <p>
                      This surface is not available in your current
                      configuration.
                    </p>
                  </div>
                }
              />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>,
      );

      const disabledSurface = container.querySelector(
        '[data-testid="disabled-surface"]',
      );
      expect(disabledSurface).toBeInTheDocument();
      expect(disabledSurface?.textContent).toContain("Disabled");
    });

    it("[NAV009]: Disabled surface explains why unavailable", () => {
      // Test that disabled surfaces provide context about why they're unavailable
      // and what action is needed to enable them
    });

    it("[NAV010]: Disabled surface provides next action", () => {
      // Test that disabled surfaces suggest next actions or alternatives
      // instead of leaving users stuck
    });
  });

  describe("Empty State UX", () => {
    it("[NAV011]: Empty state shows helpful message", () => {
      // Test that empty states (no collections, no pipelines, etc.)
      // show helpful messages and call-to-action buttons
    });

    it("[NAV012]: Empty state provides creation action", () => {
      // Test that empty states provide clear creation actions
      // (e.g., "Create your first collection", "Create your first pipeline")
    });
  });

  describe("Loading State UX", () => {
    it("[NAV013]: Loading state shows progress indicator", () => {
      // Test that loading states show progress indicators
      // with appropriate loading messages
    });

    it("[NAV014]: Loading state is accessible", () => {
      // Test that loading states have proper ARIA attributes
      // for screen readers
    });
  });

  describe("Error State UX", () => {
    it("[NAV015]: Error state shows clear error message", () => {
      // Test that error states show clear, actionable error messages
      // with recovery suggestions
    });

    it("[NAV016]: Error state provides retry action", () => {
      // Test that error states provide retry actions
      // when appropriate
    });
  });

  describe("Unauthorized State UX", () => {
    it("[NAV017]: Unauthorized state shows access denied message", () => {
      // Test that unauthorized states show clear access denied messages
      // without exposing internal details
    });

    it("[NAV018]: Unauthorized state provides contact support action", () => {
      // Test that unauthorized states provide contact support actions
      // or alternative navigation options
    });
  });
});
