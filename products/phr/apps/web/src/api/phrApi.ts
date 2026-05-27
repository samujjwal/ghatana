import { z } from 'zod';
import type {
  AppointmentCreateResult,
  AppointmentRequest,
  AppointmentSummary,
  AuditEvent,
  AuditEventsPage,
  ConditionSummary,
  ConsentGrant,
  ConsentGrantRequest,
  ConsentRevokeResult,
  DashboardData,
  DependentEntry,
  DocumentDetail,
  DocumentSummary,
  DocumentUploadResult,
  EmergencyAccessEvent,
  EmergencyAccessRequest,
  EmergencyReviewRequest,
  FchvPatientEntry,
  ImmunizationSummary,
  LabResultSummary,
  MedicationSummary,
  NotificationSummary,
  ObservationSummary,
  OcrReviewDocument,
  PatientProfile,
  PatientProfileExtended,
  PatientProfileUpdateRequest,
  PatientRecordSummary,
  PatientRosterEntry,
  PhrLoginRequest,
  PhrReleaseReadiness,
  PhrSession,
  TimelineEvent,
} from '../types';
import { t } from '../i18n/phrI18n';

// --- Config ---

export const API_BASE_URL: string = import.meta.env.VITE_PHR_API_URL ?? 'http://localhost:8080';
// A-009: Mock data only allowed in development/test environments
const USE_MOCK: boolean = import.meta.env.MODE === 'development' && import.meta.env.VITE_USE_MOCK_DATA === 'true';

// --- Error type ---

export class PhrApiError extends Error {
  constructor(
    message: string,
    public readonly statusCode: number,
    public readonly resourceType?: string,
    public readonly correlationId?: string,
  ) {
    super(message);
    this.name = 'PhrApiError';
  }
}

// --- Correlation ID helper ---

/** Returns a new RFC 4122 v4 UUID to be sent as the X-Correlation-ID header. */
function newCorrelationId(): string {
  return crypto.randomUUID();
}

// --- Shared FHIR sub-schemas ---------------------------------------------------

const FhirCodingSchema = z.object({
  system: z.string().optional(),
  code: z.string().optional(),
  display: z.string().optional(),
});

const FhirBundleEntrySchema = z.object({
  resource: z.record(z.string(), z.unknown()),
  fullUrl: z.string().optional(),
});

const FhirBundleSchema = z.object({
  resourceType: z.literal('Bundle'),
  type: z.string(),
  total: z.number().optional(),
  entry: z.array(FhirBundleEntrySchema).optional(),
});

// --- Per-resource FHIR R4 schemas ---------------------------------------------------

const FhirPatientSchema = z.object({
  resourceType: z.literal('Patient'),
  id: z.string(),
  name: z.array(z.object({
    text: z.string().optional(),
    family: z.string().optional(),
    given: z.array(z.string()).optional(),
  })).optional(),
  birthDate: z.string().optional(),
  address: z.array(z.object({ city: z.string().optional() })).optional(),
  telecom: z.array(z.object({ value: z.string().optional() })).optional(),
  extension: z.array(z.object({
    url: z.string(),
    valueString: z.string().optional(),
  })).optional(),
}).passthrough();

const FhirObservationSchema = z.object({
  resourceType: z.literal('Observation'),
  id: z.string(),
  meta: z.object({ lastUpdated: z.string().optional() }).optional(),
  code: z.object({
    text: z.string().optional(),
    coding: z.array(FhirCodingSchema).optional(),
  }).optional(),
  status: z.string().optional(),
  effectiveDateTime: z.string().optional(),
  valueQuantity: z.object({
    value: z.number().optional(),
    unit: z.string().optional(),
  }).optional(),
  valueString: z.string().optional(),
  interpretation: z.array(z.object({
    coding: z.array(FhirCodingSchema).optional(),
    text: z.string().optional(),
  })).optional(),
}).passthrough();

const FhirMedicationRequestSchema = z.object({
  resourceType: z.literal('MedicationRequest'),
  id: z.string(),
  meta: z.object({ lastUpdated: z.string().optional() }).optional(),
  medicationCodeableConcept: z.object({ text: z.string().optional() }).optional(),
  dosageInstruction: z.array(z.object({ text: z.string().optional() })).optional(),
  status: z.string().optional(),
  authoredOn: z.string().optional(),
}).passthrough();

const FhirConsentSchema = z.object({
  resourceType: z.literal('Consent'),
  id: z.string(),
  meta: z.object({ lastUpdated: z.string().optional() }).optional(),
  status: z.string().optional(),
  organization: z.array(z.object({ display: z.string().optional() })).optional(),
  purpose: z.array(FhirCodingSchema).optional(),
  provision: z.object({
    period: z.object({ end: z.string().optional() }).optional(),
  }).optional(),
}).passthrough();

const FhirAppointmentSchema = z.object({
  resourceType: z.literal('Appointment'),
  id: z.string(),
  meta: z.object({ lastUpdated: z.string().optional() }).optional(),
  status: z.string().optional(),
  start: z.string().optional(),
  specialty: z.array(z.object({
    coding: z.array(FhirCodingSchema).optional(),
    text: z.string().optional(),
  })).optional(),
  participant: z.array(z.object({
    actor: z.object({ display: z.string().optional() }).optional(),
  })).optional(),
  comment: z.string().optional(),
}).passthrough();

// --- Mock data validation schema (used when USE_MOCK=true) ------------------------

const dashboardSchema = z.object({
  patient: z.object({
    id: z.string(),
    name: z.string(),
    age: z.number(),
    bloodType: z.string(),
    location: z.string(),
    emergencyContact: z.string(),
  }),
  records: z.array(z.object({
    id: z.string(),
    title: z.string(),
    category: z.enum(['visit', 'lab', 'immunization', 'medication']),
    updatedAt: z.string(),
    resourceType: z.string(),
    fhirJson: z.string(),
  })),
  consents: z.array(z.object({
    id: z.string(),
    recipient: z.string(),
    purpose: z.string(),
    status: z.enum(['active', 'expiring', 'revoked']),
    expiresAt: z.string(),
  })),
  appointments: z.array(z.object({
    id: z.string(),
    provider: z.string(),
    specialty: z.string(),
    startsAt: z.string(),
    location: z.string(),
  })),
  labs: z.array(z.object({
    id: z.string(),
    name: z.string(),
    status: z.enum(['normal', 'attention']),
    value: z.string(),
    collectedAt: z.string(),
  })),
  medications: z.array(z.object({
    id: z.string(),
    medication: z.string(),
    dosage: z.string(),
    schedule: z.string(),
    adherence: z.number(),
  })),
});

// --- FHIR → UI type transformations --------------------------------------------------

