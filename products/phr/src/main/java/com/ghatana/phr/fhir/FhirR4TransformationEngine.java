package com.ghatana.phr.fhir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.phr.healthcare.domain.Patient;
import com.ghatana.phr.kernel.service.ImmunizationService.ImmunizationRecord;
import com.ghatana.phr.kernel.service.LabResultService.LabObservation;
import com.ghatana.phr.kernel.service.MedicationService.Prescription;
import com.ghatana.phr.kernel.service.MedicationService.PrescriptionStatus;
import com.ghatana.platform.core.exception.ServiceException;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * FHIR R4 Transformation Engine — concrete mappings between internal PHR domain types
 * and FHIR R4 JSON representations.
 *
 * <p>Supported transformations to FHIR:</p>
 * <ul>
 *   <li>{@link Patient} → {@code Patient} (R4)</li>
 *   <li>{@link Prescription} → {@code MedicationRequest} (R4)</li>
 *   <li>{@link LabObservation} → {@code Observation} (R4)</li>
 *   <li>{@link ImmunizationRecord} → {@code Immunization} (R4)</li>
 * </ul>
 *
 * <p>The reverse mapping ({@code fromFhir}) is supported for all four types and returns
 * a best-effort hydration into the internal record. Fields not present in FHIR are
 * set to {@code null} and callers must supply missing context before persisting.</p>
 *
 * <h3>Reference</h3>
 * <ul>
 *   <li>FHIR R4 specification: hl7.org/fhir/R4</li>
 *   <li>LOINC: loinc.org (lab codes)</li>
 *   <li>CVX: cdc.gov/vaccines (immunization codes)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose FHIR R4 bidirectional transformation — Patient, MedicationRequest, Observation, Immunization
 * @doc.layer product
 * @doc.pattern Transformer
 * @author Ghatana PHR Team
 * @since 1.0.0
 */
public final class FhirR4TransformationEngine {

    private static final String FHIR_VERSION = "4.0.1";
    private static final String SNOMED_SYSTEM = "http://snomed.info/sct";
    private static final String LOINC_SYSTEM   = "http://loinc.org";
    private static final String CVX_SYSTEM     = "http://hl7.org/fhir/sid/cvx";
    private static final String RXNORM_SYSTEM  = "http://www.nlm.nih.gov/research/umls/rxnorm";
    private static final String UCUM_SYSTEM    = "http://unitsofmeasure.org";
    private static final String PATIENT_SYSTEM = "urn:ghatana:phr:patient";

