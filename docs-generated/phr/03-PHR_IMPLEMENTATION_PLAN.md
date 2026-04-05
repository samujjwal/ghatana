# PHR (Personal Health Records) - Detailed Implementation Plan

## Overview

**Product**: PHR Nepal (`products/phr`)  
**Current Status**: Alpha - Core Implementation Complete (~70% test coverage)  
**Target**: 100% test coverage, production-grade, HIPAA compliant, FHIR R4 certified  
**Timeline**: Week 5 of comprehensive implementation plan

---

## Current State Analysis

### Source Files Inventory (35 files in main src/)

#### AI Agents (3 files)

```
ai/agents/
├── LabAnomalyDetectionAgent.java
├── MedicationInteractionAgent.java
└── ReadmissionRiskAgent.java
```

#### API Layer (1 file)

```
api/
└── PatientController.java
```

#### FHIR Integration (4 files)

```
fhir/
├── FhirR4TransformationEngine.java
├── FhirResourceService.java
├── FhirTransformer.java
└── FhirValidator.java
```

#### Kernel Integration (17 files)

```
kernel/
├── PhrCapabilities.java
├── PhrKernelModule.java
├── consent/
│   ├── ConsentAccessDeniedException.java
│   └── ConsentService.java
├── data/
│   ├── PhrDataService.java
│   └── PhrPatientDataService.java
├── events/
│   └── PhrEventProcessor.java
├── module/
│   ├── AdministrativeServicesModule.java
│   ├── ClinicalServicesModule.java
│   ├── EmergencyServicesModule.java
│   └── PatientServicesModule.java
├── policy/
│   └── PhrDataClassification.java
├── retention/
│   ├── DeletionOutcome.java
│   ├── LegalHoldService.java
│   └── PatientDeletionWorkflow.java
└── service/
    ├── AppointmentService.java
    ├── BillingService.java
    ├── CaregiverService.java
    ├── ClinicalDecisionSupportService.java
    ├── ClinicalNoteService.java
    ├── ConsentManagementService.java
    ├── DocumentService.java
    ├── EmergencyAccessLogService.java
    ├── ImagingService.java
    ├── ImmunizationService.java
    ├── LabResultService.java
    ├── MedicationService.java
    ├── ReferralService.java
    └── TelemedicineService.java
```

#### Extensions (1 file)

```
extension/
└── HealthcareConsentKernelExtension.java
```

### Test Files Inventory (30 files)

#### Existing Tests

```
src/test/java/com/ghatana/phr/
├── ai/agents/
│   ├── LabAnomalyDetectionAgentTest.java
│   ├── MedicationInteractionAgentTest.java
│   └── ReadmissionRiskAgentTest.java
├── extension/
│   └── HealthcareConsentKernelExtensionTest.java
├── fhir/
│   ├── FhirR4ContractTest.java
│   └── FhirR4TransformationEngineTest.java
├── kernel/
│   ├── PhrKernelModuleTest.java
│   └── service/
│       ├── BillingServiceBenchmark.java
│       ├── BillingServiceTest.java
│       ├── CaregiverServiceTest.java
│       ├── ClinicalDecisionSupportServiceTest.java
│       ├── ClinicalNoteServiceTest.java
│       ├── ConsentManagementServiceTest.java
│       ├── EmergencyAccessLogServiceTest.java
│       ├── BillingServiceKernelPluginIT.java
│       ├── ImagingServiceTest.java
│       ├── ImmunizationServiceTest.java
│       ├── LabResultServiceTest.java
│       ├── MedicationServiceTest.java
│       ├── PhrTestInfrastructure.java
│       ├── ReferralServiceTest.java
│       └── TelemedicineServiceTest.java
├── observability/
│   └── PHRAuditTrailServiceTest.java
├── plugin/
│   ├── FhirInteropKernelPluginTest.java
│   └── PhrKernelPluginTest.java
├── security/
│   ├── BreachNotificationWorkflowTest.java
│   ├── PHRPrivacyManagerImplTest.java
│   ├── PHRSecurityIntegrationTest.java
│   └── PhiFieldEncryptionServiceTest.java
└── service/
    └── PatientServiceTest.java
```

### Coverage Gaps Identified

1. **FHIR Server Endpoint**: Planned but not implemented (Critical)
2. **Emergency Access**: Implemented but missing comprehensive load tests
3. **AI Agent Integration**: Unit tests exist but integration tests missing
4. **Clinical Decision Support**: Basic tests, needs comprehensive coverage
5. **Mobile API**: Planned but not implemented
6. **Nepal HIE Integration**: Planned but not implemented

---

## Implementation Tasks

### Week 5, Day 1: FHIR Server Endpoint Implementation (Critical)

**Status**: Currently marked as "Planned" - needs implementation  
**Priority**: Critical - API gap blocking production

#### 1. FHIR REST Server Implementation

**File**: `src/main/java/com/ghatana/phr/fhir/server/FhirRestServer.java`

