package com.ghatana.phr.healthcare;

import com.ghatana.phr.pack.DomainPackDescriptor;
import com.ghatana.phr.pack.DomainPackManifest;
import com.ghatana.phr.pack.DomainPackManifest.ActivationConstraint;
import com.ghatana.phr.pack.DomainPackManifest.CapabilityRequirement;
import com.ghatana.phr.pack.DomainPackManifest.ContractExport;
import com.ghatana.phr.pack.DomainPackManifest.ContractExport.ContractType;
import com.ghatana.phr.pack.DomainPackManifest.Identity;
import com.ghatana.phr.pack.DomainPackManifest.IntegrationDeclaration;
import com.ghatana.phr.pack.DomainPackManifest.UiContribution;
import com.ghatana.phr.pack.DomainPackManifest.UiContribution.UiSlot;
import com.ghatana.phr.pack.DomainPackManifest.WorkflowDeclaration;

import java.util.List;

/**
 * Canonical {@link DomainPackDescriptor} for the H-01 Healthcare domain pack.
 *
 * <p>Declares the full manifest — identity, required kernel capabilities, exported
 * contracts, workflows, integrations, UI contributions, and activation constraints —
 * enabling the AppPlatform runtime to activate this pack in isolation (healthcare-only
 * deployment) or alongside the finance packs (shared-kernel deployment).</p>
 *
 * <p>This descriptor is the <em>runtime entry point</em> for the healthcare domain pack.
 * It must be registered with the platform's {@code DomainPackRegistry} (either via
 * Java ServiceLoader or dependency-injection scanning).</p>
 *
 * <p>Architecture story: H-01 — Healthcare Domain Pack (Backlog D1 + D2).</p>
 *
 * @doc.type class
 * @doc.purpose Runtime descriptor for the healthcare domain pack — declares all manifest sections
 * @doc.layer product
 * @doc.pattern Strategy, ServiceLoader SPI
 * @author Ghatana AppPlatform Team
 * @since 2026.3.0
 */
public class HealthcareDomainPackDescriptor implements DomainPackDescriptor {

    private static final DomainPackManifest MANIFEST = buildManifest();

    @Override
    public DomainPackManifest getManifest() {
        return MANIFEST;
    }

    // ── Manifest factory ─────────────────────────────────────────────────────

    private static DomainPackManifest buildManifest() {
        return new DomainPackManifest(
            identity(),
            requiredCapabilities(),
            exportedContracts(),
            workflows(),
            integrations(),
            uiContributions(),
            activationConstraints()
        );
    }

    /** H-01 identity. */
    private static Identity identity() {
        return new Identity("healthcare", "H-01", "2026.3.1");
    }

    /**
     * Kernel capabilities required for the healthcare pack to function.
     *
     * <p>{@code data.storage} and {@code user.authentication} are mandatory —
     * patient records and consent decisions cannot operate without them.
     * {@code workflow.engine} is mandatory for the consent lifecycle workflow.
     * {@code event.processing} is optional — used for audit streaming but
     * the pack degrades gracefully without it.</p>
     */
    private static List<CapabilityRequirement> requiredCapabilities() {
        return List.of(
            new CapabilityRequirement("data.storage",        false),  // mandatory — patient records
            new CapabilityRequirement("user.authentication", false),  // mandatory — session + consent actor resolution
            new CapabilityRequirement("workflow.engine",     false),  // mandatory — consent lifecycle workflow
            new CapabilityRequirement("event.processing",    true)    // optional  — audit event streaming
        );
    }

    /**
     * Contracts exported by the healthcare pack for consumption by other packs
     * (e.g. PHR product, insurance pack, telemedicine pack).
     */
    private static List<ContractExport> exportedContracts() {
        return List.of(
            // REST API — patient CRUD + demographics
            new ContractExport("phr.patient-api",          ContractType.REST),
            // REST API — consent lifecycle: create, revoke, query
            new ContractExport("phr.consent-api",          ContractType.REST),
            // REST API — clinical document management
            new ContractExport("phr.clinical-documents-api", ContractType.REST),
            // Event contract — consent state change events
            new ContractExport("health.consent.events",    ContractType.EVENT),
            // Event contract — patient lifecycle events (registered, deactivated, erased)
            new ContractExport("health.patient.events",    ContractType.EVENT),
            // Schema — canonical FHIR R4 Patient + Consent resources
            new ContractExport("health.fhir-schema",       ContractType.SCHEMA)
        );
    }

    /**
     * Workflows declared and owned by the healthcare domain pack.
     *
     * <p>These are registered with the workflow engine at pack activation time.</p>
     */
    private static List<WorkflowDeclaration> workflows() {
        return List.of(
            new WorkflowDeclaration(
                "consent-lifecycle",
                "com.ghatana.phr.healthcare.service.ConsentEnforcementService"),
            new WorkflowDeclaration(
                "patient-registration",
                "com.ghatana.phr.healthcare.service.PatientRegistrationService"),
            new WorkflowDeclaration(
                "right-to-erasure",
                "com.ghatana.phr.healthcare.service.RetentionPolicyService")
        );
    }

    /**
     * External systems the healthcare pack connects to.
     *
     * <p>FHIR server and HL7 interface are optional: the pack operates in
     * standalone mode without them (external interop features degrade gracefully).</p>
     */
    private static List<IntegrationDeclaration> integrations() {
        return List.of(
            new IntegrationDeclaration("fhir-server",    "FHIR R4",       false),  // optional
            new IntegrationDeclaration("hl7-interface",  "HL7 v2.5",      false),  // optional
            new IntegrationDeclaration("postgresql",     "JDBC + RLS",    true)    // required — primary store
        );
    }

    /**
     * UI components contributed by the healthcare domain pack to the AppPlatform shell.
     */
    private static List<UiContribution> uiContributions() {
        return List.of(
            new UiContribution("patient-dashboard",   UiSlot.MAIN_CONTENT),
            new UiContribution("consent-manager",     UiSlot.SIDEBAR_NAV),
            new UiContribution("health-summary-card", UiSlot.DASHBOARD_CARD),
            new UiContribution("phr-settings",        UiSlot.SETTINGS_SECTION)
        );
    }

    /**
     * Activation constraints for deployment-mode isolation.
     *
     * <p>The {@code deployment.healthcare.enabled} flag allows operators to deploy the
     * AppPlatform in finance-only mode without activating healthcare capabilities.
     * Both flags must be set to enable this pack.</p>
     *
     * <p>These constraints implement Backlog D3 — validating healthcare-only and
     * shared-kernel deployment modes at configuration level.</p>
     */
    private static List<ActivationConstraint> activationConstraints() {
        return List.of(
            new ActivationConstraint("deployment.healthcare.enabled", "true"),
            new ActivationConstraint("deployment.domain-packs.h01.active",   "true")
        );
    }
}
