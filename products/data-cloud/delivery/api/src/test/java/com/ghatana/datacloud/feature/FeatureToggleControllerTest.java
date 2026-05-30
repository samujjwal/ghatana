/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.feature;

import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for feature toggle request parsing.
 *
 * @doc.type class
 * @doc.purpose Feature toggle controller input validation tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FeatureToggleController")
class FeatureToggleControllerTest {

    @Test
    @DisplayName("parseFlag accepts complete feature flag requests")
    void parseFlagAcceptsCompleteFeatureFlagRequests() throws Exception {
        FeatureToggleController controller = new FeatureToggleController(new UnsupportedFeatureFlagService());

        FeatureFlagService.FeatureFlag flag = parseFlag(controller, """
            {
              "key": "data-quality-preview",
              "name": "Data quality preview",
              "description": "Enable the governed preview workflow",
              "tenantId": "tenant-001",
              "enabled": true,
              "rolloutPercentage": 25,
              "variants": ["control", "preview"],
              "defaultVariant": "control",
              "createdBy": "admin-001",
              "rules": [
                {
                  "name": "vip-users",
                  "action": "SERVE_VARIANT",
                  "variant": "preview",
                  "condition": {
                    "attribute": "tier",
                    "operator": "EQUALS",
                    "value": "vip"
                  }
                }
              ]
            }
            """);

        assertThat(flag.key()).isEqualTo("data-quality-preview");
        assertThat(flag.tenantId()).isEqualTo("tenant-001");
        assertThat(flag.rolloutPercentage()).isEqualTo(25);
        assertThat(flag.variants()).containsExactlyInAnyOrder("control", "preview");
        assertThat(flag.rules()).hasSize(1);
        assertThat(flag.rules().get(0).action()).isEqualTo(FeatureFlagService.Action.SERVE_VARIANT);
        assertThat(flag.rules().get(0).condition().attribute()).isEqualTo("tier");
    }

    @Test
    @DisplayName("parseFlag rejects requests missing tenant identity")
    void parseFlagRejectsRequestsMissingTenantIdentity() {
        FeatureToggleController controller = new FeatureToggleController(new UnsupportedFeatureFlagService());

        assertThatThrownBy(() -> parseFlag(controller, """
            {
              "key": "data-quality-preview",
              "name": "Data quality preview",
              "enabled": true,
              "createdBy": "admin-001"
            }
            """))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tenantId");
    }

    private FeatureFlagService.FeatureFlag parseFlag(FeatureToggleController controller, String json) throws Exception {
        Method method = FeatureToggleController.class.getDeclaredMethod("parseFlag", String.class);
        method.setAccessible(true);
        try {
            return (FeatureFlagService.FeatureFlag) method.invoke(controller, json);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static final class UnsupportedFeatureFlagService implements FeatureFlagService {
        @Override
        public Promise<Boolean> isEnabled(String featureKey, FeatureContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<String> getVariant(String featureKey, FeatureContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<FeatureFlag> createFlag(FeatureFlag flag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<Optional<FeatureFlag>> getFlag(String featureKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<List<FeatureFlag>> listFlags(String tenantId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<FeatureFlag> toggle(String featureKey, boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<Void> deleteFlag(String featureKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Promise<FeatureMetrics> getMetrics(String featureKey) {
            throw new UnsupportedOperationException();
        }
    }
}