function fhirPatientToProfile(raw: z.infer<typeof FhirPatientSchema>): PatientProfile {
  const firstName = raw.name?.[0]?.given?.join(' ') ?? '';
  const lastName = raw.name?.[0]?.family ?? '';
  const name = raw.name?.[0]?.text ?? ([firstName, lastName].filter(Boolean).join(' ') || 'Unknown');
  const bloodType = raw.extension?.find(e => e.url.includes('blood-type'))?.valueString ?? 'Unknown';
  const location = raw.address?.[0]?.city ?? 'Unknown';
  const emergencyContact = raw.telecom?.[0]?.value ?? '';
  let age = 0;
  if (raw.birthDate) {
    const birth = new Date(raw.birthDate);
    age = Math.floor((Date.now() - birth.getTime()) / (365.25 * 24 * 3600 * 1000));
  }
  return { id: raw.id, name, age, bloodType, location, emergencyContact };
}

function fhirObservationToRecord(raw: z.infer<typeof FhirObservationSchema>): PatientRecordSummary {
  const title = raw.code?.text ?? raw.code?.coding?.[0]?.display ?? 'Observation';
  return {
    id: raw.id,
    title,
    category: 'lab',
    updatedAt: raw.effectiveDateTime ?? raw.meta?.lastUpdated ?? new Date().toISOString(),
    resourceType: 'Observation',
    fhirJson: JSON.stringify(raw, null, 2),
  };
}

function fhirMedicationRequestToRecord(raw: z.infer<typeof FhirMedicationRequestSchema>): PatientRecordSummary {
  const title = raw.medicationCodeableConcept?.text ?? 'Medication';
  return {
    id: raw.id,
    title: `${title} prescription`,
    category: 'medication',
    updatedAt: raw.authoredOn ?? raw.meta?.lastUpdated ?? new Date().toISOString(),
    resourceType: 'MedicationRequest',
    fhirJson: JSON.stringify(raw, null, 2),
  };
}

function fhirObservationToLabResult(raw: z.infer<typeof FhirObservationSchema>): LabResultSummary {
  const name = raw.code?.text ?? raw.code?.coding?.[0]?.display ?? 'Result';
  const valueQty = raw.valueQuantity;
  const value = valueQty
    ? `${valueQty.value?.toString() ?? ''} ${valueQty.unit ?? ''}`.trim()
    : (raw.valueString ?? 'â€”');
  const interpretationCode = raw.interpretation?.[0]?.coding?.[0]?.code ?? 'N';
  const status: 'normal' | 'attention' = (
    interpretationCode === 'N' || interpretationCode === 'normal'
  ) ? 'normal' : 'attention';
  return {
    id: raw.id,
    name,
    status,
    value,
    collectedAt: (raw.effectiveDateTime ?? raw.meta?.lastUpdated ?? new Date().toISOString()).split('T')[0] ?? '',
  };
}

function fhirMedicationRequestToSummary(raw: z.infer<typeof FhirMedicationRequestSchema>): MedicationSummary {
  const fullName = raw.medicationCodeableConcept?.text ?? 'Unknown';
  const parts = /^(.*?)\s+(\d+\s*\w+)$/.exec(fullName);
  const medication = parts?.[1] ?? fullName;
  const dosage = parts?.[2] ?? '';
  const schedule = raw.dosageInstruction?.[0]?.text ?? '';
  return { id: raw.id, medication, dosage, schedule, adherence: 100 };
}

function fhirConsentToGrant(raw: z.infer<typeof FhirConsentSchema>): ConsentGrant {
  const recipient = raw.organization?.[0]?.display ?? 'Unknown';
  const purpose = raw.purpose?.[0]?.display ?? raw.purpose?.[0]?.code ?? 'General';
  const expiresAt = raw.provision?.period?.end?.split('T')[0] ?? '';
  const rawStatus = raw.status ?? 'active';
  let status: 'active' | 'expiring' | 'revoked' = 'active';
  if (rawStatus === 'inactive' || rawStatus === 'entered-in-error') {
    status = 'revoked';
  } else if (expiresAt) {
    const daysUntilExpiry = (new Date(expiresAt).getTime() - Date.now()) / (1000 * 3600 * 24);
    if (daysUntilExpiry >= 0 && daysUntilExpiry <= 30) {
      status = 'expiring';
    }
  }
  return { id: raw.id, recipient, purpose, status, expiresAt };
}

function fhirAppointmentToSummary(raw: z.infer<typeof FhirAppointmentSchema>): AppointmentSummary {
  const provider = raw.participant?.find(p => p.actor?.display)?.actor?.display ?? 'Provider';
  const specialty = raw.specialty?.[0]?.text ?? raw.specialty?.[0]?.coding?.[0]?.display ?? 'General';
  const location = raw.comment ?? 'TBD';
  return { id: raw.id, provider, specialty, startsAt: raw.start ?? '', location };
}

// --- HTTP helpers ---

type SessionContext = {
  tenantId?: string;
  principalId?: string;
  role?: string;
  persona?: string;
  tier?: string;
  correlationId?: string;
  idempotencyKey?: string;
};

/**
 * A-002: Centralized header builder for PHR API requests.
 * Ensures consistent tenant/principal/role/persona/tier/correlation/idempotency headers.
 */
export function buildPhrHeaders(context: SessionContext = {}): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'X-Correlation-ID': context.correlationId ?? newCorrelationId(),
  };

  if (context.tenantId) {
    headers['X-Tenant-Id'] = context.tenantId;
  }
  if (context.principalId) {
    headers['X-Principal-Id'] = context.principalId;
  }
  if (context.role) {
    headers['X-Role'] = context.role;
  }
  if (context.persona) {
    headers['X-Persona'] = context.persona;
  }
  if (context.tier) {
    headers['X-Tier'] = context.tier;
  }
  if (context.idempotencyKey) {
    headers['X-Idempotency-Key'] = context.idempotencyKey;
  }

  return headers;
}

/**
 * A-003: Safe JSON fetch with schema validation.
 * Every PHI response validates against schema to ensure type safety.
 */
export async function safeFetchJson<T>(
  path: string,
  schema: z.ZodType<T>,
  options: {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
    body?: BodyInit | null;
    context?: SessionContext;
    accept?: string;
    contentType?: string;
  } = {},
): Promise<T> {
  const {
    method = 'GET',
    body = null,
    context = {},
    accept = 'application/json',
    contentType = 'application/json',
  } = options;

  const headers = buildPhrHeaders(context);
  if (contentType && body !== null) {
    headers['Content-Type'] = contentType;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body,
  });

  if (!response.ok) {
    const correlationId = headers['X-Correlation-ID'];
    throw new PhrApiError(
      `PHR request failed: ${method} ${path} returned ${response.status}`,
      response.status,
      undefined,
      correlationId,
    );
  }

  const data = await response.json() as unknown;
  return schema.parse(data);
}

/**
 * PHR-P1-004: Central request client with consistent authenticated context.
 * All API calls should use this helper to ensure tenant/principal/role headers are injected.
 */
