package com.ghatana.yappc.kernel;

import io.activej.promise.Promise;

/**
 * G10-003: Product Web Route/Page Skeleton Generator
 *
 * Generates product web route and page skeletons from Kernel route contract.
 *
 * @doc.type class
 * @doc.purpose Generate product web route/page skeletons from Kernel route contract
 * @doc.layer integration
 * @doc.pattern Generator
 */
public class WebRouteGenerator {

    /**
     * Generate web route skeleton for a product route
     */
    public Promise<WebRouteSkeleton> generateRouteSkeleton(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            WebRouteSkeleton skeleton = new WebRouteSkeleton();
            
            skeleton.setRouteId(route.getId());
            skeleton.setPath(route.getPath());
            skeleton.setComponentName(route.getComponent());
            skeleton.setStability(route.getStability());
            skeleton.setVisibility(route.getVisibility());
            
            String routeFilePath = generateRouteFilePath(route);
            skeleton.setRouteFilePath(routeFilePath);
            
            String pageFilePath = generatePageFilePath(route);
            skeleton.setPageFilePath(pageFilePath);
            
            String routeCode = generateRouteCode(route);
            skeleton.setRouteCode(routeCode);
            
            String pageCode = generatePageCode(route);
            skeleton.setPageCode(pageCode);
            
            return skeleton;
        });
    }

    /**
     * Generate route file path
     */
    private String generateRouteFilePath(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return "products/" + productPathSegment(route) + "/apps/web/src/routes/" + ProductGenerationNaming.kebabCase(route.getId()) + ".tsx";
    }

    /**
     * Generate page file path
     */
    private String generatePageFilePath(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return "products/" + productPathSegment(route) + "/apps/web/src/pages/" + ProductGenerationNaming.pascalCase(route.getComponent()) + ".tsx";
    }

    /**
     * Generate route code
     */
    private String generateRouteCode(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return String.format("""
import { lazy } from 'react';
import type { RouteObject } from 'react-router-dom';

const %s = lazy(() => import('../pages/%s'));

export const %sRoute: RouteObject = {
  path: '%s',
  element: <%s />,
  handle: {
    requiredRoles: [%s],
    requiredFeatures: [%s],
  },
};
""", 
            route.getComponent(),
            route.getComponent(),
            ProductGenerationNaming.camelCase(route.getId()),
            route.getPath(),
            route.getComponent(),
            quoteStringList(route.getRoles()),
            quoteStringList(route.getRequiredFeatures())
        );
    }

    /**
     * Generate page code
     */
    private String generatePageCode(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return String.format("""
import { useTranslation } from 'react-i18next';
import { useSessionContext } from '../contexts/SessionContext';

/**
 * %s Page
 *
 * Route: %s
 * Stability: %s
 * Visibility: %s
 */
export default function %s() {
  const { t } = useTranslation();
  const session = useSessionContext();

  return (
    <div className="%s">
      <h1>{t('%s.title')}</h1>
      <span data-route-id="%s" data-session-present={Boolean(session)} />
    </div>
  );
}
""",
            route.getComponent(),
            route.getPath(),
            route.getStability(),
            route.getVisibility(),
            route.getComponent(),
            ProductGenerationNaming.kebabCase(route.getId()),
            ProductGenerationNaming.kebabCase(route.getId()),
            route.getId()
        );
    }

    private String productPathSegment(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return ProductGenerationNaming.productPathSegment(route.getProductId());
    }

    private String quoteStringList(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
            .map(value -> "'" + value.replace("'", "\\'") + "'")
            .collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Web Route Skeleton model
     */
    public static class WebRouteSkeleton {
        private String routeId;
        private String path;
        private String componentName;
        private String stability;
        private String visibility;
        private String routeFilePath;
        private String pageFilePath;
        private String routeCode;
        private String pageCode;

        public String getRouteId() { return routeId; }
        public void setRouteId(String routeId) { this.routeId = routeId; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getComponentName() { return componentName; }
        public void setComponentName(String componentName) { this.componentName = componentName; }

        public String getStability() { return stability; }
        public void setStability(String stability) { this.stability = stability; }

        public String getVisibility() { return visibility; }
        public void setVisibility(String visibility) { this.visibility = visibility; }

        public String getRouteFilePath() { return routeFilePath; }
        public void setRouteFilePath(String routeFilePath) { this.routeFilePath = routeFilePath; }

        public String getPageFilePath() { return pageFilePath; }
        public void setPageFilePath(String pageFilePath) { this.pageFilePath = pageFilePath; }

        public String getRouteCode() { return routeCode; }
        public void setRouteCode(String routeCode) { this.routeCode = routeCode; }

        public String getPageCode() { return pageCode; }
        public void setPageCode(String pageCode) { this.pageCode = pageCode; }
    }
}
