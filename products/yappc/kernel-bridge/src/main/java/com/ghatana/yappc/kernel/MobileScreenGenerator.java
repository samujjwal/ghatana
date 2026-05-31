package com.ghatana.yappc.kernel;

import io.activej.promise.Promise;

/**
 * G10-005: Product Mobile Screen/API Skeleton Generator
 *
 * Generates product mobile screen and API skeletons from Kernel route contract.
 *
 * @doc.type class
 * @doc.purpose Generate product mobile screen/API skeletons from Kernel route contract
 * @doc.layer integration
 * @doc.pattern Generator
 */
public class MobileScreenGenerator {

    /**
     * Generate mobile screen skeleton for a product route
     */
    public Promise<MobileScreenSkeleton> generateScreenSkeleton(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return Promise.ofBlocking(java.util.concurrent.ForkJoinPool.commonPool(), () -> {
            MobileScreenSkeleton skeleton = new MobileScreenSkeleton();
            
            skeleton.setRouteId(route.getId());
            skeleton.setPath(route.getPath());
            skeleton.setComponentName(route.getComponent());
            skeleton.setStability(route.getStability());
            skeleton.setVisibility(route.getVisibility());
            
            String screenFilePath = generateScreenFilePath(route);
            skeleton.setScreenFilePath(screenFilePath);
            
            String apiFilePath = generateApiFilePath(route);
            skeleton.setApiFilePath(apiFilePath);
            
            String screenCode = generateScreenCode(route);
            skeleton.setScreenCode(screenCode);
            
            String apiCode = generateApiCode(route);
            skeleton.setApiCode(apiCode);
            
            return skeleton;
        });
    }

    /**
     * Generate screen file path
     */
    private String generateScreenFilePath(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return "products/" + productPathSegment(route) + "/apps/mobile/src/screens/" + route.getComponent() + ".tsx";
    }

    /**
     * Generate API file path
     */
    private String generateApiFilePath(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return "products/" + productPathSegment(route) + "/apps/mobile/src/services/" + ProductGenerationNaming.pascalCase(route.getId()) + "Api.ts";
    }

    /**
     * Generate screen code
     */
    private String generateScreenCode(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return String.format("""
import React from 'react';
import { View, Text, ScrollView, StyleSheet } from 'react-native';
import { useTranslation } from '../i18n/mobileI18n';
import { useMobileSession } from '../services/mobileSessionStore';

/**
 * %s Screen
 *
 * Route: %s
 * Stability: %s
 * Visibility: %s
 */
export default function %s() {
  const { t } = useTranslation();
  const session = useMobileSession();

  return (
    <ScrollView style={styles.container} accessibilityLabel="%s screen">
      <View style={styles.header}>
        <Text style={styles.title}>{t('%s.title')}</Text>
        <Text testID="%s-session" style={styles.metadata}>{session ? 'session-ready' : 'session-missing'}</Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
  },
  header: {
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  metadata: {
    fontSize: 12,
    color: '#4b5563',
  },
});
""",
            route.getComponent(),
            route.getPath(),
            route.getStability(),
            route.getVisibility(),
            route.getComponent(),
            route.getComponent(),
            ProductGenerationNaming.kebabCase(route.getId()),
            ProductGenerationNaming.kebabCase(route.getId())
        );
    }

    /**
     * Generate API code
     */
    private String generateApiCode(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return String.format("""
import { buildMobileHeaders } from './mobileHeaders';
import type { MobileSession } from '../types';

/**
 * API functions for %s
 *
 * Route: %s
 */

interface %sResponse {
  data: unknown;
}

/**
 * Fetch %s data
 */
export async function fetch%s(session: MobileSession): Promise<%sResponse> {
  const headers = buildMobileHeaders(session);
  
  const response = await fetch('/api/v1%s', {
    method: 'GET',
    headers,
  });
  
  if (!response.ok) {
    throw new Error('Failed to fetch %s');
  }
  
  return response.json();
}
""",
            route.getId(),
            route.getPath(),
            ProductGenerationNaming.pascalCase(route.getId()),
            route.getId(),
            ProductGenerationNaming.pascalCase(route.getId()),
            ProductGenerationNaming.pascalCase(route.getId()),
            route.getPath(),
            route.getId()
        );
    }

    private String productPathSegment(ProductIntelligenceArtifactImporter.ProductRoute route) {
        return ProductGenerationNaming.productPathSegment(route.getProductId());
    }

    /**
     * Mobile Screen Skeleton model
     */
    public static class MobileScreenSkeleton {
        private String routeId;
        private String path;
        private String componentName;
        private String stability;
        private String visibility;
        private String screenFilePath;
        private String apiFilePath;
        private String screenCode;
        private String apiCode;

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

        public String getScreenFilePath() { return screenFilePath; }
        public void setScreenFilePath(String screenFilePath) { this.screenFilePath = screenFilePath; }

        public String getApiFilePath() { return apiFilePath; }
        public void setApiFilePath(String apiFilePath) { this.apiFilePath = apiFilePath; }

        public String getScreenCode() { return screenCode; }
        public void setScreenCode(String screenCode) { this.screenCode = screenCode; }

        public String getApiCode() { return apiCode; }
        public void setApiCode(String apiCode) { this.apiCode = apiCode; }
    }
}
