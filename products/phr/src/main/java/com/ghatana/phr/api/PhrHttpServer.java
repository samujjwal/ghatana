/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.api;

import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.api.routes.PhrAdministrativeRoutes;
import com.ghatana.phr.api.routes.PhrAuditRoutes;
import com.ghatana.phr.api.routes.PhrAuthRoutes;
import com.ghatana.phr.api.routes.PhrCaregiverRoutes;
import com.ghatana.phr.api.routes.PhrClinicalRoutes;
import com.ghatana.phr.api.routes.PhrConditionRoutes;
import com.ghatana.phr.api.routes.PhrConsentRoutes;
import com.ghatana.phr.api.routes.PhrDashboardRoutes;
import com.ghatana.phr.api.routes.PhrDocumentImagingRoutes;
import com.ghatana.phr.api.routes.PhrEntitlementRoutes;
import com.ghatana.phr.api.routes.PhrEmergencyRoutes;
import com.ghatana.phr.api.routes.PhrFchvRoutes;
import com.ghatana.phr.api.routes.PhrMobileRoutes;
import com.ghatana.phr.api.routes.PhrFhirRoutes;
import com.ghatana.phr.api.routes.PhrHealthRoutes;
import com.ghatana.phr.api.routes.PhrNotificationRoutes;
import com.ghatana.phr.api.routes.PhrPatientProfileRoutes;
import com.ghatana.phr.api.routes.PhrPatientRecordRoutes;
import com.ghatana.phr.api.routes.PhrProviderRoutes;
import com.ghatana.phr.api.routes.PhrReleaseReadinessRoutes;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncServlet;
import io.activej.http.RoutingServlet;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * ActiveJ HTTP server exposing the PHR product's FHIR and operational API.
 *
 * <h2>Canonical API Base Path</h2>
 * All PHR API endpoints use `/api/v1` as the canonical base path per the route contract.
 * Legacy non-versioned paths have been removed in favor of consistent `/api/v1/*` mounting.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   FHIR R4 Server:
 *   POST   /fhir/:resourceType                          — Create a FHIR R4 resource
 *   GET    /fhir/:resourceType/:id                      — Read a FHIR R4 resource by ID
 *   GET    /fhir/:resourceType                          — Search FHIR R4 resources
 *
 *   Patient Core Routes:
 *   GET    /api/v1/dashboard                            — Patient dashboard summary
 *   GET    /api/v1/records                               — Patient health records list
 *   GET    /api/v1/records/:recordId                     — Detailed record view
 *   GET    /api/v1/records/timeline                      — Health timeline
 *   GET    /api/v1/records/documents                    — Document list
 *   POST   /api/v1/records/documents                    — Upload a patient document
 *   GET    /api/v1/records/documents/:docId/ocr          — OCR document review
 *   GET    /api/v1/consents                              — Consent management
 *   POST   /api/v1/consents/grants                       — Grant consent
 *   POST   /api/v1/consents/grants/:grantId/revoke       — Revoke consent
 *   GET    /api/v1/consents/check                        — Check consent
 *   GET    /api/v1/appointments                          — Appointment scheduling
 *   POST   /api/v1/appointments                          — Patient self-scheduling request
 *   GET    /api/v1/profile                               — Patient profile
 *   GET    /api/v1/profile/settings                      — Profile and settings
 *   GET    /api/v1/notifications                         — Patient notifications
 *
 *   Clinical Routes:
 *   GET    /api/v1/clinical/labs                         — Lab results
 *   GET    /api/v1/clinical/medications                  — Medication management
 *   GET    /api/v1/clinical/medications/prescriptions/:medicationId — Medication detail
 *   GET    /api/v1/clinical/conditions                  — Health conditions
 *   GET    /api/v1/clinical/observations                 — Clinical observations
 *   GET    /api/v1/clinical/immunizations                 — Immunization records
 *
 *   Governance & Emergency:
 *   POST   /api/v1/emergency/access                      — Request break-glass emergency access
 *   GET    /api/v1/emergency/reviews                     — Emergency access review
 *   GET    /api/v1/audit/events                          — Paginated audit event trail
 *   GET    /api/v1/release-readiness                      — Admin release readiness runtime truth
 *   POST   /api/v1/auth/login                           — Session bootstrap via credentials
 *   POST   /api/v1/auth/logout                          — Session termination
 *
 *   Role-Specific Routes (hidden until implementation complete):
 *   GET    /api/v1/provider/dashboard                    — Provider dashboard
 *   GET    /api/v1/provider/patients                     — Provider patient list
 *   GET    /api/v1/caregiver/dependents                   — Caregiver dependent management
 *   GET    /api/v1/fchv/dashboard                        — FCHV community health dashboard
 *   GET    /api/v1/mobile/dashboard                      — Mobile patient dashboard (session-header auth)
 *
 *   System Routes:
 *   GET    /route-entitlements                          — Route/content entitlement payload
 *   GET    /health                                      — Liveness probe
 *   GET    /ready                                       — Readiness probe
 * </pre>
 *
 * <p>Register the servlet returned by {@link #getServlet()} into the product-level router.
 * This class does not bind a port; binding is handled by the product entry-point.
 *
 * @doc.type class
 * @doc.purpose PHR HTTP server exposing FHIR R4 and operational health endpoints
 * @doc.layer product
 * @doc.pattern Controller, Adapter
 * @author Ghatana PHR Team
 * @since 1.1.0
 */
public final class PhrHttpServer implements KernelLifecycleAware {

    private static final Logger LOG = LoggerFactory.getLogger(PhrHttpServer.class);

    private final Eventloop eventloop;
    private final PhrFhirRoutes fhirRoutes;
    private final PhrDashboardRoutes dashboardRoutes;
    private final PhrPatientRecordRoutes patientRecordRoutes;
    private final PhrConsentRoutes consentRoutes;
    private final PhrClinicalRoutes clinicalRoutes;
    private final PhrConditionRoutes conditionRoutes;
    private final PhrEmergencyRoutes emergencyRoutes;
    private final PhrAdministrativeRoutes administrativeRoutes;
    private final PhrDocumentImagingRoutes documentImagingRoutes;
    private final PhrReleaseReadinessRoutes releaseReadinessRoutes;
    private final PhrEntitlementRoutes entitlementRoutes;
    private final PhrHealthRoutes healthRoutes;
    private final PhrAuditRoutes auditRoutes;
    private final PhrAuthRoutes authRoutes;
    private final PhrProviderRoutes providerRoutes;
    private final PhrCaregiverRoutes caregiverRoutes;
    private final PhrFchvRoutes fchvRoutes;
    private final PhrMobileRoutes mobileRoutes;
    private final PhrNotificationRoutes notificationRoutes;
    private final PhrPatientProfileRoutes patientProfileRoutes;
    private volatile boolean started = false;

    /**
     * Creates a production PHR HTTP server with every stable route family.
     *
     * <p>Production construction is intentionally fail-fast: missing route adapters
     * must be fixed in the composition root instead of silently omitting endpoints.</p>
     */
    public PhrHttpServer(
            Eventloop eventloop,
            PhrFhirRoutes fhirRoutes,
            PhrDashboardRoutes dashboardRoutes,
            PhrPatientRecordRoutes patientRecordRoutes,
            PhrConsentRoutes consentRoutes,
            PhrClinicalRoutes clinicalRoutes,
            PhrConditionRoutes conditionRoutes,
            PhrEmergencyRoutes emergencyRoutes,
            PhrAdministrativeRoutes administrativeRoutes,
            PhrDocumentImagingRoutes documentImagingRoutes,
            PhrReleaseReadinessRoutes releaseReadinessRoutes,
            PhrEntitlementRoutes entitlementRoutes,
            PhrHealthRoutes healthRoutes,
            PhrAuditRoutes auditRoutes,
            PhrAuthRoutes authRoutes,
            PhrProviderRoutes providerRoutes,
            PhrCaregiverRoutes caregiverRoutes,
            PhrFchvRoutes fchvRoutes,
            PhrMobileRoutes mobileRoutes,
            PhrNotificationRoutes notificationRoutes,
            PhrPatientProfileRoutes patientProfileRoutes) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.fhirRoutes = Objects.requireNonNull(fhirRoutes, "fhirRoutes cannot be null");
        this.dashboardRoutes = Objects.requireNonNull(dashboardRoutes, "dashboardRoutes cannot be null");
        this.patientRecordRoutes = Objects.requireNonNull(patientRecordRoutes, "patientRecordRoutes cannot be null");
        this.consentRoutes = Objects.requireNonNull(consentRoutes, "consentRoutes cannot be null");
        this.clinicalRoutes = Objects.requireNonNull(clinicalRoutes, "clinicalRoutes cannot be null");
        this.conditionRoutes = Objects.requireNonNull(conditionRoutes, "conditionRoutes cannot be null");
        this.emergencyRoutes = Objects.requireNonNull(emergencyRoutes, "emergencyRoutes cannot be null");
        this.administrativeRoutes = Objects.requireNonNull(administrativeRoutes, "administrativeRoutes cannot be null");
        this.documentImagingRoutes = Objects.requireNonNull(documentImagingRoutes, "documentImagingRoutes cannot be null");
        this.releaseReadinessRoutes = Objects.requireNonNull(releaseReadinessRoutes, "releaseReadinessRoutes cannot be null");
        this.entitlementRoutes = Objects.requireNonNull(entitlementRoutes, "entitlementRoutes cannot be null");
        this.healthRoutes = Objects.requireNonNull(healthRoutes, "healthRoutes cannot be null");
        this.auditRoutes = Objects.requireNonNull(auditRoutes, "auditRoutes cannot be null");
        this.authRoutes = Objects.requireNonNull(authRoutes, "authRoutes cannot be null");
        this.providerRoutes = Objects.requireNonNull(providerRoutes, "providerRoutes cannot be null");
        this.caregiverRoutes = Objects.requireNonNull(caregiverRoutes, "caregiverRoutes cannot be null");
        this.fchvRoutes = Objects.requireNonNull(fchvRoutes, "fchvRoutes cannot be null");
        this.mobileRoutes = Objects.requireNonNull(mobileRoutes, "mobileRoutes cannot be null");
        this.notificationRoutes = Objects.requireNonNull(notificationRoutes, "notificationRoutes cannot be null");
        this.patientProfileRoutes = Objects.requireNonNull(patientProfileRoutes, "patientProfileRoutes cannot be null");
    }

    // -------------------------------------------------------------------------
    // KernelLifecycleAware
    // -------------------------------------------------------------------------

    @Override
    public Promise<Void> start() {
        started = true;
        healthRoutes.setStarted(true);
        LOG.info("PhrHttpServer started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        started = false;
        healthRoutes.setStarted(false);
        LOG.info("PhrHttpServer stopped");
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return healthRoutes.isHealthy();
    }

    @Override
    public String getName() {
        return "phr-http-server";
    }

    // -------------------------------------------------------------------------
    // Servlet factory
    // -------------------------------------------------------------------------

    /**
     * Returns the ActiveJ {@link RoutingServlet} for all PHR API routes.
     *
     * <p>Mount into a parent router at the desired base path, e.g.:
     * <pre>{@code
     *   RoutingServlet root = RoutingServlet.create()
     *       .map("/api/v1/phr/*", phrHttpServer.getServlet());
     * }</pre>
     *
     * @return routing servlet; never null
     */
    public AsyncServlet getServlet() {
        RoutingServlet.Builder builder = RoutingServlet.builder(eventloop)
            .with("/fhir/*", fhirRoutes.getServlet())
            // Legacy mounts kept for existing clients and test contract.
            .with("/dashboard", dashboardRoutes.getServlet())
            .with("/records/*", documentImagingRoutes.getServlet())
            .with("/patients/*", patientRecordRoutes.getServlet())
            .with("/consents/*", consentRoutes.getServlet())
            .with("/clinical/*", clinicalRoutes.getServlet())
            .with("/emergency/*", emergencyRoutes.getServlet())
            .with("/release-readiness", releaseReadinessRoutes.getServlet())
            .with("/appointments/*", administrativeRoutes.getPatientFacingServlet())
            .with("/admin/*", administrativeRoutes.getServlet())
            .with("/audit/*", auditRoutes.getServlet())
            .with("/auth/*", authRoutes.getServlet())
            .with("/provider/*", providerRoutes.getServlet())
            .with("/caregiver/*", caregiverRoutes.getServlet())
            .with("/fchv/*", fchvRoutes.getServlet())
            .with("/mobile/*", mobileRoutes.getServlet())
            .with("/notifications/*", notificationRoutes.getServlet())
            .with("/profile", patientProfileRoutes.getServlet())
            .with("/profile/settings", patientProfileRoutes.getServlet())
            // Versioned mounts.
            .with("/api/v1/dashboard", dashboardRoutes.getServlet())
            .with("/api/v1/records/documents/*", documentImagingRoutes.getDocumentServlet())
            .with("/api/v1/records/*", patientRecordRoutes.getServlet())
            .with("/api/v1/consents/*", consentRoutes.getServlet())
            .with("/api/v1/clinical/*", clinicalRoutes.getServlet())
            .with("/api/v1/emergency/*", emergencyRoutes.getServlet())
            .with("/api/v1/release-readiness", releaseReadinessRoutes.getServlet())
            .with("/api/v1/appointments/*", administrativeRoutes.getPatientFacingServlet())
            .with("/api/v1/admin/*", administrativeRoutes.getServlet())
            .with("/api/v1/audit/*", auditRoutes.getServlet())
            .with("/api/v1/auth/*", authRoutes.getServlet())
            .with("/api/v1/provider/*", providerRoutes.getServlet())
            .with("/api/v1/caregiver/*", caregiverRoutes.getServlet())
            .with("/api/v1/fchv/*", fchvRoutes.getServlet())
            .with("/api/v1/mobile/*", mobileRoutes.getServlet())
            .with("/api/v1/notifications/*", notificationRoutes.getServlet())
            .with("/api/v1/profile", patientProfileRoutes.getServlet())
            .with("/api/v1/profile/settings", patientProfileRoutes.getServlet())
            .with("/route-entitlements", entitlementRoutes.getServlet())
            .with("/health", healthRoutes.getServlet())
            .with("/ready", healthRoutes.getReadyServlet());

        return builder.build();
    }
}
