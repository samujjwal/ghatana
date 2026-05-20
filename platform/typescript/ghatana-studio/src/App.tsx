/**
 * Ghatana Studio - Unified Product Development Experience
 *
 * @doc.type component
 * @doc.purpose Customer-facing Studio shell for product ideation, lifecycle execution, health, learning, and evolution
 * @doc.layer platform
 */

import type { ReactElement, ReactNode } from "react";
import { useMemo } from "react";
import { Route, Routes, useLocation } from "react-router";
import { Badge, EmptyState, ErrorBoundary } from "@ghatana/design-system";
import {
  ProductShell,
  useProductShellConfig,
  type ProductRouteCapability,
  type ProductShellConfig,
} from "@ghatana/product-shell";

import AgentsPage from "./routes/AgentsPage";
import ArtifactsPage from "./routes/ArtifactsPage";
import BlueprintsPage from "./routes/BlueprintsPage";
import BuilderPage from "./routes/BuilderPage";
import CanvasPage from "./routes/CanvasPage";
import DeploymentsPage from "./routes/DeploymentsPage";
import { DesignSystemPage } from "./routes/DesignSystemPage";
import DevelopPage from "./routes/DevelopPage";
import FidelityReportPage from "./routes/FidelityReportPage";
import HealthPage from "./routes/HealthPage";
import HomePage from "./routes/HomePage";
import IdeasPage from "./routes/IdeasPage";
import ImportDecompilePage from "./routes/ImportDecompilePage";
import LearnPage from "./routes/LearnPage";
import LifecyclePage from "./routes/LifecyclePage";
import OpeningPilotsPage from "./routes/OpeningPilotsPage";
import PreviewPage from "./routes/PreviewPage";
import { getStudioCapabilityState } from "./api/kernelLifecycleClient";
import {
  findStudioNavItemFromItems,
  getStudioRouteOwnershipMetadata,
  resolveStudioNavItems,
  type StudioNavItem,
  type StudioRouteStatus,
} from "./navigation/studioNavigation";
import { useStudioTranslation } from "./i18n/studioTranslations";
import { STUDIO_ENVIRONMENT_CONFIG } from "./config/studioEnvironment";
import { studioLogger } from "./logging/studioLogger";
import { useStudioLifecycleData } from "./data/StudioLifecycleDataContext";

interface RouteShellProps {
  readonly title: string;
  readonly description: string;
  readonly status: StudioRouteStatus;
}

interface RouteAccessGuardProps {
  readonly navItem: StudioNavItem;
  readonly children: ReactNode;
}

function RouteAccessGuard(props: RouteAccessGuardProps): ReactElement {
  const { navItem, children } = props;
  const t = useStudioTranslation();
  const metadata = getStudioRouteOwnershipMetadata(navItem.id);

  if (navItem.exposure === "visible" || navItem.exposure === "preview") {
    return <>{children}</>;
  }

  if (navItem.exposure === "hidden") {
    return (
      <RouteShell
        title={t("studio.route.notFound.title")}
        description={t("studio.route.notFound.description")}
        status="blocked"
      />
    );
  }

  return (
    <section
      className="mb-4 rounded-md border border-amber-300 bg-amber-50 p-4"
      aria-label={t("studio.route.guard.accessDeniedState")}
      aria-live="polite"
    >
      <div className="flex items-center justify-between gap-3">
        <p className="text-sm font-semibold text-amber-800">
          {t("studio.route.guard.accessDisabled")}
        </p>
        <Badge tone="warning" variant="soft">
          {t(`studio.status.${navItem.status}`)}
        </Badge>
      </div>
      <p className="mt-2 text-sm text-amber-800">
        {t("studio.route.guard.reasonPrefix")} {navItem.requiredCapability}
      </p>
      <dl className="mt-3 grid gap-2 text-xs text-amber-900 sm:grid-cols-3">
        <div>
          <dt className="font-semibold">
            {t("studio.route.guard.ownerProductLabel")}
          </dt>
          <dd>{metadata.ownerProduct}</dd>
        </div>
        <div>
          <dt className="font-semibold">
            {t("studio.route.guard.truthSourceLabel")}
          </dt>
          <dd>{metadata.lifecycleTruthSource}</dd>
        </div>
        <div>
          <dt className="font-semibold">
            {t("studio.route.guard.previewTrustLabel")}
          </dt>
          <dd>{metadata.previewTrustState}</dd>
        </div>
      </dl>
      <a
        className="mt-3 inline-flex text-sm font-medium text-amber-900 underline"
        href={STUDIO_ENVIRONMENT_CONFIG.docsUrl}
        target="_blank"
        rel="noopener noreferrer"
      >
        {t("studio.header.documentation")}
      </a>
    </section>
  );
}

function RouteShell(props: RouteShellProps): ReactElement {
  const { title, description, status } = props;
  const t = useStudioTranslation();
  const titleId = `${title.toLowerCase().replaceAll(" ", "-")}-route-title`;

  return (
    <section className="studio-section" aria-labelledby={titleId}>
      <div className="studio-card">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 id={titleId} className="text-xl font-semibold text-gray-950">
            {title}
          </h2>
          <Badge
            tone={
              status === "blocked"
                ? "danger"
                : status === "degraded"
                  ? "warning"
                  : "neutral"
            }
          >
            {t(`studio.status.${status}`)}
          </Badge>
        </div>
        <EmptyState title={title} description={description} size="md" />
      </div>
    </section>
  );
}

function NotFoundRoute(): ReactElement {
  const t = useStudioTranslation();

  return (
    <RouteShell
      title={t("studio.route.notFound.title")}
      description={t("studio.route.notFound.description")}
      status="blocked"
    />
  );
}

