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

// â”€â”€â”€ Config â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export const API_BASE_URL: string = import.meta.env.VITE_PHR_API_URL ?? 'http://localhost:8080';
const USE_MOCK: boolean = import.meta.env.VITE_USE_MOCK_DATA === 'true';

// â”€â”€â”€ Error type â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export class PhrApiError extends Error {
  constructor(
    message: string,
    public readonly statusCode: number,
    public readonly resourceType?: string,
  ) {
    super(message);
    this.name = 'PhrApiError';
  }
}

// â”€â”€â”€ Correlation ID helper â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

/** Returns a new RFC 4122 v4 UUID to be sent as the X-Correlation-ID header. */
function newCorrelationId(): string {
  return crypto.randomUUID();
}

// â”€â”€â”€ Shared FHIR sub-schemas â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

// â”€â”€â”€ Per-resource FHIR R4 schemas â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

// â”€â”€â”€ Mock data validation schema (used when USE_MOCK=true) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

// â”€â”€â”€ FHIR â†’ UI type transformations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

// â”€â”€â”€ HTTP helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

type SessionContext = {
  tenantId?: string;
  principalId?: string;
  role?: string;
  correlationId?: string;
};

/**
 * PHR-P1-004: Central request client with consistent authenticated context.
 * All API calls should use this helper to ensure tenant/principal/role headers are injected.
 */
async function phrFetch<T>(
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

// â”€â”€â”€ Public API functions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
  return response.json() as Promise<PhrReleaseReadiness>;
}

// â”€â”€â”€ Audit events API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

// â”€â”€â”€ Consent management API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

export async function createConsentGrant(
  request: ConsentGrantRequest,
  context: { tenantId: string; principalId: string; role: string },
): Promise<ConsentGrant> {
  const validated = ConsentGrantRequestSchema.parse(request);
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
  context: { tenantId: string; principalId: string; role: string },
): Promise<ConsentRevokeResult> {
  if (!grantId || !patientId) {
    throw new PhrApiError('grantId and patientId are required to revoke consent', 400, 'Consent');
  }
  const url = new URL(`${API_BASE_URL}/consents/grants/${encodeURIComponent(grantId)}/revoke`);
  url.searchParams.set('patientId', patientId);
  return phrFetch(`${url.pathname}${url.search}`, {
    method: 'POST',
    context,
  }) as Promise<ConsentRevokeResult>;
}

// â”€â”€â”€ Appointment API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

const AppointmentRequestSchema = z.object({
  specialty: z.string().min(1, 'Specialty is required'),
  preferredDate: z.string().min(1, 'Preferred date is required'),
  notes: z.string().optional(),
}).strict();

export async function createAppointmentRequest(
  request: AppointmentRequest,
  context: { tenantId: string; principalId: string; role: string },
): Promise<AppointmentCreateResult> {
  const validated = AppointmentRequestSchema.parse(request);
  return phrFetch('/appointments', {
    method: 'POST',
    body: JSON.stringify(validated),
    context,
  }) as Promise<AppointmentCreateResult>;
}

// â”€â”€â”€ Emergency access API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

const EmergencyAccessRequestSchema = z.object({
  patientId: z.string().min(1, 'Patient ID is required'),
  reason: z.string().min(5, 'Reason must be at least 5 characters'),
  clinicianId: z.string().min(1, 'Clinician ID is required'),
}).strict();

export async function requestEmergencyAccess(
  request: EmergencyAccessRequest,
  context: { tenantId: string; principalId: string; role: string },
): Promise<EmergencyAccessEvent> {
  const validated = EmergencyAccessRequestSchema.parse(request);
  return phrFetch('/emergency/access', {
    method: 'POST',
    body: JSON.stringify(validated),
    context,
  }) as Promise<EmergencyAccessEvent>;
}

export async function reviewEmergencyAccess(
  review: EmergencyReviewRequest,
  context: { tenantId: string; principalId: string; role: string },
): Promise<EmergencyAccessEvent> {
  return phrFetch(`/emergency/reviews/${encodeURIComponent(review.eventId)}`, {
    method: 'POST',
    body: JSON.stringify({ reviewNote: review.reviewNote, reviewerId: review.reviewerId }),
    context,
  }) as Promise<EmergencyAccessEvent>;
}

// â”€â”€â”€ Auth session API â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

// â”€â”€â”€ Patient profile â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchPatientProfile(context?: SessionContext): Promise<PatientProfileExtended> {
  return phrFetch('/profile', { context }) as Promise<PatientProfileExtended>;
}