```java
/**
 * @doc.type class
 * @doc.purpose FHIR R4 RESTful API server for PHR Nepal
 * @doc.layer product
 * @doc.pattern Service
 * @doc.compliance FHIR_R4, HIPAA, Nepal_Directive_2081
 */
public class FhirRestServer implements KernelModule {

    private final FhirResourceService resourceService;
    private final FhirValidator validator;
    private final PHRSecurityManager securityManager;
    private final AuditTrailService auditService;
    private HttpServer httpServer;

    @Inject
    public FhirRestServer(FhirResourceService resourceService,
                          FhirValidator validator,
                          PHRSecurityManager securityManager,
                          AuditTrailService auditService) {
        this.resourceService = resourceService;
        this.validator = validator;
        this.securityManager = securityManager;
        this.auditService = auditService;
    }

    @Override
    public Promise<Void> start() {
        return Promise.ofBlocking(() -> {
            httpServer = HttpServer.create(new InetSocketAddress(8080), 0);

            // Patient Resource Endpoints
            httpServer.createContext("/fhir/R4/Patient", this::handlePatientResource);
            httpServer.createContext("/fhir/R4/Observation", this::handleObservationResource);
            httpServer.createContext("/fhir/R4/Medication", this::handleMedicationResource);
            httpServer.createContext("/fhir/R4/Appointment", this::handleAppointmentResource);
            httpServer.createContext("/fhir/R4/DocumentReference", this::handleDocumentResource);
            httpServer.createContext("/fhir/R4/Consent", this::handleConsentResource);
            httpServer.createContext("/fhir/R4/Bundle", this::handleBundle);
            httpServer.createContext("/fhir/R4/metadata", this::handleMetadata);

            httpServer.setExecutor(Executors.newFixedThreadPool(50));
            httpServer.start();

            return null;
        });
    }

    private void handlePatientResource(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // Security check
        SecurityContext securityContext = securityManager.extractContext(exchange);
        if (!securityContext.isAuthenticated()) {
            sendError(exchange, 401, "Unauthorized");
            return;
        }

        try {
            switch (method) {
                case "GET":
                    if (path.matches("/fhir/R4/Patient/[^/]+")) {
                        handleReadPatient(exchange, path, securityContext);
                    } else if (path.contains("_history")) {
                        handlePatientHistory(exchange, path, securityContext);
                    } else {
                        handleSearchPatient(exchange, securityContext);
                    }
                    break;
                case "POST":
                    handleCreatePatient(exchange, securityContext);
                    break;
                case "PUT":
                    handleUpdatePatient(exchange, path, securityContext);
                    break;
                case "DELETE":
                    handleDeletePatient(exchange, path, securityContext);
                    break;
                case "PATCH":
                    handlePatchPatient(exchange, path, securityContext);
                    break;
                default:
                    sendError(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            log.error("FHIR operation failed", e);
            sendOperationOutcome(exchange, 500, e.getMessage());
        }
    }

    private void handleReadPatient(HttpExchange exchange, String path, SecurityContext ctx)
            throws IOException {
        String patientId = extractIdFromPath(path);

        // Check authorization
        if (!securityManager.canAccess(ctx, "Patient", "read", patientId)) {
            auditService.recordAccessDenied(ctx, "Patient", patientId);
            sendError(exchange, 403, "Forbidden");
            return;
        }

        // Retrieve patient
        Patient patient = runPromise(() -> resourceService.readPatient(patientId));

        if (patient == null) {
            sendError(exchange, 404, "Patient not found");
            return;
        }

        // Filter fields based on consent
        Patient filteredPatient = securityManager.filterByConsent(patient, ctx);

        // Audit success
        auditService.recordAccess(ctx, "Patient", patientId, "read");

        // Respond
        String json = FhirSerializer.toJson(filteredPatient);
        sendResponse(exchange, 200, json, "application/fhir+json");
    }

    private void handleCreatePatient(HttpExchange exchange, SecurityContext ctx)
            throws IOException {
        // Parse request body
        String body = readBody(exchange);
        Patient patient = FhirParser.parsePatient(body);

        // Validate
        ValidationResult validation = validator.validate(patient);
        if (!validation.isValid()) {
            sendOperationOutcome(exchange, 422, validation.errors());
            return;
        }

        // Check authorization
        if (!securityManager.canCreate(ctx, "Patient")) {
            auditService.recordCreateDenied(ctx, "Patient");
            sendError(exchange, 403, "Forbidden");
            return;
        }

        // Create patient
        Patient created = runPromise(() -> resourceService.createPatient(patient));

        // Audit
        auditService.recordCreate(ctx, "Patient", created.getId());

        // Respond with 201 Created
        String json = FhirSerializer.toJson(created);
        exchange.getResponseHeaders().set("Location", "/fhir/R4/Patient/" + created.getId());
        sendResponse(exchange, 201, json, "application/fhir+json");
    }

    private void handleSearchPatient(HttpExchange exchange, SecurityContext ctx)
            throws IOException {
        Map<String, String> params = parseQueryParameters(exchange.getRequestURI());

        // Validate search parameters
        SearchValidation validation = validator.validatePatientSearch(params);
        if (!validation.isValid()) {
            sendOperationOutcome(exchange, 400, validation.errors());
            return;
        }

        // Build and execute search
        PatientSearchRequest searchRequest = PatientSearchRequest.builder()
            .name(params.get("name"))
            .birthDate(params.get("birthdate"))
            .identifier(params.get("identifier"))
            .gender(params.get("gender"))
            .phone(params.get("phone"))
            .email(params.get("email"))
            .address(params.get("address"))
            .generalPractitioner(params.get("general-practitioner"))
            .organization(params.get("organization"))
            .active(parseBoolean(params.get("active")))
            .lastUpdated(params.get("_lastUpdated"))
            .count(parseInt(params.get("_count"), 20))
            .offset(parseInt(params.get("_offset"), 0))
            .build();

        // Apply tenant scoping
        searchRequest = searchRequest.withTenantId(ctx.getTenantId());

        PatientSearchResult result = runPromise(() ->
            resourceService.searchPatients(searchRequest)
        );

        // Filter results by consent
        List<Patient> filtered = result.patients().stream()
            .map(p -> securityManager.filterByConsent(p, ctx))
            .toList();

        // Build Bundle response
        Bundle bundle = Bundle.builder()
            .type(BundleType.SEARCHSET)
            .total(result.total())
            .link(buildPaginationLinks(params, result))
            .entry(filtered.stream()
                .map(p -> BundleEntry.builder()
                    .fullUrl("/fhir/R4/Patient/" + p.getId())
                    .resource(p)
                    .search(BundleEntrySearch.builder()
                        .mode(SearchMatchMode.MATCH)
                        .score(1.0)
                        .build())
                    .build())
                .toList())
            .build();

        // Audit
        auditService.recordSearch(ctx, "Patient", params, filtered.size());

        String json = FhirSerializer.toJson(bundle);
        sendResponse(exchange, 200, json, "application/fhir+json");
    }

    private void handleBundle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if (!"POST".equals(method)) {
            sendError(exchange, 405, "Method Not Allowed");
            return;
        }

        SecurityContext ctx = securityManager.extractContext(exchange);

        String body = readBody(exchange);
        Bundle bundle = FhirParser.parseBundle(body);

        // Validate bundle
        ValidationResult validation = validator.validateBundle(bundle);
        if (!validation.isValid()) {
            sendOperationOutcome(exchange, 422, validation.errors());
            return;
        }

        // Process based on bundle type
        Bundle resultBundle;
        switch (bundle.type()) {
            case TRANSACTION:
                resultBundle = processTransaction(bundle, ctx);
                break;
            case BATCH:
                resultBundle = processBatch(bundle, ctx);
                break;
            default:
                sendOperationOutcome(exchange, 400,
                    "Bundle type " + bundle.type() + " not supported for POST");
                return;
        }

        String json = FhirSerializer.toJson(resultBundle);
        sendResponse(exchange, 200, json, "application/fhir+json");
    }

    private Bundle processTransaction(Bundle requestBundle, SecurityContext ctx) {
        // Transaction processing - all succeed or all fail (atomic)
        List<BundleEntry> responseEntries = new ArrayList<>();

        // Validate all entries first
        for (BundleEntry entry : requestBundle.entry()) {
            if (entry.request() == null) {
                throw new FhirException("Bundle entry missing request");
            }
        }

        // Process entries
        for (BundleEntry entry : requestBundle.entry()) {
            BundleEntryResponse response = processEntry(entry, ctx);
            responseEntries.add(BundleEntry.builder()
                .response(response)
                .build());
        }

        return Bundle.builder()
            .type(BundleType.TRANSACTION_RESPONSE)
            .entry(responseEntries)
            .build();
    }

    private BundleEntryResponse processEntry(BundleEntry entry, SecurityContext ctx) {
        BundleEntryRequest request = entry.request();
        String method = request.method();
        String url = request.url();

        try {
            switch (method) {
                case "POST":
                    return processCreateEntry(entry, ctx);
                case "PUT":
                    return processUpdateEntry(entry, ctx);
                case "DELETE":
                    return processDeleteEntry(entry, ctx);
                case "GET":
                    return processReadEntry(entry, ctx);
                default:
                    return BundleEntryResponse.builder()
                        .status("400")
                        .outcome(createOperationOutcome("Method not supported: " + method))
                        .build();
            }
        } catch (Exception e) {
            return BundleEntryResponse.builder()
                .status("500")
                .outcome(createOperationOutcome(e.getMessage()))
                .build();
        }
    }

    private BundleEntryResponse processCreateEntry(BundleEntry entry, SecurityContext ctx) {
        Resource resource = entry.resource();
        String resourceType = resource.getResourceType();

        Resource created;
        switch (resourceType) {
            case "Patient":
                created = runPromise(() -> resourceService.createPatient((Patient) resource));
                break;
            case "Observation":
                created = runPromise(() -> resourceService.createObservation((Observation) resource));
                break;
            case "Medication":
                created = runPromise(() -> resourceService.createMedication((Medication) resource));
                break;
            // ... other resource types
            default:
                throw new FhirException("Resource type not supported: " + resourceType);
        }

        auditService.recordCreate(ctx, resourceType, created.getId());

        return BundleEntryResponse.builder()
            .status("201")
            .location(resourceType + "/" + created.getId())
            .etag("W/\"" + created.getMeta().versionId() + "\"")
            .lastModified(created.getMeta().lastUpdated().toString())
            .build();
    }

    private void handleMetadata(HttpExchange exchange) throws IOException {
        CapabilityStatement capabilities = CapabilityStatement.builder()
            .status(PublicationStatus.ACTIVE)
            .date(Date.from(Instant.now()))
            .kind(CapabilityStatementKind.INSTANCE)
            .fhirVersion("4.0.1")
            .format(List.of("json", "xml"))
            .rest(List.of(Rest.builder()
                .mode(RestMode.SERVER)
                .resource(buildResourceCapabilities())
                .build()))
            .build();

        String json = FhirSerializer.toJson(capabilities);
        sendResponse(exchange, 200, json, "application/fhir+json");
    }

    private List<ResourceCapability> buildResourceCapabilities() {
        return List.of(
            ResourceCapability.builder()
                .type("Patient")
                .profile("http://hl7.org/fhir/StructureDefinition/Patient")
                .interaction(List.of(
                    buildInteraction(TypeRestfulInteraction.READ),
                    buildInteraction(TypeRestfulInteraction.VREAD),
                    buildInteraction(TypeRestfulInteraction.CREATE),
                    buildInteraction(TypeRestfulInteraction.UPDATE),
                    buildInteraction(TypeRestfulInteraction.DELETE),
                    buildInteraction(TypeRestfulInteraction.SEARCH_TYPE),
                    buildInteraction(TypeRestfulInteraction.HISTORY_INSTANCE),
                    buildInteraction(TypeRestfulInteraction.HISTORY_TYPE)
                ))
                .searchParam(buildPatientSearchParams())
                .build(),
            ResourceCapability.builder()
                .type("Observation")
                .profile("http://hl7.org/fhir/StructureDefinition/Observation")
                .interaction(List.of(
                    buildInteraction(TypeRestfulInteraction.READ),
                    buildInteraction(TypeRestfulInteraction.CREATE),
                    buildInteraction(TypeRestfulInteraction.UPDATE),
                    buildInteraction(TypeRestfulInteraction.SEARCH_TYPE)
                ))
                .build()
            // ... other resources
        );
    }

    private void sendResponse(HttpExchange exchange, int status, String body, String contentType)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendOperationOutcome(exchange, status, List.of(message));
    }

    private void sendOperationOutcome(HttpExchange exchange, int status, List<String> errors)
            throws IOException {
        OperationOutcome outcome = OperationOutcome.builder()
            .issue(errors.stream()
                .map(e -> OperationOutcomeIssue.builder()
                    .severity(IssueSeverity.ERROR)
                    .code(IssueType.EXCEPTION)
                    .diagnostics(e)
                    .build())
                .toList())
            .build();

        String json = FhirSerializer.toJson(outcome);
        sendResponse(exchange, status, json, "application/fhir+json");
    }

    @Override
    public Promise<Void> stop() {
        return Promise.ofBlocking(() -> {
            if (httpServer != null) {
                httpServer.stop(0);
            }
            return null;
        });
    }
}
```

