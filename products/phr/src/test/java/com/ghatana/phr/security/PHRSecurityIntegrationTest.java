package com.ghatana.phr.security;

import com.ghatana.kernel.security.KernelSecurityManager;
import com.ghatana.kernel.security.PolicyEnforcementPoint;
import com.ghatana.kernel.security.SecurityContext;
import com.ghatana.phr.model.PHRUser;
import com.ghatana.phr.model.PatientConsent;
import com.ghatana.phr.repository.ConsentRepository;
import com.ghatana.phr.repository.TenantConfigRepository;
import com.ghatana.phr.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for PHR Security Framework.
 * Tests policy enforcement and HIPAA compliance.
 */
class PHRSecurityIntegrationTest {
    private PolicyEnforcementPoint policyEnforcementPoint;
    private KernelSecurityManager securityManager;
    private UserRepository userRepository;
    private ConsentRepository consentRepository;

    @BeforeEach
    void setUp() {
        userRepository = new UserRepository();
        consentRepository = new ConsentRepository();
        TenantConfigRepository tenantConfigRepository = new TenantConfigRepository();
        
        PHRSecurityConfig config = new PHRSecurityConfig(
            userRepository, consentRepository, tenantConfigRepository
        );
        
        securityManager = config.kernelSecurityManager();
        policyEnforcementPoint = config.policyEnforcementPoint();
        
        setupTestData();
    }

    private void setupTestData() {
        PHRUser provider = new PHRUser("provider-1", "dr.smith", "dr.smith@hospital.com");
        provider.addRole("HEALTHCARE_PROVIDER");
        provider.addPermission("read:patient-records");
        provider.addPermission("export:phi");
        provider.setProviderId("PROV-001");
        provider.setAccessLevel("FULL");
        userRepository.save(provider);

        PHRUser patient = new PHRUser("patient-1", "john.doe", "john.doe@email.com");
        patient.addRole("PATIENT");
        patient.addPermission("read:patient-records");
        patient.setAccessLevel("SELF");
        userRepository.save(patient);

        PatientConsent consent = new PatientConsent();
        consent.setPatientId("patient-1");
        consent.setTenantId("tenant-1");
        consent.setPurpose("treatment");
        consent.setGranted(true);
        consent.setTimestamp(Instant.now());
        consent.setExpiresAt(Instant.now().plus(365, ChronoUnit.DAYS));
        consentRepository.save(consent);
    }

    @Test
    void testPatientRecordAccess_WithConsent_ShouldAllow() {
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "provider-1");
        
        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder()
            .resource("patient-records")
            .operation("read")
            .scope("phr")
            .dataType("patient-health-records")
            .purpose("treatment")
            .requiresConsent(true)
            .metadata(Map.of("patient_id", "patient-1"))
            .build();
        
        PolicyEnforcementPoint.EnforcementDecision decision = 
            policyEnforcementPoint.enforce(request, context);
        
        assertTrue(decision.isAllowed(), "Access should be allowed with valid consent");
    }

    @Test
    void testPatientRecordAccess_WithoutConsent_ShouldDeny() {
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "provider-1");
        
        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder()
            .resource("patient-records")
            .operation("read")
            .scope("phr")
            .dataType("patient-health-records")
            .purpose("research")
            .requiresConsent(true)
            .metadata(Map.of("patient_id", "patient-2"))
            .build();
        
        PolicyEnforcementPoint.EnforcementDecision decision = 
            policyEnforcementPoint.enforce(request, context);
        
        assertFalse(decision.isAllowed(), "Access should be denied without consent");
        assertTrue(decision.getReason().contains("Consent not granted"), 
            "Reason should mention consent");
    }

    @Test
    void testPatientRecordAccess_Unauthenticated_ShouldDeny() {
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "provider-1");
        SecurityContext unauthContext = new UnauthenticatedContext();
        
        PolicyEnforcementPoint.Request request = PolicyEnforcementPoint.Request.builder()
            .resource("patient-records")
            .operation("read")
            .scope("phr")
            .dataType("patient-health-records")
            .purpose("treatment")
            .requiresConsent(false)
            .build();
        
        PolicyEnforcementPoint.EnforcementDecision decision = 
            policyEnforcementPoint.enforce(request, unauthContext);
        
        assertFalse(decision.isAllowed(), "Access should be denied for unauthenticated user");
        assertEquals("Not authenticated", decision.getReason());
    }

    @Test
    void testPatientRecordExport_WithPermission_ShouldAllow() {
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "provider-1");
        
        KernelSecurityManager.Action action = new KernelSecurityManager.Action(
            "patient-records", "export", "phr"
        );
        
        boolean authorized = securityManager.authorizeAction(action, context);
        
        assertTrue(authorized, "Provider should be authorized to export PHI");
    }

    @Test
    void testPatientRecordExport_WithoutPermission_ShouldDeny() {
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "patient-1");
        
        KernelSecurityManager.Action action = new KernelSecurityManager.Action(
            "patient-records", "export", "phr"
        );
        
        boolean authorized = securityManager.authorizeAction(action, context);
        
        assertFalse(authorized, "Patient should not be authorized to export PHI");
    }

    @Test
    void testSecurityContextCreation() {
        SecurityContext context = securityManager.createSecurityContext("tenant-1", "provider-1");
        
        assertNotNull(context);
        assertEquals("tenant-1", context.getTenantId());
        assertEquals("provider-1", context.getUserId());
        assertTrue(context.isAuthenticated());
        assertTrue(context.hasRole("HEALTHCARE_PROVIDER"));
        assertTrue(context.hasPermission("read:patient-records"));
        assertEquals("PROV-001", context.getAttribute("healthcare_provider_id"));
    }

    @Test
    void testCredentialValidation_ValidCredentials() {
        KernelSecurityManager.Credentials credentials = 
            new KernelSecurityManager.Credentials("dr.smith", "password123", null);
        
        KernelSecurityManager.ValidationResult result = 
            securityManager.validateCredentials(credentials);
        
        assertTrue(result.isValid());
    }

    @Test
    void testCredentialValidation_InvalidUser() {
        KernelSecurityManager.Credentials credentials = 
            new KernelSecurityManager.Credentials("unknown", "password123", null);
        
        KernelSecurityManager.ValidationResult result = 
            securityManager.validateCredentials(credentials);
        
        assertFalse(result.isValid());
        assertEquals("Invalid credentials", result.getReason());
    }

    private static class UnauthenticatedContext implements SecurityContext {
        @Override
        public String getTenantId() { return "tenant-1"; }
        @Override
        public String getUserId() { return "anonymous"; }
        @Override
        public java.util.Set<String> getRoles() { return java.util.Collections.emptySet(); }
        @Override
        public Map<String, Object> getAttributes() { return java.util.Collections.emptyMap(); }
        @Override
        public boolean hasRole(String role) { return false; }
        @Override
        public boolean hasPermission(String permission) { return false; }
        @Override
        public Object getAttribute(String key) { return null; }
        @Override
        public String getSessionId() { return "none"; }
        @Override
        public boolean isAuthenticated() { return false; }
        @Override
        public long getAuthenticationTime() { return 0; }
    }
}
