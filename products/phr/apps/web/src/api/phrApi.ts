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

// ─── Config ────────────────────────────────────────────────────────────────

export const API_BASE_URL: string = import.meta.env.VITE_PHR_API_URL ?? 'http://localhost:8080';
const USE_MOCK: boolean = import.meta.env.VITE_USE_MOCK_DATA === 'true';

// ─── Error type ────────────────────────────────────────────────────────────

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

// ─── Shared FHIR sub-schemas ───────────────────────────────────────────────

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

// ─── Per-resource FHIR R4 schemas ─────────────────────────────────────────

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

// ─── Mock data validation schema (used when USE_MOCK=true) ────────────────

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

// ─── FHIR → UI type transformations ───────────────────────────────────────

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
    : (raw.valueString ?? '—');
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

// ─── HTTP helpers ──────────────────────────────────────────────────────────

async function fhirGet(resourceType: string, id?: string): Promise<unknown> {
  const path = id !== undefined ? `/fhir/${resourceType}/${id}` : `/fhir/${resourceType}`;
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Accept: 'application/fhir+json' },
  });
  if (!response.ok) {
    throw new PhrApiError(
      `FHIR ${resourceType} request failed with status ${response.status}`,
      response.status,
      resourceType,
    );
  }
  return response.json() as Promise<unknown>;
}

function extractBundleEntries(bundle: z.infer<typeof FhirBundleSchema>): Record<string, unknown>[] {
  return (bundle.entry ?? []).map(e => e.resource);
}

// ─── Public API functions ──────────────────────────────────────────────────

export async function fetchDashboardData(): Promise<DashboardData> {
  if (USE_MOCK) {
    const { demoDashboard } = await import('../demoData');
    return dashboardSchema.parse(demoDashboard);
  }

  const [patientData, obsBundle, medBundle, consentBundle, apptBundle] = await Promise.all([
    fhirGet('Patient', 'current'),
    fhirGet('Observation'),
    fhirGet('MedicationRequest'),
    fhirGet('Consent'),
    fhirGet('Appointment'),
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

export async function exportPatientBundle(): Promise<string> {
  if (USE_MOCK) {
    return JSON.stringify({
      status: 'queued',
      message: t('settings.hie.prepared'),
    });
  }

  const response = await fetch(`${API_BASE_URL}/fhir/Patient/current/$export`, {
    method: 'POST',
    headers: { Accept: 'application/json', 'Content-Type': 'application/json' },
  });
  if (!response.ok) {
    throw new PhrApiError(`Patient bundle export failed with status ${response.status}`, response.status);
  }
  return response.text();
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
    },
  });
  if (!response.ok) {
    throw new PhrApiError(`PHR release readiness failed with status ${response.status}`, response.status);
  }
  return response.json() as Promise<PhrReleaseReadiness>;
}

// ─── Audit events API ──────────────────────────────────────────────────────

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
  const tenantId = options.tenantId ?? 'tenant-health-1';
  const principalId = options.principalId ?? 'current';
  const role = options.role ?? 'patient';
  const url = new URL(`${API_BASE_URL}/audit/events`);
  if (options.patientId) url.searchParams.set('patientId', options.patientId);
  if (options.filter && options.filter !== 'all') url.searchParams.set('filter', options.filter);
  url.searchParams.set('page', String(options.page ?? 0));
  url.searchParams.set('pageSize', String(options.pageSize ?? 50));

  const response = await fetch(url.toString(), {
    headers: {
      Accept: 'application/json',
      'X-Tenant-Id': tenantId,
      'X-Principal-Id': principalId,
      'X-Role': role,
    },
  });
  if (!response.ok) {
    throw new PhrApiError(`Audit events request failed with status ${response.status}`, response.status, 'AuditEvent');
  }
  return AuditEventsPageSchema.parse(await response.json());
}

// ─── Consent management API ────────────────────────────────────────────────

const ConsentGrantRequestSchema = z.object({
  patientId: z.string().min(1),
  granteeId: z.string().min(1),
  purpose: z.string().min(1),
  resourceTypes: z.array(z.string()).min(1),
  expiresAt: z.string().min(1),
}).strict();

export async function createConsentGrant(
  request: ConsentGrantRequest,
  context: { tenantId: string; principalId: string; role: string },
): Promise<ConsentGrant> {
  const validated = ConsentGrantRequestSchema.parse(request);
  const response = await fetch(`${API_BASE_URL}/consents/grants`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Tenant-Id': context.tenantId,
      'X-Principal-Id': context.principalId,
      'X-Role': context.role,
    },
    body: JSON.stringify(validated),
  });
  if (!response.ok) {
    throw new PhrApiError(`Create consent grant failed with status ${response.status}`, response.status, 'Consent');
  }
  const raw = await response.json() as unknown;
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
  const response = await fetch(url.toString(), {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'X-Tenant-Id': context.tenantId,
      'X-Principal-Id': context.principalId,
      'X-Role': context.role,
    },
  });
  if (!response.ok) {
    throw new PhrApiError(`Revoke consent grant failed with status ${response.status}`, response.status, 'Consent');
  }
  return response.json() as Promise<ConsentRevokeResult>;
}