export async function phrFetch<T>(
  path: string,
  options: {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
    body?: BodyInit | null;
    context?: SessionContext;
    accept?: string;
    contentType?: string;
    expectedSchema?: z.ZodType<T>;
  } = {},
): Promise<T> {
  const {
    method = 'GET',
    body = null,
    context = {},
    accept = 'application/json',
    contentType = 'application/json',
    expectedSchema,
  } = options;

  const headers: Record<string, string> = {
    Accept: accept,
    'X-Correlation-ID': context.correlationId ?? newCorrelationId(),
  };

  // Inject authenticated context headers if provided
  if (context.tenantId) {
    headers['X-Tenant-Id'] = context.tenantId;
  }
  if (context.principalId) {
    headers['X-Principal-Id'] = context.principalId;
  }
  if (context.role) {
    headers['X-Role'] = context.role;
  }
  if (contentType && body !== null) {
    headers['Content-Type'] = contentType;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body,
  });

  if (!response.ok) {
    throw new PhrApiError(
      `PHR request failed: ${method} ${path} returned ${response.status}`,
      response.status,
    );
  }

  const data = await response.json() as unknown;
  return expectedSchema ? expectedSchema.parse(data) : (data as T);
}

async function fhirGet(resourceType: string, id?: string, context?: SessionContext): Promise<unknown> {
  const path = id !== undefined ? `/fhir/${resourceType}/${id}` : `/fhir/${resourceType}`;
  return phrFetch(path, {
    accept: 'application/fhir+json',
    context,
  });
}

function extractBundleEntries(bundle: z.infer<typeof FhirBundleSchema>): Record<string, unknown>[] {
  return (bundle.entry ?? []).map(e => e.resource);
}

// --- Public API functions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchDashboardData(context?: SessionContext): Promise<DashboardData> {
  if (USE_MOCK) {
    const { demoDashboard } = await import('../demoData');
    return dashboardSchema.parse(demoDashboard);
  }

  const [patientData, obsBundle, medBundle, consentBundle, apptBundle] = await Promise.all([
    fhirGet('Patient', 'current', context),
    fhirGet('Observation', undefined, context),
    fhirGet('MedicationRequest', undefined, context),
    fhirGet('Consent', undefined, context),
    fhirGet('Appointment', undefined, context),
  ]);

  const patient = fhirPatientToProfile(FhirPatientSchema.parse(patientData));

  const observations = extractBundleEntries(FhirBundleSchema.parse(obsBundle))
    .map(r => FhirObservationSchema.parse(r));
  const labs = observations.map(fhirObservationToLabResult);
  const obsRecords = observations.map(fhirObservationToRecord);

  const medications_raw = extractBundleEntries(FhirBundleSchema.parse(medBundle))
    .map(r => FhirMedicationRequestSchema.parse(r));
  const medications = medications_raw.map(fhirMedicationRequestToSummary);
  const medRecords = medications_raw.map(fhirMedicationRequestToRecord);

  const consents = extractBundleEntries(FhirBundleSchema.parse(consentBundle))
    .map(r => FhirConsentSchema.parse(r))
    .map(fhirConsentToGrant);

  const appointments = extractBundleEntries(FhirBundleSchema.parse(apptBundle))
    .map(r => FhirAppointmentSchema.parse(r))
    .map(fhirAppointmentToSummary);

  const records: PatientRecordSummary[] = [...obsRecords, ...medRecords];

  return { patient, records, consents, appointments, labs, medications };
}

export async function exportPatientBundle(context?: SessionContext): Promise<string> {
  if (USE_MOCK) {
    return JSON.stringify({
      status: 'queued',
      message: t('settings.hie.prepared'),
    });
  }

  return phrFetch('/fhir/Patient/current/$export', {
    method: 'POST',
    context,
    contentType: 'application/json',
  }) as Promise<string>;
}

export async function fetchReleaseReadiness(options: {
  environment: 'local' | 'dev' | 'staging' | 'prod';
  role: string;
  tenantId?: string;
  principalId?: string;
}): Promise<PhrReleaseReadiness> {
  const tenantId = options.tenantId ?? 'tenant-health-1';
  const principalId = options.principalId ?? `${options.role}-release-cockpit`;
  const url = new URL(`${API_BASE_URL}/release-readiness`);
  url.searchParams.set('environment', options.environment);
  const response = await fetch(url.toString(), {
    headers: {
      Accept: 'application/json',
      'X-Tenant-Id': tenantId,
      'X-Principal-Id': principalId,
      'X-Role': options.role,
      'X-Persona': options.role,
      'X-Tier': 'clinical',
      'X-Correlation-ID': newCorrelationId(),
    },
  });
  if (!response.ok) {
    throw new PhrApiError(`PHR release readiness failed with status ${response.status}`, response.status);
  }
  const data = await response.json();
  return PhrReleaseReadinessSchema.parse(data);
}

// --- Audit events API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

const AuditEventSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  eventType: z.string(),
  principal: z.string(),
  resourceType: z.string(),
  resourceId: z.string().nullable(),
  timestamp: z.string(),
  success: z.boolean(),
  details: z.record(z.string(), z.string()),
});

const AuditEventsPageSchema = z.object({
  events: z.array(AuditEventSchema),
  total: z.number(),
  page: z.number(),
  pageSize: z.number(),
});

// A-004: Add Zod schemas for all newly added DTOs
const ConditionSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  display: z.string(),
  code: z.string(),
  status: z.enum(['active', 'resolved', 'chronic']),
  onsetDate: z.string(),
}).passthrough();

const ObservationSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  value: z.string(),
  unit: z.string(),
  status: z.enum(['normal', 'attention', 'critical']),
  effectiveDate: z.string(),
  recordedAt: z.string(),
  loincCode: z.string().optional(),
}).passthrough();

const ImmunizationSummarySchema = z.object({
  id: z.string(),
  vaccine: z.string(),
  date: z.string(),
  occurrenceDate: z.string(),
  status: z.enum(['completed', 'not-done', 'entered-in-error']),
  lotNumber: z.string().optional(),
  cvxCode: z.string().optional(),
}).passthrough();

const DocumentSummarySchema = z.object({
  id: z.string(),
  title: z.string(),
  contentType: z.string(),
  uploadedAt: z.string(),
  status: z.enum(['pending', 'processing', 'ready', 'failed']).optional(),
  ocrStatus: z.enum(['pending', 'processing', 'ready', 'failed']).optional(),
}).passthrough();

const MedicationSummarySchema = z.object({
  id: z.string(),
  medication: z.string(),
  dosage: z.string(),
  schedule: z.string(),
  adherence: z.number(),
}).passthrough();

const AppointmentSummarySchema = z.object({
  id: z.string(),
  provider: z.string(),
  specialty: z.string(),
  startsAt: z.string(),
  location: z.string(),
}).passthrough();

