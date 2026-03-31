package com.ghatana.phr.kernel.service;

/**
 * @doc.type class
 * @doc.purpose Cohesive grouped access to PHR services without breaking existing APIs
 * @doc.layer product
 * @doc.pattern Facade
 */
public final class PhrServiceCatalog {

    private final ClinicalServices clinical;
    private final AdministrativeServices administrative;
    private final PatientServices patient;
    private final EmergencyServices emergency;

    public PhrServiceCatalog(
            ClinicalServices clinical,
            AdministrativeServices administrative,
            PatientServices patient,
            EmergencyServices emergency) {
        this.clinical = clinical;
        this.administrative = administrative;
        this.patient = patient;
        this.emergency = emergency;
    }

    public ClinicalServices clinical() {
        return clinical;
    }

    public AdministrativeServices administrative() {
        return administrative;
    }

    public PatientServices patient() {
        return patient;
    }

    public EmergencyServices emergency() {
        return emergency;
    }

    public record ClinicalServices(
        PatientRecordService patientRecords,
        ClinicalNoteService clinicalNotes,
        LabResultService labResults,
        ImagingService imaging,
        DocumentService documents,
        ClinicalDecisionSupportService clinicalDecisionSupport
    ) {
    }

    public record AdministrativeServices(
        AppointmentService appointments,
        BillingService billing,
        ReferralService referrals,
        TelemedicineService telemedicine
    ) {
    }

    public record PatientServices(
        ConsentManagementService consent,
        MedicationService medications,
        ImmunizationService immunizations,
        CaregiverService caregivers
    ) {
    }

    public record EmergencyServices(
        EmergencyAccessLogService emergencyAccess
    ) {
    }
}
