package com.ghatana.kernel.interaction;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ProductInteractionPolicyEnforcer.
 *
 * @doc.type class
 * @doc.purpose Test policy enforcement functionality
 * @doc.layer kernel
 */
@DisplayName("ProductInteractionPolicyEnforcer Tests")
class ProductInteractionPolicyEnforcerTest {

    @Test
    @DisplayName("Allows interaction when all policies pass")
    void allowsInteractionWhenAllPoliciesPass() {
        ProductInteractionPolicyEnforcer.AuthenticationProvider authProvider = mock();
        ProductInteractionPolicyEnforcer.TenantIsolationProvider tenantProvider = mock();
        ProductInteractionPolicyEnforcer.PurposeOfUseProvider purposeProvider = mock();
        ProductInteractionPolicyEnforcer.ConsentVerificationProvider consentProvider = mock();

        when(authProvider.authenticate(any())).thenReturn(
            ProductInteractionPolicyEnforcer.AuthenticationResult.authenticated("actor-1"));
        when(tenantProvider.validateTenantAccess(any())).thenReturn(
            ProductInteractionPolicyEnforcer.TenantIsolationResult.allow());
        when(purposeProvider.validatePurpose(any())).thenReturn(
            ProductInteractionPolicyEnforcer.PurposeOfUseResult.allow());
        when(consentProvider.verifyConsent(any())).thenReturn(
            ProductInteractionPolicyEnforcer.ConsentVerificationResult.allow());

        ProductInteractionPolicyEnforcer enforcer = new ProductInteractionPolicyEnforcer(
            authProvider, tenantProvider, purposeProvider, consentProvider);

        ProductInteractionRequest<String> request = createRequest();

        ProductInteractionPolicyEnforcer.PolicyEnforcementResult result = enforcer.enforce(request);

        assertTrue(result.allowed());
        assertNull(result.violationType());
        assertNull(result.reasonCode());
        assertNull(result.message());
    }

    @Test
    @DisplayName("Denies interaction when authentication fails")
    void deniesInteractionWhenAuthenticationFails() {
        ProductInteractionPolicyEnforcer.AuthenticationProvider authProvider = mock();
        ProductInteractionPolicyEnforcer.TenantIsolationProvider tenantProvider = mock();
        ProductInteractionPolicyEnforcer.PurposeOfUseProvider purposeProvider = mock();
        ProductInteractionPolicyEnforcer.ConsentVerificationProvider consentProvider = mock();

        when(authProvider.authenticate(any())).thenReturn(
            ProductInteractionPolicyEnforcer.AuthenticationResult.failed("invalid_token"));

        ProductInteractionPolicyEnforcer enforcer = new ProductInteractionPolicyEnforcer(
            authProvider, tenantProvider, purposeProvider, consentProvider);

        ProductInteractionRequest<String> request = createRequest();

        ProductInteractionPolicyEnforcer.PolicyEnforcementResult result = enforcer.enforce(request);

        assertFalse(result.allowed());
        assertEquals("authentication_failed", result.violationType());
        assertEquals("invalid_token", result.reasonCode());
        assertNotNull(result.message());
    }

    @Test
    @DisplayName("Denies interaction when tenant isolation fails")
    void deniesInteractionWhenTenantIsolationFails() {
        ProductInteractionPolicyEnforcer.AuthenticationProvider authProvider = mock();
        ProductInteractionPolicyEnforcer.TenantIsolationProvider tenantProvider = mock();
        ProductInteractionPolicyEnforcer.PurposeOfUseProvider purposeProvider = mock();
        ProductInteractionPolicyEnforcer.ConsentVerificationProvider consentProvider = mock();

        when(authProvider.authenticate(any())).thenReturn(
            ProductInteractionPolicyEnforcer.AuthenticationResult.authenticated("actor-1"));
        when(tenantProvider.validateTenantAccess(any())).thenReturn(
            ProductInteractionPolicyEnforcer.TenantIsolationResult.denied("tenant_mismatch"));

        ProductInteractionPolicyEnforcer enforcer = new ProductInteractionPolicyEnforcer(
            authProvider, tenantProvider, purposeProvider, consentProvider);

        ProductInteractionRequest<String> request = createRequest();

        ProductInteractionPolicyEnforcer.PolicyEnforcementResult result = enforcer.enforce(request);

        assertFalse(result.allowed());
        assertEquals("tenant_isolation_violation", result.violationType());
        assertEquals("tenant_mismatch", result.reasonCode());
    }