const NotificationSummarySchema = z.object({
  id: z.string(),
  title: z.string(),
  message: z.string(),
  createdAt: z.string(),
  read: z.boolean(),
}).passthrough();

const DependentEntrySchema = z.object({
  id: z.string(),
  name: z.string(),
  relationship: z.string(),
  status: z.enum(['active', 'inactive']),
}).passthrough();

const PatientRosterEntrySchema = z.object({
  id: z.string(),
  name: z.string(),
  status: z.enum(['active', 'inactive']),
}).passthrough();

const FchvPatientEntrySchema = z.object({
  id: z.string(),
  name: z.string(),
  community: z.string(),
  lastVisit: z.string(),
}).passthrough();

// A-004: Additional Zod schemas for DTOs
const PhrReleaseReadinessSchema = z.object({
  product: z.literal('phr'),
  tenantId: z.string(),
  principalId: z.string(),
  role: z.string(),
  environment: z.string(),
  generatedAt: z.string(),
  targetCommitSha: z.string(),
  runtimeTruthBlocked: z.boolean(),
  requiredSections: z.array(z.string()),
  releaseReadiness: z.object({
    status: z.string().optional(),
    overallScore: z.number().optional(),
    blockingIssues: z.array(z.string()).optional(),
    warnings: z.array(z.string()).optional(),
  }).optional(),
  sections: z.record(z.object({
    label: z.string(),
    status: z.string(),
    runtimeProven: z.boolean(),
    message: z.string(),
    details: z.unknown().optional(),
  })),
}).passthrough();

const ConsentRevokeResultSchema = z.object({
  grantId: z.string(),
  status: z.literal('REVOKED'),
});

const AppointmentCreateResultSchema = z.object({
  id: z.string(),
  status: z.enum(['requested', 'confirmed', 'cancelled']),
  specialty: z.string(),
  preferredDate: z.string(),
  createdAt: z.string(),
});

const EmergencyAccessEventSchema = z.object({
  id: z.string(),
  patientId: z.string(),
  clinicianId: z.string(),
  reason: z.string(),
  status: z.enum(['PENDING', 'REVIEWED', 'EXPIRED']),
  accessedAt: z.string(),
  reviewedAt: z.string().optional(),
  reviewedBy: z.string().optional(),
  reviewNote: z.string().optional(),
});

const PatientProfileExtendedSchema = z.object({
  id: z.string(),
  name: z.string(),
  age: z.number(),
  bloodType: z.string(),
  location: z.string(),
  emergencyContact: z.string(),
  birthDate: z.string().optional(),
  preferredLanguage: z.string().optional(),
  facilityId: z.string().optional(),
  mrn: z.string().optional(),
  gender: z.string().optional(),
}).passthrough();

const DocumentUploadResultSchema = z.object({
  id: z.string(),
  status: z.enum(['pending_ocr', 'processed', 'failed']),
  ocrAvailable: z.boolean(),
});

const OcrReviewDocumentSchema = z.object({
  id: z.string(),
  title: z.string(),
  ocrText: z.string(),
  extractedText: z.string(),
  correctedText: z.string().optional(),
  confidence: z.number(),
  status: z.enum(['pending_review', 'confirmed', 'rejected']),
});

const OcrRejectResultSchema = z.object({
  documentId: z.string(),
  rejected: z.boolean(),
});

const AppointmentBookingResultSchema = z.object({
  id: z.string(),
  status: z.string(),
});

const AppointmentCancelResultSchema = z.object({
  success: z.boolean(),
});

const DocumentUploadInitResultSchema = z.object({
  id: z.string(),
  status: z.string(),
  ocrStatus: z.string(),
});

export async function fetchAuditEvents(options: {
  patientId?: string;
  filter?: 'all' | 'access' | 'consent' | 'emergency';
  page?: number;
  pageSize?: number;
  tenantId?: string;
  principalId?: string;
  role?: string;
}): Promise<AuditEventsPage> {
  const context: SessionContext = {
    tenantId: options.tenantId,
    principalId: options.principalId,
    role: options.role,
  };
  const url = new URL(`${API_BASE_URL}/audit/events`);
  if (options.patientId) url.searchParams.set('patientId', options.patientId);
  if (options.filter && options.filter !== 'all') url.searchParams.set('filter', options.filter);
  url.searchParams.set('page', String(options.page ?? 0));
  url.searchParams.set('pageSize', String(options.pageSize ?? 50));

  return phrFetch(`${url.pathname}${url.search}`, {
    context,
    expectedSchema: AuditEventsPageSchema,
  });
}

// --- Consent management API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

const ConsentGrantRequestSchema = z.object({
  patientId: z.string().min(1),
  recipientId: z.string().min(1),
  purpose: z.string().min(1),
  scope: z.object({
    resourceTypes: z.array(z.string()).min(1),
    allDocuments: z.boolean().optional(),
    specificDocumentIds: z.array(z.string()).optional(),
    actions: z.array(z.string()).optional(),
  }),
  expiresAt: z.string().min(1),
}).strict();

// A-005: Backend-aligned consent grant schema with exact field names
const BackendConsentGrantRequestSchema = z.object({
  patientId: z.string().min(1),
  recipientId: z.string().min(1),
  scope: z.object({
    resourceTypes: z.array(z.string()).min(1),
    allDocuments: z.boolean().optional(),
    specificDocumentIds: z.array(z.string()).optional(),
    actions: z.array(z.string()).optional(),
  }),
  expiresAt: z.string().min(1),
}).strict();

export async function createConsentGrant(
  request: ConsentGrantRequest,
  context: { tenantId: string; principalId: string; role: string; idempotencyKey?: string },
): Promise<ConsentGrant> {
  // A-005: Transform frontend request to backend-aligned format
  const backendRequest = {
    patientId: request.patientId,
    recipientId: request.recipientId,
    scope: {
      resourceTypes: request.scope.resourceTypes,
      allDocuments: request.scope.allDocuments,
      specificDocumentIds: request.scope.specificDocumentIds,
      actions: request.scope.actions,
    },
    expiresAt: request.expiresAt,
  };
  const validated = BackendConsentGrantRequestSchema.parse(backendRequest);
  const raw = await phrFetch('/consents/grants', {
    method: 'POST',
    body: JSON.stringify(validated),
    context,
  }) as unknown;
  return FhirConsentSchema.parse(raw).id !== undefined
    ? fhirConsentToGrant(FhirConsentSchema.parse(raw))
    : (raw as ConsentGrant);
}

export async function revokeConsentGrant(
  grantId: string,
  patientId: string,
  context: { tenantId: string; principalId: string; role: string; idempotencyKey?: string },
): Promise<ConsentRevokeResult> {
  if (!grantId || !patientId) {
    throw new PhrApiError('grantId and patientId are required to revoke consent', 400, 'Consent');
  }
  const url = new URL(`${API_BASE_URL}/consents/grants/${encodeURIComponent(grantId)}/revoke`);
  url.searchParams.set('patientId', patientId);
  const data = await phrFetch(`${url.pathname}${url.search}`, {
    method: 'POST',
    context,
  });
  return ConsentRevokeResultSchema.parse(data);
}

