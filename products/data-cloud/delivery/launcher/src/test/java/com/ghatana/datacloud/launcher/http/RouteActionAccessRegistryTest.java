/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies contract-backed route/action access requirements for Data Cloud HTTP security
 * @doc.layer product
 * @doc.pattern Test
 */
class RouteActionAccessRegistryTest {

    @Test
    void shouldClassifyLearningReviewApproveAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/learning/review/123/approve"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifyLearningReviewRejectAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/learning/review/8f3c2d52-0d6f-4e1f-a5ab-1d2f88c1c1ad/reject"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldReturnNullForUnmappedRouteAction() {
        assertThat(RouteActionAccessRegistry.requiredAccess("GET", "/api/v1/learning/review")).isNull();
    }

    @Test
    void shouldClassifyConnectorCredentialRotationAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/connectors/conn-1/rotate-credentials"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifyConnectorSyncAsOperator() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/connectors/conn-1/sync"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.OPERATOR);
    }

    @Test
    void shouldClassifySettingsUpdateAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/settings/security"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifyGovernancePolicyToggleAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/governance/policies/policy-42/toggle"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifyActionNamespaceLearningReviewApproveAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/action/learning/review/123/approve"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifySettingsKeyRotateAsAdmin() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/settings/keys/key-123/rotate"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.ADMIN);
    }

    @Test
    void shouldClassifyAlertSuggestionApplyAsOperator() {
        assertThat(RouteActionAccessRegistry.requiredAccess("POST", "/api/v1/alerts/suggestions/sugg-1/apply"))
            .isEqualTo(DataCloudSecurityFilter.AccessLevel.OPERATOR);
    }
}