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

// --- Error ---

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

// --- Correlation ---

/** Returns a new RFC 4122 v4 UUID to be sent as the X-Correlation-ID header. */
function newCorrelationId(): string {
  return crypto.randomUUID();
}

// --- Shared ---

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

const BackendDashboardSchema = z.object({
  tenantId: z.string(),
  principalId: z.string(),
  role: z.string(),
  correlationId: z.string(),
  profileSummary: z.object({
    name: z.string(),
    email: z.string().nullable().optional(),
    providerId: z.string().nullable().optional(),
    active: z.boolean(),
  }),
  nextAppointment: z.unknown().nullable(),
  medications: z.object({
    activeCount: z.number(),
    adherenceAlert: z.boolean(),
  }),
  recentObservations: z.object({
    count: z.number(),
    hasCritical: z.boolean(),
  }),
  activeConditions: z.object({
    count: z.number(),
    hasChronic: z.boolean(),
  }),
  documents: z.object({
    totalCount: z.number(),
    pendingOcr: z.number(),
  }),
  accessAlerts: z.object({
    expiringConsents: z.number(),
    emergencyAccessPending: z.boolean(),
  }),
  generatedAt: z.string(),
});

// --- FHIR ---

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
    : (raw.valueString ?? '-');
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

// --- HTTP ---

type SessionContext = {
  tenantId?: string;
  principalId?: string;
  role?: string;
  persona?: string;
  tier?: string;
  correlationId?: string;
  idempotencyKey?: string;
};

function withIdempotency<T extends SessionContext>(context: T): T & { idempotencyKey: string } {
  return {
    ...context,
    idempotencyKey: context.idempotencyKey ?? newCorrelationId(),
  };
}

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

  const headers = buildPhrHeaders(context);
  headers.Accept = accept;
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
      undefined,
      headers['X-Correlation-ID'],
    );
  }

  const data = await response.json() as unknown;
  return expectedSchema ? expectedSchema.parse(data) : (data as T);
}

async function fhirGet(resourceType: string, id: string | undefined, context: SessionContext): Promise<unknown> {
  const path = id !== undefined ? `/fhir/${resourceType}/${id}` : `/fhir/${resourceType}`;
  return phrFetch(path, {
    accept: 'application/fhir+json',
    context,
  });
}

function extractBundleEntries(bundle: z.infer<typeof FhirBundleSchema>): Record<string, unknown>[] {
  return (bundle.entry ?? []).map(e => e.resource);
}

// --- Public ---

export async function fetchDashboardData(context: SessionContext): Promise<DashboardData> {
  const dashboard = BackendDashboardSchema.parse(await phrFetch('/dashboard', { context }));
  return dashboardSchema.parse({
    patient: {
      id: dashboard.principalId,
      name: dashboard.profileSummary.name,
      age: 0,
      bloodType: t('common.unknown'),
      location: dashboard.tenantId,
      emergencyContact: t('common.unknown'),
    },
    records: [],
    consents: [],
    appointments: [],
    labs: [],
    medications: Array.from({ length: dashboard.medications.activeCount }, (_, index) => ({
      id: `active-medication-${index + 1}`,
      medication: t('dashboard.medications.active'),
      dosage: '',
      schedule: '',
      adherence: dashboard.medications.adherenceAlert ? 0 : 100,
    })),
  });
}

export async function exportPatientBundle(context: SessionContext): Promise<string> {
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

// --- Audit ---

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
  contentType: z.string().optional(),
  uploadedAt: z.string(),
  status: z.enum(['pending', 'processing', 'ready', 'failed']).optional(),
  ocrStatus: z.enum(['pending', 'processing', 'ready', 'failed']).optional(),
}).passthrough();

const DocumentDetailSchema = DocumentSummarySchema.extend({
  uploadedBy: z.string(),
  description: z.string().optional(),
  versions: z.array(z.object({
    versionId: z.string(),
    versionNumber: z.number(),
    createdAt: z.string(),
    createdBy: z.string(),
    changeNote: z.string().optional(),
  }).passthrough()).optional(),
  auditLog: z.array(z.object({
    id: z.string(),
    action: z.string(),
    timestamp: z.string(),
    performedBy: z.string(),
    details: z.record(z.string(), z.unknown()).optional(),
  }).passthrough()).optional(),
}).passthrough();

const MedicationSummarySchema = z.object({
  id: z.string(),
  medication: z.string(),
  dosage: z.string(),
  schedule: z.string(),
  adherence: z.number(),
}).passthrough();