// --- Appointment API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

const AppointmentRequestSchema = z.object({
  specialty: z.string().min(1, 'Specialty is required'),
  preferredDate: z.string().min(1, 'Preferred date is required'),
  notes: z.string().optional(),
}).strict();

export async function createAppointmentRequest(
  request: AppointmentRequest,
  context: { tenantId: string; principalId: string; role: string; idempotencyKey?: string },
): Promise<AppointmentCreateResult> {
  const validated = AppointmentRequestSchema.parse(request);
  const data = await phrFetch('/appointments', {
    method: 'POST',
    body: JSON.stringify(validated),
    context,
  });
  return AppointmentCreateResultSchema.parse(data);
}

// --- Emergency access API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

const EmergencyAccessRequestSchema = z.object({
  patientId: z.string().min(1, 'Patient ID is required'),
  reason: z.string().min(5, 'Reason must be at least 5 characters'),
  clinicianId: z.string().min(1, 'Clinician ID is required'),
}).strict();

export async function requestEmergencyAccess(
  request: EmergencyAccessRequest,
  context: { tenantId: string; principalId: string; role: string; idempotencyKey?: string },
): Promise<EmergencyAccessEvent> {
  const validated = EmergencyAccessRequestSchema.parse(request);
  const data = await phrFetch('/emergency/access', {
    method: 'POST',
    body: JSON.stringify(validated),
    context,
  });
  return EmergencyAccessEventSchema.parse(data);
}

export async function reviewEmergencyAccess(
  review: EmergencyReviewRequest,
  context: { tenantId: string; principalId: string; role: string; idempotencyKey?: string },
): Promise<EmergencyAccessEvent> {
  const data = await phrFetch(`/emergency/reviews/${encodeURIComponent(review.eventId)}`, {
    method: 'POST',
    body: JSON.stringify({ reviewNote: review.reviewNote, reviewerId: review.reviewerId }),
    context,
  });
  return EmergencyAccessEventSchema.parse(data);
}

// --- Auth session API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

const PhrSessionSchema = z.object({
  principalId: z.string(),
  tenantId: z.string(),
  role: z.enum(['patient', 'caregiver', 'clinician', 'admin']),
  name: z.string(),
  expiresAt: z.string(),
});

export async function loginWithCredentials(request: PhrLoginRequest): Promise<PhrSession> {
  if (!request.nationalId.trim() || !request.password) {
    throw new PhrApiError('National ID and password are required', 400);
  }
  const data = await phrFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ nationalId: request.nationalId, password: request.password }),
    expectedSchema: PhrSessionSchema,
  });
  return data as PhrSession;
}

export async function logoutSession(context: {
  tenantId: string;
  principalId: string;
}): Promise<void> {
  await phrFetch('/auth/logout', {
    method: 'POST',
    context,
  });
}

// --- Patient profile â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchPatientProfile(context?: SessionContext): Promise<PatientProfileExtended> {
  const data = await phrFetch('/profile', { context });
  return PatientProfileExtendedSchema.parse(data);
}

export async function updatePatientProfile(
  update: PatientProfileUpdateRequest,
  context: { tenantId: string; principalId: string; correlationId?: string; idempotencyKey?: string },
): Promise<PatientProfileExtended> {
  const data = await phrFetch('/profile', {
    method: 'PUT',
    body: JSON.stringify(update),
    context,
  });
  return PatientProfileExtendedSchema.parse(data);
}

// --- Timeline â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchTimeline(
  principalId: string,
  context?: SessionContext,
  filters?: {
    category?: string;
  },
): Promise<TimelineEvent[]> {
  if (USE_MOCK) {
    // Mock data for development
    return [
      {
        id: 'tl-1',
        occurredAt: '2024-01-15T09:00:00Z',
        title: 'Outpatient consultation',
        description: 'Primary care visit',
        eventType: 'visit',
      },
      {
        id: 'tl-2',
        occurredAt: '2024-01-10T14:30:00Z',
        title: 'New prescription',
        description: 'Lisinopril 10mg',
        eventType: 'medication',
      },
    ];
  }

  const path = filters?.category
    ? `/timeline/${encodeURIComponent(principalId)}/category/${encodeURIComponent(filters.category)}`
    : `/timeline/${encodeURIComponent(principalId)}`;

  const body = await phrFetch(path, { context }) as {
    patientId: string;
    items: Array<{
      id: string;
      occurredAt: string;
      eventType: string;
      title: string;
      description: string;
      details: Record<string, unknown>;
    }>;
    count: number;
    generatedAt: string;
  };

  return body.items.map(item => ({
    id: item.id,
    occurredAt: item.occurredAt,
    title: item.title,
    description: item.description,
    eventType: item.eventType,
  }));
}

// --- Conditions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchConditions(principalId: string, context?: SessionContext): Promise<ConditionSummary[]> {
  if (USE_MOCK) {
    // Mock data for development
    return [
      {
        id: 'cond-1',
        name: 'Hypertension',
        display: 'Hypertension',
        code: 'I10',
        status: 'active',
        onsetDate: '2023-01-15',
      },
      {
        id: 'cond-2',
        name: 'Type 2 Diabetes',
        display: 'Type 2 Diabetes',
        code: 'E11.9',
        status: 'active',
        onsetDate: '2022-06-20',
      },
      {
        id: 'cond-3',
        name: 'Acute Bronchitis',
        display: 'Acute Bronchitis',
        code: 'J20.9',
        status: 'resolved',
        onsetDate: '2023-11-10',
      },
    ];
  }

  const body = await phrFetch(`/conditions/${encodeURIComponent(principalId)}`, { context }) as { items: ConditionSummary[] };
  return body.items;
}

export async function fetchConditionDetail(conditionId: string, principalId: string, context?: SessionContext): Promise<ConditionSummary> {
  if (USE_MOCK) {
    // Mock data for development
    return {
      id: conditionId,
      name: 'Hypertension',
      display: 'Hypertension',
      code: 'I10',
      status: 'active',
      onsetDate: '2023-01-15',
    };
  }

  return phrFetch(`/conditions/${encodeURIComponent(conditionId)}?patientId=${encodeURIComponent(principalId)}`, { context }) as unknown as ConditionSummary;
}