// ─── Appointment API ───────────────────────────────────────────────────────

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
  const response = await fetch(`${API_BASE_URL}/appointments`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Tenant-Id': context.tenantId,
      'X-Principal-Id': context.principalId,
      'X-Role': context.role,
    },
    body: JSON.stringify(validated),
  });
  if (!response.ok) {
    throw new PhrApiError(
      `Create appointment request failed with status ${response.status}`,
      response.status,
      'Appointment',
    );
  }
  return response.json() as Promise<AppointmentCreateResult>;
}

// ─── Emergency access API ──────────────────────────────────────────────────

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
  const response = await fetch(`${API_BASE_URL}/emergency/access`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Tenant-Id': context.tenantId,
      'X-Principal-Id': context.principalId,
      'X-Role': context.role,
    },
    body: JSON.stringify(validated),
  });
  if (!response.ok) {
    throw new PhrApiError(
      `Emergency access request failed with status ${response.status}`,
      response.status,
      'EmergencyAccess',
    );
  }
  return response.json() as Promise<EmergencyAccessEvent>;
}

export async function reviewEmergencyAccess(
  review: EmergencyReviewRequest,
  context: { tenantId: string; principalId: string; role: string },
): Promise<EmergencyAccessEvent> {
  const response = await fetch(`${API_BASE_URL}/emergency/reviews/${encodeURIComponent(review.eventId)}`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Tenant-Id': context.tenantId,
      'X-Principal-Id': context.principalId,
      'X-Role': context.role,
    },
    body: JSON.stringify({ reviewNote: review.reviewNote, reviewerId: review.reviewerId }),
  });
  if (!response.ok) {
    throw new PhrApiError(
      `Emergency access review failed with status ${response.status}`,
      response.status,
      'EmergencyAccess',
    );
  }
  return response.json() as Promise<EmergencyAccessEvent>;
}

// ─── Auth session API ──────────────────────────────────────────────────────

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
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ nationalId: request.nationalId, password: request.password }),
  });
  if (!response.ok) {
    if (response.status === 401) {
      throw new PhrApiError(t('login.error.invalidCredentials'), 401);
    }
    throw new PhrApiError(`Login failed with status ${response.status}`, response.status);
  }
  return PhrSessionSchema.parse(await response.json());
}

export async function logoutSession(context: {
  tenantId: string;
  principalId: string;
}): Promise<void> {
  await fetch(`${API_BASE_URL}/auth/logout`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'X-Tenant-Id': context.tenantId,
      'X-Principal-Id': context.principalId,
    },
  });
}

// ─── Patient profile ──────────────────────────────────────────────────────────

export async function fetchPatientProfile(): Promise<PatientProfileExtended> {
  const response = await fetch(`${API_BASE_URL}/profile`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new PhrApiError(`Failed to load profile: ${response.status}`, response.status, 'Profile');
  }
  return response.json() as Promise<PatientProfileExtended>;
}

export async function updatePatientProfile(
  update: PatientProfileUpdateRequest,
  context: { tenantId: string; principalId: string; correlationId?: string },
): Promise<PatientProfileExtended> {
  const response = await fetch(`${API_BASE_URL}/profile`, {
    method: 'PUT',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      'X-Tenant-Id': context.tenantId,
      'X-Principal-Id': context.principalId,
      ...(context.correlationId ? { 'X-Correlation-ID': context.correlationId } : {}),
    },
    body: JSON.stringify(update),
  });
  if (!response.ok) {
    throw new PhrApiError(`Failed to update profile: ${response.status}`, response.status, 'Profile');
  }
  return response.json() as Promise<PatientProfileExtended>;
}

// ─── Timeline ─────────────────────────────────────────────────────────────────

export async function fetchTimeline(principalId: string): Promise<TimelineEvent[]> {
  const response = await fetch(
    `${API_BASE_URL}/timeline/${encodeURIComponent(principalId)}`,
    { headers: { Accept: 'application/json' } },
  );
  if (!response.ok) {
    throw new PhrApiError(`Failed to load timeline: ${response.status}`, response.status, 'Timeline');
  }
  const body = await response.json() as { items: TimelineEvent[] };
  return body.items;
}

// ─── Conditions ───────────────────────────────────────────────────────────────

export async function fetchConditions(principalId: string): Promise<ConditionSummary[]> {
  const response = await fetch(
    `${API_BASE_URL}/conditions/${encodeURIComponent(principalId)}`,
    { headers: { Accept: 'application/json' } },
  );
  if (!response.ok) {
    throw new PhrApiError(`Failed to load conditions: ${response.status}`, response.status, 'Conditions');
  }
  const body = await response.json() as { items: ConditionSummary[] };
  return body.items;
}