#### 2. FHIR Server Endpoint Tests

**File**: `src/test/java/com/ghatana/phr/fhir/server/FhirServerEndpointTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Validates FHIR R4 REST server endpoint compliance
 * @doc.layer product
 * @doc.domain FHIR
 * @doc.compliance FHIR_R4
 */
@DisplayName("FHIR R4 Server Endpoint Tests")
class FhirServerEndpointTest extends EventloopTestBase {

    private FhirRestServer server;
    private HttpClient httpClient;
    private MockPHRSecurityManager mockSecurity;
    private MockAuditTrailService mockAudit;

    @BeforeEach
    void setup() {
        FhirResourceService resourceService = new MockFhirResourceService();
        FhirValidator validator = new FhirValidator();
        mockSecurity = new MockPHRSecurityManager();
        mockAudit = new MockAuditTrailService();

        server = new FhirRestServer(resourceService, validator, mockSecurity, mockAudit);
        httpClient = HttpClient.newHttpClient();

        runPromise(() -> server.start()).assertComplete();
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testCreatePatient() throws Exception {
        Patient patient = Patient.builder()
            .identifier(List.of(Identifier.builder()
                .system("http://phr.nepal.gov/id")
                .value("P12345")
                .build()))
            .name(List.of(HumanName.builder()
                .family("Sharma")
                .given(List.of("Ram"))
                .build()))
            .gender(AdministrativeGender.MALE)
            .birthDate(Date.valueOf("1980-05-15"))
            .build();

        String json = FhirSerializer.toJson(patient);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/Patient"))
            .header("Content-Type", "application/fhir+json")
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).isPresent();

        Patient created = FhirParser.parsePatient(response.body());
        assertThat(created.getId()).isNotNull();
        assertThat(created.getMeta().versionId()).isEqualTo("1");

        // Verify audit
        assertThat(mockAudit.getRecordedCreates()).hasSize(1);
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testReadPatient() throws Exception {
        String patientId = "patient-123";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/Patient/" + patientId))
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);

        Patient patient = FhirParser.parsePatient(response.body());
        assertThat(patient.getId()).isEqualTo(patientId);

        // Verify audit
        assertThat(mockAudit.getRecordedAccesses()).anyMatch(a ->
            a.resourceType().equals("Patient") && a.resourceId().equals(patientId)
        );
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testReadPatientNotFound() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/Patient/non-existent"))
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);

        OperationOutcome outcome = FhirParser.parseOperationOutcome(response.body());
        assertThat(outcome.issue()).anyMatch(i ->
            i.diagnostics().contains("not found")
        );
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testSearchPatientByName() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/Patient?name=Sharma"))
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);

        Bundle bundle = FhirParser.parseBundle(response.body());
        assertThat(bundle.type()).isEqualTo(BundleType.SEARCHSET);
        assertThat(bundle.entry()).isNotEmpty();
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testUpdatePatient() throws Exception {
        String patientId = "patient-123";

        Patient patient = Patient.builder()
            .id(patientId)
            .meta(Meta.builder().versionId("1").build())
            .name(List.of(HumanName.builder()
                .family("Sharma")
                .given(List.of("Ram", "Prasad"))
                .build()))
            .build();

        String json = FhirSerializer.toJson(patient);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/Patient/" + patientId))
            .header("Content-Type", "application/fhir+json")
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);

        Patient updated = FhirParser.parsePatient(response.body());
        assertThat(updated.getMeta().versionId()).isEqualTo("2");
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testDeletePatient() throws Exception {
        String patientId = "patient-to-delete";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/Patient/" + patientId))
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .DELETE()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(204);
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testPatientHistory() throws Exception {
        String patientId = "patient-123";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/Patient/" + patientId + "/_history"))
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);

        Bundle history = FhirParser.parseBundle(response.body());
        assertThat(history.type()).isEqualTo(BundleType.HISTORY);
        assertThat(history.entry()).isNotEmpty();
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch, path
     */
    @Test
    void testTransactionBundle() throws Exception {
        // Create transaction bundle
        Bundle bundle = Bundle.builder()
            .type(BundleType.TRANSACTION)
            .entry(List.of(
                BundleEntry.builder()
                    .fullUrl("urn:uuid:patient-1")
                    .resource(Patient.builder()
                        .name(List.of(HumanName.builder()
                            .family("Gurung")
                            .given(List.of("Sita"))
                            .build()))
                        .build())
                    .request(BundleEntryRequest.builder()
                        .method(HTTPVerb.POST)
                        .url("Patient")
                        .build())
                    .build(),
                BundleEntry.builder()
                    .resource(Observation.builder()
                        .status(ObservationStatus.FINAL)
                        .code(CodeableConcept.builder()
                            .coding(List.of(Coding.builder()
                                .system("http://loinc.org")
                                .code("8302-2")
                                .display("Body Height")
                                .build()))
                            .build())
                        .subject(Reference.builder()
                            .reference("urn:uuid:patient-1")
                            .build())
                        .valueQuantity(Quantity.builder()
                            .value(165.0)
                            .unit("cm")
                            .build())
                        .build())
                    .request(BundleEntryRequest.builder()
                        .method(HTTPVerb.POST)
                        .url("Observation")
                        .build())
                    .build()
            ))
            .build();

        String json = FhirSerializer.toJson(bundle);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/"))
            .header("Content-Type", "application/fhir+json")
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);

        Bundle result = FhirParser.parseBundle(response.body());
        assertThat(result.type()).isEqualTo(BundleType.TRANSACTION_RESPONSE);
        assertThat(result.entry()).hasSize(2);
        assertThat(result.entry().get(0).response().status()).isEqualTo("201");
        assertThat(result.entry().get(1).response().status()).isEqualTo("201");
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line
     */
    @Test
    void testMetadataEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/metadata"))
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);

        CapabilityStatement capabilities = FhirParser.parseCapabilityStatement(response.body());
        assertThat(capabilities.fhirVersion()).isEqualTo("4.0.1");
        assertThat(capabilities.rest()).isNotEmpty();
        assertThat(capabilities.rest().get(0).resource()).isNotEmpty();
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testUnauthorizedAccess() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/Patient/patient-123"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testForbiddenAccess() throws Exception {
        String patientId = "patient-from-other-tenant";
        mockSecurity.setupForbiddenAccess(patientId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/Patient/" + patientId))
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
    }

    /**
     * @doc.test_type negative
     * @doc.coverage branch
     */
    @Test
    void testInvalidResourceValidation() throws Exception {
        // Patient with missing required fields
        Patient invalidPatient = Patient.builder()
            // No name - required field
            .gender(AdministrativeGender.MALE)
            .build();

        String json = FhirSerializer.toJson(invalidPatient);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/fhir/R4/Patient"))
            .header("Content-Type", "application/fhir+json")
            .header("Authorization", "Bearer " + mockSecurity.getValidToken())
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(422);

        OperationOutcome outcome = FhirParser.parseOperationOutcome(response.body());
        assertThat(outcome.issue()).anyMatch(i ->
            i.diagnostics().toLowerCase().contains("name") ||
            i.diagnostics().toLowerCase().contains("required")
        );
    }

    @AfterEach
    void cleanup() {
        runPromise(() -> server.stop()).assertComplete();
    }
}
```