const MedicationDetailSchema = MedicationSummarySchema.extend({
  interactions: z.array(z.string()),
  warnings: z.array(z.string()),
  history: z.array(z.object({
    date: z.string(),
    action: z.string(),
  })),
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
  type: z.enum(['consent_expiry', 'appointment_reminder', 'lab_result', 'emergency_access', 'system']),
  title: z.string(),
  body: z.string(),
  createdAt: z.string(),
  readAt: z.string().optional(),
}).passthrough();

const DependentEntrySchema = z.object({
  id: z.string(),
  name: z.string(),
  relationship: z.string(),
  alertCount: z.number(),
  birthDate: z.string().optional(),
  age: z.number().optional(),
}).passthrough();

const PatientRosterEntrySchema = z.object({
  id: z.string(),
  name: z.string(),
  mrn: z.string(),
  alertCount: z.number(),
  lastVisit: z.string().optional(),
  condition: z.string().optional(),
  age: z.number().optional(),
  status: z.string().optional(),
  nextAppointment: z.string().optional(),
}).passthrough();

const FchvPatientEntrySchema = z.object({
  id: z.string(),
  name: z.string(),
  village: z.string(),
  riskLevel: z.enum(['low', 'medium', 'high']),
  pendingActions: z.number(),
  lastContact: z.string().optional(),
}).passthrough();

const ProviderAvailabilitySchema = z.object({
  id: z.string(),
  name: z.string(),
  specialty: z.string(),
  availableSlots: z.array(z.string()),
});

const DownloadDocumentResponseSchema = z.object({
  downloadUrl: z.string(),
  expiresAt: z.string(),
});

const TimelineEventWireSchema = z.object({
  id: z.string(),
  occurredAt: z.string(),
  eventType: z.string(),
  title: z.string(),
  description: z.string(),
  details: z.record(z.string(), z.unknown()),
});

const TimelinePageSchema = z.object({
  patientId: z.string(),
  items: z.array(TimelineEventWireSchema),
  count: z.number(),
  generatedAt: z.string(),
});

const PatientRecordAccessSchema = z.object({
  patientId: z.string(),
  recordId: z.string(),
  resourceType: z.string(),
  status: z.string(),
  accessedAt: z.string(),
  accessedBy: z.string(),
  accessReason: z.string(),
});

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
  sections: z.record(z.string(), z.object({
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

// --- Consent ---

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
    context: withIdempotency(context),
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
    context: withIdempotency(context),
  });
  return ConsentRevokeResultSchema.parse(data);
}

// --- Appointment ---

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
    context: withIdempotency(context),
  });
  return AppointmentCreateResultSchema.parse(data);
}

// --- Emergency ---

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
    context: withIdempotency(context),
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
    context: withIdempotency(context),
  });
  return EmergencyAccessEventSchema.parse(data);
}

// --- Auth ---

const PhrSessionSchema = z.object({
  principalId: z.string(),
  tenantId: z.string(),
  role: z.enum(['patient', 'caregiver', 'clinician', 'admin', 'fchv']),
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

// --- Patient ---

export async function fetchPatientProfile(context: SessionContext): Promise<PatientProfileExtended> {
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
    context: withIdempotency(context),
  });
  return PatientProfileExtendedSchema.parse(data);
}

// --- Timeline ---

export async function fetchTimeline(
  principalId: string,
  context: SessionContext,
  filters?: {
    category?: string;
  },
): Promise<TimelineEvent[]> {
  const path = filters?.category
    ? `/timeline/${encodeURIComponent(principalId)}/category/${encodeURIComponent(filters.category)}`
    : `/timeline/${encodeURIComponent(principalId)}`;

  const body = TimelinePageSchema.parse(await phrFetch(path, { context }));

  return body.items.map(item => {
    const type = toTimelineEventType(item.eventType);
    return {
      id: item.id,
      date: item.occurredAt.slice(0, 10),
      type,
      title: item.title,
      summary: item.description,
      occurredAt: item.occurredAt,
      description: item.description,
    };
  });
}

function toTimelineEventType(eventType: string): TimelineEvent['type'] {
  if (
    eventType === 'visit'
    || eventType === 'lab'
    || eventType === 'immunization'
    || eventType === 'medication'
    || eventType === 'consent'
    || eventType === 'document'
  ) {
    return eventType;
  }
  return 'document';
}

