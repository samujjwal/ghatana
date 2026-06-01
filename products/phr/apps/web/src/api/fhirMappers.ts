import { z } from 'zod';
import type {
  AppointmentSummary,
  ConsentGrant,
  LabResultSummary,
  MedicationSummary,
  PatientProfile,
  PatientRecordSummary,
} from '../types';

function requireSourceValue(value: string | undefined | null, fieldName: string): string {
  if (value === undefined || value === null || value.trim().length === 0) {
    throw new Error(`FHIR payload is missing required source field: ${fieldName}`);
  }
  return value;
}

const FhirCodingSchema = z.object({
  system: z.string().optional(),
  code: z.string().optional(),
  display: z.string().optional(),
});

const FhirBundleEntrySchema = z.object({
  resource: z.record(z.string(), z.unknown()),
  fullUrl: z.string().optional(),
});

export const FhirBundleSchema = z.object({
  resourceType: z.literal('Bundle'),
  type: z.string(),
  total: z.number().optional(),
  entry: z.array(FhirBundleEntrySchema).optional(),
});

export const FhirPatientSchema = z.object({
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

export const FhirObservationSchema = z.object({
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

export const FhirMedicationRequestSchema = z.object({
  resourceType: z.literal('MedicationRequest'),
  id: z.string(),
  meta: z.object({ lastUpdated: z.string().optional() }).optional(),
  medicationCodeableConcept: z.object({ text: z.string().optional() }).optional(),
  dosageInstruction: z.array(z.object({ text: z.string().optional() })).optional(),
  status: z.string().optional(),
  authoredOn: z.string().optional(),
}).passthrough();

export const FhirConsentSchema = z.object({
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

export const FhirAppointmentSchema = z.object({
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

export function fhirPatientToProfile(raw: z.infer<typeof FhirPatientSchema>): PatientProfile {
  const firstName = raw.name?.[0]?.given?.join(' ') ?? '';
  const lastName = raw.name?.[0]?.family ?? '';
  const name = requireSourceValue(raw.name?.[0]?.text ?? [firstName, lastName].filter(Boolean).join(' '), 'Patient.name');
  const bloodType = requireSourceValue(raw.extension?.find(e => e.url.includes('blood-type'))?.valueString, 'Patient.extension[blood-type]');
  const location = requireSourceValue(raw.address?.[0]?.city, 'Patient.address.city');
  const emergencyContact = requireSourceValue(raw.telecom?.[0]?.value, 'Patient.telecom.value');
  const birthDate = requireSourceValue(raw.birthDate, 'Patient.birthDate');
  const birth = new Date(birthDate);
  if (Number.isNaN(birth.getTime())) {
    throw new Error('FHIR payload has invalid Patient.birthDate');
  }
  const age = Math.floor((Date.now() - birth.getTime()) / (365.25 * 24 * 3600 * 1000));
  return { id: raw.id, name, age, bloodType, location, emergencyContact };
}

export function fhirObservationToRecord(raw: z.infer<typeof FhirObservationSchema>): PatientRecordSummary {
  const title = requireSourceValue(raw.code?.text ?? raw.code?.coding?.[0]?.display, 'Observation.code');
  const updatedAt = requireSourceValue(raw.effectiveDateTime ?? raw.meta?.lastUpdated, 'Observation.effectiveDateTime');
  return {
    id: raw.id,
    title,
    category: 'lab',
    updatedAt,
    resourceType: 'Observation',
    fhirJson: JSON.stringify(raw, null, 2),
  };
}

export function fhirMedicationRequestToRecord(raw: z.infer<typeof FhirMedicationRequestSchema>): PatientRecordSummary {
  const title = requireSourceValue(raw.medicationCodeableConcept?.text, 'MedicationRequest.medicationCodeableConcept.text');
  const updatedAt = requireSourceValue(raw.authoredOn ?? raw.meta?.lastUpdated, 'MedicationRequest.authoredOn');
  return {
    id: raw.id,
    title: `${title} prescription`,
    category: 'medication',
    updatedAt,
    resourceType: 'MedicationRequest',
    fhirJson: JSON.stringify(raw, null, 2),
  };
}

export function fhirObservationToLabResult(raw: z.infer<typeof FhirObservationSchema>): LabResultSummary {
  const name = requireSourceValue(raw.code?.text ?? raw.code?.coding?.[0]?.display, 'Observation.code');
  const valueQty = raw.valueQuantity;
  const value = valueQty
    ? requireSourceValue(`${valueQty.value?.toString() ?? ''} ${valueQty.unit ?? ''}`.trim(), 'Observation.valueQuantity')
    : requireSourceValue(raw.valueString, 'Observation.valueString');
  const interpretationCode = raw.interpretation?.[0]?.coding?.[0]?.code ?? 'N';
  const status: 'normal' | 'attention' = (
    interpretationCode === 'N' || interpretationCode === 'normal'
  ) ? 'normal' : 'attention';
  const collectedAt = requireSourceValue(raw.effectiveDateTime ?? raw.meta?.lastUpdated, 'Observation.effectiveDateTime');
  return {
    id: raw.id,
    name,
    status,
    value,
    collectedAt: collectedAt.split('T')[0] ?? collectedAt,
  };
}

export function fhirMedicationRequestToSummary(raw: z.infer<typeof FhirMedicationRequestSchema>): MedicationSummary {
  const fullName = requireSourceValue(raw.medicationCodeableConcept?.text, 'MedicationRequest.medicationCodeableConcept.text');
  const parts = /^(.*?)\s+(\d+\s*\w+)$/.exec(fullName);
  const medication = parts?.[1] ?? fullName;
  const dosage = parts?.[2] ?? '';
  const schedule = raw.dosageInstruction?.[0]?.text ?? '';
  return { id: raw.id, medication, dosage, schedule };
}

export function fhirConsentToGrant(raw: z.infer<typeof FhirConsentSchema>): ConsentGrant {
  const recipient = requireSourceValue(raw.organization?.[0]?.display, 'Consent.organization.display');
  const purpose = requireSourceValue(raw.purpose?.[0]?.display ?? raw.purpose?.[0]?.code, 'Consent.purpose');
  const expiresAt = requireSourceValue(raw.provision?.period?.end, 'Consent.provision.period.end').split('T')[0] ?? '';
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

export function fhirAppointmentToSummary(raw: z.infer<typeof FhirAppointmentSchema>): AppointmentSummary {
  const provider = requireSourceValue(raw.participant?.find(p => p.actor?.display)?.actor?.display, 'Appointment.participant.actor.display');
  const specialty = requireSourceValue(raw.specialty?.[0]?.text ?? raw.specialty?.[0]?.coding?.[0]?.display, 'Appointment.specialty');
  const location = requireSourceValue(raw.comment, 'Appointment.comment');
  const startsAt = requireSourceValue(raw.start, 'Appointment.start');
  return { id: raw.id, provider, specialty, startsAt, location };
}

export function extractBundleEntries(bundle: z.infer<typeof FhirBundleSchema>): Record<string, unknown>[] {
  return (bundle.entry ?? []).map(e => e.resource);
}