---

### Week 5, Day 2: Emergency Access Comprehensive Tests

**File**: `src/test/java/com/ghatana/phr/emergency/EmergencyAccessComprehensiveTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Comprehensive tests for emergency break-glass access workflows
 * @doc.layer product
 * @doc.domain Security
 * @doc.compliance HIPAA, Nepal_Directive_2081
 */
@DisplayName("Emergency Access Comprehensive Tests")
class EmergencyAccessComprehensiveTest extends EventloopTestBase {

    private EmergencyAccessService emergencyService;
    private PatientRecordService patientService;
    private AuditTrailService auditService;
    private ConsentManagementService consentService;

    @BeforeEach
    void setup() {
        emergencyService = new EmergencyAccessService();
        patientService = new MockPatientRecordService();
        auditService = new MockAuditTrailService();
        consentService = new MockConsentManagementService();

        emergencyService.setDependencies(patientService, auditService, consentService);
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testBreakGlassRequestFlow() {
        String patientId = "emergency-patient-123";
        String requestingProvider = "provider-dr-sharma";
        String emergencyReason = "Patient unconscious, no ID available";

        EmergencyAccessRequest request = EmergencyAccessRequest.builder()
            .patientId(patientId)
            .requestingProviderId(requestingProvider)
            .emergencyReason(emergencyReason)
            .justification(EmergencyJustification.PATIENT_UNCONSCIOUS)
            .timestamp(Instant.now())
            .build();

        EmergencyAccessResult result = runPromise(() ->
            emergencyService.requestEmergencyAccess(request)
        );

        assertThat(result.granted()).isTrue();
        assertThat(result.accessToken()).isNotNull();
        assertThat(result.expirationTime()).isBefore(Instant.now().plus(Duration.ofHours(5)));

        // Verify audit trail
        List<AuditEvent> events = auditService.findEvents("emergency.access.requested");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data().get("reason")).isEqualTo(emergencyReason);
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testDualAuthorizationRequirement() {
        // First provider requests emergency access
        String provider1 = "provider-dr-sharma";
        String provider2 = "provider-dr-gurung";
        String patientId = "emergency-patient-456";

        EmergencyAccessRequest request1 = EmergencyAccessRequest.builder()
            .patientId(patientId)
            .requestingProviderId(provider1)
            .emergencyReason("Life-threatening emergency")
            .justification(EmergencyJustification.LIFE_THREATENING)
            .build();

        EmergencyAccessResult result1 = runPromise(() ->
            emergencyService.requestEmergencyAccess(request1)
        );

        // First provider gets preliminary access, needs second authorization
        assertThat(result1.granted()).isTrue();
        assertThat(result1.requiresSecondAuthorization()).isTrue();
        assertThat(result1.fullyAuthorized()).isFalse();

        // Second provider authorizes
        SecondAuthorization auth2 = SecondAuthorization.builder()
            .originalRequestId(result1.requestId())
            .authorizingProviderId(provider2)
            .authorizationReason("Confirmed life-threatening condition")
            .build();

        EmergencyAccessResult result2 = runPromise(() ->
            emergencyService.provideSecondAuthorization(auth2)
        );

        assertThat(result2.fullyAuthorized()).isTrue();
        assertThat(result2.accessLevel()).isEqualTo(AccessLevel.FULL);
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testEmergencyAccessTimeLimit() {
        String patientId = "emergency-patient-789";

        EmergencyAccessRequest request = EmergencyAccessRequest.builder()
            .patientId(patientId)
            .requestingProviderId("provider-dr-rai")
            .emergencyReason("Severe allergic reaction")
            .build();

        EmergencyAccessResult result = runPromise(() ->
            emergencyService.requestEmergencyAccess(request)
        );

        assertThat(result.granted()).isTrue();

        // Access should expire after 4 hours
        Instant expiration = result.expirationTime();
        Instant now = Instant.now();
        Duration duration = Duration.between(now, expiration);

        assertThat(duration.toHours()).isEqualTo(4);

        // Simulate time passing
        clock.advance(Duration.ofHours(5));

        // Try to use expired token
        assertThatThrownBy(() ->
            runPromise(() -> emergencyService.validateAccess(result.accessToken()))
        )
            .isInstanceOf(AccessExpiredException.class)
            .hasMessageContaining("Emergency access has expired");
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testEmergencyAccessScopeLimitation() {
        String patientId = "emergency-patient-321";

        EmergencyAccessRequest request = EmergencyAccessRequest.builder()
            .patientId(patientId)
            .requestingProviderId("provider-dr-poudel")
            .emergencyReason("Emergency surgery preparation")
            .justification(EmergencyJustification.EMERGENCY_SURGERY)
            .build();

        EmergencyAccessResult result = runPromise(() ->
            emergencyService.requestEmergencyAccess(request)
        );

        assertThat(result.granted()).isTrue();
        // Should have limited scope for surgery context
        assertThat(result.allowedResourceTypes()).containsExactlyInAnyOrder(
            "Patient", "Observation", "AllergyIntolerance", "MedicationStatement"
        );

        // Should not have access to billing, mental health, etc.
        assertThat(result.allowedResourceTypes()).doesNotContain(
            "Claim", "Invoice", "QuestionnaireResponse"
        );
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testEmergencyAccessAuditTrail() {
        String patientId = "emergency-patient-654";
        String providerId = "provider-dr-adhikari";

        EmergencyAccessRequest request = EmergencyAccessRequest.builder()
            .patientId(patientId)
            .requestingProviderId(providerId)
            .emergencyReason("Cardiac arrest")
            .build();

        EmergencyAccessResult result = runPromise(() ->
            emergencyService.requestEmergencyAccess(request)
        );

        // Access patient record
        runPromise(() -> patientService.getRecords(patientId, result.accessToken()))
            .assertComplete();

        // Verify comprehensive audit trail
        List<AuditEvent> events = auditService.findEventsForPatient(patientId);

        assertThat(events).anyMatch(e ->
            e.type().equals("emergency.access.requested")
        );
        assertThat(events).anyMatch(e ->
            e.type().equals("emergency.access.granted")
        );
        assertThat(events).anyMatch(e ->
            e.type().equals("patient.records.accessed") &&
            e.data().containsKey("emergency_access")
        );
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testEmergencyAccessNotification() {
        String patientId = "emergency-patient-987";
        String providerId = "provider-dr-bhandari";

        NotificationService mockNotification = mock(NotificationService.class);
        emergencyService.setNotificationService(mockNotification);

        EmergencyAccessRequest request = EmergencyAccessRequest.builder()
            .patientId(patientId)
            .requestingProviderId(providerId)
            .emergencyReason("Trauma from accident")
            .build();

        runPromise(() -> emergencyService.requestEmergencyAccess(request)).assertComplete();

        // Verify notifications sent
        verify(mockNotification).notifyComplianceTeam(argThat(notification ->
            notification.type() == NotificationType.EMERGENCY_ACCESS &&
            notification.patientId().equals(patientId) &&
            notification.providerId().equals(providerId)
        ));

        verify(mockNotification).notifyPatient(argThat(notification ->
            notification.type() == NotificationType.EMERGENCY_ACCESS_NOTICE &&
            notification.patientId().equals(patientId)
        ), eq(Duration.ofHours(24))); // Delayed notification
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testEmergencyAccessRevocation() {
        String patientId = "emergency-patient-147";

        EmergencyAccessRequest request = EmergencyAccessRequest.builder()
            .patientId(patientId)
            .requestingProviderId("provider-dr-sharma")
            .emergencyReason("Critical condition")
            .build();

        EmergencyAccessResult result = runPromise(() ->
            emergencyService.requestEmergencyAccess(request)
        );

        // Revoke access
        RevocationRequest revocation = RevocationRequest.builder()
            .accessToken(result.accessToken())
            .revokingProviderId("provider-dr-admin")
            .reason("Emergency resolved, normal access restored")
            .build();

        RevocationResult revokeResult = runPromise(() ->
            emergencyService.revokeAccess(revocation)
        );

        assertThat(revokeResult.revoked()).isTrue();

        // Verify access no longer works
        assertThatThrownBy(() ->
            runPromise(() -> emergencyService.validateAccess(result.accessToken()))
        )
            .isInstanceOf(AccessRevokedException.class);
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testEmergencyAccessReportGeneration() {
        String patientId = "emergency-patient-258";
        String providerId = "provider-dr-thapa";

        EmergencyAccessRequest request = EmergencyAccessRequest.builder()
            .patientId(patientId)
            .requestingProviderId(providerId)
            .emergencyReason("Stroke symptoms")
            .build();

        EmergencyAccessResult result = runPromise(() ->
            emergencyService.requestEmergencyAccess(request)
        );

        // Access various records
        runPromise(() -> patientService.getDemographics(patientId, result.accessToken()));
        runPromise(() -> patientService.getLabResults(patientId, result.accessToken()));
        runPromise(() -> patientService.getMedications(patientId, result.accessToken()));

        // Generate post-emergency report
        EmergencyAccessReport report = runPromise(() ->
            emergencyService.generateAccessReport(result.requestId())
        );

        assertThat(report.requestId()).isEqualTo(result.requestId());
        assertThat(report.patientId()).isEqualTo(patientId);
        assertThat(report.requestingProvider()).isEqualTo(providerId);
        assertThat(report.emergencyReason()).isEqualTo("Stroke symptoms");
        assertThat(report.accessedResources()).hasSize(3);
        assertThat(report.accessDuration()).isPositive();
        assertThat(report.complianceReviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    /**
     * @doc.test_type stress
     * @doc.coverage path
     */
    @Test
    void testConcurrentEmergencyAccessRequests() {
        String patientId = "emergency-patient-concurrent";
        int concurrentRequests = 50;

        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<EmergencyAccessResult>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentRequests; i++) {
            final int providerIndex = i;
            Future<EmergencyAccessResult> future = executor.submit(() -> {
                try {
                    EmergencyAccessRequest request = EmergencyAccessRequest.builder()
                        .patientId(patientId)
                        .requestingProviderId("provider-" + providerIndex)
                        .emergencyReason("Emergency " + providerIndex)
                        .build();

                    return runPromise(() -> emergencyService.requestEmergencyAccess(request));
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        awaitLatch(latch, 30, TimeUnit.SECONDS);

        // All requests should succeed (each gets own token)
        assertThat(futures).allMatch(f -> {
            try {
                EmergencyAccessResult result = f.get(5, TimeUnit.SECONDS);
                return result.granted() && result.accessToken() != null;
            } catch (Exception e) {
                return false;
            }
        });

        executor.shutdown();
    }

    /**
     * @doc.test_type stress
     * @doc.coverage path
     */
    @Test
    void testEmergencyAccessUnderLoad() {
        // Simulate emergency department load
        int patients = 100;
        int providers = 20;

        List<EmergencyAccessResult> results = IntStream.range(0, patients)
            .parallel()
            .mapToObj(i -> {
                EmergencyAccessRequest request = EmergencyAccessRequest.builder()
                    .patientId("patient-" + i)
                    .requestingProviderId("provider-" + (i % providers))
                    .emergencyReason("Load test emergency")
                    .build();

                return runPromise(() -> emergencyService.requestEmergencyAccess(request));
            })
            .toList();

        // All should succeed
        assertThat(results).allMatch(EmergencyAccessResult::granted);

        // Verify audit trail has all events
        assertThat(auditService.getEventCount("emergency.access.requested"))
            .isEqualTo(patients);
    }

    private void awaitLatch(CountDownLatch latch, long timeout, TimeUnit unit) {
        try {
            assertThat(latch.await(timeout, unit)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        }
    }
}
```