// --- Observations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchObservations(principalId: string, context?: SessionContext): Promise<ObservationSummary[]> {
  if (USE_MOCK) {
    // Mock data for development with trend capability
    return [
      {
        id: 'obs-1',
        name: 'Blood Pressure Systolic',
        value: '120',
        unit: 'mmHg',
        status: 'normal',
        effectiveDate: '2024-01-15',
        recordedAt: '2024-01-15T10:00:00Z',
        loincCode: '8480-6',
      },
      {
        id: 'obs-2',
        name: 'Blood Pressure Diastolic',
        value: '80',
        unit: 'mmHg',
        status: 'normal',
        effectiveDate: '2024-01-15',
        recordedAt: '2024-01-15T10:00:00Z',
        loincCode: '8462-4',
      },
      {
        id: 'obs-3',
        name: 'Blood Pressure Systolic',
        value: '125',
        unit: 'mmHg',
        status: 'normal',
        effectiveDate: '2024-01-10',
        recordedAt: '2024-01-10T10:00:00Z',
        loincCode: '8480-6',
      },
      {
        id: 'obs-4',
        name: 'Blood Pressure Diastolic',
        value: '82',
        unit: 'mmHg',
        status: 'normal',
        effectiveDate: '2024-01-10',
        recordedAt: '2024-01-10T10:00:00Z',
        loincCode: '8462-4',
      },
      {
        id: 'obs-5',
        name: 'Blood Pressure Systolic',
        value: '118',
        unit: 'mmHg',
        status: 'normal',
        effectiveDate: '2024-01-05',
        recordedAt: '2024-01-05T10:00:00Z',
        loincCode: '8480-6',
      },
      {
        id: 'obs-6',
        name: 'Blood Pressure Diastolic',
        value: '78',
        unit: 'mmHg',
        status: 'normal',
        effectiveDate: '2024-01-05',
        recordedAt: '2024-01-05T10:00:00Z',
        loincCode: '8462-4',
      },
      {
        id: 'obs-7',
        name: 'Blood Glucose',
        value: '95',
        unit: 'mg/dL',
        status: 'normal',
        effectiveDate: '2024-01-15',
        recordedAt: '2024-01-15T10:00:00Z',
        loincCode: '2345-7',
      },
      {
        id: 'obs-8',
        name: 'Blood Glucose',
        value: '110',
        unit: 'mg/dL',
        status: 'abnormal',
        effectiveDate: '2024-01-10',
        recordedAt: '2024-01-10T10:00:00Z',
        loincCode: '2345-7',
      },
      {
        id: 'obs-9',
        name: 'Blood Glucose',
        value: '98',
        unit: 'mg/dL',
        status: 'normal',
        effectiveDate: '2024-01-05',
        recordedAt: '2024-01-05T10:00:00Z',
        loincCode: '2345-7',
      },
    ];
  }

  const body = await phrFetch(`/clinical/labs/observations?patientId=${encodeURIComponent(principalId)}`, { context }) as { items: ObservationSummary[] };
  return body.items;
}

export async function fetchObservationDetail(observationId: string, principalId: string, context?: SessionContext): Promise<ObservationSummary> {
  if (USE_MOCK) {
    // Mock data for development
    return {
      id: observationId,
      name: 'Blood Pressure Systolic',
      value: '120',
      unit: 'mmHg',
      status: 'normal',
      effectiveDate: '2024-01-15',
      recordedAt: '2024-01-15T10:00:00Z',
      loincCode: '8480-6',
    };
  }

  return phrFetch(`/clinical/labs/observations/${encodeURIComponent(observationId)}?patientId=${encodeURIComponent(principalId)}`, { context }) as unknown as ObservationSummary;
}

// --- Immunizations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchImmunizations(principalId: string, context?: SessionContext): Promise<ImmunizationSummary[]> {
  if (USE_MOCK) {
    // Mock data for development
    return [
      {
        id: 'imm-1',
        vaccine: 'Influenza Vaccine',
        date: '2024-01-15',
        occurrenceDate: '2024-01-15',
        status: 'complete',
        lotNumber: 'LOT12345',
        cvxCode: '141',
      },
      {
        id: 'imm-2',
        vaccine: 'COVID-19 mRNA Vaccine',
        date: '2023-08-20',
        occurrenceDate: '2023-08-20',
        status: 'complete',
        lotNumber: 'LOT67890',
        cvxCode: '207',
      },
      {
        id: 'imm-3',
        vaccine: 'Tetanus, Diphtheria, Pertussis',
        date: '2022-03-10',
        occurrenceDate: '2022-03-10',
        status: 'due',
        lotNumber: 'LOT11111',
        cvxCode: '107',
      },
      {
        id: 'imm-4',
        vaccine: 'Hepatitis B Vaccine',
        date: '2021-11-05',
        occurrenceDate: '2021-11-05',
        status: 'complete',
        lotNumber: 'LOT22222',
        cvxCode: '08',
      },
    ];
  }

  const body = await phrFetch(`/clinical/immunizations?patientId=${encodeURIComponent(principalId)}`, { context }) as { items: ImmunizationSummary[] };
  return body.items;
}

// --- Documents â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchDocuments(principalId: string, context?: SessionContext): Promise<DocumentSummary[]> {
  if (USE_MOCK) {
    // Mock data for development
    return [
      {
        id: 'doc-1',
        title: 'Lab Results - Blood Work',
        contentType: 'application/pdf',
        uploadedAt: '2024-01-15T10:00:00Z',
        ocrStatus: 'complete',
        size: 1024000,
      },
      {
        id: 'doc-2',
        title: 'MRI Scan Report',
        contentType: 'application/pdf',
        uploadedAt: '2024-01-10T14:30:00Z',
        ocrStatus: 'complete',
        size: 2048000,
      },
      {
        id: 'doc-3',
        title: 'Discharge Summary',
        contentType: 'application/pdf',
        uploadedAt: '2023-12-20T09:15:00Z',
        ocrStatus: 'pending',
        size: 512000,
      },
      {
        id: 'doc-4',
        title: 'Insurance Card',
        contentType: 'image/jpeg',
        uploadedAt: '2023-11-05T16:45:00Z',
        ocrStatus: 'complete',
        size: 256000,
      },
    ];
  }

  const body = await phrFetch(`/documents?patientId=${encodeURIComponent(principalId)}`, { context }) as { items: DocumentSummary[] };
  return body.items;
}

export async function fetchDocumentDetail(documentId: string, principalId: string, context?: SessionContext): Promise<DocumentDetail> {
  if (USE_MOCK) {
    // Mock data for development
    return {
      id: documentId,
      title: 'Lab Results - Blood Work',
      category: 'lab',
      mimeType: 'application/pdf',
      contentType: 'application/pdf',
      uploadedAt: '2024-01-15T10:00:00Z',
      ocrStatus: 'ready',
      sizeKb: 1024,
      description: 'Complete blood work results including CBC, lipid panel, and metabolic panel.',
      uploadedBy: 'Dr. Sharma',
      versions: [
        {
          versionId: 'v1',
          versionNumber: 1,
          createdAt: '2024-01-15T10:00:00Z',
          createdBy: 'Dr. Sharma',
        },
      ],
      auditLog: [
        {
          id: 'audit-1',
          action: 'uploaded',
          timestamp: '2024-01-15T10:00:00Z',
          performedBy: 'Dr. Sharma',
        },
      ],
    };
  }

  return phrFetch(`/documents/${encodeURIComponent(documentId)}?patientId=${encodeURIComponent(principalId)}`, { context }) as unknown as DocumentDetail;
}