// --- Conditions ---

export async function fetchConditions(principalId: string, context: SessionContext): Promise<ConditionSummary[]> {
  const body = z.object({ items: z.array(ConditionSummarySchema) })
    .parse(await phrFetch(`/conditions/${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

export async function fetchConditionDetail(conditionId: string, principalId: string, context: SessionContext): Promise<ConditionSummary> {
  return ConditionSummarySchema.parse(
    await phrFetch(`/conditions/${encodeURIComponent(conditionId)}?patientId=${encodeURIComponent(principalId)}`, { context }),
  );
}

// --- Observations ---

export async function fetchObservations(principalId: string, context: SessionContext): Promise<ObservationSummary[]> {
  const body = z.object({ items: z.array(ObservationSummarySchema) })
    .parse(await phrFetch(`/clinical/labs/observations?patientId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

export async function fetchObservationDetail(observationId: string, principalId: string, context: SessionContext): Promise<ObservationSummary> {
  return ObservationSummarySchema.parse(
    await phrFetch(`/clinical/labs/observations/${encodeURIComponent(observationId)}?patientId=${encodeURIComponent(principalId)}`, { context }),
  );
}

// --- Immunizations ---

export async function fetchImmunizations(principalId: string, context: SessionContext): Promise<ImmunizationSummary[]> {
  const body = z.object({ items: z.array(ImmunizationSummarySchema) })
    .parse(await phrFetch(`/clinical/immunizations?patientId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

// --- Documents ---

export async function fetchDocuments(principalId: string, context: SessionContext): Promise<DocumentSummary[]> {
  const body = z.object({ items: z.array(DocumentSummarySchema) })
    .parse(await phrFetch(`/documents?patientId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

export async function fetchDocumentDetail(documentId: string, principalId: string, context: SessionContext): Promise<DocumentDetail> {
  return DocumentDetailSchema.parse(
    await phrFetch(`/documents/${encodeURIComponent(documentId)}?patientId=${encodeURIComponent(principalId)}`, { context }),
  );
}

/**
 * A-006: Secure document download with audit trail.
 * Returns a download URL with proper audit logging instead of direct blob.
 */
export async function downloadDocument(
  documentId: string,
  patientId: string,
  context: SessionContext,
): Promise<{ downloadUrl: string; expiresAt: string }> {
  return DownloadDocumentResponseSchema.parse(await phrFetch(`/documents/${encodeURIComponent(documentId)}/download?patientId=${encodeURIComponent(patientId)}`, {
    method: 'POST',
    context,
  }));
}

export async function uploadDocument(
  patientId: string,
  file: File,
  metadata: { title: string; category?: string; description?: string },
  context: SessionContext & { idempotencyKey?: string },
): Promise<{ id: string; status: string; ocrStatus: string }> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('title', metadata.title);
  if (metadata.category) formData.append('category', metadata.category);
  if (metadata.description) formData.append('description', metadata.description);

  const data = await phrFetch(`/documents?patientId=${encodeURIComponent(patientId)}`, {
    method: 'POST',
    body: formData,
    context: withIdempotency(context),
  });
  return DocumentUploadInitResultSchema.parse(data);
}

// --- Medications ---

export async function fetchMedications(principalId: string, context: SessionContext): Promise<MedicationSummary[]> {
  const body = z.object({ items: z.array(MedicationSummarySchema) })
    .parse(await phrFetch(`/medications?patientId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

export async function fetchMedicationDetail(
  patientId: string,
  medicationId: string,
  context: SessionContext,
): Promise<MedicationSummary & { interactions: string[]; warnings: string[]; history: Array<{ date: string; action: string }> }> {
  const response = z.object({
    medication: MedicationSummarySchema,
    interactions: z.array(z.string()),
    warnings: z.array(z.string()),
    history: z.array(z.object({ date: z.string(), action: z.string() })),
  }).parse(await phrFetch(`/medications/${encodeURIComponent(medicationId)}?patientId=${encodeURIComponent(patientId)}`, { context }));

  return MedicationDetailSchema.parse({
    ...response.medication,
    interactions: response.interactions,
    warnings: response.warnings,
    history: response.history,
  });
}

// --- Appointments ---

export async function fetchAppointments(principalId: string, context: SessionContext): Promise<AppointmentSummary[]> {
  const body = z.object({ items: z.array(AppointmentSummarySchema) })
    .parse(await phrFetch(`/appointments?patientId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

export async function fetchProviders(context: SessionContext): Promise<Array<{ id: string; name: string; specialty: string; availableSlots: string[] }>> {
  const body = z.object({ items: z.array(ProviderAvailabilitySchema) })
    .parse(await phrFetch('/providers', { context }));
  return body.items;
}

export async function bookAppointment(
  patientId: string,
  providerId: string,
  slot: string,
  notes: string | undefined,
  context: SessionContext & { idempotencyKey?: string },
): Promise<{ id: string; status: string }> {
  const data = await phrFetch('/appointments', {
    method: 'POST',
    body: JSON.stringify({ patientId, providerId, slot, notes }),
    context: withIdempotency(context),
  });
  return AppointmentBookingResultSchema.parse(data);
}

export async function cancelAppointment(
  appointmentId: string,
  patientId: string,
  context: SessionContext & { idempotencyKey?: string },
): Promise<{ success: boolean }> {
  const data = await phrFetch(`/appointments/${encodeURIComponent(appointmentId)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ patientId }),
    context: withIdempotency(context),
  });
  return AppointmentCancelResultSchema.parse(data);
}

export async function rescheduleAppointment(
  appointmentId: string,
  patientId: string,
  newSlot: string,
  context: SessionContext & { idempotencyKey?: string },
): Promise<{ id: string; status: string }> {
  const data = await phrFetch(`/appointments/${encodeURIComponent(appointmentId)}/reschedule`, {
    method: 'POST',
    body: JSON.stringify({ patientId, newSlot }),
    context: withIdempotency(context),
  });
  return AppointmentBookingResultSchema.parse(data);
}

// --- Records ---

export async function fetchRecords(
  patientId: string,
  context: SessionContext,
  filters?: {
    category?: string;
    resourceType?: string;
    dateFrom?: string;
    dateTo?: string;
  },
): Promise<PatientRecordSummary[]> {
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
	      const dateFrom = filters.dateFrom;
	      records = records.filter(r => new Date(r.updatedAt) >= new Date(dateFrom));
	    }
	    if (filters.dateTo) {
	      const dateTo = filters.dateTo;
	      records = records.filter(r => new Date(r.updatedAt) <= new Date(dateTo));
	    }
  }
  
  return records;
}

export async function fetchRecordDetail(
  patientId: string,
  recordId: string,
  context: SessionContext,
): Promise<{ record: PatientRecordSummary; fhirJson: string; accessAudit: { accessedAt: string; accessedBy: string } }> {
  const response = PatientRecordAccessSchema.parse(
    await phrFetch(`/patient-records/${encodeURIComponent(patientId)}/records/${encodeURIComponent(recordId)}`, { context }),
  );

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
    context: withIdempotency(context),
  });
  return OcrReviewDocumentSchema.parse(data);
}

export async function rejectOcrDocument(
  docId: string,
  context: { tenantId: string; principalId: string; role: string; correlationId?: string; idempotencyKey?: string },
): Promise<{ documentId: string; rejected: boolean }> {
  const data = await phrFetch(`/documents/${encodeURIComponent(docId)}/ocr/reject`, {
    method: 'POST',
    context: withIdempotency(context),
  });
  return OcrRejectResultSchema.parse(data);
}

// --- Notifications ---

export async function fetchNotifications(principalId: string, context: SessionContext): Promise<NotificationSummary[]> {
  const body = z.object({ items: z.array(NotificationSummarySchema) })
    .parse(await phrFetch(`/notifications?principalId=${encodeURIComponent(principalId)}`, { context }));
  return body.items;
}

// --- Provider ---

export async function fetchProviderPatients(
  context: { tenantId: string; principalId: string; role: string },
): Promise<PatientRosterEntry[]> {
  const body = z.object({ items: z.array(PatientRosterEntrySchema) })
    .parse(await phrFetch('/provider/patients', { context }));
  return body.items;
}

// --- Caregiver ---

export async function fetchCaregiverDependents(context: SessionContext): Promise<DependentEntry[]> {
  const body = z.object({ items: z.array(DependentEntrySchema) })
    .parse(await phrFetch('/caregiver/dependents', { context }));
  return body.items;
}

// --- FCHV ---

export async function fetchFchvDashboard(context: SessionContext): Promise<FchvPatientEntry[]> {
  const body = z.object({ items: z.array(FchvPatientEntrySchema) })
    .parse(await phrFetch('/fchv/dashboard', { context }));
  return body.items;
}