---

### Week 5, Day 3: AI Agent Integration Tests

**File**: `src/test/java/com/ghatana/phr/ai/AIAgentIntegrationTest.java`

```java
/**
 * @doc.type test
 * @doc.purpose Integration tests for AI agents with clinical workflow
 * @doc.layer product
 * @doc.domain AI
 */
@DisplayName("AI Agent Integration Tests")
class AIAgentIntegrationTest extends EventloopTestBase {

    private AgentOrchestrator orchestrator;
    private LabAnomalyDetectionAgent labAgent;
    private MedicationInteractionAgent medAgent;
    private ReadmissionRiskAgent readmissionAgent;
    private ClinicalDecisionSupportService cdss;

    @BeforeEach
    void setup() {
        orchestrator = new AgentOrchestrator();
        labAgent = new LabAnomalyDetectionAgent();
        medAgent = new MedicationInteractionAgent();
        readmissionAgent = new ReadmissionRiskAgent();
        cdss = new ClinicalDecisionSupportService(orchestrator);

        orchestrator.registerAgent(labAgent);
        orchestrator.registerAgent(medAgent);
        orchestrator.registerAgent(readmissionAgent);
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testLabAnomalyDetectionWithHistoricalData() {
        // Patient with historical lab values
        String patientId = "patient-lab-001";

        // Historical baseline
        List<LabResult> history = List.of(
            LabResult.builder()
                .testCode("GLUCOSE")
                .value(95.0)
                .unit("mg/dL")
                .timestamp(Instant.now().minus(30, ChronoUnit.DAYS))
                .build(),
            LabResult.builder()
                .testCode("GLUCOSE")
                .value(98.0)
                .unit("mg/dL")
                .timestamp(Instant.now().minus(15, ChronoUnit.DAYS))
                .build()
        );

        // Current abnormal value
        LabResult current = LabResult.builder()
            .patientId(patientId)
            .testCode("GLUCOSE")
            .value(180.0) // Significantly elevated
            .unit("mg/dL")
            .referenceRange("70-100")
            .timestamp(Instant.now())
            .build();

        LabAnomalyResult result = runPromise(() ->
            labAgent.analyze(current, history)
        );

        assertThat(result.anomalyDetected()).isTrue();
        assertThat(result.severity()).isEqualTo(AnomalySeverity.HIGH);
        assertThat(result.deviationFromBaseline()).isGreaterThan(80.0);
        assertThat(result.recommendation()).contains("Follow-up");
        assertThat(result.confidence()).isGreaterThan(0.85);
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testLabAnomalyAlertGeneration() {
        LabResult criticalResult = LabResult.builder()
            .patientId("patient-lab-002")
            .testCode("POTASSIUM")
            .value(6.5) // Critical high
            .unit("mEq/L")
            .referenceRange("3.5-5.0")
            .timestamp(Instant.now())
            .build();

        LabAnomalyResult result = runPromise(() -> labAgent.analyze(criticalResult, List.of()));

        assertThat(result.anomalyDetected()).isTrue();
        assertThat(result.severity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(result.requiresImmediateAttention()).isTrue();

        // Verify alert would be generated
        ClinicalAlert alert = result.toClinicalAlert();
        assertThat(alert.priority()).isEqualTo(AlertPriority.CRITICAL);
        assertThat(alert.notificationChannels()).contains(NotificationChannel.PAGE);
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testMedicationInteractionDrugDrug() {
        // Patient on Warfarin
        Medication warfarin = Medication.builder()
            .code("warfarin")
            .name("Warfarin")
            .dosage("5mg daily")
            .build();

        // New prescription: Aspirin
        Medication aspirin = Medication.builder()
            .code("aspirin")
            .name("Aspirin")
            .dosage("81mg daily")
            .build();

        InteractionResult result = runPromise(() ->
            medAgent.checkInteraction(warfarin, aspirin)
        );

        assertThat(result.hasInteraction()).isTrue();
        assertThat(result.interactionType()).isEqualTo(InteractionType.DRUG_DRUG);
        assertThat(result.severity()).isEqualTo(Severity.MAJOR);
        assertThat(result.mechanism()).contains("anticoagulant");
        assertThat(result.recommendation()).isNotEmpty();
        assertThat(result.alternatives()).isNotEmpty();
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testMedicationInteractionDrugAllergy() {
        // Patient with known allergy
        Allergy allergy = Allergy.builder()
            .substance("Penicillin")
            .reaction("Anaphylaxis")
            .severity(AllergySeverity.SEVERE)
            .build();

        // Prescribed medication
        Medication amoxicillin = Medication.builder()
            .code("amoxicillin")
            .name("Amoxicillin")
            .build();

        InteractionResult result = runPromise(() ->
            medAgent.checkAllergyInteraction(amoxicillin, List.of(allergy))
        );

        assertThat(result.hasInteraction()).isTrue();
        assertThat(result.interactionType()).isEqualTo(InteractionType.DRUG_ALLERGY);
        assertThat(result.severity()).isEqualTo(Severity.CONTRAINDICATED);
        assertThat(result.requiresIntervention()).isTrue();
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testReadmissionRiskScoring() {
        // Patient with risk factors
        PatientProfile profile = PatientProfile.builder()
            .patientId("patient-risk-001")
            .age(78)
            .hasDiabetes(true)
            .hasHeartFailure(true)
            .previousReadmissions(2)
            .lastAdmissionDate(Instant.now().minus(45, ChronoUnit.DAYS))
            .medicationAdherenceScore(0.6)
            .socialSupportLevel(SocialSupportLevel.LOW)
            .build();

        ReadmissionRiskResult result = runPromise(() ->
            readmissionAgent.assessRisk(profile)
        );

        assertThat(result.riskScore()).isGreaterThan(0.7); // High risk
        assertThat(result.riskCategory()).isEqualTo(RiskCategory.HIGH);
        assertThat(result.timeWindow()).isEqualTo(Duration.ofDays(30));

        // Risk factors identified
        assertThat(result.riskFactors()).contains("Age > 75", "Heart Failure", "Previous Readmissions");

        // Interventions suggested
        assertThat(result.recommendedInterventions()).isNotEmpty();
        assertThat(result.recommendedInterventions()).anyMatch(i ->
            i.type() == InterventionType.CARE_COORDINATION
        );
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testAIAgentOrchestration() {
        // Patient with multiple issues
        String patientId = "patient-orchestration-001";

        ClinicalContext context = ClinicalContext.builder()
            .patientId(patientId)
            .currentLabs(List.of(
                LabResult.builder()
                    .testCode("CREATININE")
                    .value(2.5)
                    .referenceRange("0.7-1.3")
                    .build()
            ))
            .currentMedications(List.of(
                Medication.builder().code("metformin").name("Metformin").build()
            ))
            .allergies(List.of())
            .build();

        OrchestrationResult result = runPromise(() -> orchestrator.analyze(context));

        // Multiple agents should have contributed
        assertThat(result.agentResults()).hasSizeGreaterThanOrEqualTo(1);
        assertThat(result.consolidatedAlerts()).isNotEmpty();
        assertThat(result.priority()).isEqualTo(AlertPriority.HIGH);

        // Lab agent should flag creatinine
        assertThat(result.agentResults()).anyMatch(ar ->
            ar.agentType() == AgentType.LAB_ANOMALY &&
            ar.hasFinding()
        );
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testPreventiveInterventionSuggestion() {
        PatientProfile profile = PatientProfile.builder()
            .patientId("patient-preventive-001")
            .age(55)
            .hasDiabetes(false)
            .bmi(32.0)
            .familyHistory(List.of("Diabetes", "Heart Disease"))
            .lastHbA1c(6.2)
            .build();

        PreventiveCareResult result = runPromise(() ->
            readmissionAgent.suggestPreventiveCare(profile)
        );

        assertThat(result.riskScore()).isBetween(0.3, 0.6); // Moderate risk
        assertThat(result.suggestions()).anyMatch(s ->
            s.category() == PreventiveCategory.LIFESTYLE
        );
        assertThat(result.suggestions()).anyMatch(s ->
            s.category() == PreventiveCategory.SCREENING
        );
        assertThat(result.suggestions()).anyMatch(s ->
            s.category() == PreventiveCategory.MONITORING
        );
    }

    /**
     * @doc.test_type positive
     * @doc.coverage line, branch
     */
    @Test
    void testRiskTrendTracking() {
        String patientId = "patient-trend-001";

        // Historical risk scores
        List<RiskAssessment> history = List.of(
            RiskAssessment.builder()
                .patientId(patientId)
                .timestamp(Instant.now().minus(90, ChronoUnit.DAYS))
                .riskScore(0.4)
                .build(),
            RiskAssessment.builder()
                .patientId(patientId)
                .timestamp(Instant.now().minus(60, ChronoUnit.DAYS))
                .riskScore(0.45)
                .build(),
            RiskAssessment.builder()
                .patientId(patientId)
                .timestamp(Instant.now().minus(30, ChronoUnit.DAYS))
                .riskScore(0.6)
                .build()
        );

        RiskTrendResult trend = runPromise(() ->
            readmissionAgent.analyzeTrend(patientId, history)
        );

        assertThat(trend.trendDirection()).isEqualTo(TrendDirection.INCREASING);
        assertThat(trend.trendSeverity()).isEqualTo(TrendSeverity.CONCERNING);
        assertThat(trend.rateOfChange()).isPositive();
        assertThat(trend.projectedRiskScore()).isGreaterThan(0.6);
        assertThat(trend.recommendedAction()).contains("Review care plan");
    }

    /**
     * @doc.test_type positive
     * @doc.coverage branch
     */
    @Test
    void testFalsePositiveHandling() {
        // Lab value that looks abnormal but is expected for this patient
        String patientId = "patient-fp-001";

        List<LabResult> history = List.of(
            LabResult.builder()
                .testCode("CREATININE")
                .value(1.8) // Elevated but stable for this CKD patient
                .timestamp(Instant.now().minus(180, ChronoUnit.DAYS))
                .build(),
            LabResult.builder()
                .testCode("CREATININE")
                .value(1.9)
                .timestamp(Instant.now().minus(90, ChronoUnit.DAYS))
                .build(),
            LabResult.builder()
                .testCode("CREATININE")
                .value(1.85)
                .timestamp(Instant.now().minus(30, ChronoUnit.DAYS))
                .build()
        );

        LabResult current = LabResult.builder()
            .patientId(patientId)
            .testCode("CREATININE")
            .value(1.9) // Within patient's baseline variation
            .referenceRange("0.7-1.3") // Outside "normal"
            .timestamp(Instant.now())
            .build();

        LabAnomalyResult result = runPromise(() -> labAgent.analyze(current, history));

        // Should not flag as anomaly due to patient-specific baseline
        assertThat(result.anomalyDetected()).isFalse();
        assertThat(result.patientBaselineUsed()).isTrue();
        assertThat(result.confidence()).isGreaterThan(0.9);
    }
}
```

