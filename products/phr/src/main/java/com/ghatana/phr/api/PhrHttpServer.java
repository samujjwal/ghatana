/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.api;

import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.api.routes.PhrAdministrativeRoutes;
import com.ghatana.phr.api.routes.PhrAuditRoutes;
import com.ghatana.phr.api.routes.PhrAuthRoutes;
import com.ghatana.phr.api.routes.PhrClinicalRoutes;
import com.ghatana.phr.api.routes.PhrConsentRoutes;
import com.ghatana.phr.api.routes.PhrDocumentImagingRoutes;
import com.ghatana.phr.api.routes.PhrEntitlementRoutes;
import com.ghatana.phr.api.routes.PhrEmergencyRoutes;
import com.ghatana.phr.api.routes.PhrFhirRoutes;
import com.ghatana.phr.api.routes.PhrHealthRoutes;
import com.ghatana.phr.api.routes.PhrPatientRecordRoutes;
import com.ghatana.phr.api.routes.PhrReleaseReadinessRoutes;
import com.ghatana.phr.fhir.server.PhrFhirR4Server;
import com.ghatana.phr.kernel.service.BillingService;
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
 * <h2>Endpoints</h2>
 * <pre>
 *   POST   /fhir/:resourceType                          — Create a FHIR R4 resource
 *   GET    /fhir/:resourceType/:id                      — Read a FHIR R4 resource by ID
 *   GET    /fhir/:resourceType                          — Search FHIR R4 resources
 *   POST   /patients                                    — Create a patient record
 *   GET    /patients?patientId=:id                      — Search patient records
 *   GET    /patients/:patientId                         — Read a patient record
 *   PUT    /patients/:patientId                         — Update a patient record
 *   GET    /patients/:patientId/history                 — Read patient record history
 *   POST   /consents/grants                             — Grant consent
 *   POST   /consents/grants/:grantId/revoke             — Revoke consent
 *   GET    /consents/check                              — Check consent
 *   GET    /consents?patientId=:id                      — List patient consent grants
 *   POST   /clinical/labs/observations                  — Record a lab observation
 *   GET    /clinical/labs?patientId=:id                 — List patient lab observations
 *   POST   /clinical/medications/prescriptions          — Create a medication prescription
 *   GET    /clinical/medications?patientId=:id          — List patient medications
 *   POST   /clinical/immunizations                      — Record an immunization
 *   GET    /clinical/immunizations?patientId=:id        — List patient immunizations
 *   POST   /emergency/access                            — Request break-glass emergency access
 *   POST   /emergency/reviews/:eventId                  — Review a break-glass event
 *   GET    /emergency/reviews/pending                   — List pending emergency reviews
 *   POST   /admin/telemedicine/sessions                 — Schedule a telemedicine session
 *   POST   /admin/referrals                             — Create a referral
 *   POST   /admin/billing/encounters                    — Create a billing encounter
 *   POST   /records/documents                           — Upload a patient document
 *   POST   /records/imaging/orders                      — Create an imaging order
 *   POST   /appointments                               — Patient self-scheduling request
 *   GET    /audit/events                               — Paginated audit event trail
 *   POST   /auth/login                                 — Session bootstrap via credentials
 *   POST   /auth/logout                                — Session termination
 *   GET    /route-entitlements                          — Route/content entitlement payload
 *   GET    /release-readiness                           — Admin release readiness runtime truth
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
    private final PhrPatientRecordRoutes patientRecordRoutes;
    private final PhrConsentRoutes consentRoutes;
    private final PhrClinicalRoutes clinicalRoutes;
    private final PhrEmergencyRoutes emergencyRoutes;
    private final PhrAdministrativeRoutes administrativeRoutes;
    private final PhrDocumentImagingRoutes documentImagingRoutes;
    private final PhrReleaseReadinessRoutes releaseReadinessRoutes;
    private final PhrEntitlementRoutes entitlementRoutes;
    private final PhrHealthRoutes healthRoutes;
    private final PhrAuditRoutes auditRoutes;
    private final PhrAuthRoutes authRoutes;
    private volatile boolean started = false;

    /**
     * Creates a new PHR HTTP server.
     *
     * @param eventloop          the eventloop; must not be null
     * @param fhirRoutes          the FHIR route handlers; must not be null
     * @param entitlementRoutes    the entitlement route handlers; must not be null
     * @param healthRoutes         the health check route handlers; must not be null
     */
    public PhrHttpServer(Eventloop eventloop, PhrFhirRoutes fhirRoutes, PhrEntitlementRoutes entitlementRoutes, PhrHealthRoutes healthRoutes) {
        this(eventloop, fhirRoutes, null, null, null, null, null, null, null, entitlementRoutes, healthRoutes);
    }

    /**
     * Creates a new PHR HTTP server.
     *
     * @param eventloop             the eventloop; must not be null
     * @param fhirRoutes            the FHIR route handlers; must not be null
     * @param patientRecordRoutes   the patient record route handlers; may be null for legacy tests
     * @param consentRoutes         the consent route handlers; may be null for legacy tests
     * @param entitlementRoutes     the entitlement route handlers; must not be null
     * @param healthRoutes          the health check route handlers; must not be null
     */
    public PhrHttpServer(
            Eventloop eventloop,
            PhrFhirRoutes fhirRoutes,
            PhrPatientRecordRoutes patientRecordRoutes,
            PhrConsentRoutes consentRoutes,
            PhrClinicalRoutes clinicalRoutes,
            PhrEmergencyRoutes emergencyRoutes,
            PhrAdministrativeRoutes administrativeRoutes,
            PhrDocumentImagingRoutes documentImagingRoutes,
            PhrEntitlementRoutes entitlementRoutes,
            PhrHealthRoutes healthRoutes) {
        this(
            eventloop,
            fhirRoutes,
            patientRecordRoutes,
            consentRoutes,
            clinicalRoutes,
            emergencyRoutes,
            administrativeRoutes,
            documentImagingRoutes,
            null,
            entitlementRoutes,
            healthRoutes
        );
    }

    public PhrHttpServer(
            Eventloop eventloop,
            PhrFhirRoutes fhirRoutes,
            PhrPatientRecordRoutes patientRecordRoutes,
            PhrConsentRoutes consentRoutes,
            PhrClinicalRoutes clinicalRoutes,
            PhrEmergencyRoutes emergencyRoutes,
            PhrAdministrativeRoutes administrativeRoutes,
            PhrDocumentImagingRoutes documentImagingRoutes,
            PhrReleaseReadinessRoutes releaseReadinessRoutes,
            PhrEntitlementRoutes entitlementRoutes,
            PhrHealthRoutes healthRoutes) {
        this(eventloop, fhirRoutes, patientRecordRoutes, consentRoutes, clinicalRoutes,
            emergencyRoutes, administrativeRoutes, documentImagingRoutes, releaseReadinessRoutes,
            entitlementRoutes, healthRoutes, null, null);
    }

    public PhrHttpServer(
            Eventloop eventloop,
            PhrFhirRoutes fhirRoutes,
            PhrPatientRecordRoutes patientRecordRoutes,
            PhrConsentRoutes consentRoutes,
            PhrClinicalRoutes clinicalRoutes,
            PhrEmergencyRoutes emergencyRoutes,
            PhrAdministrativeRoutes administrativeRoutes,
            PhrDocumentImagingRoutes documentImagingRoutes,
            PhrReleaseReadinessRoutes releaseReadinessRoutes,
            PhrEntitlementRoutes entitlementRoutes,
            PhrHealthRoutes healthRoutes,
            PhrAuditRoutes auditRoutes,
            PhrAuthRoutes authRoutes) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop cannot be null");
        this.fhirRoutes = Objects.requireNonNull(fhirRoutes, "fhirRoutes cannot be null");
        this.patientRecordRoutes = patientRecordRoutes;
        this.consentRoutes = consentRoutes;
        this.clinicalRoutes = clinicalRoutes;
        this.emergencyRoutes = emergencyRoutes;
        this.administrativeRoutes = administrativeRoutes;
        this.documentImagingRoutes = documentImagingRoutes;
        this.releaseReadinessRoutes = releaseReadinessRoutes;
        this.entitlementRoutes = Objects.requireNonNull(entitlementRoutes, "entitlementRoutes cannot be null");
        this.healthRoutes = Objects.requireNonNull(healthRoutes, "healthRoutes cannot be null");
        this.auditRoutes = auditRoutes;
        this.authRoutes = authRoutes;
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
            .with("/fhir/*", fhirRoutes.getServlet());
        if (patientRecordRoutes != null) {
            builder.with("/patients/*", patientRecordRoutes.getServlet());
        }
        if (consentRoutes != null) {
            builder.with("/consents/*", consentRoutes.getServlet());
        }
        if (clinicalRoutes != null) {
            builder.with("/clinical/*", clinicalRoutes.getServlet());
        }
        if (emergencyRoutes != null) {
            builder.with("/emergency/*", emergencyRoutes.getServlet());
        }
        if (administrativeRoutes != null) {
            builder.with("/admin/*", administrativeRoutes.getServlet());
        }
        if (documentImagingRoutes != null) {
            builder.with("/records/*", documentImagingRoutes.getServlet());
        }
        if (releaseReadinessRoutes != null) {
            builder.with("/release-readiness", releaseReadinessRoutes.getServlet());
        }
        if (administrativeRoutes != null) {
            builder.with("/appointments/*", administrativeRoutes.getPatientFacingServlet());
        }
        if (auditRoutes != null) {
            builder.with("/audit/*", auditRoutes.getServlet());
        }
        if (authRoutes != null) {
            builder.with("/auth/*", authRoutes.getServlet());
        }
        return builder
            .with("/route-entitlements", entitlementRoutes.getServlet())
            .with("/health", healthRoutes.getServlet())
            .with("/ready", healthRoutes.getReadyServlet())
            .build();
    }
}
