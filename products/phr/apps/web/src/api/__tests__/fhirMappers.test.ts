import { describe, expect, it } from 'vitest';
import {
  extractBundleEntries,
  FhirBundleSchema,
  FhirConsentSchema,
  FhirMedicationRequestSchema,
  FhirObservationSchema,
  fhirConsentToGrant,
  fhirMedicationRequestToRecord,
  fhirMedicationRequestToSummary,
  fhirObservationToLabResult,
  fhirObservationToRecord,
} from '../fhirMappers';

describe('FHIR mappers', () => {
  it('maps observations into patient records and lab summaries', () => {
    const observation = FhirObservationSchema.parse({
      resourceType: 'Observation',
      id: 'obs-1',
      code: { text: 'Hemoglobin' },
      effectiveDateTime: '2026-05-28T08:00:00Z',
      valueQuantity: { value: 11.8, unit: 'g/dL' },
      interpretation: [{ coding: [{ code: 'H' }] }],
    });

    expect(fhirObservationToRecord(observation)).toMatchObject({
      id: 'obs-1',
      title: 'Hemoglobin',
      category: 'lab',
      resourceType: 'Observation',
    });
    expect(fhirObservationToLabResult(observation)).toEqual({
      id: 'obs-1',
      name: 'Hemoglobin',
      status: 'attention',
      value: '11.8 g/dL',
      collectedAt: '2026-05-28',
    });
  });

  it('maps medication requests into records and medication summaries', () => {
    const medication = FhirMedicationRequestSchema.parse({
      resourceType: 'MedicationRequest',
      id: 'med-1',
      medicationCodeableConcept: { text: 'Metformin 500mg' },
      dosageInstruction: [{ text: 'Twice daily' }],
      authoredOn: '2026-05-27',
    });

    expect(fhirMedicationRequestToRecord(medication)).toMatchObject({
      id: 'med-1',
      title: 'Metformin 500mg prescription',
      category: 'medication',
      resourceType: 'MedicationRequest',
    });
    expect(fhirMedicationRequestToSummary(medication)).toEqual({
      id: 'med-1',
      medication: 'Metformin',
      dosage: '500mg',
      schedule: 'Twice daily',
      adherence: 100,
    });
  });

  it('maps consent status and extracts bundle resources', () => {
    const consent = FhirConsentSchema.parse({
      resourceType: 'Consent',
      id: 'consent-1',
      status: 'inactive',
      organization: [{ display: 'City Clinic' }],
      purpose: [{ display: 'Care coordination' }],
      provision: { period: { end: '2026-06-15T00:00:00Z' } },
    });
    const bundle = FhirBundleSchema.parse({
      resourceType: 'Bundle',
      type: 'searchset',
      entry: [{ resource: consent }],
    });

    expect(fhirConsentToGrant(consent)).toEqual({
      id: 'consent-1',
      recipient: 'City Clinic',
      purpose: 'Care coordination',
      status: 'revoked',
      expiresAt: '2026-06-15',
    });
    expect(extractBundleEntries(bundle)).toEqual([consent]);
  });
});
