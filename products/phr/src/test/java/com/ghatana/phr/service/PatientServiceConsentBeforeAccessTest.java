package com.ghatana.phr.service;

import com.ghatana.kernel.observability.AuditTrailService;
import com.ghatana.kernel.observability.KernelTelemetryManager;
import com.ghatana.phr.kernel.consent.ConsentAccessDeniedException;
import com.ghatana.phr.kernel.consent.ConsentService;
import com.ghatana.phr.kernel.policy.PhrDataClassification;
import com.ghatana.phr.model.PatientRecords;
import com.ghatana.phr.observability.PHRAuditTrailServiceImpl;
import com.ghatana.phr.observability.PHRTelemetryManagerImpl;
import com.ghatana.phr.repository.PatientRecordRepository;
import com.ghatana.phr.security.SecurityContextHolder;
import com.ghatana.kernel.security.TenantSecurityContext;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Consent-before-access property tests for {@link PatientService}.
 *
 * <p>These tests verify the mandatory safety property:</p>
 * <blockquote>
 *   Patient record data MUST NOT be returned unless the consent service has
 *   checked and allowed the access for the requesting actor.
 * </blockquote>
 *
 * <p>Covers:</p>
 * <ul>
 *   <li>PHR-CBA-001: {@code getRecords} calls consent service before accessing repository</li>
 *   <li>PHR-CBA-002: consent denial blocks repository access and throws {@link ConsentAccessDeniedException}</li>
 *   <li>PHR-CBA-003: repository is never accessed when consent is denied</li>
 *   <li>PHR-CBA-004: actor context in the consent request matches the security context</li>
 *   <li>PHR-CBA-005: target resource classification is C3 for patient record reads</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose PHR-CBA consent-before-access property tests for PatientService
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PatientService — consent-before-access property tests")
@ExtendWith(MockitoExtension.class)
class PatientServiceConsentBeforeAccessTest {

    @Mock
    private ConsentService consentService;

    @Mock
    private PatientRecordRepository repository;

    private PatientService service;

    private static final String TENANT_ID = "tenant-hospital-1";
    private static final String ACTOR_ID = "provider-dr-sharma";
    private static final String PATIENT_ID = "patient-abc123";

    @BeforeEach
    void setUp() {
        KernelTelemetryManager telemetry = new PHRTelemetryManagerImpl();
        AuditTrailService auditTrail = new PHRAuditTrailServiceImpl();
        service = new PatientService(telemetry, auditTrail, repository, consentService);

        TenantSecurityContext context = TenantSecurityContext.builder()
                .tenantId(TENANT_ID)
                .userId(ACTOR_ID)
                .sessionId("session-1")
                .role("HEALTHCARE_PROVIDER")
                .permission("read:patient-records")
                .build();
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── PHR-CBA-001: consent service called before repository ──────────────

    @Test
    @DisplayName("PHR-CBA-001: getRecords invokes consent service before fetching from repository")
    void getRecordsCallsConsentServiceFirst() {
        ConsentService.ConsentAccessDecision allow = ConsentService.ConsentAccessDecision.allow(
                ConsentService.ReasonCode.EXPLICIT_GRANT, "grant-123",
                ConsentService.CacheStatus.MISS, Instant.now().plusSeconds(300));
        when(consentService.checkAccess(any())).thenReturn(Promise.of(allow));
        when(repository.findByPatientId(PATIENT_ID)).thenReturn(new PatientRecords(List.of()));

        service.getRecords(PATIENT_ID);

        // Verify consent was called exactly once
        verify(consentService, times(1)).checkAccess(any(ConsentService.ConsentCheckRequest.class));
        // And repository was subsequently called
        verify(repository, times(1)).findByPatientId(PATIENT_ID);
    }

    // ── PHR-CBA-002 / PHR-CBA-003: denial blocks access ───────────────────

    @Test
    @DisplayName("PHR-CBA-002/003: consent denial throws ConsentAccessDeniedException without touching repository")
    void getRecordsThrowsWhenConsentDenied() {
        ConsentService.ConsentAccessDecision deny = ConsentService.ConsentAccessDecision.deny(
                ConsentService.ReasonCode.GRANT_REVOKED, ConsentService.CacheStatus.MISS);
        when(consentService.checkAccess(any())).thenReturn(Promise.of(deny));

        assertThatThrownBy(() -> service.getRecords(PATIENT_ID))
                .isInstanceOf(ConsentAccessDeniedException.class)
                .hasMessageContaining("GRANT_REVOKED");

        // Repository must never be accessed when consent is denied
        verify(repository, never()).findByPatientId(anyString());
    }

    // ── PHR-CBA-004: actor context matches security context ────────────────

    @Test
    @DisplayName("PHR-CBA-004: consent request actor ID matches the current security context actor")
    void consentRequestActorMatchesSecurityContext() {
        ConsentService.ConsentAccessDecision allow = ConsentService.ConsentAccessDecision.allow(
                ConsentService.ReasonCode.ROLE_ALLOWED, null,
                ConsentService.CacheStatus.MISS, null);
        when(consentService.checkAccess(any())).thenReturn(Promise.of(allow));
        when(repository.findByPatientId(any())).thenReturn(new PatientRecords(List.of()));

        service.getRecords(PATIENT_ID);

        ArgumentCaptor<ConsentService.ConsentCheckRequest> captor =
                ArgumentCaptor.forClass(ConsentService.ConsentCheckRequest.class);
        verify(consentService).checkAccess(captor.capture());

        ConsentService.ConsentCheckRequest req = captor.getValue();
        assertThat(req.actor().actorId())
                .as("consent request actor must match the security context actor")
                .isEqualTo(ACTOR_ID);
        assertThat(req.tenantId())
                .as("consent request tenant must match the security context tenant")
                .isEqualTo(TENANT_ID);
    }

    // ── PHR-CBA-005: target resource classification is C3 ─────────────────

    @Test
    @DisplayName("PHR-CBA-005: consent request classifies patient records as C3 (restricted PHI)")
    void consentRequestClassifiesPatientRecordAsC3() {
        ConsentService.ConsentAccessDecision allow = ConsentService.ConsentAccessDecision.allow(
                ConsentService.ReasonCode.EXPLICIT_GRANT, "grant-1",
                ConsentService.CacheStatus.MISS, null);
        when(consentService.checkAccess(any())).thenReturn(Promise.of(allow));
        when(repository.findByPatientId(any())).thenReturn(new PatientRecords(List.of()));

        service.getRecords(PATIENT_ID);

        ArgumentCaptor<ConsentService.ConsentCheckRequest> captor =
                ArgumentCaptor.forClass(ConsentService.ConsentCheckRequest.class);
        verify(consentService).checkAccess(captor.capture());

        assertThat(captor.getValue().target().classification())
                .as("patient record reads must be classified as C3 restricted PHI")
                .isEqualTo(PhrDataClassification.C3);
        assertThat(captor.getValue().action())
                .isEqualTo(ConsentService.ConsentAction.PATIENT_READ);
    }
}
