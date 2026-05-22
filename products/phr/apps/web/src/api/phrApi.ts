import { z } from 'zod';
import type {
  AppointmentSummary,
  ConsentGrant,
  DashboardData,
  LabResultSummary,
  MedicationSummary,
  PatientProfile,
  PatientRecordSummary,
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