export async function updatePatientProfile(
  update: PatientProfileUpdateRequest,
  context: { tenantId: string; principalId: string; correlationId?: string },
): Promise<PatientProfileExtended> {
  return phrFetch('/profile', {
    method: 'PUT',
    body: JSON.stringify(update),
    context,
  }) as Promise<PatientProfileExtended>;
}

// â”€â”€â”€ Timeline â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchTimeline(principalId: string, context?: SessionContext): Promise<TimelineEvent[]> {
  const body = await phrFetch(`/timeline/${encodeURIComponent(principalId)}`, { context }) as { items: TimelineEvent[] };
  return body.items;
}

// â”€â”€â”€ Conditions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchConditions(principalId: string, context?: SessionContext): Promise<ConditionSummary[]> {
  const body = await phrFetch(`/conditions/${encodeURIComponent(principalId)}`, { context }) as { items: ConditionSummary[] };
  return body.items;
}

// â”€â”€â”€ Observations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchObservations(principalId: string, context?: SessionContext): Promise<ObservationSummary[]> {
  const body = await phrFetch(`/clinical/labs/observations?patientId=${encodeURIComponent(principalId)}`, { context }) as { items: ObservationSummary[] };
  return body.items;
}

// â”€â”€â”€ Immunizations â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchImmunizations(principalId: string, context?: SessionContext): Promise<ImmunizationSummary[]> {
  const body = await phrFetch(`/clinical/immunizations?patientId=${encodeURIComponent(principalId)}`, { context }) as { items: ImmunizationSummary[] };
  return body.items;
}

// â”€â”€â”€ Documents â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchDocuments(principalId: string, context?: SessionContext): Promise<DocumentSummary[]> {
  const body = await phrFetch(`/documents?patientId=${encodeURIComponent(principalId)}`, { context }) as { items: DocumentSummary[] };
  return body.items;
}

export async function uploadDocument(
  file: File,
  context?: SessionContext,
): Promise<DocumentUploadResult> {
  const formData = new FormData();
  formData.append('file', file);
  // FormData requires special handling - skip Content-Type header
  const headers: Record<string, string> = {
    'X-Correlation-ID': context?.correlationId ?? newCorrelationId(),
  };
  if (context?.tenantId) headers['X-Tenant-Id'] = context.tenantId;
  if (context?.principalId) headers['X-Principal-Id'] = context.principalId;
  if (context?.role) headers['X-Role'] = context.role;

  const response = await fetch(`${API_BASE_URL}/documents`, {
    method: 'POST',
    headers,
    body: formData,
  });
  if (!response.ok) {
    throw new PhrApiError(`Document upload failed: ${response.status}`, response.status, 'Documents');
  }
  return response.json() as Promise<DocumentUploadResult>;
}

export async function fetchOcrDocument(
  docId: string,
  context: { tenantId: string; principalId: string; role: string },
): Promise<OcrReviewDocument> {
  return phrFetch(`/documents/${encodeURIComponent(docId)}/ocr`, { context }) as Promise<OcrReviewDocument>;
}

export async function confirmOcrDocument(
  docId: string,
  context: { tenantId: string; principalId: string; role: string; correlationId?: string },
  correctedText?: string,
): Promise<OcrReviewDocument> {
  return phrFetch(`/documents/${encodeURIComponent(docId)}/ocr/confirm`, {
    method: 'POST',
    body: correctedText ? JSON.stringify({ correctedText }) : undefined,
    context,
  }) as Promise<OcrReviewDocument>;
}

// â”€â”€â”€ Notifications â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchNotifications(principalId: string, context?: SessionContext): Promise<NotificationSummary[]> {
  const body = await phrFetch(`/notifications?principalId=${encodeURIComponent(principalId)}`, { context }) as { items: NotificationSummary[] };
  return body.items;
}

// â”€â”€â”€ Provider â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchProviderPatients(
  context: { tenantId: string; principalId: string; role: string },
): Promise<PatientRosterEntry[]> {
  const body = await phrFetch('/provider/patients', { context }) as { items: PatientRosterEntry[] };
  return body.items;
}

// â”€â”€â”€ Caregiver â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchCaregiverDependents(context?: SessionContext): Promise<DependentEntry[]> {
  const body = await phrFetch('/caregiver/dependents', { context }) as { items: DependentEntry[] };
  return body.items;
}

// â”€â”€â”€ FCHV â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

export async function fetchFchvDashboard(context?: SessionContext): Promise<FchvPatientEntry[]> {
  const body = await phrFetch('/fchv/dashboard', { context }) as { items: FchvPatientEntry[] };
  return body.items;
}
