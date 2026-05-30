/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.phr.api;

import com.ghatana.phr.api.routes.*;

import io.activej.eventloop.Eventloop;

/**
 * Test-only factory for creating {@link PhrHttpServer} instances with partial route sets.
 *
 * <p>This factory is for test scenarios that need to exercise specific route families
 * in isolation. Production code must use the full {@link PhrHttpServer} constructor
 * which requires all route adapters to be non-null.</p>
 *
 * <p>Usage example for testing a single route family:</p>
 * <pre>{@code
 * PhrHttpServer server = PhrHttpServerTestFactory.forRoutes(
 *     eventloop,
 *     fhirRoutes,
 *     healthRoutes
 * );
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Test-only factory for partial PhrHttpServer instances
 * @doc.layer test
 * @doc.pattern Factory
 */
public final class PhrHttpServerTestFactory {

    private PhrHttpServerTestFactory() {
        // Utility class - prevent instantiation
    }

    /**
     * Creates a minimal PhrHttpServer with only the specified routes.
     *
     * <p>Unspecified route adapters are replaced with no-op implementations
     * that return 404 responses. This allows testing specific route families
     * without requiring the full production wiring.</p>
     *
     * @param eventloop the ActiveJ eventloop
     * @param fhirRoutes FHIR routes (required)
     * @param healthRoutes health routes (required)
     * @return a PhrHttpServer with partial route wiring
     */
    public static PhrHttpServer forRoutes(
            Eventloop eventloop,
            PhrFhirRoutes fhirRoutes,
            PhrHealthRoutes healthRoutes) {
        return new PhrHttpServer(
            eventloop,
            fhirRoutes,
            noopDashboardRoutes(eventloop),
            noopPatientRecordRoutes(eventloop),
            noopConsentRoutes(eventloop),
            noopClinicalRoutes(eventloop),
            noopConditionRoutes(eventloop),
            noopEmergencyRoutes(eventloop),
            noopAdministrativeRoutes(eventloop),
            noopDocumentImagingRoutes(eventloop),
            noopReleaseReadinessRoutes(eventloop),
            noopEntitlementRoutes(eventloop),
            healthRoutes,
            noopAuditRoutes(eventloop),
            noopAuthRoutes(eventloop),
            noopProviderRoutes(eventloop),
            noopCaregiverRoutes(eventloop),
            noopFchvRoutes(eventloop),
            noopMobileRoutes(eventloop),
            noopNotificationRoutes(eventloop),
            noopPatientProfileRoutes(eventloop)
        );
    }

    /**
     * Creates a PhrHttpServer with all routes except the specified ones.
     *
     * <p>Useful for testing that a route family is properly excluded from routing.</p>
     *
     * @param eventloop the ActiveJ eventloop
     * @param allRoutes all production route adapters
     * @param excludeRouteClass the route class to exclude
     * @return a PhrHttpServer without the specified route
     */
    public static PhrHttpServer withoutRoute(
            Eventloop eventloop,
            AllPhrRoutes allRoutes,
            Class<? extends Object> excludeRouteClass) {
        if (excludeRouteClass == PhrFhirRoutes.class) {
            return new PhrHttpServer(
                eventloop,
                noopFhirRoutes(eventloop),
                allRoutes.dashboardRoutes,
                allRoutes.patientRecordRoutes,
                allRoutes.consentRoutes,
                allRoutes.clinicalRoutes,
                allRoutes.conditionRoutes,
                allRoutes.emergencyRoutes,
                allRoutes.administrativeRoutes,
                allRoutes.documentImagingRoutes,
                allRoutes.releaseReadinessRoutes,
                allRoutes.entitlementRoutes,
                allRoutes.healthRoutes,
                allRoutes.auditRoutes,
                allRoutes.authRoutes,
                allRoutes.providerRoutes,
                allRoutes.caregiverRoutes,
                allRoutes.fchvRoutes,
                allRoutes.mobileRoutes,
                allRoutes.notificationRoutes,
                allRoutes.patientProfileRoutes
            );
        }
        // Add more exclusions as needed
        throw new UnsupportedOperationException("Excluding " + excludeRouteClass.getSimpleName() + " is not yet implemented");
    }

    /**
     * Container for all PHR route adapters.
     */
    public static class AllPhrRoutes {
        public final PhrFhirRoutes fhirRoutes;
        public final PhrDashboardRoutes dashboardRoutes;
        public final PhrPatientRecordRoutes patientRecordRoutes;
        public final PhrConsentRoutes consentRoutes;
        public final PhrClinicalRoutes clinicalRoutes;
        public final PhrEmergencyRoutes emergencyRoutes;
        public final PhrAdministrativeRoutes administrativeRoutes;
        public final PhrDocumentImagingRoutes documentImagingRoutes;
        public final PhrReleaseReadinessRoutes releaseReadinessRoutes;
        public final PhrEntitlementRoutes entitlementRoutes;
        public final PhrHealthRoutes healthRoutes;
        public final PhrAuditRoutes auditRoutes;
        public final PhrAuthRoutes authRoutes;
        public final PhrProviderRoutes providerRoutes;
        public final PhrCaregiverRoutes caregiverRoutes;
        public final PhrFchvRoutes fchvRoutes;
        public final PhrMobileRoutes mobileRoutes;
        public final PhrNotificationRoutes notificationRoutes;
        public final PhrPatientProfileRoutes patientProfileRoutes;
        public final PhrConditionRoutes conditionRoutes;

