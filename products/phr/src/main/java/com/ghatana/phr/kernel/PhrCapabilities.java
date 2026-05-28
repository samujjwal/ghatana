package com.ghatana.phr.kernel;

import com.ghatana.kernel.descriptor.KernelCapability;
import com.ghatana.kernel.descriptor.KernelCapability.CapabilityType;

import java.util.Map;

/**
 * PHR product capability definitions.
 *
 * <p>This class owns all capability constants declared by the PHR product.
 * Product-specific capabilities must be declared here, keeping product-owned
 * capability IDs out of generic Kernel capability containers.</p>
 *
 * <p>Capability ID convention: {@code phr.*} prefix to avoid collision with
 * kernel core capabilities ({@code data.*}, {@code user.*}, etc.).</p>
 *
 * <p>Reference: KERNEL_CANONICALIZATION_DECISIONS.md capability purity and
 * CODE_ALIGNMENT_SPECIFICATION.md product-owned capability targets.</p>
 *
 * @doc.type class
 * @doc.purpose PHR product-owned capability declarations
 * @doc.layer product
 * @doc.pattern ValueObject, Constants
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public final class PhrCapabilities {

    private PhrCapabilities() {}

    // ── Domain capabilities ──────────────────────────────────────────────────

    /** Patient record management with FHIR R4 and Nepal-2081 compliance support. */
    public static final KernelCapability PATIENT_RECORDS = new KernelCapability(
        "phr.patient-records", "Patient Records",
        "Healthcare patient record management with FHIR R4 and Nepal Directive 2081 support",
        CapabilityType.BUSINESS_LOGIC,
        Map.of(
            "domain", "healthcare",
            "standards", "fhir-r4,nepal-2081",
            "retention_years", "25"
        )
    );

    /** Patient consent lifecycle management — FHIR R4 Consent resource, granular field-level. */
    public static final KernelCapability CONSENT_MANAGEMENT = new KernelCapability(
        "phr.consent-management", "Consent Management",
        "Patient consent lifecycle: DRAFT → PROPOSED → ACTIVE → REVOKED/EXPIRED. "
            + "Granular field-level consent per Nepal Privacy Act 2075 and FHIR Consent.",
        CapabilityType.COMPLIANCE,
        Map.of(
            "domain", "healthcare",
            "regulations", "nepal-2081,privacy-act-2075,fhir-consent",
            "granularity", "field-level"
        )
    );

    /** FHIR R4 resource interoperability — supports Patient, Observation, Medication, Appointment. */
    public static final KernelCapability FHIR_INTEROP = new KernelCapability(
        "phr.fhir-interop", "FHIR Interoperability",
        "FHIR R4 resource processing, validation, and exchange",
        CapabilityType.INTEGRATION,
        Map.of(
            "domain", "healthcare",
            "version", "r4",
            "resources", "patient,observation,medication,appointment,consent,document"
        )
    );

    /** Clinical document management: lab reports, imaging, discharge, prescriptions. */
    public static final KernelCapability CLINICAL_DOCUMENTS = new KernelCapability(
        "phr.clinical-documents", "Clinical Documents",
        "Medical document storage (C3/C4 classification) with OCR and imaging support",
        CapabilityType.DATA_MANAGEMENT,
        Map.of(
            "domain", "healthcare",
            "types", "lab-reports,imaging,discharge-summary,prescriptions",
            "ocr", "true"
        )
    );

    /** Medication tracking: prescriptions, dispensing, adherence, interaction checks. */
    public static final KernelCapability MEDICATION_MANAGEMENT = new KernelCapability(
        "phr.medication-management", "Medication Management",
        "Prescription management, dispensing history, and interaction checks",
        CapabilityType.BUSINESS_LOGIC,
        Map.of(
            "domain", "healthcare",
            "features", "prescriptions,refills,interactions"
        )
    );

    /** Appointment lifecycle management: scheduling, rescheduling, reminders, attendance. */
    public static final KernelCapability APPOINTMENT_SCHEDULING = new KernelCapability(
        "phr.appointment-scheduling", "Appointment Scheduling",
        "Appointment scheduling, rescheduling, reminders, and attendance tracking",
        CapabilityType.WORKFLOW,
        Map.of(
            "domain", "healthcare",
            "scheduling_types", "in-person,virtual,phone",
            "notifications", "email,sms,push"
        )
    );
}
