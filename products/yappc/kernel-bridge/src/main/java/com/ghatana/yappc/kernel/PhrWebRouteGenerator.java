package com.ghatana.yappc.kernel;

import io.activej.promise.Promise;

import java.util.HashMap;
import java.util.Map;

/**
 * G10-003: PHR Web Route/Page Skeleton Generator
 *
 * Generates PHR web route and page skeletons from Kernel route contract.
 * This extends YAPPC's generic code generation for PHR-specific web scaffolding.
 *
 * @doc.type class
 * @doc.purpose Generate PHR web route/page skeletons from Kernel route contract
 * @doc.layer integration
 * @doc.pattern Generator
 */
public class PhrWebRouteGenerator {

    /**
     * Generate web route skeleton for a PHR route
     */
    public Promise<WebRouteSkeleton> generateRouteSkeleton(PhrIntelligenceArtifactImporter.PhrRoute route) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            WebRouteSkeleton skeleton = new WebRouteSkeleton();
            
            skeleton.setRouteId(route.getId());
            skeleton.setPath(route.getPath());
            skeleton.setComponentName(route.getComponent());
            skeleton.setStability(route.getStability());
            skeleton.setVisibility(route.getVisibility());
            
            // Generate route file path
            String routeFilePath = generateRouteFilePath(route);
            skeleton.setRouteFilePath(routeFilePath);
            
            // Generate page file path
            String pageFilePath = generatePageFilePath(route);
            skeleton.setPageFilePath(pageFilePath);
            
            // Generate route code
            String routeCode = generateRouteCode(route);
            skeleton.setRouteCode(routeCode);
            
            // Generate page code
            String pageCode = generatePageCode(route);
            skeleton.setPageCode(pageCode);
            
            return skeleton;
        });
    }

    /**
     * Generate route file path
     */
    private String generateRouteFilePath(PhrIntelligenceArtifactImporter.PhrRoute route) {
        return "products/phr/apps/web/src/routes/" + toKebabCase(route.getId()) + ".tsx";
    }

    /**
     * Generate page file path
     */
    private String generatePageFilePath(PhrIntelligenceArtifactImporter.PhrRoute route) {
        return "products/phr/apps/web/src/pages/" + toPascalCase(route.getComponent()) + ".tsx";
    }

    /**
     * Generate route code
     */
    private String generateRouteCode(PhrIntelligenceArtifactImporter.PhrRoute route) {
        return String.format("""
import { lazy } from 'react';
import type { RouteObject } from 'react-router-dom';

const %s = lazy(() => import('../pages/%s'));

export const %sRoute: RouteObject = {
  path: '%s',
  element: <%s />,
  // TODO: Add route guards for roles: %s
  // TODO: Add feature checks for: %s
};
""", 
            route.getComponent(),
            route.getComponent(),
            toCamelCase(route.getId()),
            route.getPath(),
            route.getComponent(),
            String.join(", ", route.getRoles()),
            String.join(", ", route.getRequiredFeatures())
        );
    }

    /**
     * Generate page code
     */
    private String generatePageCode(PhrIntelligenceArtifactImporter.PhrRoute route) {
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

  // TODO: Implement %s page logic
  // TODO: Add data fetching for %s
  // TODO: Add error handling
  // TODO: Add loading states

  return (
    <div className="%s">
      <h1>{t('%s.title')}</h1>
      {/* TODO: Add page content */}
    </div>
  );
}
""",
            route.getComponent(),
            route.getPath(),
            route.getStability(),
            route.getVisibility(),
            route.getComponent(),
            route.getComponent(),
            route.getId(),
            toKebabCase(route.getId()),
            toKebabCase(route.getId())
        );
    }

    /**
     * Convert to kebab-case
     */
    private String toKebabCase(String input) {
        return input.replaceAll("([A-Z])", "-$1").toLowerCase().replaceAll("^-", "");
    }

    /**
     * Convert to PascalCase
     */
    private String toPascalCase(String input) {
        String[] parts = input.split("(?=[A-Z])");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    result.append(part.substring(1).toLowerCase());
                }
            }
        }
        return result.toString();
    }

    /**
     * Convert to camelCase
     */
    private String toCamelCase(String input) {
        String pascal = toPascalCase(input);
        if (pascal.isEmpty()) return "";
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
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