---

### Week 5, Days 4-5: Remaining Service Tests & HIPAA Compliance

**Additional Test Files Needed:**

1. `ConsentManagementEdgeCaseTest.java` - Edge cases in consent revocation, expiration
2. `ClinicalNoteWorkflowTest.java` - Note creation, amendment, signature workflows
3. `ReferralWorkflowTest.java` - End-to-end referral process
4. `TelemedicineIntegrationTest.java` - Virtual consultation workflows
5. `ImagingDICOMIntegrationTest.java` - DICOM image handling
6. `ImmunizationForecastTest.java` - Vaccine forecasting algorithms

---

## Test Coverage Targets

### PHR Product Coverage Matrix

| Component       | Current Tests | Target Tests | Coverage Target |
| --------------- | ------------- | ------------ | --------------- |
| AI Agents       | 3             | 8            | 100%            |
| API Layer       | 0             | 5            | 100%            |
| FHIR            | 2             | 15           | 100%            |
| Kernel Services | 14            | 20           | 100%            |
| Extensions      | 1             | 3            | 100%            |
| Security        | 4             | 8            | 100%            |
| Observability   | 1             | 4            | 100%            |
| Plugins         | 2             | 4            | 100%            |
| **Total**       | **30**        | **75**       | **100%**        |

---