        public AllPhrRoutes(
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
            this.fhirRoutes = fhirRoutes;
            this.dashboardRoutes = dashboardRoutes;
            this.patientRecordRoutes = patientRecordRoutes;
            this.consentRoutes = consentRoutes;
            this.clinicalRoutes = clinicalRoutes;
            this.conditionRoutes = conditionRoutes;
            this.emergencyRoutes = emergencyRoutes;
            this.administrativeRoutes = administrativeRoutes;
            this.documentImagingRoutes = documentImagingRoutes;
            this.releaseReadinessRoutes = releaseReadinessRoutes;
            this.entitlementRoutes = entitlementRoutes;
            this.healthRoutes = healthRoutes;
            this.auditRoutes = auditRoutes;
            this.authRoutes = authRoutes;
            this.providerRoutes = providerRoutes;
            this.caregiverRoutes = caregiverRoutes;
            this.fchvRoutes = fchvRoutes;
            this.mobileRoutes = mobileRoutes;
            this.notificationRoutes = notificationRoutes;
            this.patientProfileRoutes = patientProfileRoutes;
        }
    }

    // -------------------------------------------------------------------------
    // No-op route implementations for test factory
    // -------------------------------------------------------------------------

    private static PhrDashboardRoutes noopDashboardRoutes(Eventloop eventloop) {
        return new PhrDashboardRoutes(
            eventloop,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private static PhrPatientRecordRoutes noopPatientRecordRoutes(Eventloop eventloop) {
        return new PhrPatientRecordRoutes(eventloop, null, null, null);
    }

    private static PhrConsentRoutes noopConsentRoutes(Eventloop eventloop) {
        return new PhrConsentRoutes(eventloop, null, null);
    }

    private static PhrClinicalRoutes noopClinicalRoutes(Eventloop eventloop) {
        return new PhrClinicalRoutes(eventloop, null, null, null, null, null);
    }

    private static PhrEmergencyRoutes noopEmergencyRoutes(Eventloop eventloop) {
        return new PhrEmergencyRoutes(eventloop, null, null, null);
    }

    private static PhrAdministrativeRoutes noopAdministrativeRoutes(Eventloop eventloop) {
        return new PhrAdministrativeRoutes(eventloop, null, null, null, null, null, null);
    }

    private static PhrDocumentImagingRoutes noopDocumentImagingRoutes(Eventloop eventloop) {
        return new PhrDocumentImagingRoutes(eventloop, null, null, null, null);
    }

    private static PhrReleaseReadinessRoutes noopReleaseReadinessRoutes(Eventloop eventloop) {
        return new PhrReleaseReadinessRoutes(eventloop, null);
    }

    private static PhrEntitlementRoutes noopEntitlementRoutes(Eventloop eventloop) {
        return new PhrEntitlementRoutes(eventloop, null, null);
    }

    private static PhrAuditRoutes noopAuditRoutes(Eventloop eventloop) {
        return new PhrAuditRoutes(eventloop, null, null);
    }

    private static PhrAuthRoutes noopAuthRoutes(Eventloop eventloop) {
        return new PhrAuthRoutes(eventloop, null, null, null);
    }

    private static PhrProviderRoutes noopProviderRoutes(Eventloop eventloop) {
        return new PhrProviderRoutes(eventloop, null, null, null, null);
    }

    private static PhrCaregiverRoutes noopCaregiverRoutes(Eventloop eventloop) {
        return new PhrCaregiverRoutes(eventloop, null, null, null);
    }

    private static PhrFchvRoutes noopFchvRoutes(Eventloop eventloop) {
        return new PhrFchvRoutes(eventloop, null);
    }

    private static PhrMobileRoutes noopMobileRoutes(Eventloop eventloop) {
        return new PhrMobileRoutes(eventloop, null, null, null, null);
    }

    private static PhrNotificationRoutes noopNotificationRoutes(Eventloop eventloop) {
        return new PhrNotificationRoutes(eventloop, null);
    }

    private static PhrPatientProfileRoutes noopPatientProfileRoutes(Eventloop eventloop) {
        return new PhrPatientProfileRoutes(eventloop, null);
    }

    private static PhrConditionRoutes noopConditionRoutes(Eventloop eventloop) {
        return new PhrConditionRoutes(eventloop, null, null);
    }

    private static PhrFhirRoutes noopFhirRoutes(Eventloop eventloop) {
        return new PhrFhirRoutes(eventloop, null);
    }
}