// ─── Observations ─────────────────────────────────────────────────────────────

export async function fetchObservations(principalId: string): Promise<ObservationSummary[]> {
  const response = await fetch(
    `${API_BASE_URL}/clinical/labs/observations?patientId=${encodeURIComponent(principalId)}`,
    { headers: { Accept: 'application/json' } },
  );
  if (!response.ok) {
    throw new PhrApiError(`Failed to load observations: ${response.status}`, response.status, 'Observations');
  }
  const body = await response.json() as { items: ObservationSummary[] };
  return body.items;
}

// ─── Immunizations ────────────────────────────────────────────────────────────

export async function fetchImmunizations(principalId: string): Promise<ImmunizationSummary[]> {
  const response = await fetch(
    `${API_BASE_URL}/clinical/immunizations?patientId=${encodeURIComponent(principalId)}`,
    { headers: { Accept: 'application/json' } },
  );
  if (!response.ok) {
    throw new PhrApiError(`Failed to load immunizations: ${response.status}`, response.status, 'Immunizations');
  }
  const body = await response.json() as { items: ImmunizationSummary[] };
  return body.items;
}

// ─── Documents ────────────────────────────────────────────────────────────────

export async function fetchDocuments(): Promise<DocumentSummary[]> {
  const response = await fetch(
    `${API_BASE_URL}/documents`,
    { headers: { Accept: 'application/json' } },
  );
  if (!response.ok) {
    throw new PhrApiError(`Failed to load documents: ${response.status}`, response.status, 'Documents');
  }
  const body = await response.json() as { items: DocumentSummary[] };
  return body.items;
}

export async function uploadDocument(
  file: File,
): Promise<DocumentUploadResult> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await fetch(`${API_BASE_URL}/documents`, {
    method: 'POST',
    body: formData,
  });
  if (!response.ok) {
    throw new PhrApiError(`Document upload failed: ${response.status}`, response.status, 'Documents');
  }
  return response.json() as Promise<DocumentUploadResult>;
}

export async function fetchOcrDocument(docId: string): Promise<OcrReviewDocument> {
  const response = await fetch(
    `${API_BASE_URL}/documents/${encodeURIComponent(docId)}/ocr`,
    { headers: { Accept: 'application/json' } },
  );
  if (!response.ok) {
    throw new PhrApiError(`Failed to load OCR document: ${response.status}`, response.status, 'OCR');
  }
  return response.json() as Promise<OcrReviewDocument>;
}

export async function confirmOcrDocument(
  docId: string,
): Promise<OcrReviewDocument> {
  const response = await fetch(`${API_BASE_URL}/documents/${encodeURIComponent(docId)}/ocr/confirm`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
  });
  if (!response.ok) {
    throw new PhrApiError(`OCR confirmation failed: ${response.status}`, response.status, 'OCR');
  }
  return response.json() as Promise<OcrReviewDocument>;
}

// ─── Notifications ────────────────────────────────────────────────────────────

export async function fetchNotifications(principalId: string): Promise<NotificationSummary[]> {
  const response = await fetch(
    `${API_BASE_URL}/notifications?principalId=${encodeURIComponent(principalId)}`,
    { headers: { Accept: 'application/json' } },
  );
  if (!response.ok) {
    throw new PhrApiError(`Failed to load notifications: ${response.status}`, response.status, 'Notifications');
  }
  const body = await response.json() as { items: NotificationSummary[] };
  return body.items;
}

// ─── Provider ─────────────────────────────────────────────────────────────────

export async function fetchProviderPatients(): Promise<PatientRosterEntry[]> {
  const response = await fetch(`${API_BASE_URL}/provider/patients`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new PhrApiError(`Failed to load patient roster: ${response.status}`, response.status, 'Provider');
  }
  const body = await response.json() as { items: PatientRosterEntry[] };
  return body.items;
}

// ─── Caregiver ────────────────────────────────────────────────────────────────

export async function fetchCaregiverDependents(): Promise<DependentEntry[]> {
  const response = await fetch(`${API_BASE_URL}/caregiver/dependents`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new PhrApiError(`Failed to load dependents: ${response.status}`, response.status, 'Caregiver');
  }
  const body = await response.json() as { items: DependentEntry[] };
  return body.items;
}

// ─── FCHV ─────────────────────────────────────────────────────────────────────

export async function fetchFchvDashboard(): Promise<FchvPatientEntry[]> {
  const response = await fetch(`${API_BASE_URL}/fchv/dashboard`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new PhrApiError(`Failed to load FCHV dashboard: ${response.status}`, response.status, 'FCHV');
  }
  const body = await response.json() as { items: FchvPatientEntry[] };
  return body.items;
}