/**
 * A-006: Secure document download with audit trail.
 * Returns a download URL with proper audit logging instead of direct blob.
 */
export async function downloadDocument(
  documentId: string,
  patientId: string,
  context?: SessionContext,
): Promise<{ downloadUrl: string; expiresAt: string }> {
  if (USE_MOCK) {
    return {
      downloadUrl: `blob:${documentId}`,
      expiresAt: new Date(Date.now() + 3600000).toISOString(),
    };
  }

  const response = await phrFetch(`/documents/${encodeURIComponent(documentId)}/download?patientId=${encodeURIComponent(patientId)}`, {
    method: 'POST',
    context,
  }) as { downloadUrl: string; expiresAt: string };
  return response;
}

export async function uploadDocument(
  patientId: string,
  file: File,
  metadata: { title: string; category?: string; description?: string },
  context?: SessionContext & { idempotencyKey?: string },
): Promise<{ id: string; status: string; ocrStatus: string }> {
  if (USE_MOCK) {
    return {
      id: `doc-${Date.now()}`,
      status: 'uploaded',
      ocrStatus: 'pending',
    };
  }

  const formData = new FormData();
  formData.append('file', file);
  formData.append('title', metadata.title);
  if (metadata.category) formData.append('category', metadata.category);
  if (metadata.description) formData.append('description', metadata.description);

  const data = await phrFetch(`/documents?patientId=${encodeURIComponent(patientId)}`, {
    method: 'POST',
    body: formData,
    context,
  });
  return DocumentUploadInitResultSchema.parse(data);
}

// --- Medications â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchMedications(principalId: string, context?: SessionContext): Promise<MedicationSummary[]> {
  if (USE_MOCK) {
    // Mock data for development
    return [
      {
        id: 'med-1',
        medication: 'Lisinopril',
        dosage: '10mg',
        schedule: 'Once daily',
        adherence: 95,
      },
      {
        id: 'med-2',
        medication: 'Metformin',
        dosage: '500mg',
        schedule: 'Twice daily',
        adherence: 88,
      },
      {
        id: 'med-3',
        medication: 'Atorvastatin',
        dosage: '20mg',
        schedule: 'Once daily',
        adherence: 92,
      },
    ];
  }

  const body = await phrFetch(`/medications?patientId=${encodeURIComponent(principalId)}`, { context }) as { items: MedicationSummary[] };
  return body.items;
}

export async function fetchMedicationDetail(
  patientId: string,
  medicationId: string,
  context?: SessionContext,
): Promise<MedicationSummary & { interactions: string[]; warnings: string[]; history: Array<{ date: string; action: string }> }> {
  if (USE_MOCK) {
    const medications = await fetchMedications(patientId, context);
    const medication = medications.find(m => m.id === medicationId);
    if (!medication) {
      throw new PhrApiError('Medication not found', 404, 'Medication');
    }
    return {
      ...medication,
      interactions: ['Avoid grapefruit juice', 'May interact with NSAIDs'],
      warnings: ['Monitor liver function regularly', 'Report muscle pain immediately'],
      history: [
        { date: '2023-01-15', action: 'Prescribed' },
        { date: '2023-06-15', action: 'Refill approved' },
        { date: '2023-12-15', action: 'Refill approved' },
      ],
    };
  }

  const response = await phrFetch(`/medications/${encodeURIComponent(medicationId)}?patientId=${encodeURIComponent(patientId)}`, { context }) as {
    medication: MedicationSummary;
    interactions: string[];
    warnings: string[];
    history: Array<{ date: string; action: string }>;
  };

  return {
    ...response.medication,
    interactions: response.interactions,
    warnings: response.warnings,
    history: response.history,
  };
}

// --- Appointments â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchAppointments(principalId: string, context?: SessionContext): Promise<AppointmentSummary[]> {
  if (USE_MOCK) {
    // Mock data for development
    const now = new Date();
    return [
      {
        id: 'apt-1',
        provider: 'Dr. Sarah Johnson',
        specialty: 'Cardiology',
        location: 'Main Clinic - Room 302',
        startsAt: new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000).toISOString(),
        status: 'confirmed',
        reminderSent: true,
      },
      {
        id: 'apt-2',
        provider: 'Dr. Michael Chen',
        specialty: 'Primary Care',
        location: 'Main Clinic - Room 105',
        startsAt: new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000).toISOString(),
        status: 'completed',
        reminderSent: true,
      },
      {
        id: 'apt-3',
        provider: 'Dr. Emily Davis',
        specialty: 'Dermatology',
        location: 'Main Clinic - Room 210',
        startsAt: new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000).toISOString(),
        status: 'cancelled',
        reminderSent: false,
      },
    ];
  }

  const body = await phrFetch(`/appointments?patientId=${encodeURIComponent(principalId)}`, { context }) as { items: AppointmentSummary[] };
  return body.items;
}

export async function fetchProviders(context?: SessionContext): Promise<Array<{ id: string; name: string; specialty: string; availableSlots: string[] }>> {
  if (USE_MOCK) {
    return [
      {
        id: 'prov-1',
        name: 'Dr. Sarah Johnson',
        specialty: 'Cardiology',
        availableSlots: ['2024-02-01T09:00:00Z', '2024-02-01T10:00:00Z', '2024-02-02T14:00:00Z'],
      },
      {
        id: 'prov-2',
        name: 'Dr. Michael Chen',
        specialty: 'Primary Care',
        availableSlots: ['2024-02-01T11:00:00Z', '2024-02-02T09:00:00Z', '2024-02-03T10:00:00Z'],
      },
      {
        id: 'prov-3',
        name: 'Dr. Emily Davis',
        specialty: 'Dermatology',
        availableSlots: ['2024-02-01T15:00:00Z', '2024-02-02T16:00:00Z'],
      },
    ];
  }

  const body = await phrFetch('/providers', { context }) as { items: Array<{ id: string; name: string; specialty: string; availableSlots: string[] }> };
  return body.items;
}

export async function bookAppointment(
  patientId: string,
  providerId: string,
  slot: string,
  notes?: string,
  context?: SessionContext & { idempotencyKey?: string },
): Promise<{ id: string; status: string }> {
  if (USE_MOCK) {
    return {
      id: `apt-${Date.now()}`,
      status: 'confirmed',
    };
  }

  const data = await phrFetch('/appointments', {
    method: 'POST',
    body: JSON.stringify({ patientId, providerId, slot, notes }),
    context,
  });
  return AppointmentBookingResultSchema.parse(data);
}

