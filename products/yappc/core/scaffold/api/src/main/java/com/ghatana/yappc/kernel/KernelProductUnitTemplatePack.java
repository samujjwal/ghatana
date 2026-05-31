/*
 * Copyright (c) 2026 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.ghatana.yappc.kernel;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Renders Kernel-native product artifact templates from imported route/use-case contracts.
 *
 * @doc.type class
 * @doc.purpose Generates web, mobile, backend route, schema, and test artifacts from canonical Kernel product contracts
 * @doc.layer product
 * @doc.pattern Template Pack
 */
public final class KernelProductUnitTemplatePack {

    /**
     * Renders all template artifacts for a route/use-case import.
     *
     * @param importedProduct imported Kernel product model
     * @return generated template artifacts
     */
    public List<GeneratedTemplate> render(@NotNull KernelProductContractImporter.ImportedKernelProduct importedProduct) {
        Objects.requireNonNull(importedProduct, "importedProduct must not be null");
        List<GeneratedTemplate> templates = new ArrayList<>();

        for (KernelProductContractImporter.ProductRoute route : importedProduct.routes()) {
            if (!route.path().startsWith("/mobile/")) {
                templates.add(renderWebPage(route));
            }
            if (!isBlank(route.apiEndpoint())) {
                templates.add(renderJavaRoute(importedProduct.product(), route));
                templates.add(renderZodSchema(route));
                templates.add(renderBackendContractTest(importedProduct.product(), route));
            }
        }

        for (KernelProductContractImporter.ProductUseCase useCase : importedProduct.useCases()) {
            if (!isBlank(useCase.mobileScreen())) {
                templates.add(renderMobileScreen(useCase));
            }
            templates.add(renderJourneyTest(useCase));
        }

        return List.copyOf(templates);
    }

    private static GeneratedTemplate renderWebPage(KernelProductContractImporter.ProductRoute route) {
        String artifactId = routeArtifactId(route);
        String componentName = componentName(artifactId, "Page");
        String source = """
            import { t } from '../i18n/productI18n';

            export function %s(): React.ReactElement {
              return (
                <main data-testid="%s">
                  <h1>{t('route.%s.title')}</h1>
                </main>
              );
            }
            """.formatted(componentName, artifactId, routeKey(route));
        return new GeneratedTemplate(artifactId + "-web-page", "web-page", componentName + ".tsx", source);
    }

    private static GeneratedTemplate renderMobileScreen(KernelProductContractImporter.ProductUseCase useCase) {
        String componentName = componentName(useCase.mobileScreen(), "Screen");
        String source = """
            import { Text, View } from 'react-native';
            import { t } from '../i18n/productMobileI18n';

            export function %s(): React.ReactElement {
              return (
                <View accessibilityLabel={t('app.title')}>
                  <Text>{t('app.title')}</Text>
                </View>
              );
            }
            """.formatted(componentName);
        return new GeneratedTemplate(useCase.id() + "-mobile-screen", "mobile-screen", componentName + ".tsx", source);
    }

    private static GeneratedTemplate renderJavaRoute(String productId, KernelProductContractImporter.ProductRoute route) {
        String artifactId = routeArtifactId(route);
        String className = componentName(artifactId, "Routes");
        String source = """
            package com.ghatana.%s.api.routes;

            /**
             * @doc.type class
             * @doc.purpose Generated route adapter for %s
             * @doc.layer product
             * @doc.pattern Route Adapter
             */
            public final class %s {
                public static final String API_ENDPOINT = "%s";
                public static final String POLICY_ID = "%s";
            }
            """.formatted(packageSegment(productId), route.path(), className, route.apiEndpoint(), route.policyId());
        return new GeneratedTemplate(artifactId + "-java-route", "java-route", className + ".java", source);
    }

    private static GeneratedTemplate renderZodSchema(KernelProductContractImporter.ProductRoute route) {
        String artifactId = routeArtifactId(route);
        String schemaName = componentName(artifactId, "ResponseSchema");
        String source = """
            import { z } from 'zod';

            export const %s = z.object({
              correlationId: z.string().min(1),
              route: z.literal('%s'),
            }).strict();
            """.formatted(schemaName, route.path());
        return new GeneratedTemplate(artifactId + "-zod-schema", "zod-schema", schemaName + ".ts", source);
    }

    private static GeneratedTemplate renderBackendContractTest(String productId, KernelProductContractImporter.ProductRoute route) {
        String artifactId = routeArtifactId(route);
        String className = componentName(artifactId, "ContractTest");
        String source = """
            package com.ghatana.%s.api;

            import org.junit.jupiter.api.Test;

            import static org.assertj.core.api.Assertions.assertThat;

            final class %s {
                @Test
                void routeContractIncludesPolicyMetadata() {
                    assertThat("%s").startsWith("/api/");
                    assertThat("%s").isNotBlank();
                }
            }
            """.formatted(packageSegment(productId), className, route.apiEndpoint(), route.policyId());
        return new GeneratedTemplate(artifactId + "-backend-contract-test", "backend-contract-test", className + ".java", source);
    }

    private static GeneratedTemplate renderJourneyTest(KernelProductContractImporter.ProductUseCase useCase) {
        String source = """
            import { describe, expect, it } from 'vitest';

            describe('%s journey contract', () => {
              it('preserves route and capability binding', () => {
                expect('%s').toMatch(/^\\/|^$/);
                expect('%s').toContain('.');
              });
            });
            """.formatted(useCase.id(), useCase.webRoute(), useCase.kernelCapability());
        return new GeneratedTemplate(useCase.id() + "-journey-test", "journey-test", useCase.id() + ".test.ts", source);
    }

    private static String routeKey(KernelProductContractImporter.ProductRoute route) {
        return route.path().replaceFirst("^/", "").replace("/", ".");
    }

    private static String routeArtifactId(KernelProductContractImporter.ProductRoute route) {
        if (!isBlank(route.testId())) {
            return route.testId();
        }
        return route.path().replaceFirst("^/", "").replaceAll("[^A-Za-z0-9]+", "-");
    }

    private static String componentName(String rawName, String suffix) {
        StringBuilder builder = new StringBuilder();
        for (String part : rawName.split("[^A-Za-z0-9]+")) {
            if (part.isBlank()) {
                continue;
            }
            builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        builder.append(suffix);
        return builder.toString();
    }

    private static String packageSegment(String productId) {
        String normalized = productId == null ? "" : productId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("productId must include at least one package-safe character");
        }
        return normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Rendered Kernel product template artifact.
     *
     * @doc.type record
     * @doc.purpose Carries generated template source and target identity
     * @doc.layer product
     * @doc.pattern DTO
     */
    public record GeneratedTemplate(String id, String kind, String relativePath, String source) {
    }
}
