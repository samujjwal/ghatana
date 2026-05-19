/**
 * @ghatana/ghatana-studio navigation test suite
 * Tests for Ghatana Studio navigation and routing
 */

import { describe, it, expect } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import App from "../App";
import {
  resolveStudioNavItems,
  resolveStudioRouteCapabilityState,
  STUDIO_NAV_ITEMS,
  STUDIO_ROUTE_OWNERSHIP_METADATA,
  STUDIO_SHARED_UX_STATES,
  validateStudioRouteOwnershipMetadata,
  type RouteExposurePolicy,
  type StudioNavItem,
} from "../navigation/studioNavigation";
import { STUDIO_TRANSLATIONS } from "../i18n/studioTranslations";

const UNCONFIGURED_NAV_ITEMS = resolveStudioNavItems(
  resolveStudioRouteCapabilityState({
    runtimeConfigured: false,
    lifecycleStatus: "unconfigured",
  }),
);

function renderApp(initialEntry: string = "/"): void {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <App />
    </MemoryRouter>,
  );
}

type CapabilityScenario = {
  readonly name: string;
  readonly input: {
    readonly runtimeConfigured: boolean;
    readonly lifecycleStatus: "unconfigured" | "loading" | "ready" | "degraded";
    readonly productUnit?: {
      readonly lifecycleExecutionAllowed?: boolean;
      readonly metadata?: {
        readonly lifecycleExecutionAllowed?: boolean;
      };
    };
  };
  readonly expectedCapability: {
    readonly runtimeConfigured: boolean;
    readonly lifecycleConfigured: boolean;
    readonly lifecycleExecutionAllowed: boolean;
    readonly dataCloudEvidenceReady: boolean;
  };
  readonly expectedExposureByRoute: Record<
    StudioNavItem["id"],
    RouteExposurePolicy
  >;
};

const CAPABILITY_MATRIX_SCENARIOS: readonly CapabilityScenario[] = [
  {
    name: "runtime unconfigured",
    input: {
      runtimeConfigured: false,
      lifecycleStatus: "unconfigured",
    },
    expectedCapability: {
      runtimeConfigured: false,
      lifecycleConfigured: false,
      lifecycleExecutionAllowed: false,
      dataCloudEvidenceReady: false,
    },
    expectedExposureByRoute: {
      home: "visible",
      ideas: "disabled",
      blueprints: "visible",
      canvas: "visible",
      develop: "disabled",
      lifecycle: "disabled",
      agents: "disabled",
      artifacts: "disabled",
      deployments: "hidden",
      health: "disabled",
      learn: "visible",
      settings: "visible",
      "design-system": "visible",
    },
  },
  {
    name: "runtime configured but lifecycle not configured",
    input: {
      runtimeConfigured: true,
      lifecycleStatus: "unconfigured",
    },
    expectedCapability: {
      runtimeConfigured: true,
      lifecycleConfigured: false,
      lifecycleExecutionAllowed: false,
      dataCloudEvidenceReady: false,
    },
    expectedExposureByRoute: {
      home: "visible",
      ideas: "disabled",
      blueprints: "visible",
      canvas: "visible",
      develop: "disabled",
      lifecycle: "disabled",
      agents: "disabled",
      artifacts: "disabled",
      deployments: "hidden",
      health: "disabled",
      learn: "visible",
      settings: "visible",
      "design-system": "visible",
    },
  },
  {
    name: "lifecycle configured without execution allowance",
    input: {
      runtimeConfigured: true,
      lifecycleStatus: "ready",
    },
    expectedCapability: {
      runtimeConfigured: true,
      lifecycleConfigured: true,
      lifecycleExecutionAllowed: false,
      dataCloudEvidenceReady: false,
    },
    expectedExposureByRoute: {
      home: "visible",
      ideas: "disabled",
      blueprints: "visible",
      canvas: "visible",
      develop: "visible",
      lifecycle: "visible",
      agents: "disabled",
      artifacts: "disabled",
      deployments: "hidden",
      health: "disabled",
      learn: "visible",
      settings: "visible",
      "design-system": "visible",
    },
  },
  {
    name: "lifecycle configured with execution allowance",
    input: {
      runtimeConfigured: true,
      lifecycleStatus: "ready",
      productUnit: {
        lifecycleExecutionAllowed: true,
      },
    },
    expectedCapability: {
      runtimeConfigured: true,
      lifecycleConfigured: true,
      lifecycleExecutionAllowed: true,
      dataCloudEvidenceReady: true,
    },
    expectedExposureByRoute: {
      home: "visible",
      ideas: "disabled",
      blueprints: "visible",
      canvas: "visible",
      develop: "visible",
      lifecycle: "visible",
      agents: "visible",
      artifacts: "visible",
      deployments: "preview",
      health: "visible",
      learn: "visible",
      settings: "visible",
      "design-system": "visible",
    },
  },
];

