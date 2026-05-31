package com.ghatana.phr.kernel.service;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ghatana.kernel.context.KernelContext;
import io.activej.promise.Promise;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Patient Record Service with Data-Cloud persistence.
 *
 * <p>Manages patient demographics, medical history, and health records
 * with Nepal Privacy Act 2075 compliance and FHIR R4 support.</p>
 *
 * @doc.type class
 * @doc.purpose PHR patient record service with Data-Cloud persistence
 * @doc.layer product
 * @doc.pattern Service
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public class PatientRecordService extends PhrServiceBase {

    private static final String DATASET_ID = "phr.patient.records";

    public PatientRecordService(KernelContext context) {
        super(context);
    }

    @Override
    public String getName() {
        return "patient-record";
    }

    @Override
    protected Promise<Void> initializeDatasets() {
        return createSchema(
            DATASET_ID,
            Map.of(
                "patientId", "string",
                "demographics", "json",
                "medicalHistory", "json",
                "createdAt", "timestamp",
                "updatedAt", "timestamp"
            ),
            Map.of("retention", "25years", "encryption", "required")
        );
    }

    /**
     * Creates a new patient record.
     *
     * @param patient the patient data
     * @return Promise containing the created patient
     */
    public Promise<Patient> createPatient(Patient patient) {
        ensureRunning();

        String patientId = patient.getId() != null ? patient.getId() : generateId("pat");
        Patient toStore = patient.withId(patientId).withCreatedAt(Instant.now());

        return createRecord(
            DATASET_ID,
            patientId,
            toStore,
            mutationMetadata(Map.of(
                "type", "patient",
                "version", "1.0",
                "createdAt", Instant.now().toString(),
                "patientId", patientId
            ), "system"),
            "Patient",
            1
        ).then(created -> audit("PATIENT_CREATE", patientId, "Patient record created")
            .map($ -> created));
    }

    /**
     * Retrieves a patient by ID.
     *
     * @param patientId the patient identifier
     * @return Promise containing the patient if found
     */
    public Promise<Optional<Patient>> getPatient(String patientId) {
        validateRequired(patientId, "patientId");
        return readRecord(DATASET_ID, patientId, Patient.class);
    }

    /**
     * Updates an existing patient record.
     *
     * @param patient the updated patient data
     * @return Promise containing the updated patient
     */
    public Promise<Patient> updatePatient(Patient patient) {
        validateRequired(patient.getId(), "patient.id");

        Patient toStore = patient.withUpdatedAt(Instant.now());

        return updateRecord(
            DATASET_ID,
            patient.getId(),
            toStore,
            mutationMetadata(Map.of(
                "type", "patient",
                "version", "1.0",
                "updatedAt", Instant.now().toString(),
                "patientId", patient.getId()
            ), "system"),
            "Patient",
            1
        ).then(updated -> audit("PATIENT_UPDATE", patient.getId(), "Patient record updated")
            .map($ -> updated));
    }

    /**
     * Searches patients by criteria.
     *
     * @param query the search query
     * @param params the query parameters
     * @param limit the maximum results
     * @param offset the offset for pagination
     * @return Promise containing search results
     */
    public Promise<List<Patient>> searchPatients(String query, Map<String, Object> params, int limit, int offset) {
        return queryRecords(DATASET_ID, query, params, limit, offset, Patient.class);
    }

    /**
     * Deletes a patient record.
     *
     * @param patientId the patient identifier
     * @return Promise completing when deleted
     */
    public Promise<Void> deletePatient(String patientId) {
        validateRequired(patientId, "patientId");

        return getPatient(patientId)
            .then(opt -> {
                if (opt.isEmpty()) {
                    return Promise.ofException(new IllegalStateException("Patient not found: " + patientId));
                }
                Patient patient = opt.get().asDeleted();
                return updatePatient(patient);
            })
            .then($ -> audit("PATIENT_DELETE", patientId, "Patient record soft-deleted"));
    }


    // ==================== Inner Types ====================

    /**
     * Patient record data model.
     */
    public static class Patient {
        private final String id;
        private final String nationalId; // Nepal National ID
        private final Demographics demographics;
        private final MedicalHistory medicalHistory;
        private final Instant createdAt;
        private final Instant updatedAt;
        private final boolean deleted;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Patient(
                       @JsonProperty("id") String id,
                       @JsonProperty("nationalId") String nationalId,
                       @JsonProperty("demographics") Demographics demographics,
                       @JsonProperty("medicalHistory") MedicalHistory medicalHistory,
                       @JsonProperty("createdAt") Instant createdAt,
                       @JsonProperty("updatedAt") Instant updatedAt,
                       @JsonProperty("deleted") boolean deleted) {
            this.id = id;
            this.nationalId = nationalId;
            this.demographics = demographics;
            this.medicalHistory = medicalHistory;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.deleted = deleted;
        }

        public static PatientBuilder builder() {
            return new PatientBuilder();
        }

        public String getId() { return id; }
        public String getNationalId() { return nationalId; }
        public Demographics getDemographics() { return demographics; }
        public MedicalHistory getMedicalHistory() { return medicalHistory; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public boolean isDeleted() { return deleted; }

        public Patient withId(String newId) {
            return new Patient(newId, nationalId, demographics, medicalHistory, createdAt, updatedAt, deleted);
        }

        public Patient withCreatedAt(Instant newCreatedAt) {
            return new Patient(id, nationalId, demographics, medicalHistory, newCreatedAt, updatedAt, deleted);
        }

        public Patient withUpdatedAt(Instant newUpdatedAt) {
            return new Patient(id, nationalId, demographics, medicalHistory, createdAt, newUpdatedAt, deleted);
        }

        public Patient withDemographics(Demographics newDemographics) {
            return new Patient(id, nationalId, newDemographics, medicalHistory, createdAt, updatedAt, deleted);
        }

        public Patient withMedicalHistory(MedicalHistory newMedicalHistory) {
            return new Patient(id, nationalId, demographics, newMedicalHistory, createdAt, updatedAt, deleted);
        }

        public Patient asDeleted() {
            return new Patient(id, nationalId, demographics, medicalHistory, createdAt, Instant.now(), true);
        }
    }

    public static class PatientBuilder {
        private String id;
        private String nationalId;
        private Demographics demographics;
        private MedicalHistory medicalHistory;
        private Instant createdAt;
        private Instant updatedAt;
        private boolean deleted;

        public PatientBuilder id(String id) { this.id = id; return this; }
        public PatientBuilder nationalId(String nationalId) { this.nationalId = nationalId; return this; }
        public PatientBuilder demographics(Demographics demographics) { this.demographics = demographics; return this; }
        public PatientBuilder medicalHistory(MedicalHistory medicalHistory) { this.medicalHistory = medicalHistory; return this; }
        public PatientBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public PatientBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public PatientBuilder deleted(boolean deleted) { this.deleted = deleted; return this; }

        public Patient build() {
            return new Patient(id, nationalId, demographics, medicalHistory, createdAt, updatedAt, deleted);
        }
    }

    public static class Demographics {
        private final String givenName;
        private final String familyName;
        private final String dateOfBirth; // ISO-8601
        private final String gender; // male, female, other, unknown
        private final Address address;
        private final Contact contact;
        private final String preferredLanguage;
        private final String facilityId;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Demographics(
                           @JsonProperty("givenName") String givenName,
                           @JsonProperty("familyName") String familyName,
                           @JsonProperty("dateOfBirth") String dateOfBirth,
                           @JsonProperty("gender") String gender,
                           @JsonProperty("address") Address address,
                           @JsonProperty("contact") Contact contact,
                           @JsonProperty("preferredLanguage") String preferredLanguage,
                           @JsonProperty("facilityId") String facilityId) {
            this.givenName = givenName;
            this.familyName = familyName;
            this.dateOfBirth = dateOfBirth;
            this.gender = gender;
            this.address = address;
            this.contact = contact;
            this.preferredLanguage = preferredLanguage;
            this.facilityId = facilityId;
        }

        public Demographics(
                           String givenName,
                           String familyName,
                           String dateOfBirth,
                           String gender,
                           Address address,
                           Contact contact) {
            this(givenName, familyName, dateOfBirth, gender, address, contact, null, null);
        }

        public String getGivenName() { return givenName; }
        public String getFamilyName() { return familyName; }
        public String getDateOfBirth() { return dateOfBirth; }
        public String getGender() { return gender; }
        public Address getAddress() { return address; }
        public Contact getContact() { return contact; }
        public String getPreferredLanguage() { return preferredLanguage; }
        public String getFacilityId() { return facilityId; }

        public Demographics withGender(String newGender) {
            return new Demographics(givenName, familyName, dateOfBirth, newGender, address, contact, preferredLanguage, facilityId);
        }

        public Demographics withAddress(Address newAddress) {
            return new Demographics(givenName, familyName, dateOfBirth, gender, newAddress, contact, preferredLanguage, facilityId);
        }

        public Demographics withContact(Contact newContact) {
            return new Demographics(givenName, familyName, dateOfBirth, gender, address, newContact, preferredLanguage, facilityId);
        }

        public Demographics withPreferredLanguage(String newPreferredLanguage) {
            return new Demographics(givenName, familyName, dateOfBirth, gender, address, contact, newPreferredLanguage, facilityId);
        }

        public Demographics withFacilityId(String newFacilityId) {
            return new Demographics(givenName, familyName, dateOfBirth, gender, address, contact, preferredLanguage, newFacilityId);
        }

        public String getFullName() {
            return (givenName != null ? givenName : "") + (familyName != null ? " " + familyName : "");
        }

        public String getAge() {
            if (dateOfBirth == null || dateOfBirth.isBlank()) {
                return "unknown";
            }
            try {
                LocalDate birthDate = LocalDate.parse(dateOfBirth);
                int years = Period.between(birthDate, LocalDate.now()).getYears();
                return years >= 0 ? Integer.toString(years) : "unknown";
            } catch (DateTimeException ex) {
                return "unknown";
            }
        }

        public String getDistrict() {
            return address != null ? address.getDistrict() : null;
        }

        public String getMunicipality() {
            return address != null ? address.getCity() : null;
        }

        public String getPhone() {
            return contact != null ? contact.getPhone() : null;
        }

        public String getEmail() {
            return contact != null ? contact.getEmail() : null;
        }
    }

    public static class Address {
        private final String line1;
        private final String city;
        private final String district; // Nepal districts
        private final String province;
        private final String postalCode;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Address(
                       @JsonProperty("line1") String line1,
                       @JsonProperty("city") String city,
                       @JsonProperty("district") String district,
                       @JsonProperty("province") String province,
                       @JsonProperty("postalCode") String postalCode) {
            this.line1 = line1;
            this.city = city;
            this.district = district;
            this.province = province;
            this.postalCode = postalCode;
        }

        public String getLine1() { return line1; }
        public String getCity() { return city; }
        public String getDistrict() { return district; }
        public String getProvince() { return province; }
        public String getPostalCode() { return postalCode; }

        public Address withDistrict(String newDistrict) {
            return new Address(line1, city, newDistrict, province, postalCode);
        }
    }

    public static class Contact {
        private final String phone;
        private final String email;
        private final String emergencyContact;
        private final String emergencyPhone;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Contact(
                       @JsonProperty("phone") String phone,
                       @JsonProperty("email") String email,
                       @JsonProperty("emergencyContact") String emergencyContact,
                       @JsonProperty("emergencyPhone") String emergencyPhone) {
            this.phone = phone;
            this.email = email;
            this.emergencyContact = emergencyContact;
            this.emergencyPhone = emergencyPhone;
        }

        public String getPhone() { return phone; }
        public String getEmail() { return email; }
        public String getEmergencyContact() { return emergencyContact; }
        public String getEmergencyPhone() { return emergencyPhone; }

        public Contact withEmergencyContact(String newEmergencyContact) {
            return new Contact(phone, email, newEmergencyContact, emergencyPhone);
        }
    }

    public static class MedicalHistory {
        private final List<String> conditions;
        private final List<String> allergies;
        private final List<String> medications;
        private final String bloodType;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public MedicalHistory(
                             @JsonProperty("conditions") List<String> conditions,
                             @JsonProperty("allergies") List<String> allergies,
                             @JsonProperty("medications") List<String> medications,
                             @JsonProperty("bloodType") String bloodType) {
            this.conditions = conditions != null ? conditions : List.of();
            this.allergies = allergies != null ? allergies : List.of();
            this.medications = medications != null ? medications : List.of();
            this.bloodType = bloodType;
        }

        public List<String> getConditions() { return conditions; }
        public List<String> getAllergies() { return allergies; }
        public List<String> getMedications() { return medications; }
        public List<String> getChronicConditions() { return conditions; }
        public String getBloodType() { return bloodType; }

        public MedicalHistory withBloodType(String newBloodType) {
            return new MedicalHistory(conditions, allergies, medications, newBloodType);
        }
    }
}