## HIPAA Compliance Test Requirements

### Audit Trail Compliance Tests

```java
/**
 * @doc.type test
 * @doc.purpose Validates HIPAA audit trail compliance
 * @doc.layer product
 * @doc.domain Compliance
 * @doc.compliance HIPAA
 */
@DisplayName("HIPAA Compliance - Audit Trail Tests")
class HIPAAAuditComplianceTest extends EventloopTestBase {

    /**
     * @doc.test_type compliance
     * @doc.coverage line, branch
     */
    @Test
    void testEveryPHIAccessIsLogged() {
        String patientId = "hipaa-patient-001";
        String providerId = "provider-dr-hipaa";

        // Access patient record
        runPromise(() -> patientService.getRecords(patientId, providerId)).assertComplete();

        // Verify audit entry exists
        List<AuditEvent> events = auditService.findPHIAccessEvents(patientId);
        assertThat(events).anyMatch(e ->
            e.action().equals("ACCESS") &&
            e.userId().equals(providerId) &&
            e.hasPHI()
        );
    }

    /**
     * @doc.test_type compliance
     * @doc.coverage branch
     */
    @Test
    void testAuditTrailImmutability() {
        String patientId = "hipaa-patient-002";

        // Create audit entry
        AuditEvent event = AuditEvent.builder()
            .eventType("patient.access")
            .patientId(patientId)
            .userId("provider-001")
            .timestamp(Instant.now())
            .build();

        runPromise(() -> auditService.record(event)).assertComplete();

        // Attempt modification should fail
        assertThatThrownBy(() ->
            ((MutableAuditService) auditService).modifyEvent(event.id())
        )
            .isInstanceOf(AuditImmutabilityException.class);
    }

    /**
     * @doc.test_type compliance
     * @doc.coverage line, branch
     */
    @Test
    void testMinimumNecessaryAccess() {
        String patientId = "hipaa-patient-003";
        String billingUser = "billing-staff-001";

        // Billing user requests full record
        PatientRecordRequest request = PatientRecordRequest.builder()
            .patientId(patientId)
            .requestingUser(billingUser)
            .requestedSections(List.of(PatientRecordSection.ALL))
            .purpose(PurposeOfUse.BILLING)
            .build();

        PatientRecordResult result = runPromise(() ->
            patientService.getRecordsWithMinimumNecessary(request)
        );

        // Should only get billing-relevant sections
        assertThat(result.sections()).containsOnly(
            PatientRecordSection.DEMOGRAPHICS,
            PatientRecordSection.INSURANCE,
            PatientRecordSection.BILLING_CODES
        );

        assertThat(result.sections()).doesNotContain(
            PatientRecordSection.CLINICAL_NOTES,
            PatientRecordSection.LAB_RESULTS,
            PatientRecordSection.MEDICATIONS
        );
    }

    /**
     * @doc.test_type compliance
     * @doc.coverage branch
     */
    @Test
    void testAuthorizationBeforeDisclosure() {
        String patientId = "hipaa-patient-004";
        String requester = "researcher-001";

        // Research request without patient authorization
        DisclosureRequest request = DisclosureRequest.builder()
            .patientId(patientId)
            .requester(requester)
            .purpose(PurposeOfUse.RESEARCH)
            .dataRequested(Set.of("diagnoses", "medications"))
            .hasPatientAuthorization(false)
            .build();

        DisclosureResult result = runPromise(() ->
            patientService.requestDisclosure(request)
        );

        // Should be denied without authorization
        assertThat(result.approved()).isFalse();
        assertThat(result.denialReason()).contains("Patient authorization required");

        // Request patient authorization
        PatientAuthorization auth = PatientAuthorization.builder()
            .patientId(patientId)
            .authorizedRecipient(requester)
            .authorizedData(Set.of("diagnoses"))
            .expiration(Instant.now().plus(30, ChronoUnit.DAYS))
            .build();

        runPromise(() -> consentService.recordAuthorization(auth)).assertComplete();

        // Retry with authorization
        DisclosureRequest authorizedRequest = request.withAuthorization(auth.id());
        DisclosureResult authorizedResult = runPromise(() ->
            patientService.requestDisclosure(authorizedRequest)
        );

        assertThat(authorizedResult.approved()).isTrue();
        assertThat(authorizedResult.disclosedData()).containsOnly("diagnoses");
    }
}
```