    private static final DateTimeFormatter FHIR_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FHIR_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = new ObjectMapper();
        MAPPER.registerModule(new JavaTimeModule());
    }

    private FhirR4TransformationEngine() {
        // Utility — no instances
    }

    // ==================== toFhir ====================

    /**
     * Transforms an internal PHR domain object to a FHIR R4 JSON string.
     *
     * @param internalData       the PHR domain object
     * @param targetResourceType the target FHIR resource type
     * @return FHIR R4 JSON string
     * @throws IllegalArgumentException  if the type/object combination is not supported
     * @throws FhirTransformationException if JSON serialization fails
     */
    public static String toFhir(Object internalData, String targetResourceType) {
        try {
            ObjectNode root = switch (targetResourceType) {
                case "Patient" -> {
                    if (!(internalData instanceof Patient p)) {
                        throw new IllegalArgumentException(
                                "Expected Patient for resourceType=Patient, got: "
                                        + internalData.getClass().getSimpleName());
                    }
                    yield patientToFhir(p);
                }
                case "MedicationRequest" -> {
                    if (!(internalData instanceof Prescription rx)) {
                        throw new IllegalArgumentException(
                                "Expected Prescription for resourceType=MedicationRequest, got: "
                                        + internalData.getClass().getSimpleName());
                    }
                    yield prescriptionToFhirMedicationRequest(rx);
                }
                case "Observation" -> {
                    if (!(internalData instanceof LabObservation obs)) {
                        throw new IllegalArgumentException(
                                "Expected LabObservation for resourceType=Observation, got: "
                                        + internalData.getClass().getSimpleName());
                    }
                    yield labObservationToFhirObservation(obs);
                }
                case "Immunization" -> {
                    if (!(internalData instanceof ImmunizationRecord imm)) {
                        throw new IllegalArgumentException(
                                "Expected ImmunizationRecord for resourceType=Immunization, got: "
                                        + internalData.getClass().getSimpleName());
                    }
                    yield immunizationRecordToFhirImmunization(imm);
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported FHIR resource type: " + targetResourceType);
            };
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new FhirTransformationException("Failed to serialize FHIR resource", e);
        }
    }

    // ==================== fromFhir ====================

    /**
     * Deserializes a FHIR R4 JSON string to an internal PHR object.
     *
     * <p>Fields not present in the FHIR JSON will be {@code null} or use default values.
     * Callers must validate and enrich the result before persisting.</p>
     *
     * @param fhirJson           the FHIR R4 JSON string
     * @param sourceResourceType the FHIR resource type
     * @return best-effort internal object
     * @throws FhirTransformationException if parsing fails
     */
    public static Object fromFhir(String fhirJson, String sourceResourceType) {
        try {
            JsonNode root = MAPPER.readTree(fhirJson);
            return switch (sourceResourceType) {
                case "Patient"            -> fhirPatientToInternal(root);
                case "MedicationRequest"  -> fhirMedicationRequestToPrescription(root);
                case "Observation"        -> fhirObservationToLabObservation(root);
                case "Immunization"       -> fhirImmunizationToRecord(root);
                default -> throw new IllegalArgumentException(
                        "Unsupported FHIR resource type: " + sourceResourceType);
            };
        } catch (JsonProcessingException e) {
            throw new FhirTransformationException("Failed to parse FHIR resource", e);
        }
    }

    // ==================== Patient <-> FHIR Patient ====================

    private static ObjectNode patientToFhir(Patient p) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("resourceType", "Patient");
        root.put("id", p.patientId().toString());

        // Meta
        ObjectNode meta = root.putObject("meta");
        meta.put("versionId", "1");
        meta.put("lastUpdated", FHIR_DATETIME.format(p.registeredAt()));
        meta.put("profile", "http://hl7.org/fhir/StructureDefinition/Patient");

        // NHS identifier
        if (p.nhsId() != null) {
            ArrayNode identifiers = root.putArray("identifier");
            ObjectNode nhsId = identifiers.addObject();
            nhsId.put("use", "official");
            nhsId.putObject("type").putArray("coding")
                    .addObject()
                    .put("system", "http://terminology.hl7.org/CodeSystem/v2-0203")
                    .put("code", "NI")
                    .put("display", "National unique individual identifier");
            nhsId.put("system", PATIENT_SYSTEM + "/nhsid");
            nhsId.put("value", p.nhsId());
        }

        // Active flag
        root.put("active", p.active());

        // Name
        ArrayNode names = root.putArray("name");
        ObjectNode officialName = names.addObject();
        officialName.put("use", "official");
        officialName.put("family", p.lastName());
        officialName.putArray("given").add(p.firstName());

        // Phone
        if (p.primaryPhone() != null) {
            ArrayNode telecoms = root.putArray("telecom");
            ObjectNode phone = telecoms.addObject();
            phone.put("system", "phone");
            phone.put("value", p.primaryPhone());
            phone.put("use", "mobile");
        }

        // Email
        if (p.primaryEmail() != null) {
            ArrayNode telecoms = root.has("telecom")
                    ? (ArrayNode) root.get("telecom")
                    : root.putArray("telecom");
            ObjectNode email = telecoms.addObject();
            email.put("system", "email");
            email.put("value", p.primaryEmail());
        }

        // Gender
        if (p.gender() != null) {
            root.put("gender", p.gender().toLowerCase());
        }

        // DOB
        if (p.dateOfBirth() != null) {
            root.put("birthDate", FHIR_DATE.format(p.dateOfBirth()));
        }

        // Address
        if (p.address() != null || p.province() != null) {
            ArrayNode addresses = root.putArray("address");
            ObjectNode addr = addresses.addObject();
            addr.put("use", "home");
            addr.put("country", "NP");  // Nepal
            if (p.address() != null) addr.put("text", p.address());
            if (p.province() != null) addr.put("state", "Province " + p.province());
        }

        return root;
    }

    private static Patient fhirPatientToInternal(JsonNode root) {
        // Best-effort reverse mapping — many fields will be null
        String id = root.path("id").asText(null);
        java.util.UUID patientId = id != null ? parseUuidOrNull(id) : java.util.UUID.randomUUID();

        JsonNode nameNode = root.path("name").isArray() && root.path("name").size() > 0
                ? root.path("name").get(0) : MAPPER.createObjectNode();
        String lastName  = nameNode.path("family").asText(null);
        String firstName = nameNode.path("given").isArray() && nameNode.path("given").size() > 0
                ? nameNode.path("given").get(0).asText(null) : null;

        String birthDateStr = root.path("birthDate").asText(null);
        java.time.LocalDate dob = birthDateStr != null
                ? java.time.LocalDate.parse(birthDateStr) : null;

        String gender = root.path("gender").asText(null);
        boolean active = root.path("active").asBoolean(true);

        return new Patient(
                patientId != null ? patientId : java.util.UUID.randomUUID(),
                "unknown-tenant",  // caller must set tenant
                null,              // nhsId — look up from identifiers if available
                firstName != null ? firstName : "Unknown",
                lastName != null ? lastName : "Unknown",
                dob != null ? dob : java.time.LocalDate.now(),
                gender,
                null, null, null, null, null,
                com.ghatana.phr.healthcare.domain.DataClassification.C2,
                "fhir-import",
                java.time.Instant.now(),
                null,
                active
        );
    }

    // ==================== Prescription <-> FHIR MedicationRequest ====================

    private static ObjectNode prescriptionToFhirMedicationRequest(Prescription rx) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("resourceType", "MedicationRequest");
        root.put("id", rx.id());

        // Status
        root.put("status", fhirMedicationStatus(rx.status()));
        root.put("intent", "order");

        // Medication — coded reference
        ObjectNode medConcept = root.putObject("medicationCodeableConcept");
        ArrayNode coding = medConcept.putArray("coding");
        ObjectNode code = coding.addObject();
        code.put("system", RXNORM_SYSTEM);
        code.put("code", rx.medicationCode());
        code.put("display", rx.medicationName());
        medConcept.put("text", rx.medicationName());

        // Subject
        root.putObject("subject")
                .put("reference", "Patient/" + rx.patientId());

        // Requester (prescriber)
