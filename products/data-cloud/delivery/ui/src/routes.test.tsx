/**
 * Routes Configuration Tests (P5-04)
 *
 * Tests for route configuration including lifecycle-aware preview gating,
 * role protection, and compatibility routes.
 *
 * @doc.type module
 * @doc.purpose Tests for route configuration and lifecycle gating
 * @doc.layer frontend
 * @doc.pattern Test
 */

import { describe, expect, it } from "vitest";
import { routes } from "./routes";

describe("routes", () => {
  describe("P5-04: audience-aware preview gating", () => {
    it("alerts route has explicit operator preview audience", () => {
      const alertsRoute = routes[0].children?.find(
        (child) => child.path === "alerts"
      );
      expect(alertsRoute).toBeDefined();
      
      const routeElement = alertsRoute?.element as any;
      expect(routeElement?.type?.displayName).toBe("RoleProtectedRoute");
      
      // Check RuntimeCapabilityRouteGate props
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.type?.displayName).toBe("RuntimeCapabilityRouteGate");
      expect(gateElement?.props?.allowPreview).toBe(true);
      expect(gateElement?.props?.allowPreviewFor).toBe("operator");
    });

    it("media/artifacts route has explicit operator preview audience", () => {
      const mediaRoute = routes[0].children?.find(
        (child) => child.path === "media/artifacts"
      );
      expect(mediaRoute).toBeDefined();
      
      const routeElement = mediaRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreview).toBe(true);
      expect(gateElement?.props?.allowPreviewFor).toBe("operator");
    });

    it("memory route has explicit operator preview audience", () => {
      const memoryRoute = routes[0].children?.find(
        (child) => child.path === "memory"
      );
      expect(memoryRoute).toBeDefined();
      
      const routeElement = memoryRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreview).toBe(true);
      expect(gateElement?.props?.allowPreviewFor).toBe("operator");
    });

    it("runtime-truth route has explicit internal preview audience", () => {
      const runtimeTruthRoute = routes[0].children?.find(
        (child) => child.path === "operations/runtime-truth"
      );
      expect(runtimeTruthRoute).toBeDefined();
      
      const routeElement = runtimeTruthRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreview).toBe(true);
      expect(gateElement?.props?.allowPreviewFor).toBe("internal");
    });

    it("events route has no preview (active lifecycle)", () => {
      const eventsRoute = routes[0].children?.find(
        (child) => child.path === "events"
      );
      expect(eventsRoute).toBeDefined();
      
      const routeElement = eventsRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreview).toBeUndefined();
    });

    it("connectors route has no preview (active lifecycle)", () => {
      const connectorsRoute = routes[0].children?.find(
        (child) => child.path === "connectors"
      );
      expect(connectorsRoute).toBeDefined();
      
      const routeElement = connectorsRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreview).toBeUndefined();
    });

    it("settings route has no preview (under development)", () => {
      const settingsRoute = routes[0].children?.find(
        (child) => child.path === "settings"
      );
      expect(settingsRoute).toBeDefined();
      
      const routeElement = settingsRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreview).toBeUndefined();
    });

    it("context route has no preview (target-only)", () => {
      const contextRoute = routes[0].children?.find(
        (child) => child.path === "context"
      );
      expect(contextRoute).toBeDefined();
      
      const routeElement = contextRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreview).toBeUndefined();
    });
  });

  describe("P5-04: role protection on sensitive routes", () => {
    it("trust route is role-protected", () => {
      const trustRoute = routes[0].children?.find(
        (child) => child.path === "trust"
      );
      expect(trustRoute).toBeDefined();
      
      const routeElement = trustRoute?.element as any;
      expect(routeElement?.type?.displayName).toBe("RoleProtectedRoute");
      expect(routeElement?.props?.routePath).toBe("/trust");
    });

    it("insights route is role-protected", () => {
      const insightsRoute = routes[0].children?.find(
        (child) => child.path === "insights"
      );
      expect(insightsRoute).toBeDefined();
      
      const routeElement = insightsRoute?.element as any;
      expect(routeElement?.type?.displayName).toBe("RoleProtectedRoute");
      expect(routeElement?.props?.routePath).toBe("/insights");
    });

    it("operations route is role-protected", () => {
      const operationsRoute = routes[0].children?.find(
        (child) => child.path === "operations"
      );
      expect(operationsRoute).toBeDefined();
      
      const routeElement = operationsRoute?.element as any;
      expect(routeElement?.type?.displayName).toBe("RoleProtectedRoute");
      expect(routeElement?.props?.routePath).toBe("/operations");
    });
  });

  describe("P5-04: compatibility routes for backward compatibility", () => {
    it("dashboard redirects to home", () => {
      const dashboardRoute = routes[0].children?.find(
        (child) => child.path === "dashboard"
      );
      expect(dashboardRoute).toBeDefined();
      
      const routeElement = dashboardRoute?.element as any;
      expect(routeElement?.type?.displayName).toBe("RoleProtectedRoute");
    });

    it("collections redirects to data", () => {
      const collectionsRoute = routes[0].children?.find(
        (child) => child.path === "collections"
      );
      expect(collectionsRoute).toBeDefined();
      
      const routeElement = collectionsRoute?.element as any;
      expect(routeElement?.type?.displayName).toBe("RoleProtectedRoute");
    });

    it("workflows redirects to pipelines", () => {
      const workflowsRoute = routes[0].children?.find(
        (child) => child.path === "workflows"
      );
      expect(workflowsRoute).toBeDefined();
      
      const routeElement = workflowsRoute?.element as any;
      expect(routeElement?.type?.displayName).toBe("RoleProtectedRoute");
    });

    it("governance redirects to trust", () => {
      const governanceRoute = routes[0].children?.find(
        (child) => child.path === "governance"
      );
      expect(governanceRoute).toBeDefined();
      
      const routeElement = governanceRoute?.element as any;
      expect(routeElement?.type?.displayName).toBe("RoleProtectedRoute");
    });
  });

  describe("primary route structure", () => {
    it("has home route at index", () => {
      const homeRoute = routes[0].children?.find(
        (child) => child.index === true
      );
      expect(homeRoute).toBeDefined();
    });

    it("has data route", () => {
      const dataRoute = routes[0].children?.find(
        (child) => child.path === "data"
      );
      expect(dataRoute).toBeDefined();
    });

    it("has pipelines route with children", () => {
      const pipelinesRoute = routes[0].children?.find(
        (child) => child.path === "pipelines"
      );
      expect(pipelinesRoute).toBeDefined();
      expect(pipelinesRoute?.children).toBeDefined();
      expect(pipelinesRoute?.children?.length).toBeGreaterThan(0);
    });

    it("has query route", () => {
      const queryRoute = routes[0].children?.find(
        (child) => child.path === "query"
      );
      expect(queryRoute).toBeDefined();
    });

    it("has 404 catch-all route", () => {
      const notFoundRoute = routes[0].children?.find(
        (child) => child.path === "*"
      );
      expect(notFoundRoute).toBeDefined();
    });
  });

  describe("lazy loading configuration", () => {
    it("uses lazy loading for IntelligentHub", () => {
      const homeRoute = routes[0].children?.find(
        (child) => child.index === true
      );
      const element = homeRoute?.element as any;
      expect(element).toBeDefined();
      // Lazy components have $$typeof and _payload properties
      expect(element?.$$typeof).toBeDefined();
    });

    it("uses lazy loading for DataExplorer", () => {
      const dataRoute = routes[0].children?.find(
        (child) => child.path === "data"
      );
      const element = dataRoute?.element as any;
      expect(element).toBeDefined();
      expect(element?.$$typeof).toBeDefined();
    });

    it("uses lazy loading for WorkflowsPage", () => {
      const pipelinesRoute = routes[0].children?.find(
        (child) => child.path === "pipelines"
      );
      const indexRoute = pipelinesRoute?.children?.find(
        (child) => child.index === true
      );
      const element = indexRoute?.element as any;
      expect(element).toBeDefined();
      expect(element?.$$typeof).toBeDefined();
    });
  });

  describe("AEP integration routes", () => {
    it("has events route with runtime gate", () => {
      const eventsRoute = routes[0].children?.find(
        (child) => child.path === "events"
      );
      expect(eventsRoute).toBeDefined();
      
      const routeElement = eventsRoute?.element as any;
      expect(routeElement?.type?.displayName).toBe("RoleProtectedRoute");
      
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.type?.displayName).toBe("RuntimeCapabilityRouteGate");
    });

    it("has memory route with operator preview", () => {
      const memoryRoute = routes[0].children?.find(
        (child) => child.path === "memory"
      );
      expect(memoryRoute).toBeDefined();
      
      const routeElement = memoryRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreviewFor).toBe("operator");
    });

    it("has entities route with operator preview", () => {
      const entitiesRoute = routes[0].children?.find(
        (child) => child.path === "entities"
      );
      expect(entitiesRoute).toBeDefined();
      
      const routeElement = entitiesRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreviewFor).toBe("operator");
    });

    it("has context route as target-only", () => {
      const contextRoute = routes[0].children?.find(
        (child) => child.path === "context"
      );
      expect(contextRoute).toBeDefined();
      
      const routeElement = contextRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      // target-only routes should not have allowPreview
      expect(gateElement?.props?.allowPreview).toBeUndefined();
    });

    it("has fabric route with operator preview", () => {
      const fabricRoute = routes[0].children?.find(
        (child) => child.path === "fabric"
      );
      expect(fabricRoute).toBeDefined();
      
      const routeElement = fabricRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreviewFor).toBe("operator");
    });

    it("has agents route with operator preview", () => {
      const agentsRoute = routes[0].children?.find(
        (child) => child.path === "agents"
      );
      expect(agentsRoute).toBeDefined();
      
      const routeElement = agentsRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreviewFor).toBe("operator");
    });
  });

  describe("plugins route with Outlet pattern", () => {
    it("has plugins route with children", () => {
      const pluginsRoute = routes[0].children?.find(
        (child) => child.path === "plugins"
      );
      expect(pluginsRoute).toBeDefined();
      expect(pluginsRoute?.children).toBeDefined();
      expect(pluginsRoute?.children?.length).toBe(2);
    });

    it("plugins index route uses lazy loading", () => {
      const pluginsRoute = routes[0].children?.find(
        (child) => child.path === "plugins"
      );
      const indexRoute = pluginsRoute?.children?.find(
        (child) => child.index === true
      );
      const element = indexRoute?.element as any;
      expect(element).toBeDefined();
      expect(element?.$$typeof).toBeDefined();
    });

    it("plugins detail route uses lazy loading", () => {
      const pluginsRoute = routes[0].children?.find(
        (child) => child.path === "plugins"
      );
      const detailRoute = pluginsRoute?.children?.find(
        (child) => child.path === ":id"
      );
      const element = detailRoute?.element as any;
      expect(element).toBeDefined();
      expect(element?.$$typeof).toBeDefined();
    });

    it("plugins route has operator preview audience", () => {
      const pluginsRoute = routes[0].children?.find(
        (child) => child.path === "plugins"
      );
      const routeElement = pluginsRoute?.element as any;
      const gateElement = routeElement?.props?.children as any;
      expect(gateElement?.props?.allowPreviewFor).toBe("operator");
    });
  });
});