---

## Success Criteria

1. **Test Count**: 30 → 75+ test files
2. **Line Coverage**: 100% for all components
3. **FHIR R4 Compliance**: All endpoint tests passing
4. **HIPAA Compliance**: All compliance tests passing
5. **Emergency Access**: Load tested to 100 concurrent requests
6. **AI Integration**: All agents tested with real scenarios

---

## Appendix: FHIR Resource Support Matrix

| Resource          | Read | Create | Update | Delete | Search | History |
| ----------------- | ---- | ------ | ------ | ------ | ------ | ------- |
| Patient           | ✅   | ✅     | ✅     | ✅     | ✅     | ✅      |
| Observation       | ✅   | ✅     | ✅     | ❌     | ✅     | ✅      |
| Medication        | ✅   | ✅     | ❌     | ❌     | ✅     | ❌      |
| MedicationRequest | ✅   | ✅     | ✅     | ❌     | ✅     | ✅      |
| Appointment       | ✅   | ✅     | ✅     | ✅     | ✅     | ✅      |
| DocumentReference | ✅   | ✅     | ✅     | ❌     | ✅     | ✅      |
| Consent           | ✅   | ✅     | ✅     | ❌     | ✅     | ❌      |
| Bundle            | ✅   | ✅     | ❌     | ❌     | ❌     | ❌      |
| Practitioner      | ✅   | ❌     | ❌     | ❌     | ✅     | ❌      |
| Organization      | ✅   | ❌     | ❌     | ❌     | ✅     | ❌      |

---

_Document Version: 1.0_  
_Last Updated: April 4, 2026_