describe("@ghatana/ghatana-studio - Navigation", () => {
  describe("Route Navigation", () => {
    it("should render App component", () => {
      renderApp();
      expect(screen.getByText("Ghatana Studio")).toBeTruthy();
    });

    it("should display all customer-visible navigation links in sidebar", () => {
      renderApp();

      for (const item of UNCONFIGURED_NAV_ITEMS.filter(
        (candidate) => candidate.exposure !== "hidden",
      )) {
        expect(
          screen.getByRole("link", { name: new RegExp(item.label, "i") }),
        ).toBeInTheDocument();
      }
    });

    it("should have translations for every canonical navigation label key", () => {
      for (const item of STUDIO_NAV_ITEMS) {
        expect(STUDIO_TRANSLATIONS[item.labelKey]).toBe(item.label);
      }
    });

    it("should include route status metadata for every navigation item", () => {
      for (const item of STUDIO_NAV_ITEMS) {
        expect(item.statusReasonCode.length).toBeGreaterThan(0);
        expect(item.statusMessageKey).toBe(
          `studio.navigation.status.${item.id}`,
        );
        expect(item.requiredNextAction.length).toBeGreaterThan(0);
        expect(item.evidenceRefs.length).toBeGreaterThan(0);
      }
    });

    it("should validate route ownership metadata for every route", () => {
      expect(validateStudioRouteOwnershipMetadata()).toEqual([]);

      for (const item of STUDIO_NAV_ITEMS) {
        const metadata = STUDIO_ROUTE_OWNERSHIP_METADATA[item.id];
        expect(metadata.routeId).toBe(item.id);
        expect(metadata.path).toBe(item.path);
        expect(metadata.permissions).toContain(item.requiredCapability);
        expect(metadata.ownerProduct.length).toBeGreaterThan(0);
        expect(metadata.requiredProviders).toBeDefined();
      }
    });

    it("should keep shared UX state vocabulary complete and canonical", () => {
      expect(STUDIO_SHARED_UX_STATES).toEqual([
        "loading",
        "empty",
        "error",
        "blocked",
        "degraded",
        "access-denied",
        "pending-approval",
        "requires-verification",
      ]);
      expect(
        STUDIO_ROUTE_OWNERSHIP_METADATA.lifecycle.supportedUxStates,
      ).toEqual(
        expect.arrayContaining(["pending-approval", "requires-verification"]),
      );
    });

    it("should display header with version", () => {
      renderApp();
      expect(screen.getByText("dev")).toBeTruthy();
    });

    it("should render a route-aware page title", () => {
      renderApp("/lifecycle");
      expect(
        screen.getAllByRole("heading", { name: /lifecycle/i }).length,
      ).toBeGreaterThan(0);
      expect(
        screen.getByText("Product Development Kernel owned"),
      ).toBeInTheDocument();
    });

    it("should mark the active route with aria-current", () => {
      renderApp("/health");
      expect(screen.getByRole("link", { name: /health/i })).toHaveAttribute(
        "aria-current",
        "page",
      );
    });

    it("should expose visible focus styling for keyboard navigation links", () => {
      renderApp("/health");
      expect(screen.getByRole("link", { name: /health/i })).toHaveClass(
        "focus-visible:outline",
      );
    });

    it("should render not found for unknown routes", () => {
      renderApp("/missing-route");
      expect(screen.getAllByText("Page not found").length).toBeGreaterThan(0);
      expect(
        screen.getByText(/not registered in the canonical navigation/i),
      ).toBeInTheDocument();
    });

    it("should render production Studio route surfaces for lifecycle work", () => {
      renderApp("/develop");
      expect(
        screen.getByText("Route access is disabled in this runtime mode."),
      ).toBeInTheDocument();
      expect(
        screen.getByLabelText("Access denied route state"),
      ).toBeInTheDocument();
      expect(
        screen.getByText("Product Development Kernel"),
      ).toBeInTheDocument();
    });

    it("should render lifecycle truth panels without raw execution controls", () => {
      renderApp("/lifecycle");
      expect(
        screen.getByText("Route access is disabled in this runtime mode."),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: /run command/i }),
      ).not.toBeInTheDocument();
    });

    it("should render agent, artifact, deployment, and health route content", () => {
      renderApp("/agents");
      expect(
        screen.getByText("Route access is disabled in this runtime mode."),
      ).toBeInTheDocument();

      cleanup();
      renderApp("/artifacts");
      expect(
        screen.getByText("Route access is disabled in this runtime mode."),
      ).toBeInTheDocument();

      cleanup();
      renderApp("/deployments");
      expect(screen.getAllByText(/Page not found/i).length).toBeGreaterThan(0);

      cleanup();
      renderApp("/health");
      expect(
        screen.getByText("Route access is disabled in this runtime mode."),
      ).toBeInTheDocument();
    });

    it("should render YAPPC workflow routes with intent and artifact intelligence evidence", () => {
      renderApp("/ideas");
      expect(
        screen.getByText("Route access is disabled in this runtime mode."),
      ).toBeInTheDocument();

      cleanup();
      renderApp("/blueprints");
      expect(
        screen.getAllByRole("heading", { name: "Blueprints" }).length,
      ).toBeGreaterThan(0);
      expect(screen.getByText("web-app")).toBeInTheDocument();
      expect(screen.getByText(/generated changes/i)).toBeInTheDocument();

      cleanup();
      renderApp("/canvas");
      expect(
        screen.getAllByRole("heading", { name: "Canvas" }).length,
      ).toBeGreaterThan(0);
      expect(screen.getByText(/Residual islands/i)).toBeInTheDocument();
      expect(
        screen.getAllByText(/legacy-promo-widget/i).length,
      ).toBeGreaterThan(0);

      cleanup();
      renderApp("/learn");
      expect(
        screen.getAllByRole("heading", { name: "Learn" }).length,
      ).toBeGreaterThan(0);
      expect(
        screen.getByText(/Promote legacy-promo-widget/i),
      ).toBeInTheDocument();
    });
  });

  describe("Sidebar Navigation", () => {
    it("should render sidebar with all sections", () => {
      renderApp();
      expect(
        screen.getByRole("navigation", { name: /product navigation/i }),
      ).toBeInTheDocument();
      expect(screen.getByRole("main")).toBeInTheDocument();
    });

    it("should not include duplicate navigation ids or paths", () => {
      const ids = STUDIO_NAV_ITEMS.map((item: StudioNavItem) => item.id);
      const paths = STUDIO_NAV_ITEMS.map((item: StudioNavItem) => item.path);

      expect(new Set(ids).size).toBe(ids.length);
      expect(new Set(paths).size).toBe(paths.length);
    });

    it("should preserve landmarks and active route state for every customer route", () => {
      for (const item of UNCONFIGURED_NAV_ITEMS.filter(
        (candidate) => candidate.exposure !== "hidden",
      )) {
        cleanup();
        renderApp(item.path);

        expect(
          screen.getByRole("navigation", { name: /product navigation/i }),
        ).toBeInTheDocument();
        expect(screen.getByRole("main")).toBeInTheDocument();
        expect(
          screen.getByRole("link", { name: new RegExp(item.label, "i") }),
        ).toHaveAttribute("aria-current", "page");
      }
    });

    it("should hide routes with exposure: hidden from navigation", () => {
      renderApp();
      const hiddenRoutes = UNCONFIGURED_NAV_ITEMS.filter(
        (item) => item.exposure === "hidden",
      );
      for (const route of hiddenRoutes) {
        expect(
          screen.queryByRole("link", { name: new RegExp(route.label, "i") }),
        ).not.toBeInTheDocument();
      }
    });

    it("should render but disable routes with exposure: disabled", () => {
      const disabledRoutes = UNCONFIGURED_NAV_ITEMS.filter(
        (item) => item.exposure === "disabled",
      );
      for (const route of disabledRoutes) {
        cleanup();
        renderApp(route.path);
        expect(
          screen.getByText("Route access is disabled in this runtime mode."),
        ).toBeInTheDocument();
      }
    });

    it("should render and enable routes with exposure: visible", () => {
      renderApp();
      const visibleRoutes = UNCONFIGURED_NAV_ITEMS.filter(
        (item) => item.exposure === "visible" || item.exposure === "preview",
      );
      for (const route of visibleRoutes) {
        const link = screen.getByRole("link", {
          name: new RegExp(route.label, "i"),
        });
        expect(link).toBeInTheDocument();
        expect(link).not.toHaveClass("cursor-not-allowed");
        expect(link).not.toHaveAttribute("aria-disabled");
      }
    });
  });

  describe("Capability Matrix", () => {
    it.each(CAPABILITY_MATRIX_SCENARIOS)(
      "should resolve capability and exposure for $name",
      (scenario) => {
        const capability = resolveStudioRouteCapabilityState(scenario.input);
        expect(capability).toEqual(scenario.expectedCapability);

        const resolvedItems = resolveStudioNavItems(capability);
        const exposures = new Map(
          resolvedItems.map((item) => [item.id, item.exposure]),
        );

        for (const route of STUDIO_NAV_ITEMS) {
          expect(exposures.get(route.id)).toBe(
            scenario.expectedExposureByRoute[route.id],
          );
        }
      },
    );
  });
});