function SettingsRoute(): ReactElement {
  const t = useStudioTranslation();

  return (
    <RouteShell
      title={t("studio.route.settings.title")}
      description={t("studio.route.settings.description")}
      status="ready"
    />
  );
}

function logStudioError(errorContext: { readonly error: Error }): void {
  studioLogger.error("Ghatana Studio route error", {
    message: errorContext.error.message,
    stack: errorContext.error.stack,
  });
}

export default function App(): ReactElement {
  const location = useLocation();
  const t = useStudioTranslation();
  const lifecycleData = useStudioLifecycleData();
  const runtimeConfigured = lifecycleData.authenticatedUserId !== undefined;
  const navItems = resolveStudioNavItems(
    getStudioCapabilityState({
      runtimeConfigured,
      lifecycleStatus: lifecycleData.snapshot.status,
      selectedProviderMode: lifecycleData.selectedProviderMode,
      productUnit: lifecycleData.snapshot.productUnit,
      selectedRun: lifecycleData.snapshot.selectedRun,
      manifestLoadState: lifecycleData.snapshot.manifestLoadState,
    }),
  );
  const shellRoutes = useMemo(
    () =>
      navItems.map(
        (item): ProductRouteCapability => ({
          path: item.path,
          label: item.label,
          description: item.requiredCapability,
          lifecycle: item.exposure === "preview" ? "preview" : "stable",
          discoverable: item.isCustomerVisible && item.exposure !== "hidden",
        }),
      ),
    [navItems],
  );
  const activeItem = findStudioNavItemFromItems(location.pathname, navItems);
  const activeMetadata =
    activeItem === undefined
      ? undefined
      : getStudioRouteOwnershipMetadata(activeItem.id);
  const shellConfig = useProductShellConfig({
    productName: t("studio.brand.title"),
    logo: (
      <span className="text-sm font-semibold tracking-tight text-blue-700">
        GS
      </span>
    ),
    currentRole: "viewer",
    roleOrder: { viewer: 0 },
    routes: shellRoutes,
    headerActions: (
      <div className="flex items-center gap-4 text-sm text-gray-500">
        <div className="text-right">
          <h2 className="text-sm font-medium text-gray-700">
            {activeItem === undefined
              ? t("studio.route.notFound.title")
              : t(activeItem.labelKey)}
          </h2>
          <p className="text-xs text-gray-500">
            {activeMetadata
              ? `${activeMetadata.ownerProduct} ${t("studio.header.ownershipSuffix")}`
              : t("studio.header.unknownRoute")}
          </p>
        </div>
        <span>{STUDIO_ENVIRONMENT_CONFIG.version}</span>
        <a
          href={STUDIO_ENVIRONMENT_CONFIG.docsUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="text-blue-600 hover:text-blue-700"
        >
          {t("studio.header.documentation")}
        </a>
      </div>
    ),
    sidebarFooter: (
      <p className="text-xs text-gray-500">{t("studio.brand.subtitle")}</p>
    ),
  } satisfies ProductShellConfig);
  const routeById = Object.fromEntries(
    navItems.map((item) => [item.id, item]),
  ) as Record<string, StudioNavItem>;

  return (
    <ErrorBoundary
      onError={logStudioError}
      resetButtonText={t("studio.app.retryStudio")}
    >
      <ProductShell
        config={shellConfig}
        contentClassName="bg-gray-50 pt-20 p-6"
        mainContentId="main-content"
        mainContentTabIndex={-1}
        mainContentRole="main"
      >
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route
            path="/ideas"
            element={
              <RouteAccessGuard navItem={routeById.ideas}>
                <IdeasPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/blueprints"
            element={
              <RouteAccessGuard navItem={routeById.blueprints}>
                <BlueprintsPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/canvas"
            element={
              <RouteAccessGuard navItem={routeById.canvas}>
                <CanvasPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/builder"
            element={
              <RouteAccessGuard navItem={routeById.builder}>
                <BuilderPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/design-system"
            element={
              <RouteAccessGuard navItem={routeById["design-system"]}>
                <DesignSystemPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/develop"
            element={
              <RouteAccessGuard navItem={routeById.develop}>
                <DevelopPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/lifecycle"
            element={
              <RouteAccessGuard navItem={routeById.lifecycle}>
                <LifecyclePage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/lifecycle/pilots"
            element={<OpeningPilotsPage />}
          />
          <Route
            path="/agents"
            element={
              <RouteAccessGuard navItem={routeById.agents}>
                <AgentsPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/artifacts"
            element={
              <RouteAccessGuard navItem={routeById.artifacts}>
                <ArtifactsPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/deployments"
            element={
              <RouteAccessGuard navItem={routeById.deployments}>
                <DeploymentsPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/health"
            element={
              <RouteAccessGuard navItem={routeById.health}>
                <HealthPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/learn"
            element={
              <RouteAccessGuard navItem={routeById.learn}>
                <LearnPage />
              </RouteAccessGuard>
            }
          />
          <Route
            path="/settings"
            element={
              <RouteAccessGuard navItem={routeById.settings}>
                <SettingsRoute />
              </RouteAccessGuard>
            }
          />
          <Route path="*" element={<NotFoundRoute />} />
          {/* Utility routes — no nav item, accessible directly */}
          <Route path="/import" element={<ImportDecompilePage />} />
          <Route path="/fidelity-report" element={<FidelityReportPage />} />
          <Route path="/preview" element={<PreviewPage />} />
        </Routes>
      </ProductShell>
    </ErrorBoundary>
  );
}