if (rx.prescriberId() != null) {
            root.putObject("requester")
                .put("reference", "Practitioner/" + rx.prescriberId());
        }

        // Authored on
        if (rx.prescribedAt() != null) {
            root.put("authoredOn", FHIR_DATETIME.format(rx.prescribedAt()));
        }

        // Dosage
        if (rx.dosage() != null) {
            ArrayNode dosage = root.putArray("dosageInstruction");
            dosage.addObject().put("text", rx.dosage());
        }

        // Supply / dispense
        ObjectNode dispense = root.putObject("dispenseRequest");
        dispense.put("numberOfRepeatsAllowed", Math.max(0, rx.refillsRemaining()));
        if (rx.expiresAt() != null) {
            dispense.putObject("validityPeriod")
                    .put("end", FHIR_DATETIME.format(rx.expiresAt()));
        }

        // Reason / indication
        if (rx.indication() != null) {
            root.putArray("reasonCode")
                    .addObject()
                    .put("text", rx.indication());
        }

        return root;
    }

    private static Prescription fhirMedicationRequestToPrescription(JsonNode root) {
        String id = root.path("id").asText(null);
        String patientId = extractRef(root.path("subject").path("reference").asText(""));
        String prescriberId = extractRef(root.path("requester").path("reference").asText(""));

        JsonNode medConcept = root.path("medicationCodeableConcept");
        JsonNode firstCoding = medConcept.path("coding").isArray() && medConcept.path("coding").size() > 0
                ? medConcept.path("coding").get(0) : MAPPER.createObjectNode();
        String medCode = firstCoding.path("code").asText(null);
        String medName = firstCoding.path("display").asText(
                medConcept.path("text").asText(null));

        String dosage = root.path("dosageInstruction").isArray()
                && root.path("dosageInstruction").size() > 0
                ? root.path("dosageInstruction").get(0).path("text").asText(null) : null;

        return new Prescription(
                id != null ? id : java.util.UUID.randomUUID().toString(),
                patientId,
                prescriberId,
                null,      // encounterId
                medCode,
                medName,
                dosage,
                null,      // indication
                java.time.Instant.now(),
                null,      // expiresAt
                0,         // refillsRemaining
                null,      // duration
                PrescriptionStatus.ACTIVE
        );
    }

    // ==================== LabObservation <-> FHIR Observation ====================

    private static ObjectNode labObservationToFhirObservation(LabObservation obs) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("resourceType", "Observation");
        root.put("id", obs.id());

        // Status
        root.put("status", obs.status().name().toLowerCase());

        // Category
        ArrayNode categories = root.putArray("category");
        categories.addObject().putArray("coding")
                .addObject()
                .put("system", "http://terminology.hl7.org/CodeSystem/observation-category")
                .put("code", "laboratory")
                .put("display", "Laboratory");

        // Code (LOINC)
        ObjectNode code = root.putObject("code");
        ArrayNode codeCoding = code.putArray("coding");
        ObjectNode loincCode = codeCoding.addObject();
        loincCode.put("system", LOINC_SYSTEM);
        loincCode.put("code", obs.loincCode());
        if (obs.loincDisplay() != null) {
            loincCode.put("display", obs.loincDisplay());
        }
        code.put("text", obs.testName());

        // Subject
        root.putObject("subject")
                .put("reference", "Patient/" + obs.patientId());

        // Encounter
        if (obs.encounterId() != null) {
            root.putObject("encounter")
                    .put("reference", "Encounter/" + obs.encounterId());
        }

        // Effective
        if (obs.orderedAt() != null) {
            root.put("effectiveDateTime", FHIR_DATETIME.format(obs.orderedAt()));
        }
        if (obs.resultedAt() != null) {
            root.put("issued", FHIR_DATETIME.format(obs.resultedAt()));
        }

        // Performer
        if (obs.performingLabId() != null) {
            root.putArray("performer")
                    .addObject()
                    .put("display", obs.performingLabId());
        }

        // Value
        if (obs.value() != null) {
            ObjectNode quantity = root.putObject("valueQuantity");
            quantity.put("value", obs.value().doubleValue());
            if (obs.unit() != null) {
                quantity.put("unit", obs.unit());
                quantity.put("system", UCUM_SYSTEM);
                quantity.put("code", obs.unit());
            }
        }

        // Interpretation
        if (obs.isAbnormal()) {
            root.putArray("interpretation")
                    .addObject()
                    .putArray("coding")
                    .addObject()
                    .put("system", "http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation")
                    .put("code", "A")
                    .put("display", "Abnormal");
        }

        // Reference range
        if (obs.referenceRange() != null) {
            root.putArray("referenceRange")
                    .addObject()
                    .put("text", obs.referenceRange());
        }

        // Note
        if (obs.notes() != null) {
            root.putArray("note").addObject().put("text", obs.notes());
        }

        return root;
    }

    private static LabObservation fhirObservationToLabObservation(JsonNode root) {
        String id = root.path("id").asText(null);
        String patientId = extractRef(root.path("subject").path("reference").asText(""));

        JsonNode firstCoding = root.path("code").path("coding").isArray()
                && root.path("code").path("coding").size() > 0
                ? root.path("code").path("coding").get(0) : MAPPER.createObjectNode();
        String loincCode = firstCoding.path("code").asText(null);
        String loincDisplay = firstCoding.path("display").asText(null);
        String testName = root.path("code").path("text").asText(loincDisplay);

        // Try to parse numeric value
        java.math.BigDecimal numericValue = null;
        String textValue = null;
        JsonNode quantity = root.path("valueQuantity");
        if (!quantity.isMissingNode()) {
            numericValue = new java.math.BigDecimal(quantity.path("value").asText("0"));
        } else {
            textValue = root.path("valueString").asText(null);
        }

        // Extract interpretation code from FHIR (e.g. "H", "L", "N", "A")
        String interpretationCode = null;
        JsonNode interpArray = root.path("interpretation");
        if (interpArray.isArray() && interpArray.size() > 0) {
            JsonNode firstInterp = interpArray.get(0);
            JsonNode coding = firstInterp.path("coding");
            if (coding.isArray() && coding.size() > 0) {
                interpretationCode = coding.get(0).path("code").asText(null);
            }
        }

        return new LabObservation(
                id != null ? id : java.util.UUID.randomUUID().toString(),
                patientId,
                null,         // encounterId
                null,         // orderId
                loincCode,
                loincDisplay,
                testName,
                numericValue,
                null,         // referenceRangeLow
                quantity.path("unit").asText(null),
                null,         // referenceRange
                null,         // performingLabId
                java.time.Instant.now(),  // orderedAt
                java.time.Instant.now(),  // resultedAt
                com.ghatana.phr.kernel.service.LabResultService.ObservationStatus.FINAL,
                null,         // notes
                interpretationCode
        );
    }

    // ==================== ImmunizationRecord <-> FHIR Immunization ====================

    private static ObjectNode immunizationRecordToFhirImmunization(ImmunizationRecord imm) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("resourceType", "Immunization");
        root.put("id", imm.id());

        // Status
        root.put("status", "completed");

        // Vaccine code (CVX)
        ObjectNode vaccineCode = root.putObject("vaccineCode");
        ArrayNode coding = vaccineCode.putArray("coding");
        ObjectNode cvx = coding.addObject();
        cvx.put("system", CVX_SYSTEM);
        cvx.put("code", imm.cvxCode());
        if (imm.vaccineName() != null) {
            cvx.put("display", imm.vaccineName());
        }
        vaccineCode.put("text", imm.vaccineName());

        // Patient
        root.putObject("patient")
                .put("reference", "Patient/" + imm.patientId());

        // Occurrence
        if (imm.administeredAt() != null) {
            root.put("occurrenceDateTime", FHIR_DATETIME.format(imm.administeredAt()));
        }

        // Recorded
        if (imm.recordedAt() != null) {
            root.put("recorded", FHIR_DATETIME.format(imm.recordedAt()));
        }

        // Primary source
        root.put("primarySource", true);

        // Performer
        if (imm.administeredBy() != null) {
            root.putArray("performer")
                    .addObject()
                    .putObject("actor")
                    .put("reference", "Practitioner/" + imm.administeredBy());
        }

        // Lot number and expiry
        if (imm.lotNumber() != null) {
            root.put("lotNumber", imm.lotNumber());
        }
        if (imm.expiresAt() != null) {
            root.put("expirationDate", FHIR_DATE.format(imm.expiresAt()));
        }

        // Route
        if (imm.route() != null) {
            root.putObject("route").putArray("coding")
                    .addObject()
                    .put("system", "http://terminology.hl7.org/CodeSystem/v3-RouteOfAdministration")
                    .put("code", imm.route());
        }

        // Protocol applied: series, dose number
        ObjectNode protocol = root.putArray("protocolApplied").addObject();
        if (imm.seriesName() != null) {
            protocol.put("series", imm.seriesName());
        }
        protocol.put("doseNumberPositiveInt", imm.doseNumber());

        // Notes
        if (imm.notes() != null) {
            root.putArray("note").addObject().put("text", imm.notes());
        }

        return root;
    }

    private static ImmunizationRecord fhirImmunizationToRecord(JsonNode root) {
        String id = root.path("id").asText(null);
        String patientId = extractRef(root.path("patient").path("reference").asText(""));

        JsonNode firstCoding = root.path("vaccineCode").path("coding").isArray()
                && root.path("vaccineCode").path("coding").size() > 0
                ? root.path("vaccineCode").path("coding").get(0) : MAPPER.createObjectNode();
        String cvxCode = firstCoding.path("code").asText(null);
        String vaccineName = firstCoding.path("display").asText(
                root.path("vaccineCode").path("text").asText(null));

        int doseNumber = 1;
        JsonNode protocol = root.path("protocolApplied");
        if (protocol.isArray() && protocol.size() > 0) {
            doseNumber = protocol.get(0).path("doseNumberPositiveInt").asInt(1);
        }

        return new ImmunizationRecord(
                id != null ? id : java.util.UUID.randomUUID().toString(),
                patientId,
                null,           // encounterId
                cvxCode,
                vaccineName,
                null,           // administeredBy
                java.time.Instant.now(),
                java.time.Instant.now(),
                null,           // lotNumber
                java.time.LocalDate.now().plusYears(5),  // expiresAt
                null,           // route
                null,           // seriesName
                doseNumber,
                false,          // adverseEvent
                null,           // notes
                com.ghatana.phr.kernel.service.ImmunizationService.ImmunizationStatus.ADMINISTERED
        );
    }

    // ==================== Utility ====================

    /**
     * Maps internal {@link PrescriptionStatus} to FHIR MedicationRequest status.
     */
    private static String fhirMedicationStatus(PrescriptionStatus status) {
        if (status == null) return "unknown";
        return switch (status) {
            case ACTIVE       -> "active";
            case COMPLETED    -> "completed";
            case DISCONTINUED -> "stopped";
            case EXPIRED      -> "completed";
        };
    }

    /**
     * Extracts the logical ID from a FHIR reference string like {@code "Patient/abc123"}.
     */
    private static String extractRef(String reference) {
        if (reference == null || !reference.contains("/")) return reference;
        return reference.substring(reference.lastIndexOf('/') + 1);
    }

    private static java.util.UUID parseUuidOrNull(String value) {
        try {
            return java.util.UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Signals a failure during FHIR transformation.
     *
     * @doc.type class
     * @doc.purpose Runtime exception for FHIR serialization / deserialization errors
     * @doc.layer product
     * @doc.pattern Exception
     */
    public static class FhirTransformationException extends ServiceException {
        public FhirTransformationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