export async function cancelAppointment(
  appointmentId: string,
  patientId: string,
  context?: SessionContext & { idempotencyKey?: string },
): Promise<{ success: boolean }> {
  if (USE_MOCK) {
    return { success: true };
  }

  const data = await phrFetch(`/appointments/${encodeURIComponent(appointmentId)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ patientId }),
    context,
  });
  return AppointmentCancelResultSchema.parse(data);
}

export async function rescheduleAppointment(
  appointmentId: string,
  patientId: string,
  newSlot: string,
  context?: SessionContext & { idempotencyKey?: string },
): Promise<{ id: string; status: string }> {
  if (USE_MOCK) {
    return {
      id: appointmentId,
      status: 'confirmed',
    };
  }

  const data = await phrFetch(`/appointments/${encodeURIComponent(appointmentId)}/reschedule`, {
    method: 'POST',
    body: JSON.stringify({ patientId, newSlot }),
    context,
  });
  return AppointmentBookingResultSchema.parse(data);
}

// --- Records â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchRecords(
  patientId: string,
  context?: SessionContext,
  filters?: {
    category?: string;
    resourceType?: string;
    dateFrom?: string;
    dateTo?: string;
  },
): Promise<PatientRecordSummary[]> {
  if (USE_MOCK) {
    const { demoDashboard } = await import('../demoData');
    let records = dashboardSchema.parse(demoDashboard).records;
    
    // Apply client-side filters for mock data
    if (filters) {
      if (filters.category) {
        records = records.filter(r => r.category === filters.category);
      }
      if (filters.resourceType) {
        records = records.filter(r => r.resourceType === filters.resourceType);
      }
      if (filters.dateFrom) {
        records = records.filter(r => new Date(r.updatedAt) >= new Date(filters.dateFrom!));
      }
      if (filters.dateTo) {
        records = records.filter(r => new Date(r.updatedAt) <= new Date(filters.dateTo!));
      }
    }
    
    return records;
  }

  // Fetch FHIR resources that represent patient records
  const [obsBundle, medBundle] = await Promise.all([
    fhirGet('Observation', undefined, context),
    fhirGet('MedicationRequest', undefined, context),
  ]);

  const observations = extractBundleEntries(FhirBundleSchema.parse(obsBundle))
    .map(r => FhirObservationSchema.parse(r));
  const obsRecords = observations.map(fhirObservationToRecord);

  const medications_raw = extractBundleEntries(FhirBundleSchema.parse(medBundle))
    .map(r => FhirMedicationRequestSchema.parse(r));
  const medRecords = medications_raw.map(fhirMedicationRequestToRecord);

  let records = [...obsRecords, ...medRecords];
  
  // Apply filters
  if (filters) {
    if (filters.category) {
      records = records.filter(r => r.category === filters.category);
    }
    if (filters.resourceType) {
      records = records.filter(r => r.resourceType === filters.resourceType);
    }
    if (filters.dateFrom) {
      records = records.filter(r => new Date(r.updatedAt) >= new Date(filters.dateFrom));
    }
    if (filters.dateTo) {
      records = records.filter(r => new Date(r.updatedAt) <= new Date(filters.dateTo));
    }
  }
  
  return records;
}

export async function fetchRecordDetail(
  patientId: string,
  recordId: string,
  context?: SessionContext,
): Promise<{ record: PatientRecordSummary; fhirJson: string; accessAudit: { accessedAt: string; accessedBy: string } }> {
  if (USE_MOCK) {
    const { demoDashboard } = await import('../demoData');
    const record = dashboardSchema.parse(demoDashboard).records.find(r => r.id === recordId);
    if (!record) {
      throw new PhrApiError('Record not found', 404, 'Record');
    }
    return {
      record,
      fhirJson: record.fhirJson,
      accessAudit: {
        accessedAt: new Date().toISOString(),
        accessedBy: context?.principalId ?? 'unknown',
      },
    };
  }

  // Call the backend record-detail endpoint
  const response = await phrFetch(`/patient-records/${encodeURIComponent(patientId)}/records/${encodeURIComponent(recordId)}`, { context }) as {
    patientId: string;
    recordId: string;
    resourceType: string;
    status: string;
    accessedAt: string;
    accessedBy: string;
    accessReason: string;
  };

  // Fetch the actual FHIR resource
  const fhirData = await fhirGet(response.resourceType, recordId, context);
  
  return {
    record: {
      id: recordId,
      title: `${response.resourceType} - ${recordId}`,
      category: 'clinical',
      updatedAt: response.accessedAt,
      resourceType: response.resourceType,
      fhirJson: JSON.stringify(fhirData, null, 2),
    },
    fhirJson: JSON.stringify(fhirData, null, 2),
    accessAudit: {
      accessedAt: response.accessedAt,
      accessedBy: response.accessedBy,
    },
  };
}

export async function fetchOcrDocument(
  docId: string,
  context: { tenantId: string; principalId: string; role: string },
): Promise<OcrReviewDocument> {
  const data = await phrFetch(`/documents/${encodeURIComponent(docId)}/ocr`, { context });
  return OcrReviewDocumentSchema.parse(data);
}

export async function confirmOcrDocument(
  docId: string,
  context: { tenantId: string; principalId: string; role: string; correlationId?: string; idempotencyKey?: string },
  correctedText?: string,
): Promise<OcrReviewDocument> {
  const data = await phrFetch(`/documents/${encodeURIComponent(docId)}/ocr/confirm`, {
    method: 'POST',
    body: correctedText ? JSON.stringify({ correctedText }) : undefined,
    context,
  });
  return OcrReviewDocumentSchema.parse(data);
}

export async function rejectOcrDocument(
  docId: string,
  context: { tenantId: string; principalId: string; role: string; correlationId?: string; idempotencyKey?: string },
): Promise<{ documentId: string; rejected: boolean }> {
  const data = await phrFetch(`/documents/${encodeURIComponent(docId)}/ocr/reject`, {
    method: 'POST',
    context,
  });
  return OcrRejectResultSchema.parse(data);
}

// --- Notifications â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchNotifications(principalId: string, context?: SessionContext): Promise<NotificationSummary[]> {
  const body = await phrFetch(`/notifications?principalId=${encodeURIComponent(principalId)}`, { context }) as { items: NotificationSummary[] };
  return body.items;
}

// --- Provider â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchProviderPatients(
  context: { tenantId: string; principalId: string; role: string },
): Promise<PatientRosterEntry[]> {
  const body = await phrFetch('/provider/patients', { context }) as { items: PatientRosterEntry[] };
  return body.items;
}

// --- Caregiver â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchCaregiverDependents(context?: SessionContext): Promise<DependentEntry[]> {
  const body = await phrFetch('/caregiver/dependents', { context }) as { items: DependentEntry[] };
  return body.items;
}

// --- FCHV â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchFchvDashboard(context?: SessionContext): Promise<FchvPatientEntry[]> {
  const body = await phrFetch('/fchv/dashboard', { context }) as { items: FchvPatientEntry[] };
  return body.items;
}