    @Test
    @DisplayName("Denies interaction when purpose of use fails")
    void deniesInteractionWhenPurposeOfUseFails() {
        ProductInteractionPolicyEnforcer.AuthenticationProvider authProvider = mock();
        ProductInteractionPolicyEnforcer.TenantIsolationProvider tenantProvider = mock();
        ProductInteractionPolicyEnforcer.PurposeOfUseProvider purposeProvider = mock();
        ProductInteractionPolicyEnforcer.ConsentVerificationProvider consentProvider = mock();

        when(authProvider.authenticate(any())).thenReturn(
            ProductInteractionPolicyEnforcer.AuthenticationResult.authenticated("actor-1"));
        when(tenantProvider.validateTenantAccess(any())).thenReturn(
            ProductInteractionPolicyEnforcer.TenantIsolationResult.allow());
        when(purposeProvider.validatePurpose(any())).thenReturn(
            ProductInteractionPolicyEnforcer.PurposeOfUseResult.denied("invalid_purpose"));

        ProductInteractionPolicyEnforcer enforcer = new ProductInteractionPolicyEnforcer(
            authProvider, tenantProvider, purposeProvider, consentProvider);

        ProductInteractionRequest<String> request = createRequest();

        ProductInteractionPolicyEnforcer.PolicyEnforcementResult result = enforcer.enforce(request);

        assertFalse(result.allowed());
        assertEquals("purpose_of_use_violation", result.violationType());
        assertEquals("invalid_purpose", result.reasonCode());
    }

    @Test
    @DisplayName("Denies interaction when consent verification fails")
    void deniesInteractionWhenConsentVerificationFails() {
        ProductInteractionPolicyEnforcer.AuthenticationProvider authProvider = mock();
        ProductInteractionPolicyEnforcer.TenantIsolationProvider tenantProvider = mock();
        ProductInteractionPolicyEnforcer.PurposeOfUseProvider purposeProvider = mock();
        ProductInteractionPolicyEnforcer.ConsentVerificationProvider consentProvider = mock();

        when(authProvider.authenticate(any())).thenReturn(
            ProductInteractionPolicyEnforcer.AuthenticationResult.authenticated("actor-1"));
        when(tenantProvider.validateTenantAccess(any())).thenReturn(
            ProductInteractionPolicyEnforcer.TenantIsolationResult.allow());
        when(purposeProvider.validatePurpose(any())).thenReturn(
            ProductInteractionPolicyEnforcer.PurposeOfUseResult.allow());
        when(consentProvider.verifyConsent(any())).thenReturn(
            ProductInteractionPolicyEnforcer.ConsentVerificationResult.denied("consent_revoked"));

        ProductInteractionPolicyEnforcer enforcer = new ProductInteractionPolicyEnforcer(
            authProvider, tenantProvider, purposeProvider, consentProvider);

        ProductInteractionRequest<String> request = createRequest();

        ProductInteractionPolicyEnforcer.PolicyEnforcementResult result = enforcer.enforce(request);

        assertFalse(result.allowed());
        assertEquals("consent_verification_failed", result.violationType());
        assertEquals("consent_revoked", result.reasonCode());
    }

    @Test
    @DisplayName("Null request throws exception")
    void nullRequestThrowsException() {
        ProductInteractionPolicyEnforcer.AuthenticationProvider authProvider = mock();
        ProductInteractionPolicyEnforcer.TenantIsolationProvider tenantProvider = mock();
        ProductInteractionPolicyEnforcer.PurposeOfUseProvider purposeProvider = mock();
        ProductInteractionPolicyEnforcer.ConsentVerificationProvider consentProvider = mock();

        ProductInteractionPolicyEnforcer enforcer = new ProductInteractionPolicyEnforcer(
            authProvider, tenantProvider, purposeProvider, consentProvider);

        assertThrows(NullPointerException.class, () -> {
            enforcer.enforce(null);
        });
    }

    @Test
    @DisplayName("Null provider throws exception on construction")
    void nullProviderThrowsExceptionOnConstruction() {
        assertThrows(NullPointerException.class, () -> {
            new ProductInteractionPolicyEnforcer(null, mock(), mock(), mock());
        });
        assertThrows(NullPointerException.class, () -> {
            new ProductInteractionPolicyEnforcer(mock(), null, mock(), mock());
        });
        assertThrows(NullPointerException.class, () -> {
            new ProductInteractionPolicyEnforcer(mock(), mock(), null, mock());
        });
        assertThrows(NullPointerException.class, () -> {
            new ProductInteractionPolicyEnforcer(mock(), mock(), mock(), null);
        });
    }

    private ProductInteractionRequest<String> createRequest() {
        return new ProductInteractionRequest<>(
            "1.0.0",
            "interaction-1",
            "contract-1",
            "1.0.0",
            "provider-1",
            "consumer-1",
            "product-unit-1",
            "tenant-1",
            "workspace-1",
            "run-1",
            "correlation-1",
            Instant.now(),
            Map.of(),
            "payload"
        );
    }
}
